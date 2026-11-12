from __future__ import annotations

import math

import pytest

from api.scoring.calculator import ScoringCalculator
from api.scoring.model import RequirementScore
from api.scoring.policies import (
    CriticalityWeightPolicy,
    HeuristicClaimConfidence,
    TemporalPolicy,
)
from scoring_service.v1.models.features import common_pb2


def _match(
    requirement_id: str,
    *,
    criticality: int,
    relevance: float,
    coverage: float,
    coverage_low: float,
    coverage_high: float,
) -> RequirementScore:
    return RequirementScore(
        requirement_id=requirement_id,
        criticality=criticality,
        status=common_pb2.CORRESPONDENCE_PARTIAL,
        relevance=relevance,
        coverage=coverage,
        coverage_low=coverage_low,
        coverage_high=coverage_high,
        comment="",
        label=requirement_id,
    )


def test_criticality_weights_are_ordered_without_cap() -> None:
    policy = CriticalityWeightPolicy()

    assert policy.weight(common_pb2.FEATURE_CRITICALITY_OPTIONAL) == 0.25
    assert policy.weight(common_pb2.FEATURE_CRITICALITY_NICE) == 0.5
    assert policy.weight(common_pb2.FEATURE_CRITICALITY_REQUIRED) == 0.75
    assert policy.weight(common_pb2.FEATURE_CRITICALITY_CRITICAL) == 1.0
    assert policy.weight(common_pb2.FEATURE_CRITICALITY_UNSPECIFIED) == 0.5


def test_temporal_tau_at_required_tenure_matches_formula() -> None:
    policy = TemporalPolicy()

    point, low, high = policy.tau_bounds(3.0, 3.0, 0.0)

    expected = 0.5 * (1.0 + math.tanh(1.0))
    assert point == pytest.approx(expected)
    assert low == pytest.approx(expected)
    assert high == pytest.approx(expected)


def test_unknown_tenure_expands_tau_downwards() -> None:
    policy = TemporalPolicy()

    point, low, high = policy.tau_bounds(None, 3.0, 0.0)

    assert low < point
    assert high == pytest.approx(point)


def test_unknown_recency_expands_tau_downwards() -> None:
    policy = TemporalPolicy()

    point, low, high = policy.tau_bounds(3.0, 3.0, None)

    expected = 0.5 * (1.0 + math.tanh(1.0))
    assert point == pytest.approx(expected)
    assert high == pytest.approx(expected)
    assert low < point


def test_zero_required_tenure_keeps_tenure_factor_neutral() -> None:
    policy = TemporalPolicy()

    point, low, high = policy.tau_bounds(None, 0.0, 0.0)

    assert point == pytest.approx(1.0)
    assert low == pytest.approx(1.0)
    assert high == pytest.approx(1.0)


def test_claim_confidence_distinguishes_listing_duty_and_result() -> None:
    policy = HeuristicClaimConfidence()

    assert policy.confidence("Python") == 0.3
    assert policy.confidence("Разработал сервис на Python") == 0.5
    assert policy.confidence("Снизил задержку до 20 ms") == 0.8
    assert policy.confidence("   ") == 0.0


def test_calculator_aggregates_relevance_and_coverage_with_weights() -> None:
    calculator = ScoringCalculator(CriticalityWeightPolicy())
    matches = [
        _match(
            "required",
            criticality=common_pb2.FEATURE_CRITICALITY_REQUIRED,
            relevance=1.0,
            coverage=0.8,
            coverage_low=0.6,
            coverage_high=0.9,
        ),
        _match(
            "optional",
            criticality=common_pb2.FEATURE_CRITICALITY_OPTIONAL,
            relevance=0.5,
            coverage=0.5,
            coverage_low=0.5,
            coverage_high=0.5,
        ),
    ]

    general = calculator.aggregate(matches, tenure=4)

    assert general.relevance == pytest.approx(0.875)
    assert general.score == pytest.approx(0.725)
    assert general.score_low == pytest.approx(0.575)
    assert general.score_high == pytest.approx(0.8)
    assert general.tenure == 4


def test_calculator_returns_zeros_without_requirements() -> None:
    general = ScoringCalculator(CriticalityWeightPolicy()).aggregate([], tenure=None)

    assert general.relevance == 0.0
    assert general.score == 0.0
    assert general.score_low == 0.0
    assert general.score_high == 0.0
    assert general.tenure is None
