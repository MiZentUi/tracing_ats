"""Внутренние модели скоринга, не зависящие от gRPC-сообщений.

Здесь только результаты сопоставления и агрегаты. Protobuf-сообщения
собираются отдельным слоем `mapping.py`, чтобы математику и правила
сопоставления можно было тестировать без gRPC.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Literal

from scoring_service.v1.models.features import (
    common_pb2,
    education_pb2,
    experience_pb2,
    skill_pb2,
)

ResumeFeature = (
    skill_pb2.SkillPerResume
    | education_pb2.EducationPerResume
    | experience_pb2.PriorExperiencePerResume
)
ResumeKind = Literal["skill", "education", "experience"]


@dataclass(frozen=True, slots=True)
class RequirementScore:
    """Результат сопоставления одного требования с резюме."""

    requirement_id: str
    criticality: int
    status: int
    relevance: float
    coverage: float
    coverage_low: float
    coverage_high: float
    comment: str
    label: str
    feature_key: str = ""
    evidence: common_pb2.FeatureEvidence | None = None
    years_found: float | None = None
    gap_reason: int | None = None
    resume_kind: ResumeKind | None = None
    resume_feature: ResumeFeature | None = None


@dataclass(frozen=True, slots=True)
class GeneralScore:
    """Агрегаты модели для одной вакансии."""

    relevance: float
    score: float
    score_low: float
    score_high: float
    tenure: int | None


@dataclass(frozen=True, slots=True)
class GapView:
    """Пробел по требованию для сборки `Gap`."""

    requirement_id: str
    reason: int


@dataclass(frozen=True, slots=True)
class Evaluation:
    """Полный результат оценки одной вакансии."""

    general: GeneralScore
    matches: dict[str, RequirementScore]
    strengths: list[str]
    gaps: list[GapView]
    risks: list[str]
    questions: list[str]
    recruiter_note: str
