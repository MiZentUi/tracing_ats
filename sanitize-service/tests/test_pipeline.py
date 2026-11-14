from __future__ import annotations

import pytest

from api.layers import EntitySpan, EntityType, LayerUnavailableError, MaskLayer
from api.pipeline import Pipeline


class FakeLayer:
    def __init__(
        self,
        name: MaskLayer,
        spans: list[EntitySpan] | None = None,
        model_name: str = "fake",
        model_version: str = "1",
    ) -> None:
        self.name = name
        self.spans = spans or []
        self.model_name = model_name
        self.model_version = model_version
        self.seen_threshold: float | None = None

    def detect(self, text: str, min_confidence: float = 0.0) -> list[EntitySpan]:
        self.seen_threshold = min_confidence
        return [span for span in self.spans if span.confidence >= min_confidence]


class UnavailableLayer(FakeLayer):
    def detect(self, text: str, min_confidence: float = 0.0) -> list[EntitySpan]:
        raise LayerUnavailableError("reserved")


def test_deduplicates_and_sorts_spans() -> None:
    first = FakeLayer(
        MaskLayer.SPACY,
        [
            EntitySpan(EntityType.LOCATION, 10, 15, 0.7, MaskLayer.SPACY),
            EntitySpan(EntityType.NAME, 0, 4, 0.7, MaskLayer.SPACY),
        ],
    )
    second = FakeLayer(
        MaskLayer.REGEX,
        [
            EntitySpan(EntityType.NAME, 0, 4, 0.9, MaskLayer.REGEX),
            EntitySpan(EntityType.EMAIL, 6, 9, 0.9, MaskLayer.REGEX),
        ],
    )
    pipeline = Pipeline(name="ru", layers=(first, second))

    spans, used = pipeline.detect("Иван, a@b, Москва")

    assert [(s.type, s.start, s.end) for s in spans] == [
        (EntityType.NAME, 0, 4),
        (EntityType.EMAIL, 6, 9),
        (EntityType.LOCATION, 10, 15),
    ]
    assert used == (first, second)


def test_requested_layers_limit_the_pipeline() -> None:
    spacy = FakeLayer(MaskLayer.SPACY)
    regex = FakeLayer(MaskLayer.REGEX)
    pipeline = Pipeline(name="ru", layers=(spacy, regex))

    _, used = pipeline.detect("text", requested=[MaskLayer.REGEX])

    assert used == (regex,)


def test_unknown_requested_layer_is_rejected() -> None:
    pipeline = Pipeline(name="ru", layers=(FakeLayer(MaskLayer.SPACY),))

    with pytest.raises(ValueError, match="not enabled"):
        pipeline.detect("text", requested=[MaskLayer.LLM])


def test_pipeline_threshold_is_applied() -> None:
    layer = FakeLayer(
        MaskLayer.SPACY,
        [EntitySpan(EntityType.NAME, 0, 4, 0.5, MaskLayer.SPACY)],
    )
    pipeline = Pipeline(name="ru", layers=(layer,), min_confidence=0.6)

    spans, _ = pipeline.detect("Иван")

    assert spans == []
    assert layer.seen_threshold == 0.6


def test_reserved_layer_propagates_unavailable() -> None:
    pipeline = Pipeline(name="ru", layers=(UnavailableLayer(MaskLayer.LLM),))

    with pytest.raises(LayerUnavailableError):
        pipeline.detect("text")


def test_model_metadata_is_reported() -> None:
    pipeline = Pipeline(
        name="ru",
        layers=(FakeLayer(MaskLayer.SPACY, model_name="ru_core_news_sm", model_version="3.8.0"),),
    )

    assert pipeline.model == "spacy:ru_core_news_sm"
    assert pipeline.model_version == "3.8.0"
