from __future__ import annotations

import logging

import grpc
import pytest
from conftest import REFERENCE_DATE, RESUME_TEXT, skill_requirement, vacancy
from grpc_health.v1 import health_pb2, health_pb2_grpc

from scoring_service.v1.models import messages_pb2


def score_request(text: str = RESUME_TEXT):
    return messages_pb2.ScoreResumeRequest(
        vacancy=vacancy({"req-java": skill_requirement("Java")}),
        resume=messages_pb2.UnprocessedResume(text=text),
        reference_date=REFERENCE_DATE,
    )


def test_inbound_events_are_paired_and_have_request_id(stub, caplog) -> None:
    with caplog.at_level(logging.INFO):
        stub.ScoreResume(score_request(), metadata=(("x-request-id", "req-42"),))

    assert "inbound request received" in caplog.text
    assert "inbound response sent" in caplog.text
    assert "request_id=req-42" in caplog.text
    assert "status=OK" in caplog.text


def test_logs_do_not_contain_resume_text_or_quotes(stub, caplog) -> None:
    with caplog.at_level(logging.INFO):
        stub.ScoreResume(score_request(), metadata=(("x-request-id", "req-43"),))

    assert RESUME_TEXT.strip() not in caplog.text
    assert "Навыки: Java" not in caplog.text
    assert "Ромашка" not in caplog.text


def test_failed_call_is_logged_once(stub, caplog) -> None:
    with caplog.at_level(logging.INFO), pytest.raises(grpc.RpcError):
        stub.ScoreResume(
            score_request("MOCK_SCORING_SCENARIO: unavailable"),
            metadata=(("x-request-id", "req-44"),),
        )

    assert "inbound request failed" in caplog.text
    assert "status=UNAVAILABLE" in caplog.text
    assert "request_id=req-44" in caplog.text
    assert caplog.text.count("inbound response sent") == 0


def test_health_check_does_not_log_at_info(channel, caplog) -> None:
    health_stub = health_pb2_grpc.HealthStub(channel)
    with caplog.at_level(logging.INFO):
        health_stub.Check(health_pb2.HealthCheckRequest(service=""))

    assert "inbound request received" not in caplog.text
