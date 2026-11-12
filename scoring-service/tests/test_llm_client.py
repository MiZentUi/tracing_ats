from __future__ import annotations

import json
import logging

import httpx2
import pytest
from openai import OpenAI

from api.extraction.quotes import QuoteIndex
from api.extraction.schema import (
    ITEM_QUOTE_MAX_CHARS,
    SKILL_QUOTE_MAX_CHARS,
    ParsedResume,
)
from api.llm.client import LlmInvalidResponse, LlmUnavailable, OpenAiCompatibleClient
from api.llm.mock import MockLlmClient


def _completion(content: str) -> dict:
    return {
        "id": "chatcmpl-test",
        "object": "chat.completion",
        "created": 0,
        "model": "test-model",
        "choices": [
            {
                "index": 0,
                "message": {"role": "assistant", "content": content},
                "finish_reason": "stop",
            }
        ],
        "usage": {"prompt_tokens": 10, "completion_tokens": 5, "total_tokens": 15},
    }


def _openai(handler, max_retries: int = 0) -> OpenAI:
    return OpenAI(
        api_key="test",
        base_url="http://llm/v1",
        max_retries=max_retries,
        http_client=httpx2.Client(transport=httpx2.MockTransport(handler)),
    )


def _client(handler, max_retries: int = 0) -> OpenAiCompatibleClient:
    return OpenAiCompatibleClient(
        base_url="http://llm/v1",
        model="test-model",
        client=_openai(handler, max_retries=max_retries),
    )


def test_client_returns_message_content(caplog) -> None:
    def handler(request: httpx2.Request) -> httpx2.Response:
        body = json.loads(request.content)
        assert body["max_tokens"] == 4096
        return httpx2.Response(200, json=_completion("{}"))

    with caplog.at_level(logging.INFO, logger="api.llm.client"):
        result = _client(handler).complete("system", "user")

    assert result == "{}"
    assert "llm request ok" in caplog.text
    assert "total_tokens=15" in caplog.text


def test_client_retries_server_error() -> None:
    calls: list[httpx2.Request] = []

    def handler(request: httpx2.Request) -> httpx2.Response:
        calls.append(request)
        if len(calls) == 1:
            return httpx2.Response(500, json={"error": {"message": "boom"}})
        return httpx2.Response(200, json=_completion("{}"))

    assert _client(handler, max_retries=1).complete("system", "user") == "{}"
    assert len(calls) == 2


def test_client_maps_http_error_to_unavailable(caplog) -> None:
    def handler(request: httpx2.Request) -> httpx2.Response:
        return httpx2.Response(401, json={"error": {"message": "no"}})

    with (
        caplog.at_level(logging.WARNING, logger="api.llm.client"),
        pytest.raises(LlmUnavailable),
    ):
        _client(handler).complete("system", "user")

    assert "reason=http" in caplog.text
    assert "status=401" in caplog.text


def test_client_maps_connection_error_to_unavailable(caplog) -> None:
    def handler(request: httpx2.Request) -> httpx2.Response:
        raise httpx2.ConnectError("down")

    with (
        caplog.at_level(logging.WARNING, logger="api.llm.client"),
        pytest.raises(LlmUnavailable),
    ):
        _client(handler).complete("system", "user")

    assert "reason=connection" in caplog.text


def test_client_maps_timeout_to_unavailable(caplog) -> None:
    def handler(request: httpx2.Request) -> httpx2.Response:
        raise httpx2.ReadTimeout("slow")

    with (
        caplog.at_level(logging.WARNING, logger="api.llm.client"),
        pytest.raises(LlmUnavailable),
    ):
        _client(handler).complete("system", "user")

    assert "reason=timeout" in caplog.text


def test_client_rejects_empty_content(caplog) -> None:
    def handler(request: httpx2.Request) -> httpx2.Response:
        return httpx2.Response(200, json=_completion(""))

    with (
        caplog.at_level(logging.WARNING, logger="api.llm.client"),
        pytest.raises(LlmInvalidResponse),
    ):
        _client(handler).complete("system", "user")

    assert "reason=empty_content" in caplog.text


def test_client_rejects_malformed_payload(caplog) -> None:
    def handler(request: httpx2.Request) -> httpx2.Response:
        return httpx2.Response(
            200,
            text="not json",
            headers={"content-type": "application/json"},
        )

    with (
        caplog.at_level(logging.WARNING, logger="api.llm.client"),
        pytest.raises(LlmInvalidResponse),
    ):
        _client(handler).complete("system", "user")

    assert "reason=invalid-response" in caplog.text


def test_mock_client_returns_only_quotes_from_text() -> None:
    text = "Python developer\nУниверситет МГУ, 2019\nООО Ромашка, 2020-2023"
    raw = MockLlmClient().complete("system", f"<resume_text>\n{text}\n</resume_text>")

    parsed = ParsedResume.model_validate_json(raw)
    index = QuoteIndex(text)

    assert parsed.skills
    assert parsed.education
    assert parsed.experience
    assert all(index.locate(item.quote) for item in parsed.skills)
    assert all(index.locate(item.quote) for item in parsed.education)
    assert all(index.locate(item.quote) for item in parsed.experience)
    assert all(len(item.quote) <= SKILL_QUOTE_MAX_CHARS for item in parsed.skills)
    assert all(len(item.quote) <= ITEM_QUOTE_MAX_CHARS for item in parsed.education)
    assert all(len(item.quote) <= ITEM_QUOTE_MAX_CHARS for item in parsed.experience)


def test_mock_client_shortens_long_lines_to_schema_limits() -> None:
    long_line = "Python developer " + ("x" * 500)
    text = f"{long_line}\nУниверситет МГУ, 2019\nООО Ромашка, 2020-2023"
    raw = MockLlmClient().complete("system", f"<resume_text>\n{text}\n</resume_text>")

    parsed = ParsedResume.model_validate_json(raw)
    index = QuoteIndex(text)

    assert parsed.skills
    assert all(len(item.quote) <= SKILL_QUOTE_MAX_CHARS for item in parsed.skills)
    assert all(index.locate(item.quote) for item in parsed.skills)
