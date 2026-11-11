# Changelog

В этом файле собраны все значимые изменения проекта.

Формат основан на [Keep a Changelog](https://keepachangelog.com/),
версии соответствуют [семантическому версионированию](https://semver.org/lang/ru/).

## [Unreleased]

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

### Ветка `feature/vacancy-skill-kinds` — RFC-002

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

- `vacancy-service/Dockerfile` и `compose.yaml` — сборка с composite build
  `text-extraction` из корневого контекста, передача `OPENROUTER_API_KEY`.
- `PUT` с полями `weight` и `critical` игнорирует их и не сохраняет.
- Смена типа навыка и добавление требования больше не теряют карточку:
  используется прокрутка, подсветка и фокус.

#### Security

- Ключ OpenRouter читается только из `OPENROUTER_API_KEY`/`.env`, не логируется
  и не попадает в `toString()`.
- Профиль `mock-llm` принудительно использует `test-key` и локальный хост
  заглушки; при внешнем `base-url` приложение не стартует.
- Текст вакансии передаётся в блоке `<vacancy_text>`, закрывающий тег
  экранируется без учёта регистра и пробелов.
- Требование без цитаты-подстроки отбрасывается, текст цитаты не логируется и
  не раздувает ответ.

### Базовая линия проекта

#### Added

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
  `ats.dev.extraction-endpoint.enabled`.
- **Схема данных.** Liquibase-миграции для `scoring_weights`, `specialization`,
  `level`, `skill`, `vacancy`, `vacancy_template_version` и
  `vacancy_requirement`: ограничения целостности, триггеры неизменяемости версий
  шаблона и стартовые веса/уровни (0.70/0.15/0.15, λ от 1.0 до 0.5).
- **Локальный запуск.** `compose.yaml` с PostgreSQL 18, отдельной джобой
  `migrations` и Java-сервисами; миграции накатываются до старта приложений.
- **Контракты и заготовки.** Публичный REST-контракт вынесен в
  `contracts/openapi.yaml`; зарезервирован каталог `scoring-service`.
- **Инструменты агента.** Скиллы Conventional Commits, Conventional Branch,
  code review, frontend и work-report.
- **CI.** GitHub Actions workflow запускает тесты и `spotlessCheck` для
  `text-extraction`, `resume-service` и `vacancy-service`, тесты, `ruff` и
  проверку актуальности сгенерированных gRPC-модулей для `scoring-service`,
  накатывает Liquibase-миграции на PostgreSQL через `compose.yaml` и проверяет
  заголовок PR на соответствие Conventional Commits.

#### Changed

- **Структура репозитория.** Единый модуль `backend/` разделён на
  `resume-service/` и `vacancy-service/`; общая библиотека `text-extraction`
  подключена к `vacancy-service` через Gradle composite build.
- **Сборка и запуск.** Каждый Java-сервис собирается отдельным Gradle-проектом;
  `docker-compose.yml` заменён на `compose.yaml`.

#### Security

- **Проверка загрузок.** Тип файла определяется по сигнатуре, а не по имени или
  `Content-Type`; `.doc` отклоняется, несовпадение расширения и содержимого для
  PDF/DOCX считается ошибкой.
- **Ограничения ресурсов.** Лимиты 10 МБ, 50 страниц PDF, 200 000 символов и
  10 секунд на извлечение; для DOCX ограничены размер записи и коэффициент сжатия.
- **Логи и доступ.** Тексты документов и имена файлов не логируются;
  dev-эндпоинт по умолчанию отключён и при включении открывает без авторизации
  только `/api/v1/dev/**`.
