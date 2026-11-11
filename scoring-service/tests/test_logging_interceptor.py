from __future__ import annotations

import logging
from concurrent import futures

import grpc
import pytest

from api.logging_interceptor import RequestLoggingInterceptor


def _start_server() -> tuple[grpc.Server, int]:
    handler = grpc.method_handlers_generic_handler(
        "test.Service",
        {"Call": grpc.unary_unary_rpc_method_handler(lambda request, context: b"ok")},
    )
    server = grpc.server(
        futures.ThreadPoolExecutor(max_workers=1),
        interceptors=(RequestLoggingInterceptor(),),
    )
    server.add_generic_rpc_handlers((handler,))
    port = server.add_insecure_port("127.0.0.1:0")
    server.start()
    return server, port


def test_logs_inbound_events_with_request_id(caplog: pytest.LogCaptureFixture) -> None:
    server, port = _start_server()
    try:
        with (
            caplog.at_level(logging.INFO, logger="api.logging_interceptor"),
            grpc.insecure_channel(f"127.0.0.1:{port}") as channel,
        ):
            call = channel.unary_unary("/test.Service/Call")
            assert call(b"resume text", metadata=(("x-request-id", "req-42"),)) == b"ok"
    finally:
        server.stop(grace=None)

    assert (
        "inbound_request_received transport=grpc method=/test.Service/Call request_id=req-42"
        in caplog.text
    )
    assert "inbound_response_sent transport=grpc method=/test.Service/Call status=OK" in caplog.text
    assert "request_id=req-42" in caplog.text
    assert "resume text" not in caplog.text


def test_logs_failed_handler_without_response_event(caplog: pytest.LogCaptureFixture) -> None:
    def failing(request: bytes, context: grpc.ServicerContext) -> bytes:
        raise RuntimeError("boom")

    handler = grpc.method_handlers_generic_handler(
        "test.Service",
        {"Call": grpc.unary_unary_rpc_method_handler(failing)},
    )
    server = grpc.server(
        futures.ThreadPoolExecutor(max_workers=1),
        interceptors=(RequestLoggingInterceptor(),),
    )
    server.add_generic_rpc_handlers((handler,))
    port = server.add_insecure_port("127.0.0.1:0")
    server.start()
    try:
        with (
            caplog.at_level(logging.INFO, logger="api.logging_interceptor"),
            grpc.insecure_channel(f"127.0.0.1:{port}") as channel,
        ):
            call = channel.unary_unary("/test.Service/Call")
            with pytest.raises(grpc.RpcError):
                call(b"", metadata=(("x-request-id", "req-err"),))
    finally:
        server.stop(grace=None)

    assert (
        "inbound_request_failed transport=grpc method=/test.Service/Call request_id=req-err"
        in caplog.text
    )
    assert "inbound_response_sent" not in caplog.text
