"""Арифметика агрегатов: `Rv`, `R'`, `Score` и границы.

Формулы — из `docs/rfc/rfc-scoring-model.md`. Внешних источников в контракте
пока нет, поэтому `λ = 0` и `c_i = R_i'`. `F` вынесен в `DomainMatcher`;
по умолчанию `F = 0`. Общий стаж входит отдельным слагаемым с настраиваемым
весом `tenure_weight`.
"""

from __future__ import annotations

from collections import Counter
from collections.abc import Iterable

from .model import GeneralScore, RequirementScore
from .policies import TemporalPolicy, TemporalPolicyProtocol, WeightPolicy


class ScoringCalculator:
    """Считает агрегаты по уже сопоставленным требованиям."""

    def __init__(
        self,
        weights: WeightPolicy,
        domain_weight: float = 0.0,
        tenure_weight: float = 0.0,
        temporal: TemporalPolicyProtocol | None = None,
    ) -> None:
        self._weights = weights
        self._domain_weight = _clamp01(domain_weight)
        self._tenure_weight = _clamp01(tenure_weight)
        self._temporal = temporal or TemporalPolicy()

    def aggregate(
        self,
        matches: Iterable[RequirementScore],
        tenure: float | None,
        required_tenure: float = 0.0,
        domain_score: float = 0.0,
    ) -> GeneralScore:
        matches = list(matches)
        counts = Counter(match.criticality for match in matches)
        weights = {
            criticality: self._weights.weight(criticality, counts)
            for criticality in counts
        }
        total_weight = 0.0
        relevance_sum = 0.0
        coverage_sum = 0.0
        coverage_low_sum = 0.0
        coverage_high_sum = 0.0

        for match in matches:
            weight = weights[match.criticality]
            total_weight += weight
            relevance_sum += weight * match.relevance
            coverage_sum += weight * match.coverage
            coverage_low_sum += weight * match.coverage_low
            coverage_high_sum += weight * match.coverage_high

        rounded_tenure = round(tenure) if tenure is not None else None
        if total_weight <= 0.0:
            return GeneralScore(
                relevance=0.0,
                score=0.0,
                score_low=0.0,
                score_high=0.0,
                tenure=rounded_tenure,
            )

        relevance = _clamp01(relevance_sum / total_weight)
        coverage = _clamp01(coverage_sum / total_weight)
        coverage_low = _clamp01(coverage_low_sum / total_weight)
        coverage_high = _clamp01(coverage_high_sum / total_weight)

        domain = _clamp01(domain_score)
        tenure_score, tenure_low, tenure_high = self._temporal.tau_bounds(
            tenure,
            required_tenure,
            0.0,
        )
        # Если стаж не требуется, слагаемое стажа не должно давать
        # кандидату бесплатный вклад в Score.
        tenure_weight = self._tenure_weight if required_tenure > 0 else 0.0
        model_weight = max(0.0, 1.0 - self._domain_weight - tenure_weight)
        return GeneralScore(
            relevance=relevance,
            score=_clamp01(
                model_weight * coverage
                + self._domain_weight * domain
                + tenure_weight * tenure_score
            ),
            score_low=_clamp01(
                model_weight * coverage_low
                + self._domain_weight * domain
                + tenure_weight * tenure_low
            ),
            score_high=_clamp01(
                model_weight * coverage_high
                + self._domain_weight * domain
                + tenure_weight * tenure_high
            ),
            tenure=rounded_tenure,
        )


def _clamp01(value: float) -> float:
    return min(1.0, max(0.0, value))
