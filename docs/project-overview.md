# Tracing ATS — обзор проекта и состояние веток

Документ фиксирует состояние на 2026-11-11. Ветки и коммиты, на которых основаны факты:

| Ветка | HEAD | Основание |
|---|---|---|
| `main` | `b4c8ba5` | текущая основная ветка |
| `feat/resume-service-pipeline` | `337567a` | ответвлена от `c89237b`, отстаёт от `main` |
| `feature/specialization-it-flag` | `7fca4b0` | 4 коммита поверх `main` |

Источники: `README.md`, `AGENTS.md`, `contracts/openapi.yaml`, `migrations/`, `docs/rfc/`, код сервисов.
Целевая архитектура описана в `README.md`; расхождения с ней перечислены в разделе 9.

---

## 1. Что это за продукт

ATS, который сопоставляет резюме с вакансией и объясняет результат по каждому требованию.
Вместо «оценки от 0 до 100» из LLM система разбирает вакансию на атомарные требования, для
каждого навыка определяет сходство `S`, убедительность `t` и стаж/свежесть `τ`, считает
покрытие `c_i`, релевантность `R'` и итоговый `Score` с границами. Все числа считает
детерминированный код, LLM отдаёт только категории, цитаты и текст.

Три правила (`README.md`, `AGENTS.md`):

1. **LLM не выставляет баллов.** Модель возвращает `MUST`/`NICE`/`OPTIONAL`, категории
   сходства/убедительности, цитаты и текст. Любое число считает код.
2. **Цитата — подстрока.** Каждая цитата проверяется как подстрока исходного текста;
   непроверенная отбрасывается и покрытие не повышает.
3. **Нет внешних данных — нет штрафа.** Отсутствие GitHub означает `λ = 0` и `c_i = R_i'`,
   балл не уменьшается.

Решения принимает человек: система готовит шорт-лист, отчёт, вопросы и черновик письма,
но не нанимает и не отказывает автоматически. Слепой режим: до отдельного `reveal` с
причиной и записью в аудит рекрутёр видит оценку и доказательства без PII.

### Две цепочки

**Вакансия:** текст или файл → экстракт → шаблон требований через LLM → правка HR →
веса и типы требований запоминаются по специализации × уровню → сохранение версии шаблона.

**Кандидат:** отклик hh или ручная загрузка → экстракт → маскировка PII → разбор резюме
(LLM) → предварительный `Score_pre` по тексту → шорт-лист → GitHub только для отобранных →
`Score_final` (та же модель, другие входные данные) → рейтинг, отчёт, вопросы, письмо.

---

## 2. Целевая архитектура

| Сервис | Технологии | Ответственность |
|---|---|---|
| `frontend` | Vue 3, TypeScript, Vite | Вакансии, редактор шаблона, загрузка резюме, рейтинг, отчёт |
| `vacancy-service` | Java 21, Spring Boot 4, Gradle, JPA, PostgreSQL | Публичный REST, вакансии, шаблон, справочники, политика шорт-листа, GitHub-enrichment, генерация текстов |
| `resume-service` | Java 21, Spring Boot 4 | Загрузка резюме, извлечение текста, маскировка PII, vault, оркестрация скоринга, аудит, отчёты |
| `scoring-service` | Python 3.14, uv, gRPC, Pydantic | Stateless онлайн-ML: LLM-разбор резюме, математика `S`, `R`, `τ`, `t`, `c_i`, `R'`, `Score`, границы |
| `ml-trainer` | Python 3.14, офлайн | Обучение гиперпараметров и весов по обезличенному датасету |
| `text-extraction` | Java-библиотека | PDF (PDFBox), DOCX (POI), TXT/MD; общий модуль для Java-сервисов |
| `infra/mock-llm` | Python stdlib | Детерминированная заглушка OpenAI-совместимого API для CI и демо |

Границы, которые нельзя нарушать (`AGENTS.md` §4–5):

- Java — файлы, hh, GitHub, MinIO, PostgreSQL, маскировка, шаблон вакансии, нормализация
  внешних фактов, генерация текстов.
- `scoring-service` не читает файлы и БД, не ходит в hh/GitHub/LLM с PII, не получает токены.
- `ml-trainer` видит только обезличенный датасет и тренировочный бакет MinIO.
- Внешние факты (`q_i^l`) нормализует Java; Python применяет модель к готовым значениям.
- Одна и та же модель считает `Score_pre` (без внешних данных) и `Score_final`
  (с внешними); это разные вызовы и разные поля.
- Изменение формулы — новая `scoring_version`; сохранённые запуски не пересчитываются.

---

## 3. Модель скоринга

Источник формул — `docs/rfc/rfc-scoring-model.md` и `README.md` §6.

```text
S^{ij}  = сходство навыка j с требованием i, 0..1
R_i     = max_j S^{ij}
Rv      = Σ Wv^i R_i / Σ Wv^i
τ_i     = f(E_i / E_req,i, r_i)
R_i'    = R_i · t_i · τ_i
c_i     = 1 − (1 − R_i') ∏_l (1 − λ_l q_i^l)
R'      = Σ Wv^i c_i / Σ Wv^i
Score   = 𝓜·R' + 𝓕·F,  𝓜 + 𝓕 = 1
```

- `t_i` — убедительность заявления, `F` — соответствие домена (формула не зафиксирована),
  `Wv^i` — вес требования из `weight`/`critical` (формула открыта), `λ_l`/`q_i^l` — вклады
  внешних источников, `c_i` — покрытие, `Score_l`/`Score_h` — границы.
- Критичность повышает вес `Wv`, отдельного cap на балл нет.
- Границы — детерминированная подстановка граничных значений в ту же формулу.
- Шкала `Score`, `F`, `Wv`, `λ` и значения гиперпараметров — открытые вопросы RFC.

### Двухфазный расчёт

1. **Предварительный.** Для всех откликов: обезличенный текст + шаблон → `Score_pre`,
   матрица требований, цитаты. Внешних данных нет, поэтому `c_i = R_i'`.
2. **Шорт-лист.** Политика Java: порог, top-N, пограничная зона (`BORDERLINE`).
3. **Итоговый.** Только для шорт-листа и только при согласии кандидата: GitHub → `q_i^l` →
   тот же расчёт с `λ > 0` → `Score_final` и `delta`. LLM повторно не вызывается.
4. **Рейтинг и отчёт.** В UI всегда подписано, какой балл показан; смешивать нельзя.

---

## 4. Данные и контракты

### Таблицы (`main`)

`migrations/changes/`:

| Changeset | Содержимое |
|---|---|
| `001-vacancy-schema.sql` | `scoring_weights` (seed 0.70/0.15/0.15), `specialization`, `level` (intern/junior/middle/senior), `skill`, `vacancy`, `vacancy_template_version`, `vacancy_requirement`, триггеры неизменяемости версий |
| `009-requirement-skills-and-kinds.sql` | типы требований `MUST/NICE/OPTIONAL`, `kind_source`, `llm_kind`, `skill_text`, удаление `weight` |
| `010-skill-alias-and-seed.sql` | `skill_alias`, seed навыков и алиасов |
| `011-skill-kind-stat.sql` | `skill_kind_stat` — история типов HR по специализации × уровню × навыку |
| `012-seed-specializations.sql` | backend, frontend, fullstack, mobile, data, devops, qa |
| `013-optional-level.sql` | скрытый уровень `none` |
| `014-seed-more-skills.sql` | дополнительные навыки |
| `015-specialization-it.sql` (ветка `feature/specialization-it-flag`) | `specialization.is_it`, IT-коды помечены `true` |

Ветка `feat/resume-service-pipeline` добавляет `002-resume-schema.sql` (`candidate`, `resume`,
`pii_vault`, `parsed_resume`, `application`, `screening_batch`, `score_run`,
`requirement_result`, `external_evidence`, `audit_event`) и `003-demo-vacancy.sql`.

### Публичный REST

`contracts/openapi.yaml` в `main`: `/users/signup|login|refresh` (контракт есть, реализации нет),
`/specializations` (GET/POST), `/levels` (GET), `/vacancies` (GET/POST),
`/vacancies/{id}` (GET/DELETE), `/vacancies/{id}/template` (PUT).
Схемы: `Specialization` (в ветке `it`), `ReferenceItem`, `Requirement`, `Vacancy`,
`VacancyTemplate`, `VacancyList`, `UpdateTemplateRequest`, `ApiStatusResponse`.
`security: BearerAuth` объявлен, но не применён; `SecurityConfiguration` открывает всё.

Ветка `feat/resume-service-pipeline` расширяет контракт путями `/resumes`,
`/resumes/{resumeId}`, `/vacancies/{vacancyId}/screen`, `/screening-batches/{batchId}`,
`/vacancies/{vacancyId}/ranking`, `/applications/{applicationId}/report`,
`/applications/{applicationId}/reveal`. Эндпоинтов вакансий/справочников в её версии контракта нет.

### Внутренние контракты

- `contracts/proto/scoring_service/v1/*.proto` — Java ↔ `scoring-service` (`ScoreResume`,
  модели резюме/вакансии, фичи). Черновик.
- `contracts/vacancy-internal.proto` (ветка `feat/resume-service-pipeline`) — Java ↔ Java gRPC:
  `GetTemplate`, `DecideScreening`.
- `contracts/training-artifact.schema.json` — заявлен в README, в репозитории отсутствует.

---

## 5. Ветка `main` — что реализовано

### `vacancy-service` (полный MVP шаблона вакансии)

Реализовано end-to-end:

- **REST:** справочники, создание вакансии из текста или файла, список с пагинацией,
  чтение текущей версии, сохранение шаблона с проверкой `base_version`, удаление.
- **Создание вакансии:** проверка специализации и уровня (для IT уровень обязателен с
  `feature/specialization-it-flag`; в `main` пустой уровень → скрытый `none`), ровно один
  источник `text`/`file`, экстракт текста через `text-extraction`, вызов LLM, сохранение
  `vacancy` + `vacancy_template_version` v1 (`DRAFT`, `is_confirmed=false`) + требований.
- **Шаблон:** `TemplateExtractionService` (промпт `vacancy-template.v2` + JSON-схема,
  один повтор при невалидном ответе), `TemplateResponseParser` (`schema_version=2`,
  `MUST/NICE/OPTIONAL`), `RequirementCleaner` (цитата-подстрока, дедупликация, лимит 50),
  `SkillCatalog`/`SkillNormalizer` (алиасы, `JS → JavaScript`), `KindHistoryService`
  (подсказка типа при `min-observations=3`, `min-share=0.6`).
- **LLM-клиент:** `SpringAiLlmClient` на Spring AI/OpenAI SDK: основная модель
  `dots-studio/dots-3-note-preview:free` + два fallback, повторы 5s×2, деградация
  `JSON_SCHEMA → JSON_OBJECT → NONE`, общий дедлайн. При недоступности LLM вакансия
  создаётся с пустым шаблоном (RFC-002, 17.3).
- **Хранение:** `vacancy`, версии шаблона (неизменяемы), требования, `skill_alias`,
  `skill_kind_stat`; статистика обновляется при подтверждении и откатывается при удалении.
- **Тесты:** `VacancyApiIntegrationTest` на Testcontainers/Postgres с моками LLM (11 сценариев),
  плюс unit-тесты парсера, очистки, цитат, нормализации, истории типов, LLM-клиента.

Не реализовано: auth/JWT (только контракт), фильтры прав, Rate limiting, метрики.

### `frontend` (Vue 3)

- Маршруты: `/vacancies`, `/vacancies/new`, `/vacancies/:id`.
- Список: поиск, фильтры по статусу/специализации/уровню, удаление, счётчики.
- Создание: специализация, уровень, текст или файл (PDF/DOCX/TXT/MD, drag-and-drop),
  модалка создания специализации, индикатор длительного разбора.
- Редактор: группировка по `MUST/NICE/OPTIONAL`, правка навыка/типа/лет/цитаты/warning,
  дубли, поиск по исходному тексту, подтверждение черновика, обработка 409.
- Тесты: 4 файла, 14 тестов (Vitest), сборка `vue-tsc -b && vite build`.

### `scoring-service` (заглушка)

- gRPC-сервер на `8082`, health-сервис, `ScoreResume` возвращает константы
  (`score=0.5`, `score_low=0.4`, `score_high=0.6`, `coverage=0.5` на каждое требование).
- LLM не вызывает, математики скоринга нет, каталог `src/api/scoring/` отсутствует.
- Тесты: конфигурация, health, заглушка `ScoreResume`.

### `text-extraction` и `infra/mock-llm`

- `text-extraction`: `TextExtractionService`, детектор типа по сигнатуре, PDFBox, POI,
  нормализация текста, таймаут и лимиты; коды `FILE_TOO_LARGE`, `UNSUPPORTED_TYPE`,
  `CORRUPTED_FILE`, `ENCRYPTED_FILE`, `TEXT_EMPTY`, `LIMIT_EXCEEDED`, `EXTRACTION_TIMEOUT`.
- `infra/mock-llm`: OpenAI-совместимый сервер, детерминированный разбор по 24 навыкам,
  сценарии `ok`, `fenced`, `rate-limit-once`, `rate-limit-always`, `invalid-json`,
  `always-invalid`, `truncated`, `slow`, `unauthorized`, `quota`, `bad-quote`.

### Чего в `main` нет

- `resume-service`: только каркас приложения, загрузки резюме и пайплайна нет.
- Нет hh, GitHub, скрининга, рейтинга, `reveal`, отчётов, писем, вопросов по проекту.
- Нет математики скоринга и вызова `scoring-service` из Java.
- Нет `ml-trainer`, MinIO, профилей весов, импорта артефактов.
- Нет auth/JWT и RBAC.

---

## 6. Ветка `feat/resume-service-pipeline` — что реализовано

Ответвлена от `c89237b` и **не содержит** vacancy-MVP из `main` (типы `kind`, новый frontend,
mock-llm, CI). Добавляет 132 файла, +8695/−39.

### `resume-service` (сквозной Java-пайплайн без LLM)

- **REST:** `POST /resumes` (202, батч), `GET /resumes/{id}`, `POST /vacancies/{id}/screen`
  (202), `GET /screening-batches/{id}`, `GET /vacancies/{id}/ranking`,
  `GET /applications/{id}/report`, `POST /applications/{id}/reveal`.
- **Загрузка** (`ResumeIngestionService`): детект типа → оригинал в MinIO
  (`S3ObjectStorage`) → экстракт текста → маскировка → санитайзер промпт-инъекций →
  AES-256-GCM в `pii_vault` → обезличенный текст в `parsed_resume` → `ApplicationEntity`.
- **Маскировка:** regex-детекторы (email, телефоны, паспорт, СНИЛС, ИНН, дата рождения,
  возраст, пол, семейное положение, фото, ссылки), образование и вуз, GitHub-ссылка;
  опциональный ONNX-NER (`OnnxNerModel`, fail-closed) или `NoOpNerModel`;
  плейсхолдеры `[PII_TYPE_N]`; ключ только из конфигурации.
- **Скоринг** (`LocalScoringClient`, ADR 0002 — временная замена Python-сервиса):
  эвристики вместо LLM: `m` по вхождению навыка, `t` по цифрам/глаголам/длине, `rho` по
  `halfLifeYears`, стаж по «N лет»/диапазону годов, `F` из зашитых констант.
- **Формулы** (`ats.resume.domain.scoring.ScoreCalculator`): `q=m·t·ρ`,
  `e=1−(1−q_text)(1−λ·q_ext)`, `d=clamp(used/required)`, `cR=e·d`,
  `Score=100·(wM·M+wN·N+wF·F)`, вилка LOW/HIGH; cap `score<=49` при непокрытом критичном
  требовании (это расходится с текущим `AGENTS.md` §13 — cap запрещён).
- **Проверка цитат:** `EvidenceValidator` принимает только точную подстроку
  обезличенного текста; `TenureCalculator` объединяет пересекающиеся периоды.
- **Скрининг:** `ScreeningService` (202, `screening_batch`), `ScreeningWorker`:
  шаблон из vacancy-service по gRPC → `Score_pre` для всех → решение по политике →
  для шорт-листа и согласия GitHub → `Score_final` → `REFINED`/`ENRICHED`.
- **GitHub:** `HttpGitHubClient` (репозитории, языки, README), `GitHubEnricher`
  (`q=(0.5·volume+0.3·stability+0.2·culture)·ρ`, окно 12 месяцев, антинакрутка
  `RECENT_BULK_UPLOAD`); доказательства только по конкретному навыку, без общего балла.
- **Рейтинг, отчёт, reveal:** `RankingService`, `ReportService` (требование → доказательство
  → пробел, `blind=true`), `RevealService` (расшифровка vault, `audit_event` с причиной).
- **Тесты:** пайплайн на Testcontainers, маскировка, санитайзер, скоринг с числами,
  GitHub, vault; `docs/adr/0001–0005`, `docs/resume-pipeline.md`.

### `vacancy-service` на этой ветке

- `screening/ScreeningPolicy`: порог 50, top-N 10, `borderlineMargin` 5,
  `enrich=true` только для `SHORTLISTED`.
- `template/VacancyTemplateQuery` + `VacancyTemplateData`: SQL-снимок шаблона и весов.
- `VacancyInternalGrpcService`: `GetTemplate`, `DecideScreening`; контракт
  `contracts/vacancy-internal.proto`; порт `9090`.
- Публичного REST вакансий, справочников, LLM-разбора и `skill_kind_stat` здесь нет.

### Чего нет в ветке

- `scoring-service` пуст (только `.gitkeep`/README), LLM-разбора резюме нет,
  неявные навыки не извлекаются.
- Нет frontend, mock-llm, CI, `ml-trainer`, hh, писем, вопросов по проекту, PDF-отчёта,
  auth.
- NER по умолчанию выключен (`fake-ner`).

### Конфликты при слиянии с `main`

- `migrations/db.changelog-master.xml`: 002/003 vs 009–015 (нужна перенумерация
  resume-миграций после 015).
- `contracts/openapi.yaml`: разные наборы путей и схем (нужно объединить резюме-эндпоинты
  с текущими вакансиями/типами).
- `compose.yaml`: `minio`/`minio-init` vs `mock-llm`/`frontend`/`scoring-service`.
- `README.md`, `docs/vacancy-data-model.md`, `vacancy-service/build.gradle`/`Dockerfile`,
  `application.yaml`.
- Скоринг в Java (`LocalScoringClient`, `ScoreCalculator`) и cap 49 противоречат
  `AGENTS.md`: арифметика должна уехать в `scoring-service`.

---

## 7. Ветка `feature/specialization-it-flag` — что реализовано

4 коммита поверх `main`: `027640d`, `5fe3b89`, `580e4be`, `7fca4b0`.

### Признак IT у специализации (`027640d`)

- `contracts/openapi.yaml`: схема `Specialization` (`code`, `name`, `it`),
  `CreateSpecializationRequest` требует `it`.
- `migrations/changes/015-specialization-it.sql`: `specialization.is_it`; backend/frontend/
  fullstack/mobile/data/devops/qa → `true`, пользовательские → `false`.
- `VacancyService.resolveLevel`: IT без уровня или с `none` → `422 LEVEL_REQUIRED`;
  не-IT → скрытый `none`, переданный уровень игнорируется. Аналогично в `VacancyStore`
  при сохранении шаблона.
- `SpecializationEntity`/`SpecializationItem`, `ReferenceController`, `ReferenceApiMapper`,
  `VacancyViewAssembler` — `it` в API.
- Frontend: селект грейда только для IT, чекбокс «IT-специализация» при создании,
  подсказки и тексты; тесты обновлены.

### Ошибки и таймауты LLM (`5fe3b89`)

- `SpringAiLlmClient`: ловятся `OpenAIInvalidDataException` и базовый `OpenAIException`
  (раньше давали 500). Таймаут чтения (`InterruptedIOException`/`TimeoutException`)
  → сразу следующая модель; прочие ошибки → повтор с задержкой; исчерпание →
  `LlmUnavailableException` → пустой шаблон по RFC-002.
- Таймауты: `read-timeout` 180 с, `total-timeout` 300 с, nginx 300 с, axios 310 000 мс,
  текст в UI «до 5 минут»; обновлены `docs/rfc/RFC-002` и `CHANGELOG.md`.
- Тесты: нечитаемый ответ → `LlmUnavailableException`; fallback при обрыве и при таймауте.

### Логирование (`580e4be`)

- `api/RequestLoggingFilter`: `requestId` в MDC (`[req:xxxxxxxx]`, из `X-Request-Id` или
  генерируется), лог завершения HTTP-вызова.
- `support/LogSupport`: cause-цепочка одной строкой, короткие фрагменты текста.
- Логи: параметры создания вакансии, экстракт файла, старт/итог разбора, каждая попытка
  LLM (модель, формат, `elapsedMs`, токены, `finishReason`), `429`/`5xx`/транспорт/таймаут,
  невалидный JSON с причиной и excerpt 300 символов, итог сохранения, отказы 4xx.
- `logging.pattern.level`, `vacancy-service/README.md` (таблица событий), `CHANGELOG.md`.

Проверено: `gradle test spotlessCheck` — 50 тестов; frontend — 14 тестов + сборка;
Liquibase — 16 changeset'ов; E2E на mock-LLM и на 422-пути. Реальный OpenRouter после
изменений вручную не прогонялся.

---

## 8. Сводная матрица возможностей

| Возможность | `main` | `feat/resume-service-pipeline` | `feature/specialization-it-flag` |
|---|---|---|---|
| Публичный REST вакансий и справочников | да | нет (только gRPC-фасад) | да |
| Шаблон вакансии через LLM + `kind` | да | нет | да |
| Память типов `skill_kind_stat` | да | нет | да |
| Frontend (вакансии, редактор) | да | нет | да + правило IT |
| Признак IT у специализации | нет | нет | да |
| Сквозное логирование `vacancy-service` | нет | нет | да |
| Обработка обрыва/таймаута LLM без 500 | нет | нет | да |
| `resume-service`: загрузка, PII, vault | нет | да | нет |
| Маскировка PII и санитайзер инъекций | нет | да | нет |
| Скоринг `ScoreCalculator` (Java) | нет | да, эвристики + cap | нет |
| `scoring-service` gRPC + математика | заглушка | нет | заглушка (из `main`) |
| gRPC `GetTemplate`/`DecideScreening` | нет | да | нет |
| Скрининг, шорт-лист, рейтинг | нет | да | нет |
| GitHub-enrichment | нет | да | нет |
| Отчёт, `reveal`, аудит | нет | да | нет |
| hh, письмо отказа, вопросы, PDF | нет | нет | нет |
| `ml-trainer`, MinIO, профили весов | нет | MinIO да, trainer нет | MinIO нет |
| Auth/JWT | нет | нет | нет |

---

## 9. Расхождения и что нужно решить перед сборкой

1. **Скоринг живёт в Java** (`LocalScoringClient`, `ScoreCalculator`, cap 49) — противоречит
   `AGENTS.md`: арифметика должна быть в `scoring-service`, Java только нормализует факты.
2. **`scoring-service` — заглушка** (0.5), LLM в Python нет; gRPC-контракт — черновик.
3. **Две несовместимые версии `vacancy-service`**: `main` (REST + LLM + `kind`) и ветка
   resume (gRPC-фасад + screening). Нужно объединить, а не выбирать одну.
4. **Нумерация миграций**: resume-ветка использует 002/003, `main` — 009–015.
5. **Разные `openapi.yaml`**: резюме-эндпоинты ветки отсутствуют в `main`; схемы вакансий
   ветки устарели (без `kind`).
6. **`compose.yaml`**: MinIO против mock-llm/frontend/scoring-service; нужен общий файл.
7. **Отсутствуют** auth/JWT, hh, письмо отказа, вопросы по проекту, PDF-отчёт, `ml-trainer`,
   `contracts/training-artifact.schema.json`.
8. **PII-ключ** (`ats.pii.encryption-key`) в resume-ветке обязателен на старте — нужно
   добавить в `.env.example` и документацию.
9. **NER** по умолчанию `NoOp`; `scripts/download-ner.sh` есть, модель не подключена.

---

## 10. Как запускать

### `main`

```bash
docker compose up -d --build
# frontend http://localhost:3000, vacancy-service http://localhost:8080,
# scoring-service :8082, mock-llm :8089, postgres :5432
```

Без ключа LLM: `VACANCY_PROFILES=mock-llm docker compose up -d --build`
(`MockLlmProfileGuard` разрешает только localhost/127.0.0.1/mock-llm).
С ключом: `OPENROUTER_API_KEY` в корневом `.env` (файл не коммитится).

### `feat/resume-service-pipeline`

`docker compose up -d --build` поднимает postgres, migrations, minio, vacancy-service
(8080 и 9090), resume-service (8081). Нужны `ats.pii.encryption-key`, доступ к MinIO,
URL/токен GitHub.

### `feature/specialization-it-flag`

То же, что `main`; после миграции 015 в справочнике появляется `it`, для IT-специализаций
уровень обязателен.

---

## 11. Тесты, CI, качество

- CI (`.github/workflows/ci.yml`): Java-матрица (`text-extraction`, `resume-service`,
  `vacancy-service`) — `test` + `spotlessCheck`; Python — `uv sync --locked`,
  `protoc.sh` + `git diff`, `ruff check`, `ruff format --check`, `pytest`;
  frontend — генерация клиента, `npm test`, `npm run build`; migrations — Liquibase
  в compose.
- Проверки из `README.md` §15: воспроизводимость при замоканной LLM, отбрасывание
  несуществующей цитаты, отсутствие PII в исходящих запросах, устойчивость весов,
  порядок кандидатов на синтетике, contract-тест gRPC после фиксации модели.
- `vacancy-service`: 50 тестов в ветке `feature/specialization-it-flag`; отдельный прогон
  на `main` в этой сессии не делался.
- `resume-service`: интеграционный пайплайн + юнит-тесты формул, маскировки, GitHub, vault.
- frontend: 14 тестов.

---

## 12. Открытые вопросы

**Продуктовые (`README.md` §17):** доступная LLM и бюджет, можно ли реальные резюме,
токен hh, порог шорт-листа, основной пользователь демо, языки интерфейса.

**Модель (`docs/rfc/rfc-scoring-model.md`):** формула `F`, `Wv`, значения `λ_l`, шкала
`Score`, калибровка и валидация.

**Инженерные:** где живёт арифметика после слияния (Java сейчас vs `scoring-service` по
`AGENTS.md`), нужен ли `PUT /specializations/{code}` для смены `it`, переходить ли на
платную LLM вместо `:free`, когда объединять `main` и resume-ветку.

---

## 13. Глоссарий

| Термин | Значение |
|---|---|
| `Score_pre` / `Score_final` | Предварительный балл по тексту и итоговый с внешними данными; разные вызовы одной модели |
| `c_i` | Покрытие требования с учётом внешних источников |
| `q_i^l` | Нормализованный вклад внешнего источника `l` по требованию `i` |
| `λ_l` | Вес внешнего источника; нет данных → 0 |
| `Wv^i` | Вес требования в вакансии (из `weight`/`critical`) |
| `kind` | Тип требования: `MUST`, `NICE`, `OPTIONAL` |
| `skill_kind_stat` | История типов требований, поставленных HR |
| `BORDERLINE` | Пограничный кандидат: вилка пересекает порог |
| `blind` | Слепой режим: PII скрыта до `reveal` |
| `PII vault` | Шифрованное хранилище персональных данных (AES-GCM) |
| `mock-llm` | Детерминированная LLM-заглушка для CI и демо |

---

## 14. Где что лежит

| Документ | Назначение |
|---|---|
| `README.md` | Видение, процесс, архитектура, модель, API, приоритеты, риски |
| `AGENTS.md` | Роли, инварианты, границы сервисов, DoD, приоритеты |
| `docs/rfc/RFC-002_skill-kinds-vacancy-template.md` | Шаблон вакансии, типы `kind`, LLM-клиент |
| `docs/rfc/rfc-scoring-model.md` | Формулы скоринга и открытые вопросы |
| `docs/rfc/rfc-text-extraction.md` | Извлечение текста |
| `docs/vacancy-data-model.md` | Модель данных вакансий |
| `docs/resume-pipeline.md` (ветка resume) | Пайплайн резюме |
| `docs/adr/0001–0005` (ветка resume) | Решения по PII, скорингу, GitHub, screening, инъекциям |
| `docs/reports/001–004` | Отчёты по изменениям |
| `vacancy-service/README.md` | Запуск и логирование сервиса |
| `CHANGELOG.md` | Изменения по веткам |
