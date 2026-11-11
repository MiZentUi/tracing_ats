# Отчёт 005 — единый Java-сервис `ats-service`

Ветка `feature/unified-ats-service` от `origin/main` (`eca2b75`). Решение: `vacancy-service` и
`resume-service` объединяются в один процесс, чтобы убрать размытую ответственность между
контурами вакансии и резюме.

### Что сделано

- `ats-service/` — новый каталог: `git mv vacancy-service ats-service`, код `resume-service`
  перенесён в `ats-service/src/main/java/ats/resume` и `src/test/java/ats/resume`;
  каталог `resume-service/` удалён.
- `ats-service/src/main/java/ats/AtsServiceApplication.java` — единственная точка входа;
  `@ConfigurationPropertiesScan("ats")`, сканирование сущностей и репозиториев покрывает оба
  пакета. Удалены `VacancyServiceApplication`, `ResumeServiceApplication` и дублирующий
  `ats.resume.config.SecurityConfiguration`.
- `ats-service/build.gradle` — объединены зависимости обоих сервисов (LLM/OpenAI, PII/NER,
  AWS S3, gRPC server/client, actuator, Liquibase, Testcontainers); OpenAPI-генерация
  включена в `ats.api`/`ats.gateway.model`; protobuf собирает `contracts/vacancy-internal.proto`
  и `contracts/proto/scoring_service`.
- `contracts/openapi.yaml` — восстановлены потерянные при слиянии main части:
  `/vacancies/{id}`, `/vacancies/{id}/template`, `requestBody`/`responses` у `POST /vacancies`,
  корректные `VacancyId` (`{id}`) и `VacancyPathId` (`{vacancyId}`), тела `/resumes` и
  `/vacancies/{vacancyId}/screen`, схемы `RankingItem`, `ApplicationReport`,
  `RequirementReportItem`.
- `ats-service/src/main/resources/application.yaml` — объединённая конфигурация: `8080` REST,
  `9090` gRPC, PII/S3/GitHub/screening/scoring, настройки LLM и шаблона.
- `ats-service/src/test/resources/application-test.yaml` — единый тестовый профиль: Liquibase,
  `memory`-storage, тестовый PII-ключ, `github.enabled=false`, воркеры по 1.
- `migrations/db.changelog-master.xml` — восстановлен порядок include; демо-миграция
  `003-demo-vacancy.sql` переписана под актуальную схему (`kind`, `critical`, без `weight`,
  навыки через справочник); `002-resume-schema.sql`, `004-scoring-integration.sql` включены.
- `compose.yaml` — один сервис `ats-service` (порты 8080/9090) с MinIO, migrations,
  scoring-service, mock-llm и frontend; удалён отдельный `resume-service`.
- `ats-service/Dockerfile` — сборка из корня репозитория с `text-extraction` и `contracts`.
- `frontend/nginx/templates/default.conf.template`, `frontend/Dockerfile` — прокси на
  `ATS_SERVICE_URL`.
- `.github/workflows/ci.yml` — Java-матрица: `text-extraction` и `ats-service`.
- `AGENTS.md`, `README.md` (структура репозитория), `ats-service/README.md`, `CHANGELOG.md` —
  обновлены под единый сервис.

### Проверка

- `cd ats-service && ./gradlew test` → `BUILD SUCCESSFUL`, 71 тест, 0 failed.
  Покрыты сценарии вакансий (`VacancyApiIntegrationTest`), пайплайн резюме
  (`ResumePipelineIntegrationTest`), PII/маскировка, скоринг, GitHub, vault, gRPC-клиенты.
  LLM в тестах замокана, реальных вызовов нет.
- `./gradlew spotlessCheck` → `BUILD SUCCESSFUL` после `spotlessApply` для двух тестовых файлов.
- `docker compose config --quiet` → валидно.
- `docker compose build ats-service` → `Image tracing-ats-ats-service Built`.
- `docker compose -p ats-unified-test up` (профиль `mock-llm`): `GET /specializations` → 200,
  `POST /vacancies` → 201 (1 требование от mock-LLM), `POST /resumes` → 202 со статусом
  `MASKED`, `GET /vacancies/{id}/ranking` → 200. Внешняя LLM не вызывалась.
- Проверка вскрыла две проблемы окружения, не связанные с merge: `scoring-service` в образе
  падает с `ModuleNotFoundError: scoring_service.v1` (сгенерированные protobuf-модули не
  собираются в Dockerfile), а MinIO отдаёт 507 при заполненном диске; на `memory`-storage
  загрузка резюме проходит.

### Не сделано / проблемы

- `resume-service` по-прежнему ходит в `vacancy-service` по gRPC; после слияния это вызов
  самого себя (`localhost:9090`). Работает, но прямая замена на вызов бина — отдельная задача.
- `scoring-service` остаётся Python-сервисом; Java-арифметика из `LocalScoringClient`
  не переносилась.
- `hh`, письмо отказа, вопросы по проекту, PDF-отчёт, `ml-trainer`, auth/JWT как не были
  реализованы, так и отсутствуют.
- NER по умолчанию `fake`; ONNX-модель в образ не входит.

### Требует решения пользователя

- Оставлять ли gRPC self-call между `ats.vacancy` и `ats.resume` или заменить на прямой
  вызов бина (упрощает отладку, убирает сетевой хоп).
- Нужно ли переносить расчёт скоринга из Java в `scoring-service` сразу или отдельной задачей.
