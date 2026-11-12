"""Оркестрация оценки: сопоставление, агрегаты и объяснения."""

from __future__ import annotations

from datetime import date

from scoring_service.v1.models import messages_pb2
from scoring_service.v1.models.features import common_pb2

from ..extraction.quotes import QuoteIndex
from .calculator import ScoringCalculator
from .dates import end_date, start_date, total_experience_years
from .matcher import RequirementMatcher
from .model import Evaluation, GapView, RequirementScore
from .policies import DomainMatcher

_MAX_LIST_ITEMS = 5


class ResumeEvaluator:
    """Оценивает одно извлечённое резюме по одной вакансии. LLM не вызывает."""

    def __init__(
        self,
        matcher: RequirementMatcher,
        calculator: ScoringCalculator,
        domain: DomainMatcher,
    ) -> None:
        self._matcher = matcher
        self._calculator = calculator
        self._domain = domain

    def evaluate(
        self,
        resume: messages_pb2.ParsedResume,
        text: str,
        vacancy: messages_pb2.Vacancy,
        reference_date: date,
    ) -> Evaluation:
        if not text.strip():
            raise ValueError("resume text is empty")

        index = QuoteIndex(text)
        resolution = self._matcher.prepare(vacancy, resume)
        matches = {
            key: self._matcher.match(key, requirement, resume, index, reference_date, resolution)
            for key, requirement in sorted(vacancy.requirements.items())
        }

        total_years = total_experience_years(resume.experience.values(), reference_date)
        tenure = round(total_years) if total_years is not None else None
        domain_score = self._domain.score(resume, vacancy)
        # TODO: match tenure against total years
        general = self._calculator.aggregate(matches.values(), tenure, domain_score)

        gaps = [
            GapView(requirement_id=match.requirement_id, reason=match.gap_reason)
            for match in matches.values()
            if match.gap_reason is not None
        ]
        strengths = _strengths(matches)
        risks = _risks(resume, reference_date)
        questions = _questions(matches)

        return Evaluation(
            general=general,
            matches=matches,
            strengths=strengths,
            gaps=gaps,
            risks=risks,
            questions=questions,
            recruiter_note=_recruiter_note(
                general.score,
                general.score_low,
                general.score_high,
                matches,
                gaps,
                risks,
            ),
            resolver_version=resolution.resolver_version,
            dictionary_mode=resolution.mode,
        )


def _strengths(matches: dict[str, RequirementScore]) -> list[str]:
    result = [
        _short_label(match.label)
        for match in matches.values()
        if match.status == common_pb2.CORRESPONDENCE_FULL
    ]
    return result[:_MAX_LIST_ITEMS]


def _risks(
    resume: messages_pb2.ParsedResume,
    reference_date: date,
) -> list[str]:
    risks: list[str] = []
    for key, experience in sorted(resume.experience.items()):
        begin = start_date(experience.begin)
        end = end_date(experience.end, reference_date)
        if begin is None:
            risks.append(f"experience:{key}: begin date is missing")
            continue
        if begin > reference_date:
            risks.append(f"experience:{key}: begin date is in the future")
        if end is not None and end < begin:
            risks.append(f"experience:{key}: end date is before begin date")

    if not resume.experience:
        risks.append("experience dates are missing; tenure is unknown")
    if not resume.skills:
        risks.append("skills are missing; coverage bounds are wide")

    return risks[:_MAX_LIST_ITEMS]


def _questions(matches: dict[str, RequirementScore]) -> list[str]:
    result: list[str] = []
    seen: set[str] = set()
    for match in matches.values():
        if match.status == common_pb2.CORRESPONDENCE_FULL:
            continue

        label = _short_label(match.label)
        if match.gap_reason == common_pb2.GAP_REASON_INSUFFICIENT_TENURE:
            question = f"Какой суммарный стаж по требованию «{label}»?"
        elif match.gap_reason == common_pb2.GAP_REASON_NOT_STATED:
            question = f"Уточните, есть ли опыт по требованию «{label}»."
        elif match.gap_reason == common_pb2.GAP_REASON_ABSENT:
            question = f"Есть ли опыт, близкий к требованию «{label}»?"
        else:
            question = f"Насколько глубоко кандидат владеет требованием «{label}»?"

        if question not in seen:
            seen.add(question)
            result.append(question)
    return result[:_MAX_LIST_ITEMS]


def _recruiter_note(
    score: float,
    score_low: float,
    score_high: float,
    matches: dict[str, RequirementScore],
    gaps: list[GapView],
    risks: list[str],
) -> str:
    if not matches:
        return "В вакансии нет требований для оценки."
    full = sum(1 for match in matches.values() if match.status == common_pb2.CORRESPONDENCE_FULL)
    return (
        f"Покрытие {score:.0%} (вилка {score_low:.0%}–{score_high:.0%}); "
        f"полностью закрыто {full} из {len(matches)}; "
        f"пробелов {len(gaps)}; рисков {len(risks)}."
    )


def _short_label(value: str) -> str:
    text = value.strip()
    if len(text) <= 120:
        return text
    return text[:117].rstrip() + "..."
