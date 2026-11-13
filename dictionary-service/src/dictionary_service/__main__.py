"""Точка входа dictionary-service."""

from __future__ import annotations

import logging
import os
from concurrent import futures

import grpc
from grpc_health.v1 import health, health_pb2, health_pb2_grpc
from grpc_reflection.v1alpha import reflection

from dictionary_service.v1 import dictionary_pb2 as pb
from dictionary_service.v1 import dictionary_pb2_grpc as pb_grpc

from .config import load_settings
from .interceptors import RequestLoggingInterceptor
from .postgres_store import PostgresStore
from .seed import SEED
from .server import DictionaryServicer
from .service import DictionaryService
from .store import InMemoryStore

logger = logging.getLogger(__name__)


def _build_service() -> DictionaryService:
    settings = load_settings()
    store_kind = os.getenv("DICTIONARY_STORE", "postgres")
    store = InMemoryStore() if store_kind == "memory" else PostgresStore(settings.database_url)
    if store_kind == "memory":
        store.seed(SEED)
    service = DictionaryService(
        store,
        config_hash=settings.config_hash,
        embedding_model_id=os.getenv("DICTIONARY_EMBEDDING_MODEL_ID", ""),
    )
    service.seed_if_empty()
    return service


def serve() -> None:
    settings = load_settings()
    logging.basicConfig(
        level=getattr(logging, settings.log_level.upper(), logging.INFO),
        format="%(asctime)s %(levelname)s %(name)s %(message)s",
    )
    service = _build_service()
    server = grpc.server(
        futures.ThreadPoolExecutor(max_workers=8),
        interceptors=(RequestLoggingInterceptor(),),
        maximum_concurrent_rpcs=64,
    )
    pb_grpc.add_DictionaryServiceServicer_to_server(DictionaryServicer(service, settings), server)
    health_servicer = health.HealthServicer()
    health_pb2_grpc.add_HealthServicer_to_server(health_servicer, server)
    for name in (
        pb.DESCRIPTOR.services_by_name["DictionaryService"].full_name,
        "grpc.health.v1.Health",
    ):
        health_servicer.set(name, health_pb2.HealthCheckResponse.SERVING)
    reflection.enable_server_reflection(
        (pb.DESCRIPTOR.services_by_name["DictionaryService"].full_name,), server
    )
    server.add_insecure_port(f"{settings.grpc_host}:{settings.grpc_port}")
    server.start()
    logger.info(
        "dictionary service started host=%s port=%s store=%s",
        settings.grpc_host,
        settings.grpc_port,
        os.getenv("DICTIONARY_STORE", "postgres"),
    )
    server.wait_for_termination()


def main() -> None:
    serve()


if __name__ == "__main__":
    main()
