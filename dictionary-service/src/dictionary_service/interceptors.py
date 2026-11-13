"""gRPC-интерсептор: correlation id и парные события inbound."""

from __future__ import annotations

import logging
import time
import uuid
from typing import Any

import grpc

logger = logging.getLogger(__name__)


def _request_id(metadata: tuple[tuple[str, str], ...]) -> str:
    for key, value in metadata:
        if key.lower() in {"x-request-id", "request-id"} and value:
            return value
    return uuid.uuid4().hex[:16]


class RequestLoggingInterceptor(grpc.ServerInterceptor):
    """Один inbound-лог на запрос, без тел и PII."""

    def intercept_service(self, continuation: Any, handler_call_details: Any) -> Any:
        method = handler_call_details.method
        request_id = _request_id(handler_call_details.invocation_metadata or ())
        handler = continuation(handler_call_details)
        if handler is None:
            return None
        if handler.unary_unary is None:
            return handler

        def unary_unary(request: Any, context: grpc.ServicerContext) -> Any:
            started = time.monotonic()
            logger.info("inbound request received method=%s request_id=%s", method, request_id)
            try:
                response = handler.unary_unary(request, context)
            except Exception:
                elapsed = (time.monotonic() - started) * 1000
                logger.warning(
                    "inbound request failed method=%s request_id=%s elapsed_ms=%.1f",
                    method,
                    request_id,
                    elapsed,
                )
                raise
            elapsed = (time.monotonic() - started) * 1000
            logger.info(
                "inbound response sent method=%s status=%s request_id=%s elapsed_ms=%.1f",
                method,
                context.code() if context.code() is not None else "OK",
                request_id,
                elapsed,
            )
            return response

        return grpc.unary_unary_rpc_method_handler(
            unary_unary,
            request_deserializer=handler.request_deserializer,
            response_serializer=handler.response_serializer,
        )
