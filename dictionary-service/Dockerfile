FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PYTHONPATH=/app/src \
    DICTIONARY_GRPC_HOST=0.0.0.0 \
    DICTIONARY_GRPC_PORT=8084

WORKDIR /app

COPY dictionary-service/pyproject.toml dictionary-service/README.md ./
COPY dictionary-service/src ./src
COPY contracts/proto /contracts/proto
COPY dictionary-service/protoc.sh ./protoc.sh

RUN pip install --no-cache-dir . grpcio-tools \
    && cd /contracts/proto \
    && python -m grpc_tools.protoc -I. \
        --python_out=/app/src \
        --pyi_out=/app/src \
        --grpc_python_out=/app/src \
        dictionary_service/v1/dictionary.proto

EXPOSE 8084

HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=5 \
    CMD ["dictionary-service-healthcheck"]

CMD ["dictionary-service"]
