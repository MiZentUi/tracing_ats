"""Настройки заглушки. Значения читаются из окружения."""

from __future__ import annotations

import os
from dataclasses import dataclass


@dataclass(frozen=True, slots=True)
class Settings:
    """Runtime configuration of the mock server."""

    host: str = "0.0.0.0"
    port: int = 8082
    log_level: str = "INFO"
    max_workers: int = 10
    slow_seconds: float = 1.0

    @classmethod
    def from_env(cls) -> Settings:
        return cls(
            host=os.environ.get("MOCK_SCORING_GRPC_HOST", "0.0.0.0"),
            port=int(os.environ.get("MOCK_SCORING_GRPC_PORT", "8082")),
            log_level=os.environ.get("MOCK_SCORING_LOG_LEVEL", "INFO"),
            max_workers=int(os.environ.get("MOCK_SCORING_MAX_WORKERS", "10")),
            slow_seconds=float(os.environ.get("MOCK_SCORING_SLOW_SECONDS", "1")),
        )
