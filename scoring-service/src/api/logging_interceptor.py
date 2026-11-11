from __future__ import annotations

import logging
import time
from typing import Any

import grpc

logger = logging.getLogger(__name__)

REQUEST_ID_KEY = "x-request-id"


class RequestLoggingInterceptor(grpc.ServerInterceptor):
    """Граница входящего gRPC: request_id, приём запроса и отправка ответа.

    Тела сообщений не логируются. Для незавершённых обработчиков пишется
    ``inbound_request_failed`` вместо ``inbound_response_sent``.
    """

    def intercept_service(
        self,
        continuation: Any,
        handler_call_details: grpc.HandlerCallDetails,
    ) -> grpc.RpcMethodHandler | None:
        metadata = dict(handler_call_details.invocation_metadata or ())
        request_id = metadata.get(REQUEST_ID_KEY, "-")
        method = handler_call_details.method
        started_at = time.perf_counter()
        logger.info(
            "inbound_request_received transport=grpc method=%s request_id=%s",
            method,
            request_id,
        )

        handler = continuation(handler_call_details)
        if handler is None or handler.unary_unary is None:
            return handler

        behavior = handler.unary_unary

        def unary(request: Any, context: grpc.ServicerContext) -> Any:
            try:
                response = behavior(request, context)
            except Exception as exc:
                logger.warning(
                    "inbound_request_failed transport=grpc method=%s request_id=%s"
                    " error=%s elapsed_ms=%.1f",
                    method,
                    request_id,
                    type(exc).__name__,
                    _elapsed_ms(started_at),
                )
                raise
            logger.info(
                "inbound_response_sent transport=grpc method=%s status=%s request_id=%s"
                " elapsed_ms=%.1f",
                method,
                _status(context),
                request_id,
                _elapsed_ms(started_at),
            )
            return response

        return grpc.unary_unary_rpc_method_handler(
            unary,
            request_deserializer=handler.request_deserializer,
            response_serializer=handler.response_serializer,
        )


def _status(context: grpc.ServicerContext) -> str:
    code = context.code()
    return "OK" if code is None else code.name


def _elapsed_ms(started_at: float) -> float:
    return (time.perf_counter() - started_at) * 1000.0
