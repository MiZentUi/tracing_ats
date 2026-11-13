"""Конфигурация dictionary-service. Секреты и адреса — только из окружения."""

from __future__ import annotations

import hashlib
import json
import os
from dataclasses import dataclass


@dataclass(frozen=True, slots=True)
class Settings:
    grpc_host: str
    grpc_port: int
    database_url: str
    log_level: str
    service_token: str
    suggestion_threshold: float
    max_batch: int

    @property
    def config_hash(self) -> str:
        payload = json.dumps(
            {
                "suggestion_threshold": self.suggestion_threshold,
                "max_batch": self.max_batch,
                "normalization": "nfkc-lower-edge-dot-yo-v1",
                "homoglyphs": "v1",
            },
            sort_keys=True,
        )
        return hashlib.sha256(payload.encode("utf-8")).hexdigest()[:12]


def load_settings() -> Settings:
    return Settings(
        grpc_host=os.getenv("DICTIONARY_GRPC_HOST", "0.0.0.0"),
        grpc_port=int(os.getenv("DICTIONARY_GRPC_PORT", "8084")),
        database_url=os.getenv(
            "DICTIONARY_DATABASE_URL",
            "postgresql://ats:ats@localhost:5432/ats",
        ),
        log_level=os.getenv("DICTIONARY_LOG_LEVEL", "INFO"),
        service_token=os.getenv("DICTIONARY_SERVICE_TOKEN", ""),
        suggestion_threshold=float(os.getenv("DICTIONARY_SUGGESTION_THRESHOLD", "0.6")),
        max_batch=int(os.getenv("DICTIONARY_MAX_BATCH", "2000")),
    )
