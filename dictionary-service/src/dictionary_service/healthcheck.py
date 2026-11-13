"""Healthcheck для Docker/K8s: проверяет gRPC health service."""

from __future__ import annotations

import os
import sys

import grpc
from grpc_health.v1 import health_pb2, health_pb2_grpc


def main() -> None:
    host = os.getenv("DICTIONARY_GRPC_HOST", "127.0.0.1")
    port = os.getenv("DICTIONARY_GRPC_PORT", "8084")
    channel = grpc.insecure_channel(f"{host}:{port}")
    try:
        stub = health_pb2_grpc.HealthStub(channel)
        response = stub.Check(
            health_pb2.HealthCheckRequest(service="dictionary_service.v1.DictionaryService"),
            timeout=2,
        )
        if response.status != health_pb2.HealthCheckResponse.SERVING:
            raise SystemExit(1)
    except Exception:
        raise SystemExit(1) from None


if __name__ == "__main__":
    sys.exit(main())
