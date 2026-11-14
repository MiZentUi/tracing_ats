# Известные расхождения и технический долг

Документ фиксирует места, где текущий код, контракт или документация расходятся с целевым поведением. Это не список задач на немедленное исправление; при изменении затронутого кода проверь актуальность записи.

Приоритеты: **P0** — влияет на корректность или приватность; **P1** — влияет на продуктовую полноту; **P2** — технический долг.

---

## 1. Приватность

| Приоритет | Проблема | Где | Следствие |
|---|---|---|---|
| P0 | По умолчанию NER-провайдер `fake` | `ats-service/src/main/resources/application.yaml`, `compose.yaml` | Имена и локации не маскируются, пока deploy не найдёт модель в `models/ner` или не скачает её по `NER_MODEL_URL`; тогда включается `onnx` |
| P1 | ONNX-модель не входит в репозиторий | `models/` (gitignored), `scripts/download-ner.sh` | CI работает с fake-NER; deploy распаковывает архив из `models/ner` или скачивает `NER_MODEL_URL` |
| P1 | `DICTIONARY_SERVICE_TOKEN` загружается, но не проверяется | `dictionary-service/src/dictionary_service/config.py` | Админские gRPC-методы словаря не защищены |
| P1 | Нет авторизации и ролей | `SecurityConfiguration`, все контроллеры | Любой клиент может читать и менять вакансии, резюме, словарь, раскрывать PII |
| P2 | `actor` в аудите всегда `"hr"` | `AuditEvent` writers | Нельзя различить пользователей |

---

## 2. Скоринг и внешние источники

| Приоритет | Проблема | Где | Следствие |
|---|---|---|---|
| P1 | GitHub-факты не влияют на `Score` | `GitHubEnricher`, `ScreeningWorker`, `ExternalSkillEvidence` | `q` не сохраняется и не передаётся в `scoring-service`; факты видны только в отчёте |
| P1 | `FINAL` — копия `PRELIMINARY` | `ScoreRunWriter.copy(...)` | Нет пересчёта с внешними данными; `Score_final` фактически равен `Score_pre` |
| P1 | Нет `Refine` | `contracts/proto/scoring_service/v1/scoring.proto` | Контракт не позволяет передать внешние `q` |
| P1 | `F = 0`, `𝓜 = 1` | `scoring-service/src/api/scoring/container.py` | Формула домена не реализована |
| P1 | Нет внешних `q_i^l` и `λ_l` | `scoring-service` | Вклад GitHub не входит в `c_i` |
| P1 | Нет общего `τ_total` | `scoring-service/src/api/scoring/evaluator.py` | `Vacancy.tenure` не сравнивается с общим стажем |
| P1 | `recency` не извлекается | `scoring-service/src/api/scoring/extraction/schema.py` | В обычном `ExtractResume`/`ScoreResume` давность всегда неизвестна |
| P2 | `t` реагирует на любую цифру | `HeuristicClaimConfidence._RESULT` | «Python — 3 года» может получить `0.8` вместо `0.3` |
| P2 | Уровень образования сравнивается по ordinal | `RequirementMatcher._match_education` | `COURSES` может удовлетворить `DOCTOR` |
| P2 | Опыт без `metrics` не подтверждается | `FeatureAssessment`/`PriorExperiencePerResume` | Внешне переданный опыт без метрик даёт `NOT_STATED` |
| P2 | Нет cap и поля `capApplied` всегда `false` | `score_run`, OpenAPI | Поле оставлено для совместимости; критичность уже входит в `Wv` |

---

## 3. Контракты и данные

| Приоритет | Проблема | Где | Следствие |
|---|---|---|---|
| P1 | `score_run` не хранит `resolver_version` и `dictionary_mode` | миграции `002`, `016`; Java | Версия словаря не фиксируется в запуске, хотя RFC квалификаций это предполагал |
| P1 | `/users/*` есть в OpenAPI, но не реализован | `contracts/openapi.yaml`, `ats-service` | Клиент может сгенерировать методы, которые вернут 404/не скомпилируются |
| P1 | Устаревший server `http://localhost:8081/api/v1` | `contracts/openapi.yaml` | Фактический REST — `8080`; 8081 — старый порт |
| P1 | `scoring_weights` и `w_m/w_n/w_f` не используются скорингом | `vacancy_template_version`, `scoring-service` | Таблица нужна только для совместимости; `Wv` считается из `criticality` |
| P2 | `critical` всегда `false` | `VacancyStore` | API и UI не выставляют критичность; `CRITICAL` в скоринге фактически не используется |
| P2 | `half_life_years` не передаётся в скоринг | `vacancy-internal.proto` → scoring | Используется фиксированное `2.0` |
| P2 | `requirement_result.m/t/rho` не заполняются | `ScoreRunWriter` | Колонки есть, но текущий writer их не пишет |
| P2 | `scoring_version` в шаблоне — `scoring-v3`, в клиенте — `grpc-scoring-service-v4` | `VacancyTemplateQuery`, `GrpcScoringClient` | Разные строки версии в разных местах |
| P2 | `FeatureCriticality.CRITICAL` комментирует cap | `contracts/proto/scoring_service/v1/models/features/common.proto` | Противоречит решению «критичность в `Wv`, cap нет» |
| P2 | У `candidate_message` нет DB-триггера неизменяемости | `migrations/changes/025-candidate-message.sql` | Таблица append-only только по коду; прямой `UPDATE` в БД не запрещён |

---

## 4. Конфигурация

| Приоритет | Проблема | Где | Следствие |
|---|---|---|---|
| P1 | `ATS_SCREENING_*` не привязываются | `application.yaml`: ключи под `ats.dictionary.screening`, класс ждёт `ats.vacancy.screening` | Работают defaults `50/10/5`; env не действует |
| P2 | `.env.example` включает `CLOUDFLARE_AI_ENABLED=true` | `.env.example` | Расходится с default `false` в compose/application |
| P2 | `SCORING_SERVICE_PORT` — compose-переменная, не настройка сервиса | `compose.yaml`, `scoring-service` | Внутри сервиса порт задаётся `SCORING_SERVICE_GRPC_PORT` |
| P2 | `ignore_invalid_quotes` не влияет на обычный путь `build_scoring_service` | `scoring-service/src/api/config.py`, `container.py` | Флаг фактически не отключает проверку цитат |

---

## 5. UI

| Приоритет | Проблема | Где | Следствие |
|---|---|---|---|
| P1 | Нет blind-mode toggle | `frontend/src` | PII скрыта по умолчанию, но отдельного режима нет |
| P1 | `consentGithub` всегда `false` при загрузке | `ResumeUploader.vue` | GitHub-обогащение нельзя включить из UI |
| P1 | Основной блок отчёта не показывает `phase`, `scoreLow`, `scoreHigh`, `capApplied` | `ReportView.vue` | Пользователь видит только `score`; блок письма подписывает фазу |
| P2 | Нет отдельного экрана рейтинга | `router` | Рейтинг только в сайдбарах |
| P2 | Нет запуска скрининга из UI | `VacancyView.vue` | Кнопка только обновляет рейтинг |
| P2 | Нет batch-загрузки | `ResumeUploader.vue` | Один файл за раз |
| P2 | Нет UI для добавления квалификационного требования | `VacancyEditView.vue` | Существующие квалификации можно редактировать, новую — нет |

---

## 6. Сервисы и CI

| Приоритет | Проблема | Где | Следствие |
|---|---|---|---|
| P1 | Нет CI job для `dictionary-service` | `.github/workflows/ci.yml` | Тесты и ruff словаря не проверяются в CI |
| P1 | `mock-llm` не запускается в CI | `.github/workflows/ci.yml` | Заглушка проверяется только в compose/демо |
| P2 | У `dictionary-service` нет `uv.lock` | `dictionary-service/` | Зависимости не зафиксированы |
| P2 | Три источника seed словаря | `migrations/changes/021`, `dictionary_service/seed.py`, `data/open_seed.json` | Нет автоматической проверки совпадения; открытый seed применяется при старте идемпотентно |
| P2 | `Embedding`/`fuzzy` зарезервированы в proto, но не реализованы | `dictionary.proto` | `RESOLVE_METHOD_EMBEDDING` не выдаётся |
| P2 | Нет OCR | `text-extraction` | PDF без текстового слоя даёт `TEXT_EMPTY` |
| P2 | `.doc` не поддерживается | `text-extraction` | Нужно сохранить как DOCX |

---

## 7. Документация

| Приоритет | Проблема | Где | Следствие |
|---|---|---|---|
| P1 | Старые документы описывали `resume-service`, `vacancy-service`, `ml-trainer`, `training-artifact.schema.json` | активные docs до переработки | Исправлено; исторические отчёты оставлены как архив |
| P2 | `CHANGELOG.md` содержит ссылки на исторические отчёты | `CHANGELOG.md` | Ссылки остаются историческими, не текущей спецификацией |
| P2 | В OpenAPI остались legacy-поля `capApplied`, `critical` | `contracts/openapi.yaml` | Не означают, что cap или критичность реально работают |

---

## 8. Что считать исправлением

Перед изменением записи:

1. Проверь код и контракт, а не только этот документ.
2. Если проблема решена, удали строку или перенеси в `CHANGELOG`.
3. Если проблема требует продуктового решения, сначала RFC/ADR.
4. Если проблема связана с PII, не добавляй в тесты и логи реальные данные.
