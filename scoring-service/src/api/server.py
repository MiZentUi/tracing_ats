from __future__ import annotations

import logging
from concurrent import futures
from dataclasses import dataclass

import grpc
from grpc_health.v1 import health, health_pb2, health_pb2_grpc

import scoring_service.v1.scoring_pb2_grpc as scoring_pb2_grpc

from .config import Settings, get_settings
from .scoring import ScoringService

logger = logging.getLogger(__name__)


@dataclass(frozen=True, slots=True)
class ServerHandle:
    server: grpc.Server
    health: health.HealthServicer
    port: int


def create_server(settings: Settings | None = None) -> ServerHandle:
    """Create a gRPC server with the health and scoring services."""
    settings = settings or get_settings()
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=settings.max_workers))
    health_servicer = health.HealthServicer()
    health_pb2_grpc.add_HealthServicer_to_server(health_servicer, server)

    health_servicer.set("", health_pb2.HealthCheckResponse.SERVING)
    health_servicer.set(
        "scoring_service.v1.ScoringService",
        health_pb2.HealthCheckResponse.SERVING,
    )

    scoring_servicer = ScoringService()
    scoring_pb2_grpc.add_ScoringServiceServicer_to_server(scoring_servicer, server)

    address = f"{settings.grpc_host}:{settings.grpc_port}"
    bound_port = server.add_insecure_port(address)
    if not bound_port:
        raise RuntimeError(f"failed to bind gRPC server to {address}")

    return ServerHandle(server=server, health=health_servicer, port=bound_port)


def run(settings: Settings | None = None) -> None:
    settings = settings or get_settings()
    logging.basicConfig(
        level=settings.log_level.upper(),
        format="%(asctime)s %(levelname)s %(name)s %(message)s",
    )

    handle = create_server(settings)
    handle.server.start()
    logger.info(
        "scoring-service gRPC listening on %s:%s",
        settings.grpc_host,
        handle.port,
    )

    try:
        handle.server.wait_for_termination()
    except KeyboardInterrupt:
        logger.info("stopping scoring-service")
    finally:
        handle.server.stop(grace=5)


def check_health(host: str = "127.0.0.1", port: int = 8082, timeout: float = 2.0) -> bool:
    with grpc.insecure_channel(f"{host}:{port}") as channel:
        stub = health_pb2_grpc.HealthStub(channel)
        try:
            response = stub.Check(  # type: ignore
                health_pb2.HealthCheckRequest(service=""), timeout=timeout
            )
        except grpc.RpcError:
            return False

    return response.status == health_pb2.HealthCheckResponse.SERVING
