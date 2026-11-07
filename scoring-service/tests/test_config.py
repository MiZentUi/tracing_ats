from api.config import Settings


def test_defaults(monkeypatch) -> None:
    monkeypatch.delenv("SCORING_SERVICE_GRPC_PORT", raising=False)

    settings = Settings(_env_file=None)

    assert settings.grpc_host == "0.0.0.0"
    assert settings.grpc_port == 8082
    assert settings.log_level == "INFO"
    assert settings.max_workers == 10


def test_environment_override(monkeypatch) -> None:
    monkeypatch.setenv("SCORING_SERVICE_GRPC_PORT", "9090")
    monkeypatch.setenv("SCORING_SERVICE_LOG_LEVEL", "debug")

    settings = Settings(_env_file=None)

    assert settings.grpc_port == 9090
    assert settings.log_level == "debug"
