from __future__ import annotations

import logging
from collections.abc import Iterator
from contextlib import contextmanager

import grpc
import pytest

from api.config import Settings
from api.extraction import ResumeExtractor
from api.llm.mock import MockLlmClient
from api.server import create_server
from scoring_service.v1 import scoring_pb2_grpc
from scoring_service.v1.models import features_pb2, messages_pb2
from scoring_service.v1.models.features import common_pb2, skill_pb2

METHOD = "/scoring_service.v1.ScoringService/ScoreResume"


def _request(text: str) -> messages_pb2.ScoreResumeRequest:
    return messages_pb2.ScoreResumeRequest(
        vacancy=messages_pb2.Vacancy(
            requirements={
                "python": features_pb2.FeatureRequirement(
                    criticality=common_pb2.FEATURE_CRITICALITY_REQUIRED,
                    skill=skill_pb2.SkillRequirement(name="Python"),
                )
            }
        ),
        resume=messages_pb2.UnprocessedResume(text=text),
    )


@contextmanager
def _running() -> Iterator[scoring_pb2_grpc.ScoringServiceStub]:
    handle = create_server(
        Settings(grpc_host="127.0.0.1", grpc_port=0, _env_file=None),
        extractor=ResumeExtractor(MockLlmClient()),
    )
    handle.server.start()
    try:
        with grpc.insecure_channel(f"127.0.0.1:{handle.port}") as channel:
            yield scoring_pb2_grpc.ScoringServiceStub(channel)
    finally:
        handle.server.stop(grace=None)


def test_score_resume_logs_method_start_and_end(caplog) -> None:
    with caplog.at_level(logging.INFO, logger="api.interceptors"), _running() as stub:
        stub.ScoreResume(_request("Python"), timeout=2)

    assert f"grpc request start method={METHOD} request_id=-" in caplog.text
    assert f"grpc request end method={METHOD} request_id=- status=OK" in caplog.text
    assert "elapsed_ms=" in caplog.text


def test_score_resume_logs_request_id_from_metadata(caplog) -> None:
    with caplog.at_level(logging.INFO, logger="api.interceptors"), _running() as stub:
        stub.ScoreResume(
            _request("Python"),
            timeout=2,
            metadata=(("x-request-id", "req-42"),),
        )

    assert "request_id=req-42" in caplog.text


def test_score_resume_logs_error_status(caplog) -> None:
    with (
        caplog.at_level(logging.WARNING, logger="api.interceptors"),
        _running() as stub,
        pytest.raises(grpc.RpcError) as exc_info,
    ):
        stub.ScoreResume(_request("   "), timeout=2)

    assert exc_info.value.code() == grpc.StatusCode.INVALID_ARGUMENT
    assert f"grpc request end method={METHOD} request_id=- status=INVALID_ARGUMENT" in caplog.text
    assert "error=" in caplog.text
