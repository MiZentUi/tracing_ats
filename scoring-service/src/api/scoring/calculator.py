"""Арифметика агрегатов: `Rv`, `R'`, `Score` и границы.

Формулы — из `docs/rfc/rfc-scoring-model.md`. Внешних источников в контракте
пока нет, поэтому `λ = 0` и `c_i = R_i'`. `F` вынесен в `DomainMatcher`;
по умолчанию `F = 0`, `M = 1`, пока формула домена не зафиксирована в RFC.
"""

from __future__ import annotations

from collections.abc import Iterable

from .model import GeneralScore, RequirementScore
from .policies import WeightPolicy


class ScoringCalculator:
    """Считает агрегаты по уже сопоставленным требованиям."""

    def __init__(
        self,
        weights: WeightPolicy,
        domain_weight: float = 0.0,
    ) -> None:
        self._weights = weights
        self._domain_weight = min(1.0, max(0.0, domain_weight))

    def aggregate(
        self,
        matches: Iterable[RequirementScore],
        tenure: int | None,
        domain_score: float = 0.0,
    ) -> GeneralScore:
        total_weight = 0.0
        relevance_sum = 0.0
        coverage_sum = 0.0
        coverage_low_sum = 0.0
        coverage_high_sum = 0.0

        for match in matches:
            weight = self._weights.weight(match.criticality)
            total_weight += weight
            relevance_sum += weight * match.relevance
            coverage_sum += weight * match.coverage
            coverage_low_sum += weight * match.coverage_low
            coverage_high_sum += weight * match.coverage_high

        if total_weight <= 0.0:
            return GeneralScore(
                relevance=0.0,
                score=0.0,
                score_low=0.0,
                score_high=0.0,
                tenure=tenure,
            )

        relevance = _clamp01(relevance_sum / total_weight)
        coverage = _clamp01(coverage_sum / total_weight)
        coverage_low = _clamp01(coverage_low_sum / total_weight)
        coverage_high = _clamp01(coverage_high_sum / total_weight)

        domain = _clamp01(domain_score)
        model_weight = 1.0 - self._domain_weight
        return GeneralScore(
            relevance=relevance,
            score=_clamp01(model_weight * coverage + self._domain_weight * domain),
            score_low=_clamp01(model_weight * coverage_low + self._domain_weight * domain),
            score_high=_clamp01(model_weight * coverage_high + self._domain_weight * domain),
            tenure=tenure,
        )


def _clamp01(value: float) -> float:
    return min(1.0, max(0.0, value))
