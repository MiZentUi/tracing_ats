# Changelog

В этом файле собраны все значимые изменения проекта.

Формат основан на [Keep a Changelog](https://keepachangelog.com/),
версии соответствуют [семантическому версионированию](https://semver.org/lang/ru/).

## [Unreleased]

### Ветка `feature/unified-ats-service` — единый Java-сервис

#### Breaking changes

- `vacancy-service` и `resume-service` объединены в один проект `ats-service`
  (пакеты `ats.vacancy` и `ats.resume`); отдельные каталоги и compose-сервисы удалены.
  REST обоих контуров доступен на `:8080`, внутренний gRPC — на `:9090`.
- В compose сервис называется `ats-service`; переменные `VACANCY_SERVICE_PORT`,
  `VACANCY_GRPC_PORT`, `VACANCY_PROFILES`, `VACANCY_SERVICE_URL` заменены на
  `ATS_SERVICE_PORT`, `ATS_GRPC_PORT`, `ATS_PROFILES`, `ATS_SERVICE_URL`.
- Исправлен публичный контракт после слияния main: восстановлены
  `GET/DELETE /vacancies/{id}` и `PUT /vacancies/{id}/template`, тело и ответы
  `POST /vacancies`, разделены path-параметры `{id}` и `{vacancyId}`, восстановлены
  схемы `RankingItem`, `ApplicationReport`, `RequirementReportItem`
  и тела `/resumes`, `/vacancies/{vacancyId}/screen`.

#### Changed

- Единый `build.gradle`: OpenAPI-генерация снова включена (`ats.api`,
  `ats.gateway.model`), proto-контракты scoring и vacancy-internal собираются в одном
  модуле, объединены зависимости (LLM, PII/NER, MinIO, gRPC server/client, actuator).
- `db.changelog-master.xml` собран в рабочий порядок (`001`, `009–015`, `002–004`);
  демо-вакансия из `003` переписана под актуальную схему (`kind`, без `weight`,
  навыки берутся из справочника).
- В CI одна Java-джоба `ats-service` вместо двух; frontend ходит в `ats-service`.

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

