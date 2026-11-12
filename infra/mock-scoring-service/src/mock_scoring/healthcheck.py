"""Healthcheck для Docker: завершается с кодом 1, если gRPC-сервер не отвечает."""

from __future__ import annotations

from .config import Settings
from .server import check_health


def main() -> None:
    settings = Settings.from_env()
    host = "127.0.0.1" if settings.host in {"0.0.0.0", "::"} else settings.host
    if not check_health(host=host, port=settings.port):
        raise SystemExit(1)
