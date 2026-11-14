from __future__ import annotations

from api.config import Settings
from api.scoring.policies import StatusPolicy
from scoring_service.v1.models.features import common_pb2


def test_default_skill_threshold_is_configured() -> None:
    settings = Settings(_env_file=None)

    assert settings.skill_full_coverage == 0.70


def test_skill_threshold_can_be_overridden_by_environment(monkeypatch) -> None:
    monkeypatch.setenv("SCORING_SERVICE_SKILL_FULL_COVERAGE", "0.8")

    settings = Settings(_env_file=None)

    assert settings.skill_full_coverage == 0.80


def test_skill_low_coverage_is_partial_not_full() -> None:
    status, gap = StatusPolicy().skill(
        relevance=1.0,
        coverage=0.3,
        years=None,
        required=0.0,
    )

    assert status == common_pb2.CORRESPONDENCE_PARTIAL
    assert gap == common_pb2.GAP_REASON_PARTIAL


def test_skill_full_requires_coverage_threshold() -> None:
    policy = StatusPolicy()

    status, gap = policy.skill(relevance=1.0, coverage=0.70, years=None, required=0.0)
    assert status == common_pb2.CORRESPONDENCE_FULL
    assert gap is None

    status, gap = policy.skill(relevance=1.0, coverage=0.69, years=None, required=0.0)
    assert status == common_pb2.CORRESPONDENCE_PARTIAL
    assert gap == common_pb2.GAP_REASON_PARTIAL


def test_skill_threshold_is_policy_value() -> None:
    policy = StatusPolicy(skill_full_coverage=0.8)

    status, _ = policy.skill(relevance=1.0, coverage=0.75, years=None, required=0.0)
    assert status == common_pb2.CORRESPONDENCE_PARTIAL

    status, _ = policy.skill(relevance=1.0, coverage=0.8, years=None, required=0.0)
    assert status == common_pb2.CORRESPONDENCE_FULL


def test_skill_insufficient_tenure_has_priority_over_coverage() -> None:
    status, gap = StatusPolicy().skill(
        relevance=1.0,
        coverage=0.8,
        years=1.0,
        required=3.0,
    )

    assert status == common_pb2.CORRESPONDENCE_PARTIAL
    assert gap == common_pb2.GAP_REASON_INSUFFICIENT_TENURE


def test_skill_absent_by_relevance() -> None:
    status, gap = StatusPolicy().skill(
        relevance=0.4,
        coverage=0.4,
        years=5.0,
        required=0.0,
    )

    assert status == common_pb2.CORRESPONDENCE_ABSENT
    assert gap == common_pb2.GAP_REASON_ABSENT


def test_experience_full_requires_coverage_threshold() -> None:
    policy = StatusPolicy()

    status, _ = policy.experience(relevance=1.0, coverage=0.85, years=None, required=0.0)
    assert status == common_pb2.CORRESPONDENCE_FULL

    status, gap = policy.experience(relevance=1.0, coverage=0.84, years=None, required=0.0)
    assert status == common_pb2.CORRESPONDENCE_PARTIAL
    assert gap == common_pb2.GAP_REASON_PARTIAL


def test_education_and_text_use_coverage_bands() -> None:
    policy = StatusPolicy()

    assert policy.education(0.85)[0] == common_pb2.CORRESPONDENCE_FULL
    assert policy.education(0.50)[0] == common_pb2.CORRESPONDENCE_PARTIAL
    assert policy.education(0.49)[0] == common_pb2.CORRESPONDENCE_ABSENT

    assert policy.text(0.75)[0] == common_pb2.CORRESPONDENCE_FULL
    assert policy.text(0.35)[0] == common_pb2.CORRESPONDENCE_PARTIAL
    assert policy.text(0.34)[0] == common_pb2.CORRESPONDENCE_ABSENT
