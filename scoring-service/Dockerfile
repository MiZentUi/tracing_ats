FROM ghcr.io/astral-sh/uv:0.13.7 AS uv

FROM python:3.14-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    UV_COMPILE_BYTECODE=1 \
    UV_LINK_MODE=copy \
    PATH="/app/.venv/bin:$PATH"

COPY --from=uv /uv /uvx /bin/

WORKDIR /app

# Контекст сборки — корень репозитория: нужны contracts/proto.
COPY scoring-service/pyproject.toml scoring-service/uv.lock scoring-service/README.md ./
RUN --mount=type=cache,target=/root/.cache/uv \
    uv sync --locked --no-install-project

COPY scoring-service/src ./src
COPY contracts/proto /contracts/proto

# Сгенерированные protobuf-модули не коммитятся: собираем их в образе.
RUN --mount=type=cache,target=/root/.cache/uv \
    cd /contracts/proto \
    && python -m grpc_tools.protoc -I. \
        --python_out=/app/src \
        --pyi_out=/app/src \
        --grpc_python_out=/app/src \
        scoring_service/v1/models/features/common.proto \
        scoring_service/v1/models/features/education.proto \
        scoring_service/v1/models/features/experience.proto \
        scoring_service/v1/models/features.proto \
        scoring_service/v1/models/features/skill.proto \
        scoring_service/v1/models/messages.proto \
        scoring_service/v1/scoring.proto

RUN --mount=type=cache,target=/root/.cache/uv \
    uv sync --locked --no-dev

EXPOSE 8082

HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=5 \
    CMD ["scoring-service-healthcheck"]

CMD ["scoring-service"]
