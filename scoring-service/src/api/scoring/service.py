"""gRPC-сервис: извлечение признаков и сборка ответа.

Математика `R_i`, `c_i`, `R'`, `Score` и границ пока не реализована: агрегаты
остаются заглушкой `0.5`/`0.4`/`0.6`. По требованиям отдаётся `S^{ij}` из
псевдослучайной матрицы, поэтому `coverage = relevance` (внешних источников нет,
`λ = 0`).
"""

from __future__ import annotations

import logging
from typing import Any

import grpc
from google.type import date_pb2

from scoring_service.v1 import scoring_pb2_grpc
from scoring_service.v1.models import features_pb2, messages_pb2
from scoring_service.v1.models.features import (
    common_pb2,
    education_pb2,
    experience_pb2,
    skill_pb2,
)

from ..extraction.extractor import ExtractionError, ResumeExtractor
from ..extraction.normalize import normalize_key
from ..extraction.quotes import QuoteIndex
from ..extraction.schema import (
    ExtractedEducation,
    ExtractedExperience,
    ExtractedSkill,
    ParsedResume,
)
from ..llm.client import LlmUnavailable
from .similarity import best_match, resume_seed

logger = logging.getLogger(__name__)


class ScoringService(scoring_pb2_grpc.ScoringServiceServicer):
    """Онлайн-оценка резюме: LLM извлекает признаки, код собирает ответ."""

    def __init__(self, extractor: ResumeExtractor) -> None:
        self._extractor = extractor

    def ScoreResume(
        self,
        request: messages_pb2.ScoreResumeRequest,
        context: grpc.ServicerContext,
    ) -> messages_pb2.ScoreResumeResponse:
        text = request.resume.text
        if not text.strip():
            context.abort(grpc.StatusCode.INVALID_ARGUMENT, "resume text is empty")

        try:
            result = self._extractor.extract(text)
        except LlmUnavailable:
            context.abort(grpc.StatusCode.UNAVAILABLE, "LLM is unavailable")
        except ExtractionError:
            context.abort(grpc.StatusCode.INTERNAL, "LLM response failed validation")

        index = QuoteIndex(text)
        seed = resume_seed(text)
        features: dict[str, features_pb2.FeatureAssessment] = {}
        refine = False

        for key, requirement in request.vacancy.requirements.items():
            assessment = self._assess(key, requirement, result.parsed, index, seed)
            if assessment is None:
                refine = True
                assessment = features_pb2.FeatureAssessment(relevance=0.0, coverage=0.0)
            features[key] = assessment

        logger.info(
            "score_resume model=%s prompt=%s requirements=%d "
            "skills=%d education=%d experience=%d dropped=%d",
            self._extractor.model,
            self._extractor.prompt_version,
            len(request.vacancy.requirements),
            len(result.parsed.skills),
            len(result.parsed.education),
            len(result.parsed.experience),
            result.dropped,
        )

        # TODO(scoring-model): Rv, c_i, R', Score и границы считает модель.
        # До её появления агрегаты остаются заглушкой; per-feature значения уже
        # берутся из матрицы S.
        return messages_pb2.ScoreResumeResponse(
            relevance=0.5,
            score_low=0.4,
            score_high=0.6,
            score=0.5,
            refine=refine,
            features=features,
        )

    def _assess(
        self,
        key: str,
        requirement: features_pb2.FeatureRequirement,
        parsed: ParsedResume,
        index: QuoteIndex,
        seed: int,
    ) -> features_pb2.FeatureAssessment | None:
        kind = requirement.WhichOneof("feature")
        if kind == "skill":
            return self._assess_skill(key, parsed.skills, index, seed)
        if kind == "education":
            return self._assess_education(key, parsed.education, index, seed)
        if kind == "experience":
            return self._assess_experience(key, parsed.experience, index, seed)
        return None

    def _assess_skill(
        self,
        key: str,
        skills: list[ExtractedSkill],
        index: QuoteIndex,
        seed: int,
    ) -> features_pb2.FeatureAssessment | None:
        candidates = [
            (f"skill:{position}:{normalize_key(skill.name)}", skill)
            for position, skill in enumerate(skills)
        ]
        match = best_match(seed, key, candidates)
        if match is None:
            return None

        skill, score = match
        evidence = _evidence(index, skill.quote)
        if evidence is None:
            return None

        resume_kwargs: dict[str, Any] = {
            "name": skill.name,
            "normalized_name": normalize_key(skill.name),
            "evidence": evidence,
        }
        if skill.years is not None:
            resume_kwargs["tenure"] = skill.years

        assessment_kwargs: dict[str, Any] = {
            "relevance": score,
            "coverage": score,
            "skill": skill_pb2.SkillPerResume(**resume_kwargs),
        }
        if skill.years is not None:
            assessment_kwargs["tenure"] = skill.years
        return features_pb2.FeatureAssessment(**assessment_kwargs)

    def _assess_education(
        self,
        key: str,
        education: list[ExtractedEducation],
        index: QuoteIndex,
        seed: int,
    ) -> features_pb2.FeatureAssessment | None:
        candidates = [
            (
                f"education:{position}:{normalize_key(item.field or item.institution or '')}",
                item,
            )
            for position, item in enumerate(education)
        ]
        match = best_match(seed, key, candidates)
        if match is None:
            return None

        item, score = match
        evidence = _evidence(index, item.quote)
        if evidence is None:
            return None

        resume_kwargs: dict[str, Any] = {
            "institution": item.institution or "",
            "master": item.field or "",
            "evidence": evidence,
        }
        if item.graduation_year is not None:
            resume_kwargs["graduation_year"] = date_pb2.Date(year=item.graduation_year)

        return features_pb2.FeatureAssessment(
            relevance=score,
            coverage=score,
            education=education_pb2.EducationPerResume(**resume_kwargs),
        )

    def _assess_experience(
        self,
        key: str,
        experience: list[ExtractedExperience],
        index: QuoteIndex,
        seed: int,
    ) -> features_pb2.FeatureAssessment | None:
        candidates = [
            (
                f"experience:{position}:{normalize_key(item.category or item.position or '')}",
                item,
            )
            for position, item in enumerate(experience)
        ]
        match = best_match(seed, key, candidates)
        if match is None:
            return None

        item, score = match
        evidence = _evidence(index, item.quote)
        if evidence is None:
            return None

        # Основной цитатой считаем первую метрику: отдельного поля evidence
        # в `PriorExperiencePerResume` нет, иначе блок опыта остался бы без
        # подтверждения.
        metrics = [evidence]
        for metric in item.metrics:
            metric_evidence = _evidence(index, metric)
            if metric_evidence is not None:
                metrics.append(metric_evidence)

        resume_kwargs: dict[str, Any] = {
            "category": item.category or "",
            "position": item.position or "",
            "metrics": metrics,
        }
        if item.begin_year is not None:
            resume_kwargs["begin"] = date_pb2.Date(year=item.begin_year)
        if item.end_year is not None:
            resume_kwargs["end"] = date_pb2.Date(year=item.end_year)

        return features_pb2.FeatureAssessment(
            relevance=score,
            coverage=score,
            experience=experience_pb2.PriorExperiencePerResume(**resume_kwargs),
        )


def _evidence(index: QuoteIndex, quote: str) -> common_pb2.FeatureEvidence | None:
    located = index.locate(quote)
    if located is None:
        return None
    start, end = located
    return common_pb2.FeatureEvidence(
        start_index=start,
        end_index=end,
        text=index.slice(start, end),
    )
