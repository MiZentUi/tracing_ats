from __future__ import annotations

import logging
from concurrent import futures
from dataclasses import dataclass

import grpc
from grpc_health.v1 import health, health_pb2, health_pb2_grpc
from grpc_reflection.v1alpha import reflection

import sanitize_service.v1.sanitize_pb2_grpc as sanitize_pb2_grpc
from sanitize_service.v1 import sanitize_pb2

from .config import Settings, get_settings
from .interceptors import RequestLoggingInterceptor
from .pipeline import Pipeline
from .pipelines import build_pipelines
from .service import SanitizeServicer

logger = logging.getLogger(__name__)

SERVICE_NAME = "sanitize_service.v1.SanitizeService"


@dataclass(frozen=True, slots=True)
class ServerHandle:
    server: grpc.Server
    health: health.HealthServicer
    port: int


def create_server(
    settings: Settings | None = None,
    pipelines: dict[str, Pipeline] | None = None,
) -> ServerHandle:
    """Create a gRPC server with the health and sanitize services."""
    settings = settings or get_settings()
    server = grpc.server(
        futures.ThreadPoolExecutor(max_workers=settings.max_workers),
        interceptors=[RequestLoggingInterceptor()],
        options=[
            # Резюме целиком, с запасом. Лимит защищает от случайных гигантских запросов.
            ("grpc.max_receive_message_length", 8 * 1024 * 1024),
        ],
    )
    health_servicer = health.HealthServicer()
    health_pb2_grpc.add_HealthServicer_to_server(health_servicer, server)

    health_servicer.set("", health_pb2.HealthCheckResponse.SERVING)
    health_servicer.set(SERVICE_NAME, health_pb2.HealthCheckResponse.SERVING)

    pipelines = pipelines or build_pipelines(settings)
    servicer = SanitizeServicer(
        pipelines,
        settings.default_pipeline,
        max_text_chars=settings.max_text_chars,
    )
    sanitize_pb2_grpc.add_SanitizeServiceServicer_to_server(servicer, server)

    grpc_services = [
        *[x.full_name for x in sanitize_pb2.DESCRIPTOR.services_by_name.values()],
        *[x.full_name for x in health_pb2.DESCRIPTOR.services_by_name.values()],
        reflection.SERVICE_NAME,
    ]
    reflection.enable_server_reflection(grpc_services, server)

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
        "sanitize-service gRPC listening on %s:%s default_pipeline=%s",
        settings.grpc_host,
        handle.port,
        settings.default_pipeline,
    )

    try:
        handle.server.wait_for_termination()
    except KeyboardInterrupt:
        logger.info("stopping sanitize-service")
    finally:
        handle.server.stop(grace=5)


def check_health(host: str = "127.0.0.1", port: int = 8083, timeout: float = 2.0) -> bool:
    with grpc.insecure_channel(f"{host}:{port}") as channel:
        stub = health_pb2_grpc.HealthStub(channel)
        try:
            response = stub.Check(  # type: ignore
                health_pb2.HealthCheckRequest(service=""), timeout=timeout
            )
        except grpc.RpcError:
            return False

    return response.status == health_pb2.HealthCheckResponse.SERVING
