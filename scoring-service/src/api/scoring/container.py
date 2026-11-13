"""Сборка зависимостей scoring-service.

Все политики создаются в одном месте, чтобы тесты и будущие замены
(другая матрица сходства, другая формула `F`, другой источник весов)
не требовали правок gRPC-сервиса.
"""

from __future__ import annotations

from ..config import Settings
from ..dictionary.client import DictionaryClient
from ..extraction.extractor import ResumeExtractor
from ..llm import build_llm_client
from .calculator import ScoringCalculator
from .evaluator import ResumeEvaluator
from .mapping import ExtractionMapper
from .matcher import RequirementMatcher
from .policies import (
    CriticalityWeightPolicy,
    HeuristicClaimConfidence,
    NoDomainMatcher,
    SystemClock,
    TemporalPolicy,
)
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
    matcher = RequirementMatcher(
        similarity=similarity,
        temporal=TemporalPolicy(),
        confidence=HeuristicClaimConfidence(),
        text_matcher=DeterministicTextMatcher(similarity),
        resolver=resolver,
    )
    calculator = ScoringCalculator(
        weights=CriticalityWeightPolicy(),
        # F пока не зафиксирована в RFC; fallback F = 0, M = 1.
        domain_weight=0.0,
    )
    return ResumeEvaluator(
        matcher=matcher,
        calculator=calculator,
        domain=NoDomainMatcher(),
    )


def build_scoring_service(settings: Settings) -> ScoringService:
    return ScoringService(
        extractor=ResumeExtractor(build_llm_client(settings)),
        evaluator=build_evaluator(settings),
        mapper=ExtractionMapper(),
        clock=SystemClock(),
    )
