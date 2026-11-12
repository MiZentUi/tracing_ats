"""Сборка gRPC-сервера заглушки: health, reflection и `ScoringService`."""

from __future__ import annotations

import logging
from concurrent import futures
from dataclasses import dataclass

import grpc
from grpc_health.v1 import health, health_pb2, health_pb2_grpc
from grpc_reflection.v1alpha import reflection

from scoring_service.v1 import scoring_pb2, scoring_pb2_grpc

from .config import Settings
from .interceptors import RequestLoggingInterceptor
from .service import MockScoringService

logger = logging.getLogger(__name__)


@dataclass(frozen=True, slots=True)
class ServerHandle:
    server: grpc.Server
    health: health.HealthServicer
    port: int


def create_server(
    settings: Settings | None = None,
    servicer: MockScoringService | None = None,
    port: int | None = None,
) -> ServerHandle:
    settings = settings or Settings.from_env()
    server = grpc.server(
        futures.ThreadPoolExecutor(max_workers=settings.max_workers),
        interceptors=[RequestLoggingInterceptor()],
    )

    health_servicer = health.HealthServicer()
    health_pb2_grpc.add_HealthServicer_to_server(health_servicer, server)
    health_servicer.set("", health_pb2.HealthCheckResponse.SERVING)
    health_servicer.set("scoring_service.v1.ScoringService", health_pb2.HealthCheckResponse.SERVING)

    scoring_pb2_grpc.add_ScoringServiceServicer_to_server(
        servicer or MockScoringService(settings), server
    )

    services = [
        *[item.full_name for item in scoring_pb2.DESCRIPTOR.services_by_name.values()],
        *[item.full_name for item in health_pb2.DESCRIPTOR.services_by_name.values()],
        reflection.SERVICE_NAME,
    ]
    reflection.enable_server_reflection(services, server)

    address = f"{settings.host}:{settings.port if port is None else port}"
    bound_port = server.add_insecure_port(address)
    if not bound_port:
        raise RuntimeError(f"failed to bind gRPC server to {address}")

    return ServerHandle(server=server, health=health_servicer, port=bound_port)


def run(settings: Settings | None = None) -> None:
    settings = settings or Settings.from_env()
    logging.basicConfig(
        level=settings.log_level.upper(),
        format="%(asctime)s %(levelname)s %(name)s %(message)s",
    )

    handle = create_server(settings)
    handle.server.start()
    logger.info("mock scoring-service gRPC listening on %s:%s", settings.host, handle.port)

    try:
        handle.server.wait_for_termination()
    except KeyboardInterrupt:
        logger.info("stopping mock scoring-service")
    finally:
        handle.server.stop(grace=5)


def check_health(host: str = "127.0.0.1", port: int = 8082, timeout: float = 2.0) -> bool:
    with grpc.insecure_channel(f"{host}:{port}") as channel:
        stub = health_pb2_grpc.HealthStub(channel)
        try:
            response = stub.Check(health_pb2.HealthCheckRequest(service=""), timeout=timeout)
        except grpc.RpcError:
            return False
    return response.status == health_pb2.HealthCheckResponse.SERVING
