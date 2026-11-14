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
COPY sanitize-service/pyproject.toml sanitize-service/uv.lock sanitize-service/README.md ./
RUN --mount=type=cache,target=/root/.cache/uv \
    uv sync --locked --no-install-project

COPY sanitize-service/src ./src
COPY contracts/proto /contracts/proto

# Сгенерированные protobuf-модули не коммитятся: собираем их в образе.
RUN --mount=type=cache,target=/root/.cache/uv \
    cd /contracts/proto \
    && python -m grpc_tools.protoc -I. \
        --python_out=/app/src \
        --pyi_out=/app/src \
        --grpc_python_out=/app/src \
        sanitize_service/v1/sanitize.proto

RUN --mount=type=cache,target=/root/.cache/uv \
    uv sync --locked --no-dev

# spaCy-модель ставится после uv sync: иначе uv удалит пакет, которого нет в lock.
# Модель — локальная, в сеть во время работы сервис не ходит.
RUN --mount=type=cache,target=/root/.cache/uv \
    uv pip install --python /app/.venv/bin/python \
        https://github.com/explosion/spacy-models/releases/download/ru_core_news_md-3.8.0/ru_core_news_md-3.8.0-py3-none-any.whl

EXPOSE 8083

HEALTHCHECK --interval=10s --timeout=3s --start-period=20s --retries=5 \
    CMD ["sanitize-service-healthcheck"]

CMD ["sanitize-service"]
