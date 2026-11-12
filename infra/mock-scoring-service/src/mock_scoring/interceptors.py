"""gRPC-интерсептор логирования входящих вызовов.

Пишутся только метод, статус, `request_id` и длительность. Тела запросов и
ответов не логируются: в них текст резюме и цитаты.
"""

from __future__ import annotations

import logging
import time
from collections.abc import Callable
from typing import Any

import grpc

logger = logging.getLogger(__name__)

_QUIET_PREFIXES = ("/grpc.health.v1.Health/", "/grpc.reflection.")


class RequestLoggingInterceptor(grpc.ServerInterceptor):
    """Парные события `inbound request received` / `inbound response sent`."""

    def intercept_service(
        self,
        continuation: Callable[[grpc.HandlerCallDetails], grpc.RpcMethodHandler | None],
        handler_call_details: grpc.HandlerCallDetails,
    ) -> grpc.RpcMethodHandler | None:
        method = handler_call_details.method
        request_id = _request_id(handler_call_details)
        handler = continuation(handler_call_details)
        if handler is None:
            logger.warning(
                "inbound request rejected method=%s request_id=%s status=UNIMPLEMENTED",
                method,
                request_id,
            )
            return None

        if handler.unary_unary is None or method.startswith(_QUIET_PREFIXES):
            # Health-check и reflection не шумят на INFO.
            return handler

        inner = handler.unary_unary

        def unary_unary(request: Any, context: grpc.ServicerContext) -> Any:
            started = time.perf_counter()
            logger.info("inbound request received method=%s request_id=%s", method, request_id)
            try:
                response = inner(request, context)
            except Exception as exc:
                logger.warning(
                    "inbound request failed method=%s status=%s request_id=%s "
                    "error=%s elapsed_ms=%.1f",
                    method,
                    _status(context),
                    request_id,
                    type(exc).__name__,
                    (time.perf_counter() - started) * 1000,
                )
                raise
            logger.info(
                "inbound response sent method=%s status=%s request_id=%s elapsed_ms=%.1f",
                method,
                _status(context),
                request_id,
                (time.perf_counter() - started) * 1000,
            )
            return response

        return grpc.unary_unary_rpc_method_handler(
            unary_unary,
            request_deserializer=handler.request_deserializer,
            response_serializer=handler.response_serializer,
        )


def _status(context: grpc.ServicerContext) -> str:
    code = context.code()  # type: ignore[attr-defined]
    return code.name if code is not None else "OK"


def _request_id(handler_call_details: grpc.HandlerCallDetails) -> str:
    for key, value in handler_call_details.invocation_metadata:
        if key.lower() == "x-request-id":
            return value
    return "-"
