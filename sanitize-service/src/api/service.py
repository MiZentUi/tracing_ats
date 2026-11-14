"""gRPC-реализация sanitize_service.v1.SanitizeService."""

from __future__ import annotations

import grpc

from sanitize_service.v1 import sanitize_pb2, sanitize_pb2_grpc

from .layers.base import EntitySpan, EntityType, LayerUnavailableError, MaskLayer
from .pipeline import Pipeline

_PROTO_LAYER_TO_INTERNAL: dict[int, MaskLayer] = {
    sanitize_pb2.LAYER_SPACY: MaskLayer.SPACY,
    sanitize_pb2.LAYER_LLM: MaskLayer.LLM,
    sanitize_pb2.LAYER_REGEX: MaskLayer.REGEX,
}

_ENTITY_TYPE_TO_PROTO: dict[EntityType, int] = {
    EntityType.NAME: sanitize_pb2.PII_TYPE_NAME,
    EntityType.EMAIL: sanitize_pb2.PII_TYPE_EMAIL,
    EntityType.PHONE: sanitize_pb2.PII_TYPE_PHONE,
    EntityType.URL: sanitize_pb2.PII_TYPE_URL,
    EntityType.TELEGRAM: sanitize_pb2.PII_TYPE_TELEGRAM,
    EntityType.LINKEDIN: sanitize_pb2.PII_TYPE_LINKEDIN,
    EntityType.GITHUB: sanitize_pb2.PII_TYPE_GITHUB,
    EntityType.PASSPORT: sanitize_pb2.PII_TYPE_PASSPORT,
    EntityType.SNILS: sanitize_pb2.PII_TYPE_SNILS,
    EntityType.INN: sanitize_pb2.PII_TYPE_INN,
    EntityType.BIRTH_DATE: sanitize_pb2.PII_TYPE_BIRTH_DATE,
    EntityType.AGE: sanitize_pb2.PII_TYPE_AGE,
    EntityType.GENDER: sanitize_pb2.PII_TYPE_GENDER,
    EntityType.LOCATION: sanitize_pb2.PII_TYPE_LOCATION,
    EntityType.UNIVERSITY: sanitize_pb2.PII_TYPE_UNIVERSITY,
    EntityType.GRADUATION_YEAR: sanitize_pb2.PII_TYPE_GRADUATION_YEAR,
    EntityType.MARITAL_STATUS: sanitize_pb2.PII_TYPE_MARITAL_STATUS,
    EntityType.PHOTO: sanitize_pb2.PII_TYPE_PHOTO,
}

_INTERNAL_LAYER_TO_PROTO: dict[MaskLayer, int] = {
    MaskLayer.SPACY: sanitize_pb2.LAYER_SPACY,
    MaskLayer.LLM: sanitize_pb2.LAYER_LLM,
    MaskLayer.REGEX: sanitize_pb2.LAYER_REGEX,
}


class SanitizeServicer(sanitize_pb2_grpc.SanitizeServiceServicer):
    def __init__(
        self,
        pipelines: dict[str, Pipeline],
        default_pipeline: str,
        max_text_chars: int = 200_000,
    ) -> None:
        self._pipelines = pipelines
        self._default_pipeline = default_pipeline
        self._max_text_chars = max_text_chars

    def DetectPii(self, request, context) -> sanitize_pb2.DetectPiiResponse:
        text = request.text
        if not text:
            return sanitize_pb2.DetectPiiResponse(
                pipeline=self._pipeline_name(request),
            )

        if len(text) > self._max_text_chars:
            context.abort(
                grpc.StatusCode.INVALID_ARGUMENT,
                f"text is longer than {self._max_text_chars} characters",
            )

        pipeline_name = self._pipeline_name(request)
        pipeline = self._pipelines.get(pipeline_name)
        if pipeline is None:
            context.abort(
                grpc.StatusCode.INVALID_ARGUMENT,
                f"unknown pipeline {pipeline_name!r}",
            )

        if not 0.0 <= request.min_confidence <= 1.0:
            context.abort(
                grpc.StatusCode.INVALID_ARGUMENT,
                "min_confidence must be between 0 and 1",
            )

        requested = self._requested_layers(request, context)
        try:
            spans, used_layers = pipeline.detect(
                text,
                requested=requested,
                min_confidence=request.min_confidence,
            )
        except LayerUnavailableError as exc:
            context.abort(grpc.StatusCode.UNIMPLEMENTED, str(exc))
        except ValueError as exc:
            context.abort(grpc.StatusCode.INVALID_ARGUMENT, str(exc))

        return sanitize_pb2.DetectPiiResponse(
            entities=[_to_proto(span) for span in spans],
            pipeline=pipeline.name,
            layers=[_INTERNAL_LAYER_TO_PROTO[layer.name] for layer in used_layers],
            model=pipeline.model,
            model_version=pipeline.model_version,
        )

    def _pipeline_name(self, request) -> str:
        return request.pipeline or self._default_pipeline

    @staticmethod
    def _requested_layers(request, context) -> list[MaskLayer]:
        requested: list[MaskLayer] = []
        for value in request.layers:
            if value == sanitize_pb2.LAYER_UNSPECIFIED:
                continue
            layer = _PROTO_LAYER_TO_INTERNAL.get(value)
            if layer is None:
                context.abort(
                    grpc.StatusCode.INVALID_ARGUMENT,
                    f"unknown layer value {value}",
                )
            requested.append(layer)
        return requested


def _to_proto(span: EntitySpan) -> sanitize_pb2.PiiEntity:
    return sanitize_pb2.PiiEntity(
        type=_ENTITY_TYPE_TO_PROTO[span.type],
        start=span.start,
        end=span.end,
        confidence=span.confidence,
        layer=_INTERNAL_LAYER_TO_PROTO[span.layer],
    )


__all__ = ["SanitizeServicer"]
