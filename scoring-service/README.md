# scoring-service

Python 3.14, uv, gRPC, Pydantic. Онлайн-ML: разбор обезличенного резюме через LLM и расчёт математической модели.

Сервис stateless: не читает файлы, не ходит в PostgreSQL, MinIO, hh, GitHub и не получает PII. LLM вызывается только с обезличенным текстом.

## Текущее состояние

Каркас поднимает gRPC-сервер и стандартный `grpc.health.v1.Health`. Доменные RPC скоринга добавляются по мере фиксации gRPC-контракта.

## Требования

- Python 3.14
- uv 0.13+

## Локальный запуск

```bash
uv sync
uv run scoring-service
```

Сервис слушает `0.0.0.0:8082`. Проверка здоровья:

```bash
uv run scoring-service-healthcheck
```

## Конфигурация

Настройки читаются из переменных окружения с префиксом `SCORING_SERVICE_`.

| Переменная | По умолчанию | Назначение |
|---|---|---|
| `SCORING_SERVICE_GRPC_HOST` | `0.0.0.0` | Адрес gRPC-сервера |
| `SCORING_SERVICE_GRPC_PORT` | `8082` | Порт gRPC-сервера |
| `SCORING_SERVICE_LOG_LEVEL` | `INFO` | Уровень логирования |
| `SCORING_SERVICE_MAX_WORKERS` | `10` | Размер пула обработчиков gRPC |

Локальные значения можно положить в `scoring-service/.env`; файл не коммитится.

## Проверки

```bash
uv run pytest
uv run ruff check .
uv run ruff format --check .
```

## Docker

Из корня репозитория:

```bash
docker compose up --build scoring-service
```

Порт публикуется через `SCORING_SERVICE_PORT` (по умолчанию `8082`). Образ собирается на `python:3.14-slim`, зависимости ставятся из `uv.lock` через `uv sync --locked`.

## Структура

```text
scoring-service/
├── pyproject.toml
├── uv.lock
├── Dockerfile
├── src/api/
│   ├── config.py       # настройки из окружения
│   ├── server.py       # gRPC-сервер и health service
│   └── healthcheck.py  # проверка для Docker/Compose
└── tests/
```

Арифметика скоринга будет жить в `src/api/scoring/`; формулы не дублируются в Java и других сервисах.
