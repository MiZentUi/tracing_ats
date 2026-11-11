from functools import lru_cache
from typing import Literal, Self

from pydantic import Field, SecretStr, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


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

    # LLM. `mock` — детерминированная заглушка без сети, `openai` — любой
    # OpenAI-совместимый `/chat/completions` (OpenRouter, локальная модель).
    llm_provider: Literal["mock", "openai"] = "mock"
    llm_base_url: str = ""
    llm_api_key: SecretStr = SecretStr("")
    llm_model: str = ""
    llm_timeout_seconds: float = Field(default=30.0, gt=0, le=300)
    llm_max_retries: int = Field(default=1, ge=0, le=5)

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
