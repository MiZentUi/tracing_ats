# ats-service

Единый Java 21 / Spring Boot 4 сервис: вакансии и справочники, шаблон требований через LLM, приём и маскировка резюме, PII-vault, разбор через `scoring-service`, скрининг, рейтинг, отчёт, письма кандидату и раскрытие PII.

## Сборка и запуск

```bash
./gradlew test          # тесты с Testcontainers и замоканной LLM
./gradlew spotlessCheck
./gradlew bootRun       # локально; нужны PostgreSQL, MinIO/S3 и ключ LLM или профиль mock-llm
```

Порты:

- `8080` — REST `/api/v1`;
- `9090` — внутренний gRPC (`GetTemplate`, `DecideScreening`).

## Обязательная инфраструктура

- PostgreSQL с накатанными Liquibase-миграциями. Приложение работает с `ddl-auto=validate` и само схему не создаёт; используй контейнер `migrations` или `spring.liquibase.enabled=true` в тестах.
- `ATS_PII_ENCRYPTION_KEY` — base64 32 байта; без него приложение не стартует.
- MinIO/S3 (`ATS_STORAGE_TYPE=s3`, `ATS_S3_*`) или `ATS_STORAGE_TYPE=memory`.
- `SCORING_GRPC_TARGET` — адрес `scoring-service`.
- `DICTIONARY_GRPC_TARGET` — адрес `dictionary-service`; при `ATS_DICTIONARY_ENABLED=false` Java использует локальные таблицы.

## LLM

- Провайдер по умолчанию — OpenRouter; модель задаётся `VACANCY_LLM_MODEL`.
- Ключ — `VACANCY_LLM_API_KEY` или `OPENROUTER_API_KEY`.
- Fallback Cloudflare Workers AI включается `CLOUDFLARE_AI_ENABLED=true`.
- Для запуска без ключа: `SPRING_PROFILES_ACTIVE=mock-llm` и `MOCK_LLM_URL`.

LLM вызывается для:

- шаблона требований вакансии;
- генерации словаря по специальности;
- черновиков писем кандидату.

Разбор резюме и скоринг — зона `scoring-service`.

## Профили

- `mock-llm` — заглушка LLM, внешние хосты запрещены;
- `test` — in-memory storage, fake-NER, Liquibase из classpath, gRPC-порт `0`;
- отдельного `prod`-профиля нет; production-настройки приходят из env и `application.yaml`.

## Логирование

Один `request_id` на вызов: входящий `X-Request-Id` или сгенерированные 8 символов UUID. Он попадает в MDC, печатается как `[req:xxxxxxxx]`, возвращается в заголовке ответа и передаётся в gRPC через metadata `x-request-id`.

События (стабильное имя + `key=value`; тела запросов, ответов, промпты, цитаты и PII не логируются):

| Событие | Уровень | Где |
|---|---|---|
| `inbound_request_received`, `inbound_response_sent` | INFO | HTTP-фильтр, gRPC server interceptor |
| `outbound_request_sent`, `outbound_response_received` | INFO | gRPC client interceptor |
| `outbound_request_failed` | WARN | gRPC client |
| `inbound_request_failed` | ERROR | gRPC server |
| `llm_request_sent`, `llm_response_received` | INFO | LLM-клиент |
| `llm_rate_limited`, `llm_timeout`, `llm_transport_error`, `llm_request_failed` | WARN | LLM-клиент |
| `llm_model_exhausted`, `llm_chain_exhausted` | WARN/ERROR | LLM-клиент |
| `llm_provider_started`, `llm_provider_fallback`, `llm_provider_exhausted`, `llm_provider_failed` | INFO/WARN | fallback |
| `template_extraction_started/finished`, `template_response_invalid` | INFO/WARN | шаблон |
| `template_requirements_all_dropped` | WARN | шаблон |
| `vacancy_created`, `vacancy_template_updated`, `vacancy_deleted` | INFO | вакансии |
| `screening_batch_started/finished/failed` | INFO/ERROR | скрининг |
| `preliminary_scoring_started/finished/failed` | INFO/WARN | предварительный скор |
| `candidate_message_generation_started/finished/failed/rejected` | INFO/WARN | письма |
| `report_decision_saved`, `resume_ingested`, `resume_updated`, `resume_deleted` | INFO | бизнес-операции |
| `api_request_rejected`, `api_error_unexpected` | INFO/ERROR | HTTP-граница |

Не логируются: тела запросов и ответов, промпты и ответы LLM, тексты резюме и вакансии, цитаты, PII, токены и `Authorization`.

Диагностика по запросу:

```bash
docker compose logs ats-service | grep '\[req:xxxxxxxx\]'
```

## Тесты

- `ResumePipelineIntegrationTest` — upload → masking → preliminary score → screening → final copy → ranking → report → reveal.
- `VacancyApiIntegrationTest` — вакансии, шаблон, правки, справочники, ошибки LLM.
- `PiiMaskingServiceTest`, `PromptInjectionSanitizerTest`, `AesGcmPiiVaultCryptoTest`.
- `ScreeningPolicyTest`, `ScreeningWorkerTest`, `GitHubEnricherTest`.
- `GrpcScoringClientTest` — in-process fake scoring.
- `SpringAiLlmClientTest` — MockWebServer, повторы, fallback, таймауты.
- `CandidateMessageServiceTest`, `CandidateMessageParserTest`, `CandidateMessagePromptTest`, `ReportControllerTest` — письма кандидату.

## Ограничения

- Нет авторизации и ролей; все endpoints открыты.
- NER по умолчанию `fake`; ONNX-модель не входит в репозиторий.
- GitHub-факты не влияют на `Score`; `FINAL` копирует `PRELIMINARY`.
- `ATS_SCREENING_*` не привязываются из-за пути `ats.dictionary.screening`.
- Нет hh, PDF-отчёта, `ml-trainer`, профилей весов.
- Письма кандидату создаются только как черновик; автоматической отправки нет.
- `/users/*` в OpenAPI не реализованы.

Подробнее — [`../docs/service-overview.md`](../docs/service-overview.md), [`../docs/known-issues.md`](../docs/known-issues.md).
