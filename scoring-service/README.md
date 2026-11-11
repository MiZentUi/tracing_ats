# scoring-service

Python 3.14, uv, gRPC, Pydantic. Онлайн-ML: разбор обезличенного резюме через LLM и расчёт математической модели.

Сервис stateless: не читает файлы, не ходит в PostgreSQL, MinIO, hh, GitHub и не получает PII. LLM вызывается только с обезличенным текстом.

## Текущее состояние

Сервис поднимает gRPC-сервер, стандартный `grpc.health.v1.Health` и заглушку `ScoringService.ScoreResume`. Заглушка возвращает детерминированный результат `0.5` с вилкой `0.4–0.6` и по одному `FeatureAssessment` на каждое требование вакансии. Реальная математика скоринга появится позже.

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

## Генерация gRPC

Сгенерированные protobuf/gRPC-модули лежат в `src/scoring_service/` и коммитятся вместе с контрактом. После изменения `.proto`:

```bash
uv run sh protoc.sh
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

CI выполняет эти же проверки в job `Python / scoring-service`, а также перегенерирует protobuf-модули через `protoc.sh` и проверяет, что они совпадают с закоммиченными.

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
│   ├── server.py       # gRPC-сервер, health и ScoringService
│   ├── scoring.py      # заглушка ScoreResume
│   └── healthcheck.py  # проверка для Docker/Compose
├── src/scoring_service/v1/  # сгенерированные protobuf/gRPC-модули
└── tests/
```

Заглушка живёт в `src/api/scoring.py`; реальная арифметика будет в `src/api/scoring/`, формулы не дублируются в Java и других сервисах.
