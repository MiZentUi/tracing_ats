# Changelog

В этом файле собраны все значимые изменения проекта.

Формат основан на [Keep a Changelog](https://keepachangelog.com/),
версии соответствуют [семантическому версионированию](https://semver.org/lang/ru/).

## [Unreleased]

### Ветка `feature/resume-to-vacancy-matching` — обратный подбор и новый контракт

#### Added

- **Режим «вакансии под резюме».** `POST /api/v1/resumes` без `vacancyId` принимает
  пачку резюме, не создаёт отклик и запускает сравнение со всеми вакансиями.
  `GET /api/v1/resumes/{resumeId}/ranking` отдаёт рейтинг вакансий,
  `GET /api/v1/resumes/{resumeId}/vacancies/{vacancyId}/report` — отчёт по паре,
  `POST /api/v1/resumes/{resumeId}/match` пересчитывает без LLM.
- **Пакетная оценка вакансий.** `EvaluateResumeMatches` вызывается чанками по
  `ats.resume.match.batch-size` (по умолчанию 3, env `ATS_RESUME_MATCH_BATCH_SIZE`).
- **Кеш `ParsedResume`.** `ExtractResume` вызывается один раз на резюме, снимок
  хранится в неизменяемой `resume_extraction`; повторные расчёты LLM не вызывают.
- **Frontend.** Экран «Подбор» с переключением режимов, загрузка пачки, прогресс
  пакета, рейтинг кандидатов и вакансий, отчёт и reveal.

#### Changed

- **Java переведена на новый контракт scoring.** Вместо legacy `ScoreResume`
  используются `ExtractResume` и `EvaluateResumeMatch`/`EvaluateResumeMatches`.
  Статусы требований теперь различают `COVERED`, `PARTIAL`, `GAP`, `NOT_STATED` и `ABSENT`.
- **Единое хранилище запусков.** `score_run` и `screening_batch` получили
  `direction` (`VACANCY_TO_CANDIDATES` / `RESUME_TO_VACANCIES`) и nullable-владельца,
  чтобы оба рейтинга считались одной моделью и одним отчётом.

#### Security

- **Образование больше не маскируется**: вуз, специальность и годы остаются в
  `masked_text`, ORG от NER не превращается в `PII_UNIVERSITY`. Инвариант
  «маскировка до LLM» обновлён в `AGENTS.md` и ADR 0001; вуз не является отдельным
  сигналом скоринга. Имена, контакты, адреса и документы маскируются как раньше.

### Ветка `feature/scoring-service-mock` — заглушка scoring-service

#### Added

- **Мок scoring-service по актуальному gRPC-контракту.**
  `infra/mock-scoring-service` реализует `ExtractResume`, `EvaluateResumeMatch`,
  `EvaluateResumeMatches` и `ScoreResume` из
  `contracts/proto/scoring_service/v1/scoring.proto`. Модели скоринга нет:
  навыки, образование и опыт извлекаются по фиксированному словарю, цитаты
  проверяются как точные срезы текста, статусы `FULL`/`PARTIAL`/`ABSENT`/
  `NOT_STATED` и пробелы собираются детерминированно.
- **Режимы для тестов.** Сценарии `unavailable`, `invalid-argument`, `internal`,
  `slow`, `empty` через маркер в тексте или metadata `x-mock-scenario`; изоляция
  ошибки одной вакансии в пакетном вызове через `MOCK_VACANCY_ERROR`.
- **Compose-профиль `mock-scoring` и CI-джоба** `mock-scoring`; адрес скоринга
  в `ats-service` теперь переопределяется через `SCORING_GRPC_TARGET`.

#### Security

- Заглушка не вызывает LLM и не ходит в БД, файлы и внешние API. В логи пишутся
  только метод, статус, `request_id`, длительность и счётчики — без текста
  резюме и цитат.

#### Docs

- `infra/mock-scoring-service/README.md`: правила сопоставления, ограничения,
  запуск и переменные окружения.

### Ветка `feature/scoring-service` — пайплайн извлечения

#### Added

- **Извлечение признаков резюме через LLM.** `scoring-service` вызывает
  OpenAI-совместимый `/chat/completions` через официальный SDK `openai`
  (`base_url` настраивается: OpenRouter, локальные OpenAI-совместимые
  серверы), получает строгий JSON `ParsedResume` со навыками, образованием
  и опытом, проверяет цитаты как подстроки со схлопыванием пробелов и
  отбрасывает непрошедшие.
- **Детерминированная заглушка `S^{ij}`.** Псевдослучайная матрица с seed из
  SHA-256 текста резюме; `coverage = relevance`, агрегаты `Score` остаются
  заглушкой до реализации модели.
- **Провайдер LLM.** `mock` по умолчанию (без сети, для CI и демо) и `openai`
  для любого OpenAI-совместимого эндпоинта; таймауты, повторы на 429/5xx
  силами SDK, один повтор на невалидный JSON/схему.
- **Логирование LLM.** Успех и ошибка внешнего вызова пишутся с задержкой,
  статусом HTTP, `request_id`, расходом токенов и `finish_reason`; текст
  запроса и ответа не логируется.
- **Ошибки gRPC.** `INVALID_ARGUMENT` для пустого текста, `UNAVAILABLE` при
  недоступной LLM, `INTERNAL` после невалидного ответа — без «0 баллов».

#### Changed

- `ScoringService` принимает `ResumeExtractor`; `create_server` собирает
  LLM-клиент из настроек. Заглушка `api/scoring.py` заменена пакетом
  `api/scoring/`.

#### Security

- В LLM уходит только обезличенный текст резюме; тексты, цитаты и ответы
  модели не логируются, в логах только счётчики.
- Ключ LLM читается из окружения, хранится как `SecretStr` и не попадает в
  логи.

### Ветка `feature/scoring-service-impl` — пайплайн извлечения

#### Added

- **Извлечение признаков резюме через LLM.** `scoring-service` вызывает
  OpenAI-совместимый `/chat/completions` через официальный SDK `openai`
  (`base_url` настраивается: OpenRouter, локальные OpenAI-совместимые
  серверы), получает строгий JSON `ParsedResume` со навыками, образованием
  и опытом, проверяет цитаты как подстроки со схлопыванием пробелов и
  отбрасывает непрошедшие.
- **Метод `ExtractResume`.** Новый gRPC-метод возвращает `ParsedResume`
  (навыки, образование, опыт) без сверки с вакансией и без скоринга; в контракт
  добавлены `ParsedResume`, `ExtractResumeRequest/Response`,
  `EducationPerResume.level` и `PriorExperiencePerResume.company`.
- **Детерминированная заглушка `S^{ij}`.** Псевдослучайная матрица с seed из
  SHA-256 текста резюме; `coverage = relevance`, агрегаты `Score` остаются
  заглушкой до реализации модели.
- **Провайдер LLM.** `mock` по умолчанию (без сети, для CI и демо) и `openai`
  для любого OpenAI-совместимого эндпоинта; таймауты, повторы на 429/5xx
  силами SDK, один повтор на невалидный JSON/схему.
- **Логирование LLM.** Успех и ошибка внешнего вызова пишутся с задержкой,
  статусом HTTP, `request_id`, расходом токенов и `finish_reason`; текст
  запроса и ответа не логируется.
- **Логирование gRPC-запросов.** Интерсептор пишет полное имя метода при
  старте и статус с длительностью при завершении; тела запросов и ответов
  не логируются.
- **Ошибки gRPC.** `INVALID_ARGUMENT` для пустого текста, `UNAVAILABLE` при
  недоступной LLM, `INTERNAL` после невалидного ответа — без «0 баллов».

#### Changed

- `ScoringService` принимает `ResumeExtractor`; `create_server` собирает
  LLM-клиент из настроек. Заглушка `api/scoring.py` заменена пакетом
  `api/scoring/`.
- `ScoreResume` заполняет новые поля `EducationPerResume.level` и
  `PriorExperiencePerResume.company`; расчёт скоринга не меняется.

#### Security

- В LLM уходит только обезличенный текст резюме; тексты, цитаты и ответы
  модели не логируются, в логах только счётчики.
- Ключ LLM читается из окружения, хранится как `SecretStr` и не попадает в
  логи.

### Ветка `feature/unified-ats-service` — event logging

#### Added

- Единый `request_id` для HTTP, gRPC и фоновых задач: MDC `request_id`, заголовок ответа
  `X-Request-Id`, metadata `x-request-id` при исходящих gRPC-вызовах.
- `GrpcServerLoggingInterceptor` и `GrpcClientLoggingInterceptor`: парные события
  `inbound_request_received`/`inbound_response_sent` и `outbound_request_sent`/
  `outbound_response_received`/`outbound_request_failed`, health/reflection исключены.
- События LLM и шаблона в стабильном формате `event_name key=value`: `llm_chain_started`,
  `llm_request_sent`, `llm_response_received`, `llm_rate_limited`, `llm_timeout`,
  `llm_model_exhausted`, `template_extraction_started/finished`,
  `template_requirements_all_dropped`.
- Фоновые задачи: `screening_batch_started/finished/failed`,
  `preliminary_scoring_started/finished/failed`, `scoring_wait_timed_out`,
  `github_enrichment_failed/skipped`.
- `resume_ingested` в `ResumeIngestionService`: статус, тип файла, размер, предупреждения.
- `request_id` переносится в фоновые задачи (`PreliminaryScoringListener`, `ScreeningService`),
  поэтому скоринг и скрининг связаны с исходным HTTP-запросом.
- `scoring-service`: gRPC interceptor с теми же событиями и `score_resume_completed`.
- Frontend: модуль `src/lib/logger.ts`, логирование ошибок API с `method`, `status`,
  `request_id`; глобальные обработчики `ui_error` и `ui_unhandled_rejection`.
- `infra/mock-llm`: событие `mock_llm_request method= path= status= scenario= elapsed_ms=`
  без тел запросов.

#### Changed

- События переведены с фраз на snake_case; `requestId` в MDC переименован в `request_id`.
- Из логов убраны фрагменты ответа LLM и цитаты требований: остаются только причины,
  счётчики и длины.


### Ветка `feature/unified-ats-service` — единый Java-сервис

#### Breaking changes

- В `vacancy_requirement` удалена колонка `weight` и ограничение
  `ck_requirement_weight`. Для миграции применить Liquibase changeset `009-014`;
  клиенты должны перестать читать и отправлять `weight` и `critical`.
- `kind` требования теперь три значения: `MUST`, `NICE`, `OPTIONAL`. В API вместо
  весов возвращаются `kind_source`, `llm_kind` и `history`; `critical` остаётся
  в БД со значением `false`, но в API и UI не отдаётся.
- Старые версии шаблонов не пересчитываются и не редактируются: любое сохранение
  создаёт новую версию.

#### Added

- **Шаблон вакансии через LLM.** `POST /api/v1/vacancies` из текста или файла,
  промпт `vacancy-template.v2`, JSON-схема ответа, повторы, запасные модели и
  общий дедлайн OpenRouter.
- **Разбор и очистка требований.** Проверка цитаты как подстроки с нормализацией
  пробелов, отбрасывание бесполезных требований, лимит 50, дедупликация с
  приоритетом `MUST > NICE > OPTIONAL`.
- **Словарь навыков.** `skill_alias`, нормализация `JS → JavaScript`,
  `C++`, `C#`, `.NET`, `Node.js.`; создание новых навыков только при сохранении.
- **История типов HR.** `skill_kind_stat` по паре «специализация × уровень ×
  навык», подсказка типа из истории и её показ в UI.
- **API управления вакансиями.** Список, чтение, сохранение шаблона, удаление,
  справочники специализаций и уровней, создание специализации. Ответы содержат
  исходный текст вакансии `source_text`.
- **Вакансии без уровня.** Скрытый уровень `none` («Без уровня») для
  специализаций без деления на грейды; история типов изолирована от грейдов.
- **Удаление вакансий.** `DELETE /api/v1/vacancies/{id}` с вычитанием вклада
  последней подтверждённой версии из `skill_kind_stat`.
- **Frontend вакансий.** Список с поиском, фильтрами и адаптивными карточками;
  создание через компактный прогресс и drag-and-drop; редактор с тремя секциями,
  сегментированными типами, историей HR в процентах, sticky-панелью действий,
  подтверждением черновика, поиском и копированием текста вакансии.
- **UX-улучшения.** Тосты, модальные подтверждения, скелетоны, пустые состояния;
  при смене типа и добавлении требования карточка подсвечивается, страница
  прокручивается к ней, новое требование получает фокус.
- **Mock-LLM.** `infra/mock-llm` со сценариями `ok`, `fenced`,
  `rate-limit-once`, `rate-limit-always`, `invalid-json`, `always-invalid`,
  `truncated`, `slow`, `unauthorized`, `quota`, `bad-quote` и примерами вакансий.
- **CI.** Добавлена job frontend: генерация клиента docker-образом, `npm ci`, компонентные тесты и сборка без `setup-java`.
- **Документация.** Обновлены `README.md`, `AGENTS.md`,
  `docs/vacancy-data-model.md`; добавлен сводный отчёт `003-vacancy-skill-kinds.md`.
- **Seed навыков.** Changeset `014` добавляет Rust-фреймворки (`Axum`,
  `Actix Web`, `Rocket`), а также `UNIX`, `LaTeX` и `Typst` с алиасами.

#### Changed

- `vacancy_requirement.kind` расширен до трёх значений; `raw_text` используется
  как цитата, добавлены `skill_text`, `kind_source`, `llm_kind`.
- `vacancy_template_version` получила `is_confirmed`, `llm_model` и
  `dropped_requirements`; версия 1 из LLM не подтверждена, версии из `PUT`
  подтверждены.
- Уровень при создании вакансии стал необязательным; `GET /levels` по-прежнему
  возвращает только `intern/junior/middle/senior`.
- UI списка и редактора переработан; количество подтверждённых/черновиков и
  история HR показываются в процентах, прогресс создания — компактной полосой.
- При недоступной LLM вакансия создаётся с пустым шаблоном по решению RFC-002
  (вопрос 17.3); `LLM_INVALID_RESPONSE` остаётся `502`.
- **Сборка frontend.** OpenAPI-клиент генерируется в multi-stage
  `frontend/Dockerfile` образом `openapi-generator-cli:7.22.0`; JRE из образа
  убран, CI генерирует клиент через docker без `setup-java`.
- **Иконки.** `lucide-vue-next` заменён на `@lucide/vue`: старый пакет
  помечен deprecated, набор иконок тот же.
- **Маппинг API.** `ApiMapper` разбит на MapStruct-мапперы `VacancyApiMapper`,
  `RequirementApiMapper`, `ReferenceApiMapper`; запросы в команды собирает
  `CommandMapper`.
- **Сущности.** `VacancyEntity`, `TemplateVersionEntity`, `RequirementEntity`
  и `SpecializationEntity` собираются через Lombok `@Builder`.
- **Пагинация.** Native query списка вакансий заменён JPQL-фрагментом с точным
  `offset`; порядок `created_at desc` сохранён.
- **Счётчики типов.** SQL upsert вынесен в `SkillKindStatRepository`, результат
  `RETURNING` заменил предварительный `SELECT` при отрицательных дельтах.
- **Тесты и миграции.** Gradle копирует Liquibase-changelog в test resources;
  тесты читают его из classpath, а не по относительному пути.

#### Removed

- Колонка `vacancy_requirement.weight` и ограничение `ck_requirement_weight`.
- Веса, `critical` и профили весов из публичного шаблона вакансии.

#### Fixed

- `VacancyTemplateQuery` больше не читает удалённую в `009` колонку `weight`: вес требования
  выводится из `kind` (MUST=3, NICE=2, OPTIONAL=1). Из-за этого gRPC `GetTemplate` падал,
  и предварительный скоринг резюме оставался в `SCORING_FAILED`.
- `scoring-service/Dockerfile` генерирует protobuf-модули при сборке; контейнер больше не
  падает с `ModuleNotFoundError: scoring_service.v1`.
- LLM-клиент явно передаёт таймаут в `OpenAiChatOptions`: Spring AI по умолчанию ставит 60 с и
  перекрывал `read-timeout`/`total-timeout`, из-за чего реальные ответы обрывались.
- `max-tokens` поднят до 20000: reasoning-модель `dots-studio/dots-3-note-preview:free`
  расходовала 6000 токенов на reasoning и возвращала пустой `content` (`finish_reason=length`).
- `fallback-models` очищен: free-модели Google отдают `429` из общего пула, поэтому при
  ошибке основной модели возвращается `LLM_UNAVAILABLE`, без переключения.
- `compose.yaml`: `ATS_STORAGE_TYPE` можно переопределить из окружения (например, `memory`
  без MinIO); сборка `scoring-service` идёт из корня репозитория.


### Ветка `feature/specialization-it-flag`

#### Breaking changes

- `POST /api/v1/specializations` требует поле `it`: `true` для IT-специализации,
  `false` для остальных. Без него API отвечает `422 VALIDATION_ERROR`.
- `GET /api/v1/specializations` и ответ создания специализации возвращают
  `Specialization` (`code`, `name`, `it`) вместо `ReferenceItem`.
- Для IT-специализации грейд обязателен: `POST /api/v1/vacancies` без `level`
  отвечает `422 LEVEL_REQUIRED`, значение `none` не принимается.
  Для не-IT специализаций уровень игнорируется и подставляется скрытый `none`.

#### Added

- Колонка `specialization.is_it` (changeset `015-specialization-it`); засеянные
  IT-направления помечены `true`, пользовательские специализации остаются `false`.
- В форме создания вакансии селект грейда показывается только для IT-специализаций,
  в модалке создания специализации появился чекбокс «IT-специализация».
- Сквозное логирование `vacancy-service`: идентификатор запроса в MDC (`[req:...]`),
  параметры создания вакансии и экстракта файла, каждая попытка LLM с моделью, форматом,
  длительностью, токенами и `finish_reason`, причина отклонённого ответа с коротким
  фрагментом, итог сохранения. PII и полные тексты в логи не пишутся.

#### Fixed

- Оборванный на середине ответ LLM (`OpenAIInvalidDataException`) больше не превращается
  в `500 INTERNAL_ERROR`: все необработанные ошибки SDK считаются временными, цепочка
  переходит к следующей модели, а после её исчерпания бросается `LlmUnavailableException`
  и вакансия создаётся с пустым шаблоном (RFC-002, вопрос 17.3).

#### Changed

- Таймауты LLM увеличены по всей цепочке: `read-timeout` 180 с, `total-timeout` 300 с,
  `proxy_read_timeout` nginx 300 с, таймаут axios 310 с.

### Ветка `feature/vacancy-skill-kinds` — RFC-002
Первый набор изменений проекта: документация и архитектура, каркас Java-сервисов,
схема данных и извлечение текста из документов.

### Added

- **Документация.** README с описанием идеи, двухфазного скоринга, модели
  `q`/`E`/`D`/`c_r`/`M`/`N`/`F`, весов, контрактов и границ MVP; `AGENTS.md`
  с инвариантами и правилами работы; `scoring_model.pdf`.
- **Каркас Java-сервисов.** Отдельные `resume-service` (порт 8081) и
  `vacancy-service` (порт 8080) на Java 21, Spring Boot 4 и Gradle. Подключены
  зависимости для REST, JPA, Security, Validation, OpenAPI UI, JWT и MapStruct;
  добавлены Testcontainers и Dockerfile. В `resume-service` добавлен Spring AI PDF,
  в `vacancy-service` — gRPC-клиент и библиотека `text-extraction`.
- **Извлечение текста.** Библиотека `text-extraction` для PDF (PDFBox 3),
  DOCX (Apache POI), TXT и MD: определение формата по содержимому, отказ от
  старого `.doc` (OLE2), проверка несовпадения расширения и содержимого,
  защита от zip-бомб, лимиты размера/страниц/времени, нормализация текста
  и доменные коды ошибок.
- **Кодировки.** TXT и MD читаются как строгий UTF-8 с fallback на windows-1251
  и предупреждением; BOM обрабатывается.
- **Dev-эндпоинт.** `POST /api/v1/dev/extraction` в `vacancy-service` для ручной
  проверки извлечения. По умолчанию выключен, включается флагом
  `ats.dev.extraction-endpoint.enabled`; временный, будет удалён после подключения
  библиотеки к производственным эндпоинтам.
- **Схема данных.** Liquibase-миграции для `scoring_weights`, `specialization`,
  `level`, `skill`, `vacancy`, `vacancy_template_version` и
  `vacancy_requirement`: ограничения целостности, триггеры неизменяемости версий
  шаблона и стартовые веса/уровни (0.70/0.15/0.15, λ от 1.0 до 0.5).
  Схема описана в `docs/vacancy-data-model.md`.
- **Локальный запуск.** `compose.yaml` с PostgreSQL 18, отдельной джобой
  `migrations` и двумя Java-сервисами; миграции накатываются до старта приложений.
- **Контракты и заготовки.** Публичный REST-контракт вынесен в
  `contracts/openapi.yaml`; зарезервирован каталог `scoring-service` под будущий
  Python-сервис.
- **Инструменты агента.** Скиллы Conventional Commits, Conventional Branch,
  code review, frontend и work-report; отчёты и RFC по извлечению текста в `docs/`.
- **CI.** GitHub Actions workflow запускает тесты и `spotlessCheck` для
  `text-extraction`, `resume-service` и `vacancy-service`, накатывает
  Liquibase-миграции на PostgreSQL через `compose.yaml` и проверяет заголовок
  PR на соответствие Conventional Commits.
- **Пайплайн резюме.** `resume-service`: загрузка пачки PDF/DOCX/TXT/MD,
  извлечение текста, маскировка PII, очистка prompt injection, предварительный
  скор, шорт-лист, GitHub только для отобранных, итоговый скор, рейтинг и отчёт
  «требование → доказательство → пробел». Схема — в `docs/resume-pipeline.md`.
- **PII-маскировка.** Regex + `libphonenumber` + локальная ONNX NER
  (`yqelz/xml-roberta-large-ner-russian`, int8, MIT) без словарей имён и вузов;
  ORG от NER маскируется только в блоке образования, компании в опыте остаются.
  PII шифруется AES-256-GCM в `pii_vault`, в `parsed_resume` остаётся только
  обезличенный текст.
- **Prompt injection.** Детерминированный санитайзер на 20–30 правил,
  замена `[REMOVED_INSTRUCTION]`, флаги без исходного текста.
- **Скоринг MVP.** Порт `ScoringClient` и временный детерминированный
  `LocalScoringClient` с `ScoreCalculator`: `q`, `E`, `D`, `c_r`, `M`, `N`, `F`,
  cap и вилка. Заменяется gRPC-адаптером `scoring-service` (ADR 0002).
- **gRPC vacancy-service.** `contracts/vacancy-internal.proto`: `GetTemplate` и
  один `DecideScreening` на пачку; политика порога, top-N и borderline — в
  `vacancy-service`.
- **GitHub-обогащение.** Только шорт-лист с согласием, `q_gh` по конкретному
  навыку, антинакрутка флагом и множителем 0.5; отсутствие GitHub не штрафует.
- **Хранение и аудит.** MinIO для оригиналов, неизменяемые `score_run` с версиями
  модели/промптов/формулы/весов, `external_evidence`, `audit_event` для reveal.
  Версия весов приходит из `vacancy-service` (`scoring_weights.version`), а
  нормализованные факты образования передаются в скоринг отдельно от текста и
  пока не участвуют в формуле.
- **Документация и модель.** ADR 0001–0005, `docs/resume-pipeline.md`,
  `scripts/download-ner.sh`; `models/` и `.ner-venv/` в `.gitignore`.

### Changed

- **Структура репозитория.** Единый модуль `backend/` разделён на
  `resume-service/` и `vacancy-service/`; общая библиотека `text-extraction`
  подключена к `vacancy-service` через Gradle composite build. README и `AGENTS.md`
  обновлены под новую структуру и название `scoring-service`.
- **Сборка и запуск.** Каждый Java-сервис собирается отдельным Gradle-проектом;
  `docker-compose.yml` заменён на `compose.yaml`. S3-совместимое хранилище
  переведено с удалённых community-образов MinIO на форк `pgsty/minio` и клиент
  `pgsty/mc` с закреплёнными версиями.
- **Docker.** Сборка `resume-service` и `vacancy-service` идёт из корня репозитория:
  в образ попадают `text-extraction`, `contracts` и каталог сервиса; добавлен
  общий `.dockerignore`.
- **Контракты.** `contracts/openapi.yaml` дополнен загрузкой резюме, скринингом,
  рейтингом, отчётом и reveal; добавлен `contracts/vacancy-internal.proto`.

### Security

- **Проверка загрузок.** Тип файла определяется по сигнатуре, а не по имени или
  `Content-Type`; `.doc` отклоняется, несовпадение расширения и содержимого для
  PDF/DOCX считается ошибкой.
- **Ограничения ресурсов.** Лимиты 10 МБ, 50 страниц PDF, 200 000 символов и
  10 секунд на извлечение; для DOCX ограничены размер записи и коэффициент сжатия.
- **Логи и доступ.** Тексты документов и имена файлов не логируются;
  dev-эндпоинт по умолчанию отключён и при включении открывает без авторизации
  только `/api/v1/dev/**`.
- **PII до LLM.** Маскировка выполняется до записи `masked_text` и до любого
  вызова скоринга/LLM; в `parsed_resume` и отчётах PII нет. Оригиналы доступны
  только через `reveal` с обязательной причиной и записью в аудит.
- **NER fail-closed.** В production при недоступной ONNX-модели приложение не
  стартует, а `NerPiiDetector` при `fail-closed=true` не пропускает текст дальше.
- **GitHub.** Запросы только для шорт-листа и только при согласии кандидата;
  логин не логируется, сырые ответы API не сохраняются.
- **Prompt injection.** Инструкции удаляются детерминированно до скоринга;
  исходные фрагменты не сохраняются, только идентификатор правила, строка и хеш.

