"""Детерминированное сопоставление требований вакансии с признаками резюме.

Это заглушка контракта, а не модель скоринга: числа синтетические, но правило
одно и то же для любого входа. Цитаты берутся только из переданного текста.
"""

from __future__ import annotations

import re
from dataclasses import dataclass

from scoring_service.v1.models.features import common_pb2

from .dictionary import canonical_skill, normalize
from .extraction import (
    Evidence,
    ParsedEducation,
    ParsedExperience,
    ParsedResume,
    ParsedSkill,
    total_experience_years,
)

FULL = common_pb2.CORRESPONDENCE_FULL
PARTIAL = common_pb2.CORRESPONDENCE_PARTIAL
ABSENT = common_pb2.CORRESPONDENCE_ABSENT
NOT_STATED = common_pb2.CORRESPONDENCE_NOT_STATED

GAP_NOT_STATED = common_pb2.GAP_REASON_NOT_STATED
GAP_ABSENT = common_pb2.GAP_REASON_ABSENT
GAP_PARTIAL = common_pb2.GAP_REASON_PARTIAL
GAP_INSUFFICIENT_TENURE = common_pb2.GAP_REASON_INSUFFICIENT_TENURE

_CRITICALITY_WEIGHTS = {
    common_pb2.FEATURE_CRITICALITY_UNSPECIFIED: 1,
    common_pb2.FEATURE_CRITICALITY_OPTIONAL: 1,
    common_pb2.FEATURE_CRITICALITY_NICE: 2,
    common_pb2.FEATURE_CRITICALITY_REQUIRED: 3,
    common_pb2.FEATURE_CRITICALITY_CRITICAL: 4,
}

ABSENCE_PATTERNS = (
    r"не работал[а]?\s+с\s+{name}",
    r"нет\s+опыта\s+(?:с\s+|по\s+)?{name}",
    r"без\s+опыта\s+(?:с\s+|по\s+)?{name}",
    r"не\s+знаком[а]?\s+с\s+{name}",
)

MatchedFeature = ParsedSkill | ParsedEducation | ParsedExperience


@dataclass(frozen=True, slots=True)
class MatchOutcome:
    requirement_id: str
    status: int
    relevance: float
    coverage: float
    gap_reason: int | None
    comment: str
    evidence: Evidence | None
    resume_feature_key: str
    years_found: float | None
    matched: MatchedFeature | None


@dataclass(frozen=True, slots=True)
class Evaluation:
    relevance: float
    score: float
    score_low: float
    score_high: float
    tenure: int | None
    outcomes: tuple[MatchOutcome, ...]
    strengths: tuple[str, ...]
    risks: tuple[str, ...]
    questions: tuple[str, ...]
    recruiter_note: str


def evaluate(parsed: ParsedResume, text: str, vacancy: object, year: int) -> Evaluation:
    """Оценивает резюме по вакансии. Один вход — один результат."""
    lower_text = text.lower()
    outcomes = tuple(
        _match(requirement_id, requirement, parsed, text, lower_text, year)
        for requirement_id, requirement in vacancy.requirements.items()
    )

    weights = [
        _CRITICALITY_WEIGHTS.get(requirement.criticality, 1)
        for requirement in vacancy.requirements.values()
    ]
    total_weight = sum(weights)

    if outcomes and total_weight > 0:
        relevance = round(
            sum(w * o.relevance for w, o in zip(weights, outcomes, strict=True)) / total_weight, 4
        )
        coverage = (
            sum(w * o.coverage for w, o in zip(weights, outcomes, strict=True)) / total_weight
        )
        score = round(coverage, 4)
        score_low = round(max(0.0, coverage - 0.05), 4)
        score_high = round(min(1.0, coverage + 0.05), 4)
    else:
        relevance = score = score_low = score_high = 0.0

    tenure_years = total_experience_years(parsed, year)
    full = [o for o in outcomes if o.status == FULL]
    partial = [o for o in outcomes if o.status == PARTIAL]
    absent = [o for o in outcomes if o.status == ABSENT]
    not_stated = [o for o in outcomes if o.status == NOT_STATED]

    risks: list[str] = []
    if partial:
        risks.append(f"Частичные подтверждения: {len(partial)}")
    if absent:
        risks.append(f"Подтверждено отсутствие: {len(absent)}")
    if not_stated:
        risks.append(f"Нет сведений в резюме: {len(not_stated)}")

    questions = tuple(
        f"Уточните: {_label(requirement)}"
        for requirement_id, requirement in vacancy.requirements.items()
        if any(
            o.requirement_id == requirement_id
            and (o.status == NOT_STATED or o.gap_reason == GAP_INSUFFICIENT_TENURE)
            for o in outcomes
        )
    )[:5]

    strengths = tuple(
        _label(requirement)
        for requirement_id, requirement in vacancy.requirements.items()
        if any(o.requirement_id == requirement_id and o.status == FULL for o in outcomes)
    )[:5]

    note = (
        f"Полное соответствие: {len(full)} из {len(outcomes)}; "
        f"частично: {len(partial)}; нет сведений: {len(not_stated)}; "
        f"подтверждено отсутствие: {len(absent)}."
    )

    return Evaluation(
        relevance=relevance,
        score=score,
        score_low=score_low,
        score_high=score_high,
        tenure=int(tenure_years) if tenure_years is not None else None,
        outcomes=outcomes,
        strengths=strengths,
        risks=tuple(risks),
        questions=questions,
        recruiter_note=note,
    )


def _match(
    requirement_id: str,
    requirement: object,
    parsed: ParsedResume,
    text: str,
    lower_text: str,
    year: int,
) -> MatchOutcome:
    kind = requirement.WhichOneof("feature")
    if kind == "skill":
        return _match_skill(requirement_id, requirement.skill, parsed, lower_text)
    if kind == "education":
        return _match_education(requirement_id, requirement.education, parsed)
    if kind == "experience":
        return _match_experience(requirement_id, requirement.experience, parsed, year)
    if kind == "text_requirement":
        return _match_text(requirement_id, requirement.text_requirement, text, lower_text)
    return _outcome(requirement_id, NOT_STATED, GAP_NOT_STATED, "Требование без структуры")


def _match_skill(
    requirement_id: str,
    requirement: object,
    parsed: ParsedResume,
    lower_text: str,
) -> MatchOutcome:
    if _explicit_absence(lower_text, requirement.name):
        return _outcome(
            requirement_id,
            ABSENT,
            GAP_ABSENT,
            "В резюме указано отсутствие опыта по навыку",
        )

    target = canonical_skill(requirement.normalized_name) or canonical_skill(requirement.name)
    target = target or normalize(requirement.name)
    candidate = next(
        (skill for skill in parsed.skills if normalize(skill.normalized_name) == normalize(target)),
        None,
    )

    if candidate is None:
        return _outcome(
            requirement_id,
            NOT_STATED,
            GAP_NOT_STATED,
            "Навык не найден в обезличенном тексте",
        )

    required = int(requirement.tenure or 0)
    if required <= 0:
        coverage, status, gap, comment = (
            1.0,
            FULL,
            None,
            "Навык подтверждён фрагментом резюме",
        )
    elif candidate.tenure is None:
        coverage, status, gap, comment = (
            0.5,
            PARTIAL,
            GAP_PARTIAL,
            "Навык подтверждён, стаж не указан",
        )
    elif candidate.tenure < required:
        coverage, status, gap, comment = (
            0.5,
            PARTIAL,
            GAP_INSUFFICIENT_TENURE,
            f"Навык подтверждён, стаж {candidate.tenure} лет меньше требуемых {required}",
        )
    else:
        coverage, status, gap, comment = (
            1.0,
            FULL,
            None,
            f"Навык подтверждён, стаж {candidate.tenure} лет",
        )

    return MatchOutcome(
        requirement_id=requirement_id,
        status=status,
        relevance=1.0 if status == FULL else 0.5,
        coverage=coverage,
        gap_reason=gap,
        comment=comment,
        evidence=candidate.evidence,
        resume_feature_key=candidate.key,
        years_found=float(candidate.tenure) if candidate.tenure is not None else None,
        matched=candidate,
    )


def _match_education(
    requirement_id: str,
    requirement: object,
    parsed: ParsedResume,
) -> MatchOutcome:
    required = int(requirement.level or 0)
    candidate = max(parsed.education, key=lambda item: item.level) if parsed.education else None
    if candidate is None:
        return _outcome(
            requirement_id,
            NOT_STATED,
            GAP_NOT_STATED,
            "Образование не найдено в обезличенном тексте",
        )
    if required <= 0 or candidate.level >= required:
        return MatchOutcome(
            requirement_id=requirement_id,
            status=FULL,
            relevance=1.0,
            coverage=1.0,
            gap_reason=None,
            comment="Образование подтверждено фрагментом резюме",
            evidence=candidate.evidence,
            resume_feature_key=candidate.key,
            years_found=None,
            matched=candidate,
        )
    return MatchOutcome(
        requirement_id=requirement_id,
        status=PARTIAL,
        relevance=0.5,
        coverage=0.5,
        gap_reason=GAP_PARTIAL,
        comment="Уровень образования ниже требуемого",
        evidence=candidate.evidence,
        resume_feature_key=candidate.key,
        years_found=None,
        matched=candidate,
    )


def _match_experience(
    requirement_id: str,
    requirement: object,
    parsed: ParsedResume,
    year: int,
) -> MatchOutcome:
    required = int(requirement.tenure or 0)
    found = total_experience_years(parsed, year)
    if found is None:
        return _outcome(
            requirement_id,
            NOT_STATED,
            GAP_NOT_STATED,
            "Опыт работы не найден в обезличенном тексте",
        )
    candidate = parsed.experience[0]
    if found >= required:
        return MatchOutcome(
            requirement_id=requirement_id,
            status=FULL,
            relevance=1.0,
            coverage=1.0,
            gap_reason=None,
            comment=f"Стаж подтверждён: {found:g} лет",
            evidence=candidate.evidence,
            resume_feature_key=candidate.key,
            years_found=found,
            matched=candidate,
        )
    return MatchOutcome(
        requirement_id=requirement_id,
        status=PARTIAL,
        relevance=0.5,
        coverage=0.5,
        gap_reason=GAP_INSUFFICIENT_TENURE,
        comment=f"Стаж {found:g} лет меньше требуемых {required}",
        evidence=candidate.evidence,
        resume_feature_key=candidate.key,
        years_found=found,
        matched=candidate,
    )


def _match_text(
    requirement_id: str,
    requirement: object,
    text: str,
    lower_text: str,
) -> MatchOutcome:
    required_text = normalize(getattr(requirement, "text", ""))
    tokens = [token for token in re.findall(r"[a-zа-я0-9+#.]{5,}", required_text)][:8]
    for token in tokens:
        index = lower_text.find(token)
        if index >= 0:
            evidence = Evidence(
                start=index,
                end=index + len(token),
                text=text[index : index + len(token)],
            )
            return MatchOutcome(
                requirement_id=requirement_id,
                status=PARTIAL,
                relevance=0.6,
                coverage=0.6,
                gap_reason=GAP_PARTIAL,
                comment="Требование частично подтверждено фрагментом резюме",
                evidence=evidence,
                resume_feature_key="",
                years_found=None,
                matched=None,
            )
    return _outcome(
        requirement_id,
        NOT_STATED,
        GAP_NOT_STATED,
        "В резюме нет сведений по текстовому требованию",
    )


def _outcome(
    requirement_id: str,
    status: int,
    gap_reason: int | None,
    comment: str,
) -> MatchOutcome:
    return MatchOutcome(
        requirement_id=requirement_id,
        status=status,
        relevance=0.0,
        coverage=0.0,
        gap_reason=gap_reason,
        comment=comment,
        evidence=None,
        resume_feature_key="",
        years_found=None,
        matched=None,
    )


def _explicit_absence(lower_text: str, name: str) -> bool:
    target = re.escape(normalize(name))
    if not target:
        return False
    return any(re.search(pattern.format(name=target), lower_text) for pattern in ABSENCE_PATTERNS)


def _label(requirement: object) -> str:
    kind = requirement.WhichOneof("feature")
    if kind == "skill":
        return requirement.skill.name.strip() or "навык"
    if kind == "text_requirement":
        return requirement.text_requirement.text.strip() or "требование"
    if kind == "education":
        return requirement.education.master.strip() or "образование"
    if kind == "experience":
        return requirement.experience.category.strip() or "опыт"
    return "требование"
