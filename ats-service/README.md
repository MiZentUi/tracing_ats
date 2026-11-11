# ats-service

Единый Java 21 / Spring Boot 4 сервис проекта: вакансии и справочники, шаблон требований через
LLM, приём и маскировка резюме, PII-vault, скрининг, рейтинг, отчёт и раскрытие PII. Пакеты
`ats.vacancy` и `ats.resume` живут в одном процессе; Python-скоринг вызывается по gRPC.

## Сборка и запуск

```bash
./gradlew test          # тесты с Testcontainers и замоканной LLM
./gradlew bootRun       # локально; нужны PostgreSQL и ключ LLM или профиль mock-llm
```

Порты: `8080` — REST (`/api/v1`), `9090` — внутренний gRPC (`GetTemplate`, `DecideScreening`).
PostgreSQL передаётся через `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`,
`SPRING_DATASOURCE_PASSWORD`.

Обязательные для резюме настройки: `ATS_PII_ENCRYPTION_KEY` (base64 32 байта), MinIO/S3
(`ATS_STORAGE_TYPE=s3`, `ATS_S3_*`) или `ATS_STORAGE_TYPE=memory`, `SCORING_GRPC_TARGET`.
Для запуска без ключа LLM: `SPRING_PROFILES_ACTIVE=mock-llm` и `MOCK_LLM_URL`.

## Docker

```bash
docker compose up -d --build ats-service
```

## Логирование

Каждый HTTP-запрос получает идентификатор (входящий `X-Request-Id` или сгенерированные 8
символов), он попадает в MDC и печатается в каждой строке как `[req:xxxxxxxx]`. По нему видно
всю цепочку одного вызова: контроллер → разбор → LLM-клиент → сохранение.

Что искать при неудачном разборе (в порядке появления):

| Событие | Уровень | Что означает |
|---|---|---|
| `Create vacancy: specialization=..., textChars=...` | INFO | Параметры запроса до валидации |
| `Vacancy source file extracted: ...` | INFO | Экстракт файла: размер, страницы, предупреждения |
| `Template extraction started` | INFO | Начало разбора: длина текста, специализация, уровень, модель |
| `LLM chain started` / `LLM attempt started` | INFO | Какая модель и какая попытка пошла в сеть |
| `LLM attempt succeeded` | INFO | Успех: `elapsedMs`, токены, `finishReason` |
| `LLM rate limited` / `LLM server error` / `LLM transport error` | WARN | Ошибка провайдера, повтор или переход к следующей модели |
| `LLM timed out mid-response` | WARN | Сработал `read-timeout`, повтор той же модели не делается |
| `LLM model exhausted` | WARN | У модели кончились попытки, берётся следующая |
| `LLM chain exhausted` | ERROR | Все модели исчерпаны или истёк `total-timeout` |
| `LLM is unavailable, vacancy will be created with an empty template` | WARN | Деградация по RFC-002: вакансия создаётся без требований |
| `LLM template response is invalid, retrying once` | WARN | Невалидный JSON/схема: причина и первые 300 символов ответа |
| `Vacancy created` | INFO | Итог: id, версия, статус, число требований |
| `HTTP request completed` | INFO | Метод, путь, статус, длительность запроса |

Полный текст вакансии, промпта, резюме и ответа LLM в логи не пишется: только длины, причины и
короткие фрагменты ответа при ошибке разбора. PII в логи не попадает.

Для подробностей по CRUD-операциям включи DEBUG для пакета:

```bash
docker run -e LOGGING_LEVEL_ATS=DEBUG ...
```
