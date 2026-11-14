from __future__ import annotations

from collections.abc import Iterator
from contextlib import contextmanager

import grpc
import pytest

from api.config import Settings
from api.layers import EntitySpan, EntityType, LayerUnavailableError, MaskLayer
from api.pipeline import Pipeline
from api.server import create_server
from sanitize_service.v1 import sanitize_pb2, sanitize_pb2_grpc

METHOD = "/sanitize_service.v1.SanitizeService/DetectPii"


class FakeLayer:
    def __init__(
        self,
        name: MaskLayer,
        spans: list[EntitySpan] | None = None,
        model_name: str = "fake-model",
        model_version: str = "1.0",
    ) -> None:
        self.name = name
        self.spans = spans or []
        self.model_name = model_name
        self.model_version = model_version

    def detect(self, text: str, min_confidence: float = 0.0) -> list[EntitySpan]:
        return [span for span in self.spans if span.confidence >= min_confidence]


class UnavailableLayer(FakeLayer):
    def detect(self, text: str, min_confidence: float = 0.0) -> list[EntitySpan]:
        raise LayerUnavailableError("LLM layer is reserved")


def _pipeline() -> Pipeline:
    return Pipeline(
        name="ru",
        layers=(
            FakeLayer(
                MaskLayer.SPACY,
                [
                    EntitySpan(EntityType.NAME, 0, 4, 0.7, MaskLayer.SPACY),
                    EntitySpan(EntityType.LOCATION, 6, 12, 0.7, MaskLayer.SPACY),
                ],
            ),
        ),
        min_confidence=0.0,
    )


@contextmanager
def _running(
    pipelines: dict[str, Pipeline] | None = None,
) -> Iterator[sanitize_pb2_grpc.SanitizeServiceStub]:
    handle = create_server(
        Settings(grpc_host="127.0.0.1", grpc_port=0, _env_file=None),
        pipelines=pipelines or {"ru": _pipeline()},
    )
    handle.server.start()
    try:
        with grpc.insecure_channel(f"127.0.0.1:{handle.port}") as channel:
            yield sanitize_pb2_grpc.SanitizeServiceStub(channel)
    finally:
        handle.server.stop(grace=None)


def test_detect_pii_returns_entities_and_audit_metadata() -> None:
    with _running() as stub:
        response = stub.DetectPii(
            sanitize_pb2.DetectPiiRequest(text="Иван, Москва"),
            timeout=2,
        )

    assert [(e.type, e.start, e.end, e.layer) for e in response.entities] == [
        (sanitize_pb2.PII_TYPE_NAME, 0, 4, sanitize_pb2.LAYER_SPACY),
        (sanitize_pb2.PII_TYPE_LOCATION, 6, 12, sanitize_pb2.LAYER_SPACY),
    ]
    assert response.pipeline == "ru"
    assert list(response.layers) == [sanitize_pb2.LAYER_SPACY]
    assert response.model == "spacy:fake-model"
    assert response.model_version == "1.0"


def test_unknown_pipeline_is_invalid_argument() -> None:
    with _running() as stub, pytest.raises(grpc.RpcError) as exc_info:
        stub.DetectPii(
            sanitize_pb2.DetectPiiRequest(text="Иван", pipeline="de"),
            timeout=2,
        )

    assert exc_info.value.code() == grpc.StatusCode.INVALID_ARGUMENT


def test_reserved_llm_layer_is_unimplemented() -> None:
    pipeline = Pipeline(name="ru", layers=(UnavailableLayer(MaskLayer.LLM),))
    with _running({"ru": pipeline}) as stub, pytest.raises(grpc.RpcError) as exc_info:
        stub.DetectPii(
            sanitize_pb2.DetectPiiRequest(text="Иван", layers=[sanitize_pb2.LAYER_LLM]),
            timeout=2,
        )

    assert exc_info.value.code() == grpc.StatusCode.UNIMPLEMENTED


def test_layer_not_enabled_in_pipeline_is_invalid_argument() -> None:
    with _running() as stub, pytest.raises(grpc.RpcError) as exc_info:
        stub.DetectPii(
            sanitize_pb2.DetectPiiRequest(text="Иван", layers=[sanitize_pb2.LAYER_REGEX]),
            timeout=2,
        )

    assert exc_info.value.code() == grpc.StatusCode.INVALID_ARGUMENT


def test_min_confidence_out_of_range_is_invalid_argument() -> None:
    with _running() as stub, pytest.raises(grpc.RpcError) as exc_info:
        stub.DetectPii(
            sanitize_pb2.DetectPiiRequest(text="Иван", min_confidence=1.5),
            timeout=2,
        )

    assert exc_info.value.code() == grpc.StatusCode.INVALID_ARGUMENT


def test_empty_text_returns_empty_response() -> None:
    with _running() as stub:
        response = stub.DetectPii(sanitize_pb2.DetectPiiRequest(text=""), timeout=2)

    assert list(response.entities) == []
    assert response.pipeline == "ru"


def test_too_long_text_is_invalid_argument() -> None:
    handle = create_server(
        Settings(
            grpc_host="127.0.0.1",
            grpc_port=0,
            max_text_chars=5,
            _env_file=None,
        ),
        pipelines={"ru": _pipeline()},
    )
    handle.server.start()
    try:
        with (
            grpc.insecure_channel(f"127.0.0.1:{handle.port}") as channel,
            pytest.raises(grpc.RpcError) as exc_info,
        ):
            stub = sanitize_pb2_grpc.SanitizeServiceStub(channel)
            stub.DetectPii(sanitize_pb2.DetectPiiRequest(text="Иван Петров"), timeout=2)
    finally:
        handle.server.stop(grace=None)

    assert exc_info.value.code() == grpc.StatusCode.INVALID_ARGUMENT


def test_health_reports_serving() -> None:
    from grpc_health.v1 import health_pb2, health_pb2_grpc

    handle = create_server(
        Settings(grpc_host="127.0.0.1", grpc_port=0, _env_file=None),
        pipelines={"ru": _pipeline()},
    )
    handle.server.start()
    try:
        with grpc.insecure_channel(f"127.0.0.1:{handle.port}") as channel:
            health_stub = health_pb2_grpc.HealthStub(channel)
            response = health_stub.Check(health_pb2.HealthCheckRequest(service=""), timeout=2)
    finally:
        handle.server.stop(grace=None)

    assert response.status == health_pb2.HealthCheckResponse.SERVING
