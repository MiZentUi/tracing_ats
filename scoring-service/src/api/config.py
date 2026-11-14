from functools import lru_cache
from typing import Literal, Self

from pydantic import Field, SecretStr, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

DEFAULT_TENURE_WEIGHT = 0.1
DEFAULT_SKILL_FULL_COVERAGE = 0.70


class Settings(BaseSettings):
    """Runtime configuration. All values come from the environment."""

    model_config = SettingsConfigDict(
        env_prefix="SCORING_SERVICE_",
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    grpc_host: str = "0.0.0.0"
    grpc_port: int = 8082
    log_level: str = "INFO"
    max_workers: int = 10

    # Доля общего стажа в Score. При vacancy.tenure = 0 слагаемое отключено.
    tenure_weight: float = Field(default=DEFAULT_TENURE_WEIGHT, ge=0.0, le=1.0)

    # Минимальный coverage навыка для статуса FULL. Вынесено в конфигурацию,
    # потому что статус влияет на отчёт и решения HR, не только на Score.
    skill_full_coverage: float = Field(default=DEFAULT_SKILL_FULL_COVERAGE, ge=0.0, le=1.0)

    # LLM. `mock` — детерминированная заглушка без сети, `openai` — любой
    # OpenAI-совместимый `/chat/completions` (OpenRouter, локальная модель).
    llm_provider: Literal["mock", "openai"] = "mock"
    llm_base_url: str = ""
    llm_api_key: SecretStr = SecretStr("")
    llm_model: str = ""
    llm_timeout_seconds: float = Field(default=30.0, gt=0, le=300)
    llm_max_retries: int = Field(default=1, ge=0, le=5)
    llm_max_output_tokens: int = Field(default=4096, ge=256, le=32768)

    # Словарь навыков. При недоступности скоринг деградирует до точного ключа.
    dictionary_enabled: bool = True
    dictionary_target: str = "localhost:8084"
    dictionary_deadline_seconds: float = Field(default=2.0, gt=0, le=10)
    dictionary_as_of_version: str = ""

    ignore_invalid_quotes: bool = False

    @model_validator(mode="after")
    def _check_llm(self) -> Self:
        if self.llm_provider == "openai":
            if not self.llm_base_url.strip():
                raise ValueError(
                    "SCORING_SERVICE_LLM_BASE_URL is required when llm_provider=openai"
                )
            if not self.llm_model.strip():
                raise ValueError("SCORING_SERVICE_LLM_MODEL is required when llm_provider=openai")
        return self


@lru_cache
def get_settings() -> Settings:
    return Settings()
