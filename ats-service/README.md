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
Если OpenRouter недоступен, можно включить fallback Cloudflare Workers AI:
`CLOUDFLARE_AI_ENABLED=true`, `CLOUDFLARE_AI_BASE_URL` вида
`https://api.cloudflare.com/client/v4/accounts/<ACCOUNT_ID>/ai/v1`,
`CLOUDFLARE_AI_API_TOKEN` (право Account > Workers AI > Read). В mock-профиле fallback запрещён.

## Docker

```bash
docker compose up -d --build ats-service
```

## Логирование

Один `request_id` на вызов: входящий `X-Request-Id` или 8 символов UUID. Он попадает в MDC,
печатается как `[req:xxxxxxxx]`, возвращается в заголовке ответа и передаётся в gRPC через
metadata `x-request-id`. По нему видно всю цепочку: HTTP → бизнес-операция → gRPC → LLM.

События (стабильное имя + `key=value`, тела запросов и ответов не логируются):

| Событие | Уровень | Сторона / слой |
|---|---|---|
| `inbound_request_received`, `inbound_response_sent` | INFO | HTTP-фильтр, gRPC server interceptor |
| `outbound_request_sent`, `outbound_response_received` | INFO | gRPC client interceptor |
| `outbound_request_failed` | WARN | gRPC client: `UNAVAILABLE`/`DEADLINE_EXCEEDED` |
| `inbound_request_failed` | ERROR | gRPC server: обработчик упал до ответа |
| `llm_request_sent`, `llm_response_received` | INFO | клиент LLM: модель, попытка, формат, `elapsed_ms`, токены, `finish_reason` |
| `llm_rate_limited`, `llm_timeout`, `llm_transport_error`, `llm_request_failed` | WARN | внешний провайдер: повтор или следующая модель |
| `llm_model_exhausted`, `llm_chain_exhausted` | WARN/ERROR | исчерпание попыток/цепочки |
| `llm_provider_started`, `llm_provider_fallback`, `llm_provider_exhausted`, `llm_provider_failed` | INFO/WARN | переключение OpenRouter → Cloudflare Workers AI |
| `template_extraction_started/finished`, `template_response_invalid` | INFO/WARN | разбор шаблона; причина без содержимого ответа |
| `template_requirements_all_dropped` | WARN | все требования отброшены: счётчики причин и примеры |
| `vacancy_created`, `vacancy_template_updated`, `vacancy_deleted` | INFO | бизнес-итог операции |
| `screening_batch_started/finished`, `screening_batch_failed` | INFO/ERROR | фоновая задача скрининга |
| `preliminary_scoring_started/finished/failed` | INFO/WARN | предварительный скор при загрузке |
| `api_request_rejected`, `api_error_unexpected` | INFO/ERROR | граница ошибок HTTP |

Не логируются: тела запросов и ответов, промпты и ответы LLM, текст резюме и вакансии, цитаты,
PII, токены и `Authorization`. Ошибку со stack trace пишет только граница, которая превращает её
в ответ.

Диагностика по одному запросу:

```bash
docker compose logs ats-service | grep '\[req:xxxxxxxx\]'
```

Для подробностей по CRUD включи DEBUG для пакета:

```bash
docker run -e LOGGING_LEVEL_ATS=DEBUG ...
```
