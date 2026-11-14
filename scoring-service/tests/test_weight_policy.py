from __future__ import annotations

from collections.abc import Mapping

from api.scoring.calculator import ScoringCalculator
from api.scoring.model import RequirementScore
from api.scoring.policies import CriticalityWeightPolicy
from scoring_service.v1.models.features import common_pb2


def _match(
    requirement_id: str,
    *,
    criticality: int,
    coverage: float,
) -> RequirementScore:
    return RequirementScore(
        requirement_id=requirement_id,
        criticality=criticality,
        status=common_pb2.CORRESPONDENCE_FULL,
        relevance=1.0,
        coverage=coverage,
        coverage_low=coverage,
        coverage_high=coverage,
        comment="",
        label=requirement_id,
    )


def test_default_weights_are_normalized_by_group_size() -> None:
    policy = CriticalityWeightPolicy()
    counts = {
        common_pb2.FEATURE_CRITICALITY_REQUIRED: 3,
        common_pb2.FEATURE_CRITICALITY_NICE: 1,
    }

    assert policy.weight(common_pb2.FEATURE_CRITICALITY_REQUIRED, counts) == 0.25
    assert policy.weight(common_pb2.FEATURE_CRITICALITY_NICE, counts) == 0.5


def test_count_normalization_can_be_disabled() -> None:
    policy = CriticalityWeightPolicy(normalize_by_counts=False)
    counts = {common_pb2.FEATURE_CRITICALITY_REQUIRED: 3}

    assert policy.weight(common_pb2.FEATURE_CRITICALITY_REQUIRED, counts) == 0.75


def test_custom_weight_function_receives_counts() -> None:
    seen: list[tuple[int, Mapping[int, int]]] = []

    def custom(criticality: int, counts: Mapping[int, int]) -> float:
        seen.append((criticality, counts))
        return 2.0 if counts.get(criticality, 0) > 1 else 0.5

    policy = CriticalityWeightPolicy(weight_fn=custom)
    counts = {common_pb2.FEATURE_CRITICALITY_REQUIRED: 2}

    assert policy.weight(common_pb2.FEATURE_CRITICALITY_REQUIRED, counts) == 2.0
    assert seen == [(common_pb2.FEATURE_CRITICALITY_REQUIRED, counts)]


def test_calculator_passes_counts_and_normalizes_group_mass() -> None:
    calculator = ScoringCalculator(CriticalityWeightPolicy())
    matches = [
        _match("must-1", criticality=common_pb2.FEATURE_CRITICALITY_REQUIRED, coverage=1.0),
        _match("must-2", criticality=common_pb2.FEATURE_CRITICALITY_REQUIRED, coverage=0.0),
        _match("must-3", criticality=common_pb2.FEATURE_CRITICALITY_REQUIRED, coverage=0.0),
        _match("nice-1", criticality=common_pb2.FEATURE_CRITICALITY_NICE, coverage=1.0),
    ]

    general = calculator.aggregate(matches, tenure=None)

    # required: 3 × 0.75/3 = 0.75 суммарно, nice: 0.5 суммарно.
    # coverage = (0.25×1 + 0.25×0 + 0.25×0 + 0.5×1) / 1.25 = 0.6
    assert general.score == 0.6
    assert general.relevance == 1.0
