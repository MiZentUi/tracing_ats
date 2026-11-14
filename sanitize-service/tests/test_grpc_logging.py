from __future__ import annotations

import logging
from collections.abc import Iterator
from contextlib import contextmanager

import grpc
import pytest

from api.config import Settings
from api.layers import EntitySpan, EntityType, MaskLayer
from api.pipeline import Pipeline
from api.server import create_server
from sanitize_service.v1 import sanitize_pb2, sanitize_pb2_grpc

METHOD = "/sanitize_service.v1.SanitizeService/DetectPii"


class FakeLayer:
    name = MaskLayer.SPACY
    model_name = "fake-model"
    model_version = "1.0"

    def detect(self, text: str, min_confidence: float = 0.0) -> list[EntitySpan]:
        return [EntitySpan(EntityType.NAME, 0, 4, 0.7, MaskLayer.SPACY)]


@contextmanager
def _running() -> Iterator[sanitize_pb2_grpc.SanitizeServiceStub]:
    handle = create_server(
        Settings(grpc_host="127.0.0.1", grpc_port=0, _env_file=None),
        pipelines={"ru": Pipeline(name="ru", layers=(FakeLayer(),))},
    )
    handle.server.start()
    try:
        with grpc.insecure_channel(f"127.0.0.1:{handle.port}") as channel:
            yield sanitize_pb2_grpc.SanitizeServiceStub(channel)
    finally:
        handle.server.stop(grace=None)


def test_detect_pii_logs_method_start_and_end(caplog) -> None:
    with caplog.at_level(logging.INFO, logger="api.interceptors"), _running() as stub:
        stub.DetectPii(sanitize_pb2.DetectPiiRequest(text="Иван"), timeout=2)

    assert f"grpc request start method={METHOD} request_id=-" in caplog.text
    assert f"grpc request end method={METHOD} request_id=- status=OK" in caplog.text
    assert "elapsed_ms=" in caplog.text
    # Текст и найденные сущности в лог не попадают.
    assert "Иван" not in caplog.text


def test_request_id_from_metadata_is_logged(caplog) -> None:
    with caplog.at_level(logging.INFO, logger="api.interceptors"), _running() as stub:
        stub.DetectPii(
            sanitize_pb2.DetectPiiRequest(text="Иван"),
            timeout=2,
            metadata=(("x-request-id", "req-42"),),
        )

    assert "request_id=req-42" in caplog.text


def test_error_status_is_logged(caplog) -> None:
    with (
        caplog.at_level(logging.WARNING, logger="api.interceptors"),
        _running() as stub,
        pytest.raises(grpc.RpcError) as exc_info,
    ):
        stub.DetectPii(
            sanitize_pb2.DetectPiiRequest(text="Иван", pipeline="de"),
            timeout=2,
        )

    assert exc_info.value.code() == grpc.StatusCode.INVALID_ARGUMENT
    assert f"grpc request end method={METHOD} request_id=- status=INVALID_ARGUMENT" in caplog.text
