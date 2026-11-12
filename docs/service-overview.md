# Tracing ATS — текущее состояние сервиса

> [!NOTE]
> Исторический снимок на 2026-11-12: ветка `feature/unified-ats-service`,
> коммит `1fadb9b`. Утверждения о заглушке `scoring-service` и нереализованной
> математике устарели. Актуальное состояние — `scoring-service/README.md`,
> `docs/rfc/rfc-scoring-model.md` и `docs/rfc/rfc-feedback-generation.md`.

Документ фиксирует, что реализовано в объединённом Java-сервисе `ats-service` на ветке
`feature/unified-ats-service` (коммит `1fadb9b`), какие решения приняты и что делать дальше.
Дата: 2026-11-12.

---

## 1. Кратко

| Блок | Состояние |
|---|---|
| Создание вакансии из текста/файла | Готово |
| Шаблон требований через LLM (`kind`, цитаты, дедупликация) | Готово |
| Справочники специализаций/уровней, IT/не-IT | Готово |
| Редактирование и подтверждение шаблона HR | Готово |
| Загрузка резюме PDF/DOCX/TXT/MD | Готово |
| Маскировка PII, vault, sanitizer инъекций | Готово |
| Предварительный скоринг резюме при загрузке | Готово по потоку, но **математика — заглушка** |
| Шорт-лист (порог, top-N, borderline) | Готово |
| GitHub-enrichment только для шорт-листа | Готово (по согласию) |
| Рейтинг и отчёт «требование → доказательство → пробел» | Готово |
| Слепой режим и `reveal` с аудитом | Готово |
| Frontend | Только экраны вакансий |
| hh, письмо отказа, вопросы, PDF-отчёт, auth | Нет |
| `ml-trainer`, профили весов | Нет |

**Главное ограничение:** `scoring-service` возвращает константы (`score=0.5`, вилка 0.4–0.6),
LLM разбор резюме и формулы `S/τ/c_i` не реализованы. Весь остальной поток уже работает
сквозным путём.

---

## 2. Что запускается

`docker compose up` поднимает:

| Сервис | Порт | Назначение |
|---|---|---|
| `frontend` | 3000 | Vue 3 + nginx, проксирует `/api` на `ats-service:8080` |
| `ats-service` | 8080 (REST), 9090 (gRPC) | вакансии, резюме, PII, скрининг, рейтинг, отчёт |
| `scoring-service` | 8082 | Python gRPC, пока заглушка `ScoreResume` |
| `mock-llm` | 8089 | детерминированная заглушка OpenAI API для CI/демо |
| `postgres` | 5432 | PostgreSQL 18, общая схема |
| `minio` | 9000/9001 | оригиналы резюме (S3 API) |
| `migrations` | — | Liquibase, накатывает схему и завершается |

Профили LLM:
- по умолчанию — реальный OpenRouter, модель `dots-studio/dots-3-note-preview:free`;
- при `CLOUDFLARE_AI_ENABLED=true` и заданном токене — fallback Cloudflare Workers AI
  (Qwen `@cf/qwen/qwen3.8-27b`), если OpenRouter недоступен;
- `ATS_PROFILES=mock-llm` — `mock-llm` вместо внешней модели (для CI и проверки без ключа).

Обязательные переменные: `OPENROUTER_API_KEY` (или настроенный fallback Cloudflare),
`ATS_PII_ENCRYPTION_KEY` (base64 32 байта), `SPRING_DATASOURCE_*`, `ATS_S3_*`,
`SCORING_GRPC_TARGET`. Для fallback дополнительно: `CLOUDFLARE_AI_ENABLED`,
`CLOUDFLARE_AI_BASE_URL`, `CLOUDFLARE_AI_API_TOKEN`.

---

## 3. Архитектура

```text
Vue 3 (frontend)
   │ HTTP /api/v1
   ▼
ats-service (Java 21, Spring Boot 4)
   ├─ ats.vacancy  — REST вакансий, LLM-шаблон, kind-история, справочники, screening, gRPC-фасад
   ├─ ats.resume   — загрузка, PII, vault, MinIO, предварительный скор, скрининг, рейтинг, отчёт
   └─ ats.support  — request_id, логирование, gRPC interceptors
   │ gRPC (ScoreResume)
   ▼
scoring-service (Python 3.14, gRPC) — stateless, без БД/файлов/PII
   │ LLM (после реализации)
   ▼
OpenRouter / Cloudflare Workers AI (fallback) / mock-llm
```

- Java — единственный владелец PostgreSQL, MinIO, GitHub, hh и PII.
- `scoring-service` получает обезличенный текст и шаблон, возвращает баллы в шкале 0..1.
- Политика шорт-листа и решение «кого обогащать» живут в Java (`ats.vacancy.screening`).
- Один `request_id` связывает HTTP, gRPC и фоновые задачи.

---

## 4. Структура репозитория

```text
ats-service/                 единый Java-сервис
  src/main/java/ats/
    AtsServiceApplication.java
    vacancy/                 REST + LLM + шаблон + screening + gRPC
    resume/                  резюме, PII, vault, GitHub, скоринг, отчёт
    support/                 request_id, gRPC/server/client логирование
  src/test/java/ats/         27 тестовых классов
  Dockerfile
scoring-service/             Python gRPC (пока заглушка)
frontend/                    Vue 3, 3 экрана вакансий
infra/mock-llm/              OpenAI-совместимая заглушка
contracts/
  openapi.yaml               публичный REST
  vacancy-internal.proto     Java ↔ Java gRPC (GetTemplate, DecideScreening)
  proto/scoring_service/v1   Java ↔ Python gRPC (ScoreResume)
migrations/                  Liquibase, общая схема
docs/                        RFC, ADR, отчёты, этот документ
compose.yaml
```

---

## 5. Модули `ats-service`

### 5.1 `ats.vacancy`

| Пакет | Содержимое |
|---|---|
| `api` | `VacancyController`, `ReferenceController`, `ApiExceptionHandler`, `RequestLoggingFilter`, MapStruct-мапперы |
| `application` | `VacancyService`, `VacancyStore` (транзакции, версии, статистика), `SpecializationService`, `VacancyViewAssembler`, команды и view |
| `domain`-модели | `VacancyView`, `RequirementView`, `SpecializationItem`, `ReferenceItem`, `VacancyPage` |
| `infrastructure.persistence` | сущности и репозитории: `vacancy`, версии шаблона, требования, `skill`, `skill_alias`, `skill_kind_stat`, `specialization`, `level`, `scoring_weights` |
| `llm` | `SpringAiLlmClient`, `LlmClient`, ошибки `LLM_*`, повторы, деградация формата |
| `template` | `TemplateExtractionService`, `TemplateResponseParser`, `RequirementCleaner`, `QuoteMatcher`, `SkillCatalog`, `SkillNormalizer`, `KindHistoryService`, `VacancyInternalGrpcService`, `VacancyTemplateQuery` |
| `screening` | `ScreeningPolicy`, `ScreeningPolicyProperties` (порог 50, top-N 10, borderline 5) |
| `config` | `VacancyLlmProperties`, `TemplateProperties`, `SecurityConfiguration` (пока всё открыто), `MockLlmProfileGuard` |
| `dev` | временный `POST /api/v1/dev/extraction` (по флагу) |

### 5.2 `ats.resume`

| Пакет | Содержимое |
|---|---|
| `api` | `ResumeController`, `ScreeningController`, `RankingController`, `ReportController` |
| `domain.ingestion` | `UploadOutcome`, `ResumeMaskedEvent` |
| `domain.privacy` | `PiiType` (18 категорий), детекторы, `MaskingResult`, `SanitizedText` |
| `domain.scoring` | `ScoringClient`, `ScoringRequest/Result`, `EvidenceValidator` |
| `domain.vacancy` | `VacancyClient`, `VacancyTemplate`, `Requirement` |
| `domain.vault` | `PiiVaultCrypto`, `EncryptedPii`, `PiiPayload` |
| `infrastructure.ingestion` | `ResumeIngestionService`, `ResumeQueryService` |
| `infrastructure.pii` | `RegexPiiDetector`, `EducationExtractor`, `GitHubUrlExtractor`, `PiiSpanResolver`, `PiiMaskingService`, `PromptInjectionSanitizer`, NER (`OnnxNerModel`/`NoOpNerModel`) |
| `infrastructure.vault` | `AesGcmPiiVaultCrypto` (AES-256-GCM, AAD = resumeId) |
| `infrastructure.storage` | `S3ObjectStorage` / `InMemoryObjectStorage` |
| `infrastructure.scoring` | `GrpcScoringClient`, `PreliminaryScoringService`, `PreliminaryScoringListener`, `ScoreRunWriter` |
| `infrastructure.screening` | `ScreeningService`, `ScreeningWorker`, `ScreeningQueryService` |
| `infrastructure.github` | `HttpGitHubClient`, `GitHubEnricher` (q по навыку, антинакрутка) |
| `infrastructure.ranking` | `RankingService` |
| `infrastructure.report` | `ReportService`, `RevealService` |
| `infrastructure.persistence` | 10 сущностей и репозиториев |

### 5.3 `ats.support`

- `RequestIds` — `request_id` в MDC, HTTP-заголовок, gRPC metadata.
- `GrpcServerLoggingInterceptor`, `GrpcClientLoggingInterceptor` — парные события caller/callee.
- `LogSupport` — компактное описание cause-цепочки.

---

## 6. Реализованные функции

### 6.1 Вакансии и справочники

- `POST /vacancies` — текст или файл (PDF/DOCX/TXT/MD), специализация обязательна.
- `GET /vacancies` — пагинация (limit/offset), текущая версия, число требований.
- `GET /vacancies/{id}`, `PUT /vacancies/{id}/template`, `DELETE /vacancies/{id}`.
- `GET/POST /specializations`, `GET /levels`.
- Специализация имеет признак `it`: для IT уровень обязателен (`422 LEVEL_REQUIRED`),
  для не-IT подставляется скрытый `none`.
- Версии шаблона неизменяемы; `DELETE` вычитает вклад подтверждённой версии из статистики.

### 6.2 Шаблон из LLM

- Промпт `vacancy-template.v2` + строгая JSON-схема; текст вакансии передаётся как данные
  в `<vacancy_text>`.
- `TemplateResponseParser`: `schema_version=2`, `kind` из `MUST/NICE/OPTIONAL`.
- `RequirementCleaner`: цитата — подстрока текста, дедупликация с приоритетом
  `MUST > NICE > OPTIONAL`, отбрасывание пунктов без навыка и предупреждения, лимит 50.
- `SkillCatalog`/`SkillNormalizer`: алиасы (`JS → JavaScript`, `C#`, `.NET`).
- `KindHistoryService`: подсказка типа по `skill_kind_stat` (3 наблюдения, доля 0.6).
- Один повтор при невалидном ответе; при недоступности LLM вакансия создаётся с пустым
  шаблоном (RFC-002, 17.3).

### 6.3 LLM-клиент

- Модель `dots-studio/dots-3-note-preview:free` (reasoning), fallback **отключён**.
- `max-tokens 20000`, `read-timeout 180s`, `total-timeout 300s`.
- Повторы 5s×2, деградация `JSON_SCHEMA → JSON_OBJECT → NONE`.
- Явная обработка `OpenAIInvalidDataException`/`OpenAIException`, таймаутов и `429`.
- `OpenAiChatOptions.timeout` выставлен принудительно (Spring AI по умолчанию ставит 60 с).
- Профиль `mock-llm` запрещает внешний хост (`MockLlmProfileGuard`).

### 6.4 Резюме и приватность

- `POST /resumes` (мультипарт, несколько файлов) → `202`, `GET /resumes/{id}`.
- Пайплайн: детект типа по сигнатуре → оригинал в MinIO → экстракт текста →
  маскировка PII → санитайзер промпт-инъекций → AES-GCM в `pii_vault` →
  обезличенный текст в `parsed_resume` → `ApplicationEntity`.
- `PiiType`: имя, email, телефон, мессенджеры, соцсети, паспорт, СНИЛС, ИНН, дата рождения,
  возраст, пол, локация, вуз, год выпуска, семейное положение, фото.
- NER: `OnnxNerModel` (если модель подключена) или `NoOpNerModel` (по умолчанию `fake`).
- Статусы резюме: `MASKED`, `TEXT_EMPTY`, `MASKING_FAILED`, `FAILED`.

### 6.5 Предварительный скоринг

- После коммита загрузки публикуется `ResumeMaskedEvent`; отдельный пул `scoringExecutor`
  вызывает `GrpcScoringClient`.
- Статусы отклика: `SCORING → SCORED / SCORING_FAILED`; `score_run` (PRELIMINARY)
  создаётся с `template_version` и без `batch_id`.
- Арифметики в Java нет: `Score` и границы приходят из Python в 0..1 и переводятся в 0..100.
- **Сейчас `scoring-service` — заглушка:** `score=0.5`, `score_low=0.4`, `score_high=0.6`,
  `coverage=0.5`, `refine=false`. LLM внутри Python не вызывается.

### 6.6 Скрининг и шорт-лист

- `POST /vacancies/{id}/screen` → `202`, `GET /screening-batches/{id}`.
- Пакет выбирает отклики `SCORED/SCORING/SCORING_FAILED`, ждёт скоринг до
  `ats.screening.scoring-wait` (2m), пересчитывает устаревший PRELIMINARY по текущей версии.
- `DecideScreening` вызывается один раз на пакет (gRPC в тот же процесс).
- Политика: `SHORTLISTED` при `score ≥ 50` или top-N 10; `BORDERLINE`, если вилка
  пересекает порог; иначе `NOT_SHORTLISTED`. GitHub — только `SHORTLISTED` + согласие.
- `FINAL` копирует `PRELIMINARY` (контракт пока без `Refine`, поэтому балл не меняется).

### 6.7 GitHub

- `HttpGitHubClient`: репозитории, языки, README.
- `GitHubEnricher`: `q = (0.5·volume + 0.3·stability + 0.2·culture)·ρ`, окно 12 месяцев,
  флаг `RECENT_BULK_UPLOAD` и `q *= 0.5`.
- Доказательства привязаны к конкретному навыку; отсутствие GitHub не уменьшает балл.

### 6.8 Рейтинг, отчёт, reveal

- `GET /vacancies/{id}/ranking?phase=PRELIMINARY|FINAL`.
- `GET /applications/{id}/report?phase=...`: матрица требований (`COVERED/PARTIAL/GAP`),
  внешние доказательства, флаги, `blind=true`.
- `POST /applications/{id}/reveal` с причиной: расшифровка `pii_vault`, запись
  `audit_event` (`REVEAL_PII`).

### 6.9 Frontend

- `/vacancies` — список, поиск, фильтры, удаление.
- `/vacancies/new` — специализация/уровень, текст или файл, модалка специализации.
- `/vacancies/:id` — редактор: MUST/NICE/OPTIONAL, цитаты, дубли, поиск по тексту,
  подтверждение черновика, обработка 409.
- Нет экранов резюме, скрининга, рейтинга, отчёта, reveal.

### 6.10 Логирование

- `request_id` в MDC (`[req:xxxxxxxx]`), заголовок `X-Request-Id`, gRPC metadata.
- Пары событий HTTP/gRPC caller/callee, события LLM (`llm_*`), шаблона, скрининга,
  предварительного скоринга, `resume_ingested`.
- Тела запросов, промпты, ответы LLM, текст резюме и PII не логируются.
- Подробная таблица событий — `ats-service/README.md`, отчёт — `docs/reports/006-event-logging.md`.

---

## 7. Публичный API

Из `contracts/openapi.yaml` (реализованы все, кроме `/users/*`):

| Метод | Путь | Статус |
|---|---|---|
| POST | `/users/signup`, `/users/login`, `/users/refresh` | контракт есть, кода нет |
| GET/POST | `/specializations` | готово |
| GET | `/levels` | готово |
| GET/POST | `/vacancies` | готово |
| GET/DELETE | `/vacancies/{id}` | готово |
| PUT | `/vacancies/{id}/template` | готово |
| POST/GET | `/resumes`, `/resumes/{id}` | готово |
| POST/GET | `/vacancies/{id}/screen`, `/screening-batches/{id}` | готово |
| GET | `/vacancies/{id}/ranking` | готово |
| GET | `/applications/{id}/report` | готово |
| POST | `/applications/{id}/reveal` | готово |

Внутренние контракты:
- `contracts/vacancy-internal.proto`: `GetTemplate`, `DecideScreening`.
- `contracts/proto/scoring_service/v1`: `ScoreResume` (JobPositionLevel, требования,
  резюме; ответ `score`, `score_low`, `score_high`, `tenure`, `refine`, `features`).

---

## 8. Данные

Миграции Liquibase (11 файлов), таблицы:

- Вакансии: `vacancy`, `vacancy_template_version`, `vacancy_requirement`,
  `specialization`, `level`, `skill`, `skill_alias`, `skill_kind_stat`, `scoring_weights`.
- Резюме: `candidate`, `resume`, `pii_vault`, `parsed_resume`, `application`,
  `screening_batch`, `score_run`, `requirement_result`, `external_evidence`, `audit_event`.

Правила:
- версии шаблона и запуски скоринга неизменяемы (триггеры);
- PII только в `pii_vault` (шифротекст), в `parsed_resume` — обезличенный текст;
- `vacancy_id` в `application` без FK (владелец — Java-домен вакансий);
- демо-данные: вакансия `00000000-…-0001`, 6 требований.

---

## 9. Конфигурация

Ключевые переменные/свойства:

| Переменная / свойство | Значение |
|---|---|
| `OPENROUTER_API_KEY` | ключ LLM (не коммитится) |
| `CLOUDFLARE_AI_ENABLED` | `true` включает fallback Cloudflare Workers AI |
| `CLOUDFLARE_AI_BASE_URL` / `CLOUDFLARE_AI_API_TOKEN` | OpenAI-совместимый endpoint и токен Cloudflare |
| `CLOUDFLARE_AI_MODEL` | `@cf/qwen/qwen3.8-27b` |
| `ATS_PROFILES` | `mock-llm` или пусто (реальная LLM) |
| `vacancy.llm.model` | `dots-studio/dots-3-note-preview:free` |
| `vacancy.llm.fallback-models` | `[]` |
| `vacancy.llm.max-tokens` | `20000` |
| `vacancy.llm.read-timeout` / `total-timeout` | `180s` / `300s` |
| `ats.vacancy.screening.threshold/top-n/borderline-margin` | `50` / `10` / `5` |
| `ats.screening.scoring-wait/poll` | `2m` / `500ms` |
| `ats.scoring.grpc.target` | `scoring-service:8082` (в compose) |
| `ATS_PII_ENCRYPTION_KEY` | base64 32 байта, обязателен |
| `ATS_PII_NER_PROVIDER` | `fake` по умолчанию, `onnx` при наличии модели |
| `ATS_STORAGE_TYPE` | `s3` (MinIO) или `memory` |
| `ATS_S3_*` | endpoint, ключи, бакет `ats-resumes` |
| `ATS_GITHUB_ENABLED`, `ATS_GITHUB_TOKEN` | enrichment только для шорт-листа |
| `ATS_GRPC_PORT` | `9090` |
| `ATS_SERVICE_PORT` | `8080` |

---

## 10. Принятые решения

| Решение | Где зафиксировано | Следствие |
|---|---|---|
| `vacancy-service` и `resume-service` объединены в `ats-service` | `AGENTS.md`, `CHANGELOG.md` | один процесс, один порт REST, один gRPC-сервер |
| Арифметика скоринга — в `scoring-service` (Python) | ADR 0006, `AGENTS.md` §4 | Java считает только нормализацию и адаптеры |
| Предварительный скор при загрузке, скрининг использует готовые запуски | ADR 0006 | `score_run` PRELIMINARY без batch, FINAL копия |
| Политика шорт-листа — Java | ADR 0004 | порог/top-N/borderline не в модели |
| GitHub только для шорт-листа и по согласию | ADR 0003 | нет штрафа за отсутствие |
| PII маскируется локально до LLM/Python, vault AES-256-GCM | ADR 0001 | PII нет в `parsed_resume`, `masked_text` |
| Детерминированный санитайзер промпт-инъекций | ADR 0005 | инструкции в тексте удаляются до LLM |
| Типы требований `MUST/NICE/OPTIONAL` вместо `weight` | RFC-002 | критичность — через `kind`/`Wv`, без cap |
| IT/не-IT специализации | миграция `015`, контракт | для IT грейд обязателен |
| Реальная LLM: `dots-studio`, `max-tokens 20000`, fallback выключен | `application.yaml`, RFC-002 | либо ответ основной модели, либо `LLM_UNAVAILABLE` |
| Единый `request_id` и event logging | `docs/reports/006` | сквозная корреляция HTTP/gRPC/фонов |
| `scoring-service` — внешний контракт без `Refine` | ADR 0006 | FINAL пока равен PRELIMINARY по баллу |

---

## 11. Тесты и CI

- `ats-service`: **76 тестов** (Testcontainers PostgreSQL, LLM замокана). Покрыто:
  создание/редактирование/удаление вакансий, дедупликация, `kind`-история, IT/не-IT,
  gRPC `GetTemplate`, пайплайн резюме, маскировка, vault, GitHub, скоринг-адаптер,
  screening policy, логирование (RequestLoggingFilter, gRPC interceptors, отсутствие промпта).
- `scoring-service`: 6 тестов (конфигурация, health, заглушка `ScoreResume`, gRPC interceptor).
- `frontend`: 16 тестов + `vue-tsc` + Vite build.
- CI: матрица `text-extraction` + `ats-service`, Python (`protoc.sh`, `ruff`, `pytest`),
  frontend (генерация клиента, тесты, build), миграции (Liquibase в compose).

---

## 12. Ограничения и техдолг

1. **Математика скоринга не реализована** — заглушка 0.5; нет `q`, `τ`, `t`, `c_i`, `R'`,
   границ, `F`.
2. **LLM-разбор резюме отсутствует** — scoring-service не вызывает LLM, неявные навыки
   не извлекаются.
3. **FINAL = PRELIMINARY по баллу** — контракт без `Refine`; GitHub сохраняет факты,
   но не меняет Score.
4. **Frontend покрывает только вакансии**; нет загрузки резюме, рейтинга, отчёта, reveal.
5. **Нет hh-импорта**, письма отказа, вопросов по проекту, PDF-отчёта.
6. **Нет auth/JWT**; `/users/*` в контракте есть, кода нет; все эндпоинты открыты.
7. **NER по умолчанию выключена** (`fake`): имена и часть сущностей не маскируются.
8. **gRPC self-call**: `ats.resume` зовёт `ats.vacancy` через `localhost:9090`; работает,
   но лишний сетевой хоп.
9. **`request_id` не описан в OpenAPI**, хотя возвращается в заголовке.
10. **Fallback-модели отключены**; при недоступности основной модели вакансия создаётся
    с пустым шаблоном.
11. **`ml-trainer`, профили весов и MinIO-датасеты обучения** отсутствуют.
12. `docs/adr/0002` исторически описывает Java-скоринг, заменён ADR 0006.

---

## 13. Вектор дальнейших действий

### P0 — довести демо до сквозного смысла

1. **Реализовать математику в `scoring-service`** по `docs/rfc/rfc-scoring-model.md`:
   `S`, `R_i`, `Rv`, `τ`, `t`, `c_i`, `R'`, `Score`, границы; golden-тесты на числа из README.
2. **LLM-разбор резюме в `scoring-service`**: промпт, неявные навыки, категории `m/t`,
   цитаты; проверка цитаты как подстроки; без PII.
3. **Frontend: резюме и результат** — загрузка, статус скоринга, рейтинг, отчёт,
   слепой режим и `reveal`.
4. **PDF-отчёт** в Java (HTML → PDF) и кнопка в UI.
5. **Проверить сквозной сценарий** на 10–15 синтетических резюме: порядок, границы,
   отсутствие штрафа без GitHub.

### P1 — расширение

6. **hh-импорт** откликов и коннектор; хранение токена только в Java.
7. **Профили junior/senior** (гиперпараметры по уровню) и память весов HR.
8. **Письмо отказа и вопросы по проекту** на основе непокрытых требований и GitHub-фактов.
9. **Реальная NER-модель** (`scripts/download-ner.sh`) и fail-closed в production.
10. **Auth/JWT** и RBAC, аудит раскрытий в UI.

### P2 — после хакатона

11. `ml-trainer`: датасет, seed, метрики (Спирмен, NDCG@10), импорт артефакта как `candidate`.
12. Классификация резюме, карта смежных навыков, калибровка, OCR, другие источники.

### Технические задачи, не блокирующие демо

- заменить gRPC self-call на прямой вызов бина (или оставить как контрактную границу);
- вынести `X-Request-Id` в OpenAPI;
- пересмотреть fallback-модели/платную модель;
- обновить `docs/adr/0002` и `docs/project-overview.md` под единый сервис.

---

## 14. Как запустить и проверить

```bash
docker compose up -d --build
# frontend http://localhost:3000
# REST http://localhost:8080, Swagger http://localhost:8080/swagger-ui/index.html

# без внешней LLM
ATS_PROFILES=mock-llm docker compose up -d --force-recreate ats-service

# проверки
(cd ats-service && ./gradlew test spotlessCheck)
(cd scoring-service && uv run pytest -q && uv run ruff check .)
(cd frontend && npm test && npm run build)
```

Сквозной сценарий:

```bash
curl -X POST http://localhost:8080/api/v1/vacancies \
  -F specialization=backend -F level=middle \
  -F 'text=Ищем backend: Java, Spring Boot, PostgreSQL.'
# → vacancy_id

curl -X POST http://localhost:8080/api/v1/resumes \
  -F vacancyId=<id> -F files=@resume.txt -F consentGithub=false
# → resumeId; application становится SCORED

curl -X POST http://localhost:8080/api/v1/vacancies/<id>/screen
curl http://localhost:8080/api/v1/vacancies/<id>/ranking?phase=FINAL
curl http://localhost:8080/api/v1/applications/<appId>/report
```
