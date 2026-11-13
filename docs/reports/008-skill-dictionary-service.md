# Отчёт 008 — dictionary-service: нормализация навыков, алиасы и админка

## 1. Задача

До ветки нормализация навыков существовала только на стороне вакансии:

- `ats-service` хранил `skill` и `skill_alias` в `public`;
- `SkillNormalizer` + `SkillCatalog` применяли алиасы при разборе и сохранении шаблона;
- `scoring-service` нормализовал имя навыка из резюме только через `normalize_key` (NFKC, регистр, пробелы, краевая пунктуация);
- алиасы к резюме не применялись.

Практические последствия:

- `js` в резюме не находил `javascript` из вакансии;
- `питон` не находил `python`;
- `1С` (кириллическая `С`) не совпадала с `1C`;
- `кубернетесом` не находилось точным ключом;
- у `vue`/`vuex` срабатывал ложный fuzzy `0.85`;
- не-IT навыки (`1С:Бухгалтерия`, `Excel`, учёт ОС/ТМЦ, делопроизводство) отсутствовали;
- новые навыки создавались молча, словарь крошился, HR не мог привязать алиас;
- не было версии словаря и воспроизводимости запусков;
- требование без `skill_id` уходило в скоринг как цитата (`- Tauri`), а не как название навыка.

## 2. Принятые решения и мотивы

### 2.1. Отдельный `dictionary-service`, а не логика внутри `scoring-service`

Решение: словарь и нормализация вынесены в новый Python-сервис.

Мотивы:

- один владелец данных вместо двух копий алгоритма;
- одинаковый конвейер для вакансии и резюме;
- `scoring-service` остаётся stateless и не получает доступ к БД;
- `ats-service` не дублирует python-нормализацию.

Компромисс: появился новый сервис, новая схема и синхронная зависимость на пути скоринга.

### 2.2. Python 3.12 + pymorphy3

Мотивы:

- нужна морфология русского языка;
- `pymorphy3` даёт леммы (`кубернетесом → кубернетес`);
- Python-сервис проще расширять под будущие эмбеддинги и админскую генерацию алиасов.

### 2.3. Своя схема `dictionary` в том же Postgres

Решение: таблицы словаря лежат в `dictionary`, а не в `public`.

Мотивы:

- `public.skill` и `public.skill_alias` уже заняты `ats-service`;
- не нужно поднимать второй инстанс Postgres;
- схема даёт отдельные имена, права и возможность `pg_dump -n dictionary`.

Реализация: `PostgresStore` создаёт схему и подключается с `search_path=dictionary`; `ats-service` продолжает работать в `public`.

Ограничение: это не физическая изоляция — один инстанс, один WAL, один пул.

### 2.4. gRPC внутри, REST-админка в `ats-service` (BFF)

Решение:

- `dictionary-service` предоставляет только gRPC (`Resolve`, `GetVersion`, `GetSkills`, админские RPC);
- публичный REST для админки живёт в `ats-service` (`/api/v1/admin/dictionary`).

Мотивы:

- только `ats-service` знает ссылки на `skill_id` в `vacancy_requirement` и `skill_kind_stat`;
- проверка «на навык нет ссылок» перед удалением делается там, где эти таблицы;
- `dictionary-service` остаётся внутренним и не требует отдельной auth-модели;
- фронтенд уже ходит в `ats-service`.

### 2.5. Два ключа и свёртка гомоглифов только по точному алиасу

Решение: для каждого написания считаются:

- `normalized_key` — NFKC, пробелы, lower, `ё → е`, краевая пунктуация, лемматизация;
- `folded_key` — свёртка кириллических двойников, но только если в токене есть ASCII-сигнал: латиница, цифра, `+` или `#`.

Свёрнутый ключ используется только для точного поиска по алиасу.

Мотивы:

- `1С → 1c`, `С++ → c++`, `Еxcel → excel`;
- `сок` не превращается в `cok`, потому что алиаса `cok` нет;
- чисто кириллические слова не искажаются.

### 2.6. `кубы` — RAW_ONLY

Проблема: pymorphy3 лемматизирует `кубы → куб`, а `куб` — это геометрия, не Kubernetes.

Решение: у алиаса есть `lemma_mode {AUTO, RAW_ONLY}`. Для `кубы` включён `RAW_ONLY`: сохраняется только исходный ключ.

### 2.7. Алиасы: ключи + ровно один канонический, без merge

Модель:

- `skill_alias` — написание, флаг `is_canonical`, язык, источник, `lemma_mode`;
- `skill_alias_key` — ключи алиаса (`RAW`, `FOLDED`, `LEMMA`, `LEMMA_FOLDED`), ключ глобально уникален;
- частичный unique index: ровно один канонический алиас на навык.

Merge не реализован. Мотив: merge меняет смысл исторических шаблонов и запусков, требует перепривязки `skill_kind_stat` и разрешения конфликтов `uq_requirement_skill`. Вместо merge:

- удалить ошибочный алиас;
- добавить его к нужному навыку;
- неиспользуемый навык удалить/пометить `DEPRECATED`.

### 2.8. `skill_type` вместо `kind`

Поле переименовано в `skill_type` со значениями `TECH`, `TOOL`, `DOMAIN`, `SOFT`, `OTHER`.

Мотивы:

- `kind` уже занят модальностью требования (`MUST`, `NICE`, `OPTIONAL`);
- профессии и квалификации — не навыки; права, допуски и разряды в словарь не входят.

### 2.9. Составной `resolver_version`

`resolver_version = d<dictionary_version>-e<embedding_model_id>-c<config_hash>-p<pymorphy_version>-h<homoglyph_rules_version>`.

Мотивы:

- изменение порогов, модели эмбеддингов или pymorphy3 тоже меняет результат;
- запуск должен фиксировать не только версию словаря, но и весь конвейер;
- `Resolve` принимает `as_of_resolver_version`, снимки хранятся в `dictionary_snapshot`.

### 2.10. Деградация до точного ключа

Решение: при `UNAVAILABLE`/`DEADLINE_EXCEEDED` scoring не падает, а сравнивает навыки по локальному точному ключу без лемматизации.

Мотивы:

- словарь не должен быть единой точкой отказа всего скоринга;
- запуск фиксирует `dictionary_mode=EXACT_FALLBACK` и версию `local-exact-v1`;
- результат воспроизводим и явно помечен.

Компромисс: во время недоступности `js` снова не равен `javascript`.

### 2.11. Fuzzy для навыков убран

Решение: сравнение только по `skill_id` или точному ключу.

Мотивы:

- `vue`/`vuex` давали ложный `0.85`;
- `1С:Бухгалтерия` давала ложный partial;
- fuzzy оставлен только для образования и текстовых требований.

### 2.12. Эмбеддинги отложены

В v1 подсказки в очереди NEW считаются `difflib.SequenceMatcher` по канонам и алиасам.

Мотивы:

- эмбеддинги на коротких названиях путают `Java`/`JavaScript`;
- нельзя включать непроверенную похожесть в `S^{ij}`;
- подсказка не влияет на матчинг, только помогает HR.

### 2.13. Локальное зеркало в `ats-service`

Решение: `ats` не удаляет `public.skill`/`public.skill_alias`, а использует их как зеркало.

Мотивы:

- `vacancy_requirement.skill_id` и `skill_kind_stat.skill_id` имеют FK;
- полная миграция ID и удаление таблиц — отдельная задача;
- `ensureMirror` ищет локальный навык по `id`, затем по `normalized_name`, и только потом вставляет.

Ограничение: у старых seed-навыков локальные ID могут отличаться от ID dictionary; scoring сравнивает по канону, поэтому матчинг не страдает, но история `skill_kind_stat` остаётся на старых ID.

## 3. Технологии

| Слой | Технологии |
|---|---|
| `dictionary-service` | Python 3.12, grpcio, protobuf, pymorphy3, psycopg3 + pool, difflib, Postgres 18 |
| Контракт | Protobuf `dictionary_service.v1`, Java package `ats.dictionary.proto` |
| `ats-service` | Java 21, Spring Boot 4, Spring gRPC client, JdbcTemplate/JPA, Lombok, Spotless |
| `scoring-service` | Python 3.14, grpcio, pydantic, protobuf, pytest |
| Frontend | Vue 3, TypeScript, Tailwind, axios, vue-router |
| Инфраструктура | Docker Compose, Postgres, MinIO, mock-llm |

## 4. Где что хранится

### 4.1. `dictionary-service`, схема `dictionary`

```text
skill(
  id uuid PK,
  status {ACTIVE, DEPRECATED},
  skill_type {TECH, TOOL, DOMAIN, SOFT, OTHER},
  created_at, updated_at
)

skill_alias(
  id uuid PK,
  skill_id uuid FK -> skill ON DELETE CASCADE,
  alias_raw text,
  is_canonical boolean,
  lang {ru, en, mixed},
  source {SEED, HR, LLM, EMBEDDING_CONFIRMED},
  lemma_mode {AUTO, RAW_ONLY},
  created_by, created_at
)
-- partial unique: (skill_id) WHERE is_canonical

skill_alias_key(
  key text PK,
  alias_id uuid FK -> skill_alias ON DELETE CASCADE,
  kind {RAW, FOLDED, LEMMA, LEMMA_FOLDED}
)

review_item(
  id, key UNIQUE, sample_masked text[], occurrences,
  status {PENDING, RESOLVED_ALIAS, RESOLVED_NEW_SKILL, REJECTED},
  suggested_skill_id, suggested_confidence, candidates jsonb,
  resolved_skill_id, resolved_by, resolved_at, first_seen, last_seen
)

dictionary_meta(
  id smallint PK CHECK (id = 1),
  dictionary_version bigint,
  embedding_model_id, config_hash,
  pymorphy_version, homoglyph_rules_version
)

dictionary_snapshot(
  resolver_version text PK,
  payload jsonb,
  created_at
)
```

Seed: 79 навыков, включая не-IT. ID seed-навыков детерминированы через UUID5, чтобы повторный запуск не создавал дубли.

### 4.2. `ats-service`, `public`

- `vacancy_requirement.skill_id` — локальное зеркало ID;
- `vacancy_requirement.skill_text` — каноническое или исходное имя;
- `vacancy_requirement.raw_text` — цитата;
- `skill` и `skill_alias` — локальное зеркало для FK и fallback;
- `skill_kind_stat` — статистика HR по `skill_id`.

### 4.3. `scoring-service`

- `ParsedResume` хранит `name` и `normalized_name` навыка из LLM;
- канон не подменяет имя и цитату;
- в ответе `EvaluateResumeMatchResponse` / `ScoreResumeResponse` добавлены `resolver_version` и `dictionary_mode`;
- `EvaluateResumeMatchesResponse` содержит общие `resolver_version`/`dictionary_mode` для батча.

## 5. Как работает `Resolve`

1. Валидация: непустое имя, до 256 символов, до 2000 элементов в батче.
2. `normalize_base`: NFKC, схлопывание пробелов, lower, `ё → е`, снятие краевой пунктуации и завершающей точки.
3. `fold_homoglyphs`: только для токенов с ASCII-сигналом.
4. `lemmatize`: только полностью кириллические токены, через pymorphy3; результат снова нормализуется.
5. Строятся ключи в порядке `RAW → FOLDED → LEMMA → LEMMA_FOLDED`.
6. Поиск по `skill_alias_key`; первый найденный ключ определяет `method` (`ALIAS_EXACT`, `ALIAS_FOLDED`, `ALIAS_LEMMA`, `ALIAS_LEMMA_FOLDED`).
7. Если совпадения нет — `NEW`, запись в `review_item`, кандидаты-подсказки.
8. `as_of_resolver_version` переключает чтение на снимок `dictionary_snapshot`.

Примеры:

| Вход | Результат |
|---|---|
| `js` | `JavaScript`, `ALIAS_EXACT` |
| `1С` | `1C`, `ALIAS_FOLDED` |
| `С++` | `C++`, `ALIAS_FOLDED` |
| `кубернетесом` | `Kubernetes`, `ALIAS_LEMMA` |
| `кубы` | `Kubernetes`, `ALIAS_EXACT` (RAW_ONLY) |
| `куб` | NEW, не Kubernetes |
| `сок` | NEW, не `cok` |
| `учёт` / `учет` | один ключ `учет` |
| `vue` / `vuex` | разные навыки |

## 6. Как это встроено в `ats-service`

- `DictionaryProperties` (`ats.dictionary.grpc`) — `enabled`, `target`, `deadline`.
- `DictionaryGrpcConfiguration` — канал и blocking stub с `GrpcClientLoggingInterceptor`.
- `DictionaryClient` — `resolve`, `createSkill`, `addAlias`, `listReview`, `resolveReview`, `listSkills`, `setCanonical`, `deleteAlias`, `deprecate`, `deleteSkill`, `resolverVersion`.
- `SkillCatalog.resolve`:
  1. если словарь включён — `DictionaryClient.resolve`;
  2. при `DictionaryUnavailableException`/`DictionaryRpcException` — локальный fallback;
  3. `MATCHED` получает `skill_id` и канон, `NEW` — исходное имя.
- `NewSkillWriter`:
  - `ensureSkill` создаёт навык в dictionary, затем локальное зеркало;
  - `ensureMirror` для `MATCHED` гарантирует наличие локального `skill` перед вставкой требования;
  - при недоступности словаря — локальное создание.
- `RequirementCleaner` и `VacancyStore` используют `SkillCatalog`; дубликаты считаются по `skill_id` или нормализованному ключу.
- `VacancyTemplateQuery` исправлен: для требования без `skill_id` берётся `skill_text`, затем `raw_text`; это убрало отправку `- Tauri` в скоринг.
- Админка — `DictionaryAdminController` в `/api/v1/admin/dictionary`.

## 7. Как это встроено в `scoring-service`

- `DictionaryClient` в `api/dictionary/client.py`:
  - батч `Resolve` на список требований и навыков резюме;
  - таймаут, логирование `outbound request sent/received/failed`;
  - `as_of` из конфигурации.
- `SkillResolver` в `api/scoring/resolver.py`:
  - `DICTIONARY` — использует `skill_id` и ключи из ответа;
  - `EXACT_FALLBACK` — локальные ключи `normalize_key` + гомоглифы без лемм;
  - score = `1.0`, если совпал `skill_id` или ключ, иначе `0.0`.
- `RequirementMatcher.prepare` собирает имена требований и резюме, делает один батч на вакансию.
- `matcher._match_skill` больше не вызывает `SequenceMatcher`; fuzzy остался только для образования и опыта.
- `mapping` добавляет `resolver_version` и `dictionary_mode` в ответы.
- `config.py` — `SCORING_SERVICE_DICTIONARY_ENABLED`, `_TARGET`, `_DEADLINE_SECONDS`, `_AS_OF_VERSION`.
- `Dockerfile` и `protoc.sh` генерируют `dictionary_service/v1` stubs; `pyproject` включает пакет в сборку.

## 8. Админка и фронтенд

- `DictionaryAdminController`:
  - `GET /version`;
  - `GET /review-items?status=`;
  - `POST /review-items/{id}/resolve`;
  - `GET /skills`, `POST /skills`;
  - `POST /skills/{id}/aliases`, `POST /skills/{id}/canonical`;
  - `DELETE /aliases/{id}`;
  - `POST /skills/{id}/deprecate`;
  - `DELETE /skills/{id}?confirmNoRefs=true`.
- Удаление проверяет `vacancy_requirement` и `skill_kind_stat`; при ссылках — 409 `SKILL_IN_USE`.
- Ошибки dictionary маппятся в 404/409/422/503.
- Frontend: `frontend/src/views/DictionaryView.vue`, `frontend/src/api/dictionary.ts`, маршрут `/dictionary`, ссылка «Словарь» в шапке.
- Очередь NEW показывает `key`, `samples`, вхождения, кандидатов; действия «Похоже на …», «Создать навык», «Отклонить».
- Вкладка «Навыки» — поиск, список алиасов, добавление алиаса.

## 9. Добавленная функциональность

- Нормализация и алиасы применяются к вакансии и резюме одним конвейером.
- Поддержка `js/javascript`, `питон/python`, `1С/1C`, `С++/c++`, `учёт/учет`, `кубернетесом/Kubernetes`, `постгрес/PostgreSQL`.
- Очередь NEW и ручное подтверждение HR.
- Ровно один канонический алиас; конфликт алиасов возвращает 409.
- Удаление/перепривязка алиасов без merge.
- Удаление неиспользуемого навыка с проверкой ссылок.
- `skill_type`, не-IT seed.
- `resolver_version`, снимки, `as_of`, `dictionary_mode`.
- Fallback без падения скоринга.
- Исправлен `- Tauri` → `Tauri` для NEW-требований.
- Mock-LLM знает `JS` и `Tauri` для демо.
- Демо-скрипт `scripts/demo-skill-dictionary.sh`, документ `docs/demo-skill-dictionary.md`.

## 10. Проверка

- `dictionary-service`: `pytest` → 18 passed; `ruff check` → clean.
- `scoring-service`: `pytest` → 74 passed; `ruff check` → clean.
- `ats-service`: `./gradlew test` → BUILD SUCCESSFUL; `spotlessCheck` → clean.
- `docker compose config -q` → OK.
- Docker-образы `dictionary-service`, `scoring-service`, `ats-service`, `frontend` собираются.
- `docker compose up -d --build` с нуля → все контейнеры поднялись, `dictionary-service` healthy.
- Frontend production-сборка (`vue-tsc` + `vite build`) проходит в Docker.
- Реальная LLM:
  - создание вакансии — 201 за ~8.6 с, модель `dots-studio/dots-3-note-preview:free`;
  - разбор резюме — ~36.5 с, 4 навыка, 1 образование, 1 опыт;
  - отчёт по вакансии: `PostgreSQL`, `JavaScript`, `Docker` — `COVERED` с цитатами.
- Fallback: при остановленном dictionary в логах scoring `dictionary_unavailable fallback=exact`; запросы завершаются без 500.
- Демо-скрипт на mock-профиле: `JS`, `Tauri`, `PostgreSQL` → все `COVERED`, score 45.4.

## 11. Не сделано и ограничения

- Админские REST-эндпоинты не внесены в `contracts/openapi.yaml`; frontend использует ручной модуль `api/dictionary.ts`.
- `resolver_version` возвращается scoring-сервисом, но не сохраняется в сущности запуска ats.
- `as_of_resolver_version` задаётся конфигурацией scoring-сервиса, а не каждым запуском.
- Локальные `skill_id` ats не мигрированы в dictionary; работает зеркало и сопоставление по `normalized_name`.
- `mock-scoring-service` продолжает использовать собственный мини-словарь.
- Эмбеддинги не подключены; подсказки — `difflib` с порогом 0.6.
- Merge отсутствует по решению.
- Проверка `is_confirmed` перед скорингом не добавлена.
- Per-skill трасса «сырое имя → канон → method → confidence» в отчёт не выводится; есть `resolver_version`/`dictionary_mode` в ответе scoring.
- Схема dictionary создаётся самим сервисом при старте, без Liquibase-миграции.
- Аутентификации в проекте нет: админка открыта, как и остальной API.
- `dictionary-service` не использует `DICTIONARY_SERVICE_TOKEN`, поле конфигурации зарезервировано.

## 12. Файлы

Основные новые:

- `contracts/proto/dictionary_service/v1/dictionary.proto`;
- `dictionary-service/` — сервис, тесты, Dockerfile, seed;
- `ats-service/src/main/java/ats/vacancy/dictionary/` — клиент, BFF, DTO;
- `scoring-service/src/api/dictionary/`, `scoring-service/src/api/scoring/resolver.py`;
- `frontend/src/views/DictionaryView.vue`, `frontend/src/api/dictionary.ts`;
- `scripts/demo-skill-dictionary.sh`, `scripts/demo-resume.txt`;
- `docs/demo-skill-dictionary.md`, `docs/technical_specification/tz-skill-dictionary-service.md`.

Основные изменённые:

- `ats-service/build.gradle`, `application.yaml`, `SkillCatalog`, `NewSkillWriter`, `VacancyStore`, `VacancyTemplateQuery`, `ApiExceptionHandler`;
- `scoring-service/Dockerfile`, `protoc.sh`, `pyproject.toml`, `config.py`, `extraction/normalize.py`, `scoring/*`;
- `contracts/proto/scoring_service/v1/models/messages.proto`;
- `compose.yaml`, `.env.example`, `README.md`, `CHANGELOG.md`, `docs/rfc/rfc-skill-dictionary.md`;
- `infra/mock-llm/server.py`, `scoring-service/src/api/llm/mock.py`.

## 13. Как проверить руками

```bash
export ATS_PROFILES=mock-llm
export SCORING_SERVICE_LLM_PROVIDER=mock
docker compose up -d --build
./scripts/demo-skill-dictionary.sh
```

UI: <http://localhost:3000>, словарь: <http://localhost:3000/dictionary>.
Для реальной LLM:

```bash
unset ATS_PROFILES
export SCORING_SERVICE_LLM_PROVIDER=openai
docker compose up -d --force-recreate ats-service scoring-service
```
