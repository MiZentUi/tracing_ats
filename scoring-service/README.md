# scoring-service

Python 3.14, uv, gRPC, Pydantic. Онлайн-ML: разбор обезличенного резюме через LLM и расчёт математической модели.

Сервис stateless: не читает файлы, не ходит в PostgreSQL, MinIO, hh, GitHub и не получает PII. LLM вызывается только с обезличенным текстом.

## Текущее состояние

`ScoringService.ScoreResume` вызывает LLM для извлечения признаков из
обезличенного резюме: навыков, образования и опыта. Ответ модели — строгий JSON
по схеме `ParsedResume`; каждая цитата проверяется как подстрока текста (пробелы
схлопываются, offsets считаются по исходному тексту). Признаки без валидной
цитаты отбрасываются.

`S^{ij}` пока не считается: используется детерминированная псевдослучайная
матрица, seed которой — SHA-256 текста резюме. По требованию выбирается
`argmax`, поэтому `coverage = relevance` (внешних источников нет, `λ = 0`).
Агрегаты `Score`, `Score_l`, `Score_h` остаются заглушкой `0.5`/`0.4`/`0.6`
до реализации модели из `docs/rfc/rfc-scoring-model.md`.

Провайдер по умолчанию — `mock`: детерминированная заглушка без сети и ключей,
нужная для CI и демо. Реальный OpenAI-совместимый провайдер включается
`SCORING_SERVICE_LLM_PROVIDER=openai`.

## LLM

- Промпт и JSON-схема лежат в `src/api/prompts/resume_extraction.py`, версия
  промпта — `resume-extraction.v1`.
- Клиент — `src/api/llm/client.py` на официальном SDK `openai`. `base_url`
  настраивается, поэтому подходит OpenRouter и локальные OpenAI-совместимые
  серверы. Повторы на 429/5xx и сетевые ошибки делает SDK (`max_retries`);
  один повтор на невалидный JSON/схему — на уровне экстрактора, затем ошибка,
  а не «0 баллов».
- В LLM уходит только обезличенный текст резюме. Тексты, цитаты и ответы модели
  не логируются; логируются только метаданные вызова: задержка, статус,
  `request_id`, токены и `finish_reason`.
- Ошибки: `INVALID_ARGUMENT` — пустой текст, `UNAVAILABLE` — LLM недоступна,
  `INTERNAL` — ответ не прошёл схему после повтора.

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
| `SCORING_SERVICE_LLM_PROVIDER` | `mock` | `mock` или `openai` |
| `SCORING_SERVICE_LLM_BASE_URL` | — | База OpenAI-совместимого API; обязательна для `openai` |
| `SCORING_SERVICE_LLM_API_KEY` | — | Ключ провайдера; не логируется |
| `SCORING_SERVICE_LLM_MODEL` | — | Модель; обязательна для `openai` |
| `SCORING_SERVICE_LLM_TIMEOUT_SECONDS` | `30` | Таймаут одного запроса |
| `SCORING_SERVICE_LLM_MAX_RETRIES` | `1` | Повторы на 429/5xx и сеть |

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
│   ├── config.py             # настройки из окружения
│   ├── server.py             # gRPC-сервер, health и ScoringService
│   ├── extraction/           # схема, нормализация и валидация цитат
│   ├── llm/                  # OpenAI-совместимый клиент и mock
│   ├── prompts/              # промпты и JSON-схема ответа
│   ├── scoring/              # gRPC-сервис и заглушка S^{ij}
│   └── healthcheck.py        # проверка для Docker/Compose
├── src/scoring_service/v1/   # сгенерированные protobuf/gRPC-модули
└── tests/
```

Арифметика модели будет жить в `src/api/scoring/`; формулы не дублируются в Java
и других сервисах. Заглушка матрицы `S^{ij}` — `src/api/scoring/similarity.py`.
