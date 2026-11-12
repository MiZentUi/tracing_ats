"""LLM-клиент на официальном SDK `openai`.

`base_url` настраивается, поэтому один клиент подходит для OpenRouter и
локальных OpenAI-совместимых серверов. Запросы и ответы не логируются:
в них может быть текст резюме. Логируются только метаданные, статус, задержка
и расход токенов.
"""

from __future__ import annotations

import logging
import time
from typing import Protocol

from openai import (
    APIConnectionError,
    APIError,
    APIStatusError,
    APITimeoutError,
    OpenAI,
)

logger = logging.getLogger(__name__)


class LlmError(Exception):
    """Базовая ошибка LLM."""


class LlmUnavailable(LlmError):
    """Провайдер недоступен: сеть, таймаут, 429/5xx после повторов."""


class LlmInvalidResponse(LlmError):
    """Ответ провайдера не соответствует ожидаемому конверту."""


class LlmClient(Protocol):
    """Минимальный контракт: system + user → сырой текст ответа."""

    @property
    def model(self) -> str: ...

    def complete(self, system: str, user: str) -> str: ...


class OpenAiCompatibleClient:
    """Обёртка над `openai.OpenAI` для OpenAI-совместимых провайдеров."""

    def __init__(
        self,
        base_url: str,
        model: str,
        api_key: str = "",
        timeout_seconds: float = 600.0,
        max_retries: int = 1,
        # max_output_tokens: int = 50000,
        temperature: float = 0.0,
        client: OpenAI | None = None,
    ) -> None:
        self._model = model
        self._base_url = base_url
        self._timeout_seconds = timeout_seconds
        self._max_retries = max_retries
        # self._max_output_tokens = max_output_tokens
        self._temperature = temperature
        self._client = client or OpenAI(
            # Локальным OpenAI-совместимым серверам ключ не нужен, но SDK
            # требует непустое значение.
            api_key=api_key or "not-needed",
            base_url=base_url,
            timeout=timeout_seconds,
            max_retries=max_retries,
        )

    @property
    def model(self) -> str:
        return self._model

    def complete(self, system: str, user: str) -> str:
        started = time.perf_counter()
        logger.info(
            "llm request start model=%s base_url=%s timeout_s=%.1f max_retries=%d ",
            # "max_output_tokens=%d",
            self._model,
            self._base_url,
            self._timeout_seconds,
            self._max_retries,
            # self._max_output_tokens,
        )

        try:
            response = self._client.chat.completions.create(
                model=self._model,
                temperature=self._temperature,
                # max_tokens=self._max_output_tokens,
                response_format={"type": "json_object"},
                messages=[
                    {"role": "system", "content": system},
                    {"role": "user", "content": user},
                ],
            )
        except APITimeoutError as exc:
            self._log_failure("timeout", exc, started)
            raise LlmUnavailable("LLM request timed out") from exc
        except APIConnectionError as exc:
            self._log_failure("connection", exc, started)
            raise LlmUnavailable("LLM connection failed") from exc
        except APIStatusError as exc:
            self._log_failure("http", exc, started)
            raise LlmUnavailable(f"LLM provider returned HTTP {exc.status_code}") from exc
        except (APIError, ValueError) as exc:
            # ValueError — например, JSONDecodeError, если провайдер прислал
            # битый JSON при content-type: application/json.
            self._log_failure("invalid-response", exc, started)
            raise LlmInvalidResponse("LLM response could not be parsed") from exc

        elapsed_ms = (time.perf_counter() - started) * 1000
        # Провайдер может ответить 200 и content-type text/plain; тогда SDK
        # вернёт строку вместо ChatCompletion. Это такой же невалидный ответ,
        # как пустой choices.
        choices = getattr(response, "choices", None)
        if not choices:
            logger.warning(
                "llm request invalid model=%s response_id=%s reason=no_choices elapsed_ms=%.1f",
                self._model,
                getattr(response, "id", "-"),
                elapsed_ms,
            )
            raise LlmInvalidResponse("LLM response has no choices")

        choice = response.choices[0]
        content = choice.message.content
        usage = response.usage
        logger.info(
            "llm request ok model=%s response_id=%s response_model=%s finish_reason=%s "
            "elapsed_ms=%.1f prompt_tokens=%s completion_tokens=%s total_tokens=%s "
            "content_chars=%d",
            self._model,
            response.id,
            response.model,
            choice.finish_reason,
            elapsed_ms,
            usage.prompt_tokens if usage else None,
            usage.completion_tokens if usage else None,
            usage.total_tokens if usage else None,
            len(content) if content else 0,
        )

        if not content or not content.strip():
            logger.warning(
                "llm request invalid model=%s response_id=%s reason=empty_content elapsed_ms=%.1f",
                self._model,
                response.id,
                elapsed_ms,
            )
            raise LlmInvalidResponse("LLM returned empty content")

        return content

    def _log_failure(self, reason: str, exc: Exception, started: float) -> None:
        status = str(exc.status_code) if isinstance(exc, APIStatusError) else "-"
        request_id = (exc.request_id or "-") if isinstance(exc, APIStatusError) else "-"
        logger.warning(
            "llm request failed model=%s reason=%s error=%s status=%s request_id=%s "
            "elapsed_ms=%.1f",
            self._model,
            reason,
            type(exc).__name__,
            status,
            request_id,
            (time.perf_counter() - started) * 1000,
        )
