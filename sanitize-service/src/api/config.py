from __future__ import annotations

from functools import lru_cache
from typing import Literal, Self

from pydantic import BaseModel, Field, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

# Слой маскировки. `llm` зарезервирован: контракт и фабрика готовы, реализации
# пока нет. Значение повторяет sanitize_service.v1.Layer без SPACY/LLM-префикса.
LayerName = Literal["spacy", "llm"]


class PipelineConfig(BaseModel):
    """Один именованный пайплайн маскировки.

    Пайплайны задаются в конфигурации сервиса, а не в коде: новый язык или
    другая модель добавляются переменной окружения без пересборки.
    """

    # Модель spaCy. Устанавливается отдельно от пакета (см. README/Dockerfile).
    # md точнее sm на русских ФИО; lg не даёт выигрыша на проверенных примерах.
    spacy_model: str = "ru_core_news_md"

    # Порядок слоёв. Слои складываются; пересечения разрешает Java.
    layers: list[LayerName] = Field(default_factory=lambda: ["spacy"])

    # spaCy NER не отдаёт вероятность. Для слоя используется фиксированное
    # значение, которое участвует в пороге min_confidence.
    spacy_confidence: float = Field(default=0.7, ge=0.0, le=1.0)

    # Прогонять первую строку капсом через модель в title case: spaCy иначе
    # пропускает ФИО в заголовке резюме. Offsets остаются исходными.
    normalize_first_line_case: bool = True

    # Порог пайплайна по умолчанию. Ненулевой min_confidence в запросе
    # заменяет его.
    min_confidence: float = Field(default=0.0, ge=0.0, le=1.0)


class Settings(BaseSettings):
    """Runtime configuration. All values come from the environment."""

    model_config = SettingsConfigDict(
        env_prefix="SANITIZE_SERVICE_",
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    grpc_host: str = "0.0.0.0"
    grpc_port: int = 8083
    log_level: str = "INFO"
    max_workers: int = 4

    # Защита от гигантских запросов. Текст резюме заметно короче.
    max_text_chars: int = Field(default=200_000, gt=0)

    default_pipeline: str = "ru"
    pipelines: dict[str, PipelineConfig] = Field(default_factory=lambda: {"ru": PipelineConfig()})

    @model_validator(mode="after")
    def _check_pipelines(self) -> Self:
        if not self.pipelines:
            raise ValueError("SANITIZE_SERVICE_PIPELINES must contain at least one pipeline")
        if self.default_pipeline not in self.pipelines:
            raise ValueError(
                f"default pipeline {self.default_pipeline!r} is not configured; "
                f"available: {sorted(self.pipelines)}"
            )
        return self


@lru_cache
def get_settings() -> Settings:
    return Settings()
