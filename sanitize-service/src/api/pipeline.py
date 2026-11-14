"""Именованный пайплайн: набор слоёв и порог уверенности."""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from typing import Protocol

from .layers.base import EntitySpan, LayerUnavailableError, MaskLayer


class Layer(Protocol):
    name: MaskLayer
    model_name: str
    model_version: str

    def detect(self, text: str, min_confidence: float = 0.0) -> list[EntitySpan]: ...


@dataclass(frozen=True, slots=True)
class Pipeline:
    name: str
    layers: tuple[Layer, ...]
    min_confidence: float = 0.0

    @property
    def available_layers(self) -> tuple[MaskLayer, ...]:
        return tuple(layer.name for layer in self.layers)

    def select(self, requested: Sequence[MaskLayer]) -> tuple[Layer, ...]:
        if not requested:
            return self.layers

        available = set(self.available_layers)
        unknown = [layer for layer in requested if layer not in available]
        if unknown:
            names = ", ".join(layer.value for layer in unknown)
            raise ValueError(f"layer(s) {names} are not enabled in pipeline {self.name!r}")
        wanted = set(requested)
        return tuple(layer for layer in self.layers if layer.name in wanted)

    def detect(
        self,
        text: str,
        requested: Sequence[MaskLayer] = (),
        min_confidence: float = 0.0,
    ) -> tuple[list[EntitySpan], tuple[Layer, ...]]:
        """Применить слои и вернуть сущности вместе с фактически использованными слоями."""
        selected = self.select(requested)
        # 0 — порог пайплайна; ненулевое значение запроса заменяет его.
        threshold = min_confidence if min_confidence > 0.0 else self.min_confidence
        found: dict[tuple[str, int, int], EntitySpan] = {}
        for layer in selected:
            for span in layer.detect(text, threshold):
                if not span.is_valid(len(text)):
                    continue
                found.setdefault((span.type.value, span.start, span.end), span)
        ordered = sorted(found.values(), key=lambda s: (s.start, s.end, s.type.value))
        return ordered, selected

    @property
    def model(self) -> str:
        return ",".join(f"{layer.name.value.lower()}:{layer.model_name}" for layer in self.layers)

    @property
    def model_version(self) -> str:
        versions = [layer.model_version for layer in self.layers if layer.model_version]
        return ",".join(dict.fromkeys(versions))


__all__ = ["Layer", "LayerUnavailableError", "Pipeline"]
