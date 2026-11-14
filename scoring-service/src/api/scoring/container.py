"""Сборка зависимостей scoring-service.

Все политики создаются в одном месте, чтобы тесты и будущие замены
(другая матрица сходства, другая формула `F`, другой источник весов)
не требовали правок gRPC-сервиса.
"""

from __future__ import annotations

from ..config import DEFAULT_SKILL_FULL_COVERAGE, DEFAULT_TENURE_WEIGHT, Settings
from ..dictionary.client import DictionaryClient
from ..extraction.extractor import ResumeExtractor
from ..llm import build_llm_client
from .calculator import ScoringCalculator
from .evaluator import ResumeEvaluator
from .feedback import FeedbackBuilder
from .mapping import ExtractionMapper
from .matcher import RequirementMatcher
from .policies import (
    CriticalityWeightPolicy,
    HeuristicClaimConfidence,
    NoDomainMatcher,
    RelationPolicy,
    StatusPolicy,
    SystemClock,
    TemporalPolicy,
)
from .qualification import QualificationResolver
from .resolver import SkillResolver
from .service import ScoringService
from .similarity import DeterministicSimilarity
from .text import DeterministicTextMatcher


def build_evaluator(settings: Settings | None = None) -> ResumeEvaluator:
    similarity = DeterministicSimilarity()
    client = None
    enabled = False
    if settings is not None:
        enabled = settings.dictionary_enabled
        if enabled:
            client = DictionaryClient(
                settings.dictionary_target,
                settings.dictionary_deadline_seconds,
                settings.dictionary_as_of_version,
            )
    resolver = SkillResolver(client, enabled=enabled)
    qualification_resolver = QualificationResolver(client, enabled=enabled)
    temporal = TemporalPolicy()
    skill_full_coverage = (
        settings.skill_full_coverage if settings is not None else DEFAULT_SKILL_FULL_COVERAGE
    )
    matcher = RequirementMatcher(
        similarity=similarity,
        temporal=temporal,
        confidence=HeuristicClaimConfidence(),
        text_matcher=DeterministicTextMatcher(similarity),
        resolver=resolver,
        relations=RelationPolicy(),
        status=StatusPolicy(skill_full_coverage=skill_full_coverage),
    )
    tenure_weight = settings.tenure_weight if settings is not None else DEFAULT_TENURE_WEIGHT
    calculator = ScoringCalculator(
        weights=CriticalityWeightPolicy(),
        # F пока не зафиксирована в RFC; fallback F = 0.
        domain_weight=0.0,
        tenure_weight=tenure_weight,
        temporal=temporal,
    )
    return ResumeEvaluator(
        matcher=matcher,
        calculator=calculator,
        domain=NoDomainMatcher(),
        feedback=FeedbackBuilder(),
        qualification_resolver=qualification_resolver,
    )


def build_scoring_service(settings: Settings) -> ScoringService:
    return ScoringService(
        extractor=ResumeExtractor(build_llm_client(settings)),
        evaluator=build_evaluator(settings),
        mapper=ExtractionMapper(),
        clock=SystemClock(),
    )
