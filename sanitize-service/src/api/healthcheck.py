from __future__ import annotations

from .config import get_settings
from .server import check_health


def main() -> None:
    settings = get_settings()
    host = "127.0.0.1" if settings.grpc_host in {"0.0.0.0", "::"} else settings.grpc_host
    if not check_health(host=host, port=settings.grpc_port):
        raise SystemExit(1)
