from __future__ import annotations

import grpc
import pytest
from conftest import REFERENCE_DATE, RESUME_TEXT

from scoring_service.v1.models import messages_pb2
from scoring_service.v1.models.features import education_pb2


def test_extract_returns_features_with_exact_quotes(stub) -> None:
    response = stub.ExtractResume(
        messages_pb2.ExtractResumeRequest(
            resume=messages_pb2.UnprocessedResume(text=RESUME_TEXT),
            reference_date=REFERENCE_DATE,
        )
    )
    parsed = response.parsed

    assert response.dropped == 0
    assert parsed.schema_version == 1
    assert parsed.skills
    assert parsed.education
    assert parsed.experience

    for skill in parsed.skills.values():
        assert skill.evidence.text
        assert RESUME_TEXT[skill.evidence.start_index : skill.evidence.end_index] == (
            skill.evidence.text
        )
    for item in parsed.experience.values():
        for metric in item.metrics:
            assert RESUME_TEXT[metric.start_index : metric.end_index] == metric.text


def test_extract_keeps_tenure_only_for_single_skill_line(stub) -> None:
    parsed = stub.ExtractResume(
        messages_pb2.ExtractResumeRequest(
            resume=messages_pb2.UnprocessedResume(text=RESUME_TEXT),
            reference_date=REFERENCE_DATE,
        )
    ).parsed

    kafka = next(item for item in parsed.skills.values() if item.normalized_name == "Kafka")
    java = next(item for item in parsed.skills.values() if item.normalized_name == "Java")
    assert kafka.tenure == 3
    assert not java.HasField("tenure")


def test_extract_detects_experience_and_education(stub) -> None:
    parsed = stub.ExtractResume(
        messages_pb2.ExtractResumeRequest(
            resume=messages_pb2.UnprocessedResume(text=RESUME_TEXT),
            reference_date=REFERENCE_DATE,
        )
    ).parsed

    experience = next(iter(parsed.experience.values()))
    assert experience.begin.year == 2019
    assert experience.end.year == 2022

    education = next(iter(parsed.education.values()))
    assert education.level == education_pb2.EDUCATION_LEVEL_BACHELOR


def test_extract_empty_text_is_invalid_argument(stub) -> None:
    with pytest.raises(grpc.RpcError) as error:
        stub.ExtractResume(
            messages_pb2.ExtractResumeRequest(
                resume=messages_pb2.UnprocessedResume(text="   "),
                reference_date=REFERENCE_DATE,
            )
        )
    assert error.value.code() == grpc.StatusCode.INVALID_ARGUMENT


def test_empty_scenario_returns_no_features(stub) -> None:
    response = stub.ExtractResume(
        messages_pb2.ExtractResumeRequest(
            resume=messages_pb2.UnprocessedResume(
                text="MOCK_SCORING_SCENARIO: empty\nJava, Python"
            ),
            reference_date=REFERENCE_DATE,
        )
    )
    assert not response.parsed.skills
    assert not response.parsed.education
    assert not response.parsed.experience
