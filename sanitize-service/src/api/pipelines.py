"""Сборка пайплайнов из конфигурации."""

from __future__ import annotations

from collections.abc import Callable
from typing import Any

import spacy

from .config import Settings
from .layers.llm import LlmLayer
from .layers.spacy import SpacyLayer
from .pipeline import Pipeline

NlpLoader = Callable[[str], Any]


def build_pipelines(
    settings: Settings,
    nlp_loader: NlpLoader | None = None,
) -> dict[str, Pipeline]:
    """Создать пайплайны. Модели spaCy загружаются на старте: ошибка видна сразу."""
    load = nlp_loader or spacy.load
    pipelines: dict[str, Pipeline] = {}

    for name, config in settings.pipelines.items():
        layers = []
        for layer_name in config.layers:
            if layer_name == "spacy":
                nlp = load(config.spacy_model)
                meta = getattr(nlp, "meta", {}) or {}
                layers.append(
                    SpacyLayer(
                        nlp=nlp,
                        model_name=config.spacy_model,
                        confidence=config.spacy_confidence,
                        model_version=str(meta.get("version", "")),
                        normalize_first_line_case=config.normalize_first_line_case,
                    )
                )
            elif layer_name == "llm":
                # Зарезервирован: место в пайплайне есть, реализации пока нет.
                layers.append(LlmLayer())
            else:  # pragma: no cover - Literal защищает на уровне pydantic
                raise ValueError(f"unknown layer {layer_name!r} in pipeline {name!r}")

        pipelines[name] = Pipeline(
            name=name,
            layers=tuple(layers),
            min_confidence=config.min_confidence,
        )

    return pipelines


__all__ = ["NlpLoader", "build_pipelines"]
