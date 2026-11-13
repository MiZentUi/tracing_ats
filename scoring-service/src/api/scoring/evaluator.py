"""Оркестрация оценки: сопоставление, агрегаты и объяснения."""

from __future__ import annotations

from datetime import date

from scoring_service.v1.models import messages_pb2
from scoring_service.v1.models.features import qualification_pb2

from ..extraction.quotes import QuoteIndex
from .calculator import ScoringCalculator
from .dates import total_experience_years
from .feedback import FeedbackBuilder
from .matcher import RequirementMatcher
from .model import Evaluation, GapView
from .policies import DomainMatcher
from .qualification import QualificationNormalizer


class ResumeEvaluator:
    """Оценивает одно извлечённое резюме по одной вакансии. LLM не вызывает."""

    def __init__(
        self,
        matcher: RequirementMatcher,
        calculator: ScoringCalculator,
        domain: DomainMatcher,
        feedback: FeedbackBuilder,
    ) -> None:
        self._matcher = matcher
        self._calculator = calculator
        self._domain = domain
        self._feedback = feedback

    def evaluate(
        self,
        resume: messages_pb2.ParsedResume,
        text: str,
        vacancy: messages_pb2.Vacancy,
        reference_date: date,
        qualification_catalog: qualification_pb2.QualificationCatalog | None = None,
    ) -> Evaluation:
        if not text.strip():
            raise ValueError("resume text is empty")

        index = QuoteIndex(text)
        resolution = self._matcher.prepare(vacancy, resume)
        qualifications = QualificationNormalizer(qualification_catalog)
        matches = {
            key: self._matcher.match(
                key,
                requirement,
                resume,
                index,
                reference_date,
                resolution,
                qualifications,
            )
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
        feedback = self._feedback.build(
            resume=resume,
            vacancy=vacancy,
            matches=matches,
            general=general,
            reference_date=reference_date,
            total_years=total_years,
        )

        return Evaluation(
            general=general,
            matches=matches,
            strengths=feedback.strengths,
            gaps=gaps,
            risks=feedback.risks,
            questions=feedback.questions,
            recruiter_note=feedback.recruiter_note,
            resolver_version=resolution.resolver_version,
            dictionary_mode=resolution.mode,
        )
