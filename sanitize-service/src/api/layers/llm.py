"""Зарезервированный LLM-слой.

Контракт и место в пайплайне уже есть: слой можно включить конфигурацией, не
меняя Java-шлюз. Реализация появится отдельной задачей; до неё запрос слоя
завершается UNIMPLEMENTED, а не молча пропускает PII.
"""

from __future__ import annotations

from .base import EntitySpan, LayerUnavailableError, MaskLayer


class LlmLayer:
    name = MaskLayer.LLM

    def __init__(self, model_name: str = "llm") -> None:
        self.model_name = model_name
        self.model_version = ""

    def detect(self, text: str, min_confidence: float = 0.0) -> list[EntitySpan]:
        raise LayerUnavailableError("LLM layer is reserved but not implemented yet")
