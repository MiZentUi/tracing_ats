"""Сопоставление требований вакансии с признаками резюме.

Здесь нет арифметики агрегатов: модуль выбирает лучшее подтверждение,
проверяет цитату и возвращает `RequirementScore` с `R_i`, `c_i` и границами.
"""

from __future__ import annotations

from datetime import date

from scoring_service.v1.models import features_pb2, messages_pb2
from scoring_service.v1.models.features import (
    common_pb2,
    education_pb2,
    experience_pb2,
    skill_pb2,
)

from ..extraction.quotes import QuoteIndex
from .dates import experience_years
from .errors import InvalidRequirement
from .model import RequirementScore
from .policies import ClaimConfidence, TemporalPolicyProtocol
from .resolver import BatchResolution, SkillResolver
from .similarity import SimilarityScorer
from .text import TextMatcher

_NOT_STATED_HIGH = 1.0


class RequirementMatcher:
    """Сопоставляет одно требование с уже извлечённым резюме."""

    def __init__(
        self,
        similarity: SimilarityScorer,
        temporal: TemporalPolicyProtocol,
        confidence: ClaimConfidence,
        text_matcher: TextMatcher,
        resolver: SkillResolver,
    ) -> None:
        self._similarity = similarity
        self._temporal = temporal
        self._confidence = confidence
        self._text = text_matcher
        self._resolver = resolver

    def prepare(
        self,
        vacancy: messages_pb2.Vacancy,
        resume: messages_pb2.ParsedResume,
    ) -> BatchResolution:
        """Один batch-Resolve: требования вакансии и навыки резюме."""
        names: list[tuple[str, str]] = []
        for requirement in vacancy.requirements.values():
            if requirement.WhichOneof("feature") == "skill":
                spec = requirement.skill
                names.append((spec.normalized_name or spec.name, "VACANCY"))
        for skill in resume.skills.values():
            names.append((skill.normalized_name or skill.name, "RESUME"))
        return self._resolver.resolve(names)

    def match(
        self,
        requirement_id: str,
        requirement: features_pb2.FeatureRequirement,
        resume: messages_pb2.ParsedResume,
        index: QuoteIndex,
        reference_date: date,
        resolution: BatchResolution,
    ) -> RequirementScore:
        kind = requirement.WhichOneof("feature")
        if kind is None:
            raise InvalidRequirement(requirement_id)
        if kind == "skill":
            return self._match_skill(requirement_id, requirement, resume, index, resolution)
        if kind == "education":
            return self._match_education(requirement_id, requirement, resume, index)
        if kind == "experience":
            return self._match_experience(
                requirement_id,
                requirement,
                resume,
                index,
                reference_date,
            )
        if kind == "text_requirement":
            return self._match_text(requirement_id, requirement, index)
        raise InvalidRequirement(requirement_id)

    def _match_skill(
        self,
        requirement_id: str,
        requirement: features_pb2.FeatureRequirement,
        resume: messages_pb2.ParsedResume,
        index: QuoteIndex,
        resolution: BatchResolution,
    ) -> RequirementScore:
        spec = requirement.skill
        target = spec.normalized_name or spec.name
        target_identity = resolution.identity(target)
        best: tuple[float, str, skill_pb2.SkillPerResume, common_pb2.FeatureEvidence] | None = None

        for key, skill in resume.skills.items():
            evidence = _verified_evidence(index, skill.evidence)
            if evidence is None:
                continue
            score = self._resolver.score(
                target_identity,
                resolution.identity(skill.normalized_name or skill.name),
            )
            if best is None or (score, key) > (best[0], best[1]):
                best = (score, key, skill, evidence)

        if best is None:
            return _not_stated(requirement_id, requirement, spec.name or target)

        score, key, skill, evidence = best
        if score <= 0.0:
            # Отсутствие навыка в списке не доказывает его отсутствие у кандидата.
            return _not_stated(requirement_id, requirement, spec.name or target)

        years = float(skill.tenure) if skill.HasField("tenure") else None
        recency = float(skill.recency) if skill.HasField("recency") else None
        tau, tau_low, tau_high = self._temporal.tau_bounds(
            years,
            float(spec.tenure),
            recency,
        )
        confidence = self._confidence.confidence(evidence.text)
        coverage = _clamp01(score * confidence * tau)
        coverage_low = _clamp01(score * confidence * tau_low)
        coverage_high = _clamp01(score * confidence * tau_high)

        status, gap_reason = _skill_status(score, years, float(spec.tenure))
        comment = _skill_comment(status, gap_reason, skill.name)

        return RequirementScore(
            requirement_id=requirement_id,
            criticality=requirement.criticality,
            status=status,
            relevance=score,
            coverage=coverage,
            coverage_low=coverage_low,
            coverage_high=coverage_high,
            comment=comment,
            label=spec.name or target,
            feature_key=key,
            evidence=evidence,
            years_found=years,
            gap_reason=gap_reason,
            resume_kind="skill",
            resume_feature=skill,
        )

    def _match_education(
        self,
        requirement_id: str,
        requirement: features_pb2.FeatureRequirement,
        resume: messages_pb2.ParsedResume,
        index: QuoteIndex,
    ) -> RequirementScore:
        spec = requirement.education
        best: (
            tuple[
                float,
                str,
                education_pb2.EducationPerResume,
                common_pb2.FeatureEvidence,
            ]
            | None
        ) = None

        for key, education in resume.education.items():
            evidence = _verified_evidence(index, education.evidence)
            if evidence is None:
                continue
            field_score = (
                self._similarity.score(spec.master, education.master) if spec.master else 1.0
            )
            level_factor = (
                1.0
                if spec.level == education_pb2.EDUCATION_LEVEL_UNSPECIFIED
                or education.level >= spec.level
                else 0.0
            )
            score = _clamp01(0.7 * field_score + 0.3 * level_factor)
            if best is None or (score, key) > (best[0], best[1]):
                best = (score, key, education, evidence)

        label = spec.master or "образование"
        if best is None:
            return _not_stated(requirement_id, requirement, label)

        score, key, education, evidence = best
        if score <= 0.0:
            return _absent(requirement_id, requirement, label)

        status, gap_reason = _status_for_score(score)
        return RequirementScore(
            requirement_id=requirement_id,
            criticality=requirement.criticality,
            status=status,
            relevance=score,
            coverage=score,
            coverage_low=score,
            coverage_high=score,
            comment=_education_comment(status, spec.master),
            label=label,
            feature_key=key,
            evidence=evidence,
            gap_reason=gap_reason,
            resume_kind="education",
            resume_feature=education,
        )

    def _match_experience(
        self,
        requirement_id: str,
        requirement: features_pb2.FeatureRequirement,
        resume: messages_pb2.ParsedResume,
        index: QuoteIndex,
        reference_date: date,
    ) -> RequirementScore:
        spec = requirement.experience
        best: (
            tuple[
                float,
                str,
                experience_pb2.PriorExperiencePerResume,
                common_pb2.FeatureEvidence,
                float | None,
            ]
            | None
        ) = None

        for key, experience in resume.experience.items():
            evidence = _first_experience_evidence(index, experience)
            if evidence is None:
                continue
            category_score = (
                self._similarity.score(spec.category, experience.category) if spec.category else 1.0
            )
            years = experience_years(experience, reference_date)
            if best is None or (category_score, key) > (best[0], best[1]):
                best = (category_score, key, experience, evidence, years)

        label = spec.category or "опыт работы"
        if best is None:
            return _not_stated(requirement_id, requirement, label)

        category_score, key, experience, evidence, years = best
        if category_score <= 0.0:
            return _absent(requirement_id, requirement, label)

        tau, tau_low, tau_high = self._temporal.tau_bounds(
            years,
            float(spec.tenure),
            0.0,
        )
        coverage = _clamp01(category_score * tau)
        coverage_low = _clamp01(category_score * tau_low)
        coverage_high = _clamp01(category_score * tau_high)
        status, gap_reason = _experience_status(
            category_score,
            years,
            float(spec.tenure),
        )

        return RequirementScore(
            requirement_id=requirement_id,
            criticality=requirement.criticality,
            status=status,
            relevance=category_score,
            coverage=coverage,
            coverage_low=coverage_low,
            coverage_high=coverage_high,
            comment=_experience_comment(status, gap_reason, spec.category),
            label=label,
            feature_key=key,
            evidence=evidence,
            years_found=years,
            gap_reason=gap_reason,
            resume_kind="experience",
            resume_feature=experience,
        )

    def _match_text(
        self,
        requirement_id: str,
        requirement: features_pb2.FeatureRequirement,
        index: QuoteIndex,
    ) -> RequirementScore:
        spec = requirement.text_requirement
        label = spec.text.strip()[:160] or "текстовое требование"
        match = self._text.match(spec.text, index.text)
        if match.score <= 0.0:
            return _not_stated(requirement_id, requirement, label)

        status, gap_reason = _text_status(match.score)
        evidence = None
        if match.evidence_text:
            evidence = _verified_evidence(
                index,
                common_pb2.FeatureEvidence(text=match.evidence_text),
            )

        return RequirementScore(
            requirement_id=requirement_id,
            criticality=requirement.criticality,
            status=status,
            relevance=match.score,
            coverage=match.score,
            coverage_low=match.score,
            coverage_high=match.score,
            comment=_text_comment(status),
            label=label,
            evidence=evidence,
            gap_reason=gap_reason,
        )


def _verified_evidence(
    index: QuoteIndex,
    evidence: common_pb2.FeatureEvidence | None,
) -> common_pb2.FeatureEvidence | None:
    if evidence is None or not evidence.text:
        return None
    located = index.locate(evidence.text)
    if located is None:
        return None
    start, end = located
    return common_pb2.FeatureEvidence(
        start_index=start,
        end_index=end,
        text=index.slice(start, end),
    )


def _first_experience_evidence(
    index: QuoteIndex,
    experience: experience_pb2.PriorExperiencePerResume,
) -> common_pb2.FeatureEvidence | None:
    for metric in experience.metrics:
        evidence = _verified_evidence(index, metric)
        if evidence is not None:
            return evidence
    return None


def _not_stated(
    requirement_id: str,
    requirement: features_pb2.FeatureRequirement,
    label: str,
) -> RequirementScore:
    return RequirementScore(
        requirement_id=requirement_id,
        criticality=requirement.criticality,
        status=common_pb2.CORRESPONDENCE_NOT_STATED,
        relevance=0.0,
        coverage=0.0,
        coverage_low=0.0,
        coverage_high=_NOT_STATED_HIGH,
        comment="В резюме нет сведений по требованию.",
        label=label,
        gap_reason=common_pb2.GAP_REASON_NOT_STATED,
    )


def _absent(
    requirement_id: str,
    requirement: features_pb2.FeatureRequirement,
    label: str,
) -> RequirementScore:
    return RequirementScore(
        requirement_id=requirement_id,
        criticality=requirement.criticality,
        status=common_pb2.CORRESPONDENCE_ABSENT,
        relevance=0.0,
        coverage=0.0,
        coverage_low=0.0,
        coverage_high=0.0,
        comment="В резюме есть признаки, но требование не выполнено.",
        label=label,
        gap_reason=common_pb2.GAP_REASON_ABSENT,
    )


def _status_for_score(score: float) -> tuple[int, int | None]:
    if score >= 0.85:
        return common_pb2.CORRESPONDENCE_FULL, None
    if score >= 0.5:
        return common_pb2.CORRESPONDENCE_PARTIAL, common_pb2.GAP_REASON_PARTIAL
    return common_pb2.CORRESPONDENCE_ABSENT, common_pb2.GAP_REASON_ABSENT


def _skill_status(
    score: float,
    years: float | None,
    required: float,
) -> tuple[int, int | None]:
    if score < 0.5:
        return common_pb2.CORRESPONDENCE_ABSENT, common_pb2.GAP_REASON_ABSENT
    if score < 0.85:
        return common_pb2.CORRESPONDENCE_PARTIAL, common_pb2.GAP_REASON_PARTIAL
    if required > 0 and years is None:
        return common_pb2.CORRESPONDENCE_PARTIAL, None
    if required > 0 and years is not None and years + 0.01 < required:
        return common_pb2.CORRESPONDENCE_PARTIAL, common_pb2.GAP_REASON_INSUFFICIENT_TENURE
    return common_pb2.CORRESPONDENCE_FULL, None


def _experience_status(
    score: float,
    years: float | None,
    required: float,
) -> tuple[int, int | None]:
    if score < 0.5:
        return common_pb2.CORRESPONDENCE_ABSENT, common_pb2.GAP_REASON_ABSENT
    if score < 0.85:
        return common_pb2.CORRESPONDENCE_PARTIAL, common_pb2.GAP_REASON_PARTIAL
    if required <= 0:
        return common_pb2.CORRESPONDENCE_FULL, None
    if years is None:
        return common_pb2.CORRESPONDENCE_PARTIAL, None
    if years + 0.01 < required:
        return common_pb2.CORRESPONDENCE_PARTIAL, common_pb2.GAP_REASON_INSUFFICIENT_TENURE
    return common_pb2.CORRESPONDENCE_FULL, None


def _text_status(score: float) -> tuple[int, int | None]:
    if score >= 0.75:
        return common_pb2.CORRESPONDENCE_FULL, None
    if score >= 0.35:
        return common_pb2.CORRESPONDENCE_PARTIAL, common_pb2.GAP_REASON_PARTIAL
    return common_pb2.CORRESPONDENCE_ABSENT, common_pb2.GAP_REASON_ABSENT


def _skill_comment(status: int, gap_reason: int | None, name: str) -> str:
    if status == common_pb2.CORRESPONDENCE_FULL:
        return "Навык подтверждён в резюме."
    if gap_reason == common_pb2.GAP_REASON_INSUFFICIENT_TENURE:
        return "Навык есть, но подтверждённого стажа меньше требуемого."
    if status == common_pb2.CORRESPONDENCE_PARTIAL:
        return "Найден близкий навык, соответствие частичное."
    return f"Навык «{name}» не найден в резюме."


def _education_comment(status: int, master: str) -> str:
    if status == common_pb2.CORRESPONDENCE_FULL:
        return "Образование соответствует требованию."
    if status == common_pb2.CORRESPONDENCE_PARTIAL:
        return "Образование подтверждено частично."
    return f"Образование по направлению «{master}» не найдено."


def _experience_comment(status: int, gap_reason: int | None, category: str) -> str:
    if status == common_pb2.CORRESPONDENCE_FULL:
        return "Опыт в требуемой категории подтверждён."
    if gap_reason == common_pb2.GAP_REASON_INSUFFICIENT_TENURE:
        return "Опыт в категории есть, но его меньше требуемого."
    if status == common_pb2.CORRESPONDENCE_PARTIAL:
        return "Опыт подтверждён частично."
    return f"Опыт в категории «{category}» не найден."


def _text_comment(status: int) -> str:
    if status == common_pb2.CORRESPONDENCE_FULL:
        return "Требование подтверждено текстом резюме."
    if status == common_pb2.CORRESPONDENCE_PARTIAL:
        return "Требование подтверждено частично."
    return "В резюме нет достаточного подтверждения требования."


def _clamp01(value: float) -> float:
    return min(1.0, max(0.0, value))
