from __future__ import annotations

import grpc
import pytest
from conftest import REFERENCE_DATE, RESUME_TEXT, skill_requirement, vacancy

from scoring_service.v1.models import features_pb2, messages_pb2
from scoring_service.v1.models.features import common_pb2


def request(text: str = RESUME_TEXT):
    return messages_pb2.ScoreResumeRequest(
        vacancy=vacancy(
            {
                "req-java": skill_requirement("Java"),
                "req-go": skill_requirement("Go"),
            }
        ),
        resume=messages_pb2.UnprocessedResume(text=text),
        reference_date=REFERENCE_DATE,
    )


def test_features_are_keyed_by_requirements(stub) -> None:
    response = stub.ScoreResume(request())

    assert set(response.features) == {"req-java", "req-go"}
    assert response.features["req-java"].coverage == 1.0
    assert response.features["req-java"].WhichOneof("resume") == "skill"
    assert response.features["req-go"].coverage == 0.0
    assert response.features["req-go"].WhichOneof("resume") is None


def test_general_score_within_bounds(stub) -> None:
    score = stub.ScoreResume(request()).general_score

    assert 0.0 <= score.score_low <= score.score <= score.score_high <= 1.0
    assert score.tenure == 3


def test_score_resume_is_deterministic(stub) -> None:
    first = stub.ScoreResume(request()).SerializeToString(deterministic=True)
    second = stub.ScoreResume(request()).SerializeToString(deterministic=True)

    assert first == second


def test_score_resume_empty_text_is_invalid_argument(stub) -> None:
    with pytest.raises(grpc.RpcError) as error:
        stub.ScoreResume(request(text=""))
    assert error.value.code() == grpc.StatusCode.INVALID_ARGUMENT


def test_scenario_from_text_marker(stub) -> None:
    with pytest.raises(grpc.RpcError) as error:
        stub.ScoreResume(request(text="MOCK_SCORING_SCENARIO: unavailable\nJava"))
    assert error.value.code() == grpc.StatusCode.UNAVAILABLE


def test_scenario_from_metadata(stub) -> None:
    with pytest.raises(grpc.RpcError) as error:
        stub.ScoreResume(request(), metadata=(("x-mock-scenario", "internal"),))
    assert error.value.code() == grpc.StatusCode.INTERNAL


def test_requirement_without_feature_is_not_stated(stub) -> None:
    parsed = stub.ExtractResume(
        messages_pb2.ExtractResumeRequest(
            resume=messages_pb2.UnprocessedResume(text=RESUME_TEXT),
            reference_date=REFERENCE_DATE,
        )
    ).parsed
    response = stub.EvaluateResumeMatch(
        messages_pb2.EvaluateResumeMatchRequest(
            resume=parsed,
            unprocessed_resume=messages_pb2.UnprocessedResume(text=RESUME_TEXT),
            vacancy=vacancy({"req-empty": features_pb2.FeatureRequirement()}),
            reference_date=REFERENCE_DATE,
        )
    )

    assert response.matches["req-empty"].status == common_pb2.CORRESPONDENCE_NOT_STATED
