from functools import lru_cache

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


@lru_cache
def get_settings() -> Settings:
    return Settings()
