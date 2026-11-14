import pytest
from pydantic import ValidationError

from api.config import Settings


def test_defaults(monkeypatch) -> None:
    monkeypatch.delenv("SCORING_SERVICE_GRPC_PORT", raising=False)

    settings = Settings(_env_file=None)

    assert settings.grpc_host == "0.0.0.0"
    assert settings.grpc_port == 8082
    assert settings.log_level == "INFO"
    assert settings.max_workers == 10
    assert settings.tenure_weight == 0.1
    assert settings.llm_max_output_tokens == 4096


def test_environment_override(monkeypatch) -> None:
    monkeypatch.setenv("SCORING_SERVICE_GRPC_PORT", "9090")
    monkeypatch.setenv("SCORING_SERVICE_LOG_LEVEL", "debug")
    monkeypatch.setenv("SCORING_SERVICE_TENURE_WEIGHT", "0.25")
    monkeypatch.setenv("SCORING_SERVICE_LLM_MAX_OUTPUT_TOKENS", "2048")

    settings = Settings(_env_file=None)

    assert settings.grpc_port == 9090
    assert settings.log_level == "debug"
    assert settings.tenure_weight == 0.25
    assert settings.llm_max_output_tokens == 2048


def test_llm_defaults_to_mock(monkeypatch) -> None:
    monkeypatch.delenv("SCORING_SERVICE_LLM_PROVIDER", raising=False)

    settings = Settings(_env_file=None)

    assert settings.llm_provider == "mock"


def test_openai_provider_requires_base_url_and_model(monkeypatch) -> None:
    monkeypatch.delenv("SCORING_SERVICE_LLM_BASE_URL", raising=False)
    monkeypatch.delenv("SCORING_SERVICE_LLM_MODEL", raising=False)

    with pytest.raises(ValidationError):
        Settings(_env_file=None, llm_provider="openai")


def test_openai_provider_accepts_explicit_configuration(monkeypatch) -> None:
    monkeypatch.delenv("SCORING_SERVICE_LLM_API_KEY", raising=False)

    settings = Settings(
        _env_file=None,
        llm_provider="openai",
        llm_base_url="http://127.0.0.1:8089/v1",
        llm_model="test-model",
    )

    assert settings.llm_api_key.get_secret_value() == ""
