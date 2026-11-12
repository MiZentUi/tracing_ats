# Отчёт 006 — event logging в ats-service, scoring-service и frontend

Ветка `feature/unified-ats-service`. Скилл `event-logging` загружен и применён к Java, Python,
frontend и `infra/mock-llm`.

### Что сделано

- `ats-service/src/main/java/ats/support/logging/RequestIds.java` — единый `request_id`
  (`MDC=request_id`, заголовок `X-Request-Id`, gRPC metadata `x-request-id`).
- `ats-service/.../api/RequestLoggingFilter.java` — события `inbound_request_received` и
  `inbound_response_sent`, заголовок ответа `X-Request-Id`, health/swagger исключены.
- `ats-service/.../support/logging/GrpcServerLoggingInterceptor.java` — gRPC-граница:
  `inbound_request_received`, `inbound_response_sent`, `inbound_request_failed`; health и
  reflection не логируются.
- `ats-service/.../support/logging/GrpcClientLoggingInterceptor.java` — исходящие вызовы:
  `outbound_request_sent`, `outbound_response_received`, `outbound_request_failed`; пробрасывает
  `request_id` в metadata. Подключён к каналам scoring-service и vacancy-internal.
- `SpringAiLlmClient` — события `llm_chain_started/succeeded/exhausted`, `llm_request_sent`,
  `llm_response_received`, `llm_rate_limited`, `llm_timeout`, `llm_transport_error`,
  `llm_request_failed`, `llm_model_exhausted`, `llm_retry_scheduled`. Тела промптов и ответов
  не логируются.
- `TemplateExtractionService` — `template_extraction_started/finished`,
  `template_response_received`, `template_response_invalid` (без excerpt ответа LLM).
- `RequirementCleaner` — `template_requirement_dropped` (DEBUG) и
  `template_requirements_all_dropped` (WARN) со счётчиками причин и примерами без цитат.
- `VacancyService`, `VacancyController`, `ApiExceptionHandler`, `SpecializationService`,
  `SkillKindStatWriter` — события переведены в `snake_case` (`vacancy_created`,
  `vacancy_template_updated`, `api_request_rejected`, `specialization_created`,
  `skill_kind_stat_clamped`).
- `ScreeningWorker` — `screening_batch_started/finished/failed`,
  `screening_application_failed`, `scoring_wait_timed_out`, `github_enrichment_failed`.
- `PreliminaryScoringService`/`PreliminaryScoringListener` — `preliminary_scoring_started/
  finished/failed/rejected`, `vacancy_template_fetch_failed`.
- `resume`-инфраструктура: `resume_ingested`, `pii_masking_failed`, `ner_unavailable`,
  `ner_model_loaded`, `github_enrichment_skipped`, `object_storage_bucket_created`.
- `PreliminaryScoringListener` и `ScreeningService` переносят `request_id` из HTTP-потока в
  фоновую задачу, поэтому `preliminary_scoring_*` и `screening_*` связаны с исходной загрузкой.
- `scoring-service/src/api/logging_interceptor.py` — gRPC-граница с теми же именами событий;
  `scoring.py` — `score_resume_completed`; интерцептор подключён в `server.py`.
- `frontend/src/lib/logger.ts` — общий logger (debug/info только в dev); `api/index.ts` логирует
  `api_request_failed` с `method`, `status`, `request_id`; `main.ts` — `ui_error`,
  `ui_unhandled_rejection`.
- `infra/mock-llm/server.py` — `mock_llm_request` со статусом, сценарием и `elapsed_ms`, без тел.
- `ats-service/README.md` — таблица событий и правила корреляции; `CHANGELOG.md` — раздел ветки.

### Проверка

- `cd ats-service && ./gradlew test spotlessCheck` → 76 тестов, 0 падений (включая новые
  `RequestLoggingFilterTest`, `GrpcLoggingInterceptorsTest`, проверку отсутствия промпта в логах).
- `scoring-service`: `pytest -q` → 6 тестов, 0 падений (добавлены 2 теста интерцептора:
  парные события, `request_id`, отсутствие тела запроса в логах) и `ruff check`/`ruff format`.
- frontend: `npm test` → 5 файлов, 16 тестов; `npm run build` → успешно.
- `infra/mock-llm/server.py`, `scoring-service/src/api/*.py` — `py_compile` без ошибок.
- Release-проверка: `grep` по Java-логам не находит `password|secret|token|Authorization`,
  текстов резюме/вакансий и цитат; фрагменты ответа LLM из логов удалены.

### Не сделано / проблемы

- Полноценный OpenTelemetry/трейсинг не добавлялся — вне scope скилла.
- `request_id` не передаётся во внешние API (OpenRouter): для них логируется только наша сторона.
- Аудит `reveal` по-прежнему пишется в таблицу `audit_event`, в обычный лог попадает только
  технический контекст.
- Frontend не логирует успешные запросы (только ошибки) — сознательно, чтобы не шуметь.

### Требует решения пользователя

- Нужен ли `request_id` в ответах API как публичный контракт (сейчас заголовок
  `X-Request-Id` добавлен, но в OpenAPI не описан).
