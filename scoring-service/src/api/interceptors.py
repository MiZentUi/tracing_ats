"""gRPC-интерсептор логирования запросов.

Пишутся только полное имя метода, статус и длительность. Тела запросов
и ответов не логируются: в них может быть текст резюме.
"""

from __future__ import annotations

import logging
import time
from collections.abc import Callable
from typing import Any

import grpc

logger = logging.getLogger(__name__)


class RequestLoggingInterceptor(grpc.ServerInterceptor):
    """Логирует начало и завершение unary-unary вызова."""

    def intercept_service(
        self,
        continuation: Callable[[grpc.HandlerCallDetails], grpc.RpcMethodHandler | None],
        handler_call_details: grpc.HandlerCallDetails,
    ) -> grpc.RpcMethodHandler | None:
        method = handler_call_details.method
        handler = continuation(handler_call_details)
        if handler is None:
            logger.warning("grpc request rejected method=%s status=UNIMPLEMENTED", method)
            return None

        if handler.unary_unary is None:
            logger.info("grpc streaming request begins")
            # Сервис пока содержит только unary-unary; streaming не оборачиваем.
            return handler

        inner = handler.unary_unary

        def unary_unary(request: Any, context: grpc.ServicerContext) -> Any:
            started = time.perf_counter()
            logger.info("grpc request start method=%s", method)
            try:
                response = inner(request, context)
            except Exception as exc:
                code = context.code() # type: ignore
                status = code.name if code is not None else "UNKNOWN"
                logger.warning(
                    "grpc request end method=%s status=%s error=%s elapsed_ms=%.1f",
                    method,
                    status,
                    type(exc).__name__,
                    (time.perf_counter() - started) * 1000,
                )
                raise

            code = context.code() # type: ignore
            status = code.name if code is not None else "OK"
            logger.info(
                "grpc request end method=%s status=%s elapsed_ms=%.1f",
                method,
                status,
                (time.perf_counter() - started) * 1000,
            )
            return response

        return grpc.unary_unary_rpc_method_handler(
            unary_unary,
            request_deserializer=handler.request_deserializer,
            response_serializer=handler.response_serializer,
        )
