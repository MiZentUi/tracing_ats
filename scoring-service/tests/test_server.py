import grpc
from grpc_health.v1 import health_pb2, health_pb2_grpc

from api.config import Settings
from api.server import create_server


def test_health_service_reports_serving() -> None:
    handle = create_server(Settings(grpc_host="127.0.0.1", grpc_port=0, _env_file=None))
    handle.server.start()

    try:
        with grpc.insecure_channel(f"127.0.0.1:{handle.port}") as channel:
            stub = health_pb2_grpc.HealthStub(channel)
            response = stub.Check(health_pb2.HealthCheckRequest(service=""), timeout=2)
            assert response.status == health_pb2.HealthCheckResponse.SERVING
    finally:
        handle.server.stop(grace=None)
