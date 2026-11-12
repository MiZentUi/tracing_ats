"""Сборка LLM-клиента из настроек сервиса."""

from __future__ import annotations

from ..config import Settings
from .client import (
    LlmClient,
    LlmError,
    LlmInvalidResponse,
    LlmUnavailable,
    OpenAiCompatibleClient,
)
from .mock import MockLlmClient

__all__ = [
    "LlmClient",
    "LlmError",
    "LlmInvalidResponse",
    "LlmUnavailable",
    "MockLlmClient",
    "OpenAiCompatibleClient",
    "build_llm_client",
]


def build_llm_client(settings: Settings) -> LlmClient:
    if settings.llm_provider == "mock":
        return MockLlmClient()

    return OpenAiCompatibleClient(
        base_url=settings.llm_base_url,
        model=settings.llm_model,
        api_key=settings.llm_api_key.get_secret_value(),
        timeout_seconds=settings.llm_timeout_seconds,
        max_retries=settings.llm_max_retries,
        max_output_tokens=settings.llm_max_output_tokens,
    )
