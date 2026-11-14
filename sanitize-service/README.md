# sanitize-service

Локальный stateless-детектор PII для Tracing ATS. Получает исходный текст резюме,
возвращает только найденные сущности. Плейсхолдеры, шифрованный vault и решение о
дальнейшей передаче текста остаются в Java (`ats-service`).

Сервис не ходит в сеть, не читает файлы и БД, не логирует текст и сущности. По
умолчанию работает слой spaCy; LLM-слой зарезервирован контрактом и включается
конфигурацией, когда появится реализация.

## Контракт

`contracts/proto/sanitize_service/v1/sanitize.proto`:

```proto
service SanitizeService {
  rpc DetectPii(DetectPiiRequest) returns (DetectPiiResponse);
}
```

- `DetectPiiRequest.text` — исходный текст; наружу не уходит.
- `pipeline` — имя пайплайна из конфигурации, пусто — пайплайн по умолчанию.
- `layers` — какие слои применить, пусто — все слои пайплайна.
- `min_confidence` — порог 0..1, `0` — порог пайплайна.
- `PiiEntity.start`/`end` — смещения в Unicode code points, `end` исключительно.
  Java переводит их в UTF-16 перед созданием `PiiSpan`.
- `DetectPiiResponse.model`/`model_version` — фактически использованная модель,
  нужны для аудита запуска.

## Пайплайны в конфигурации

Пайплайны задаются переменной `SANITIZE_SERVICE_PIPELINES` (JSON) и не требуют
изменения кода. Значение по умолчанию:

```json
{
  "ru": {
    "spacy_model": "ru_core_news_md",
    "layers": ["spacy"],
    "spacy_confidence": 0.7,
    "normalize_first_line_case": true,
    "min_confidence": 0.0
  }
}
```

`normalize_first_line_case` — детерминированная поправка: spaCy плохо находит
ФИО, записанные капсом (типичный заголовок резюме). Первая непустая строка
капсом дополнительно прогоняется через модель в title case; длина строки и
offsets не меняются. Результат принимается, только если это имя из двух и более
слов, поэтому заголовки вроде «ОПЫТ РАБОТЫ» не маскируются.

Пример второго языка:

```bash
SANITIZE_SERVICE_PIPELINES='{
  "ru": {"spacy_model": "ru_core_news_md", "layers": ["spacy"]},
  "en": {"spacy_model": "en_core_web_sm", "layers": ["spacy"]}
}'
```

Модель `en_core_web_sm` должна быть установлена в образ/окружение. Запрос с
неизвестным пайплайном или слоем получает `INVALID_ARGUMENT`.

`LAYER_LLM` сейчас отвечает `UNIMPLEMENTED`: слой не должен молча пропускать PII.
Организации (`ORG`) намеренно не маскируются.

## Конфигурация

| Переменная | По умолчанию | Назначение |
|---|---|---|
| `SANITIZE_SERVICE_GRPC_HOST` | `0.0.0.0` | адрес gRPC-сервера |
| `SANITIZE_SERVICE_GRPC_PORT` | `8083` | порт gRPC-сервера |
| `SANITIZE_SERVICE_LOG_LEVEL` | `INFO` | уровень логов |
| `SANITIZE_SERVICE_MAX_WORKERS` | `4` | потоков обработки |
| `SANITIZE_SERVICE_MAX_TEXT_CHARS` | `200000` | лимит длины текста |
| `SANITIZE_SERVICE_DEFAULT_PIPELINE` | `ru` | пайплайн по умолчанию |
| `SANITIZE_SERVICE_PIPELINES` | `{"ru": ...}` | пайплайны и слои |

Логи содержат только имя метода, `request_id` из `x-request-id`, статус и
длительность. Текст и PII в логи не попадают.

## Разработка

```bash
cd sanitize-service
uv sync
./protoc.sh
uv run python -m spacy download ru_core_news_md  # один раз, для ручного запуска
uv run pytest -q
uv run ruff check .
uv run sanitize-service
```

Проверка здоровья:

```bash
uv run sanitize-service-healthcheck
```

## Docker

Сервис собирается из корня репозитория, потому что образу нужен
`contracts/proto`:

```bash
docker build -f sanitize-service/Dockerfile -t sanitize-service .
```
