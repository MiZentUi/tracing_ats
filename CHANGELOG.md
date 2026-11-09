# Changelog

В этом файле собраны все значимые изменения проекта.

Формат основан на [Keep a Changelog](https://keepachangelog.com/),
версии соответствуют [семантическому версионированию](https://semver.org/lang/ru/).

## [Unreleased]

Первый набор изменений проекта: документация и архитектура, каркас Java-сервисов,
схема данных и извлечение текста из документов.

### Added

- **Шаблон вакансии (RFC-002).** `POST /api/v1/vacancies` из текста или файла,
  LLM-разметка требований `MUST`/`NICE`/`OPTIONAL`, словарь синонимов
  `skill_alias`, память типов `skill_kind_stat`, версии шаблона и сохранение
  правок HR через `PUT /api/v1/vacancies/{id}/template`. Добавлены справочники
  специализаций и уровней, список вакансий, профиль `mock-llm`, детерминированная
  заглушка `infra/mock-llm`, промпт `vacancy-template.v2` и контракт в
  `contracts/openapi.yaml`.
- **UI вакансий.** Обновлён интерфейс списка, создания и редактора шаблона:
  поиск и фильтры, карточки и таблица, stepper создания, drag-and-drop файла,
  сегментированные типы навыков, sticky-панель действий, история HR в карточке,
  поиск по тексту вакансии, тосты и модальные подтверждения.
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
  `text-extraction`, `resume-service` и `vacancy-service`, тесты, `ruff` и
  проверку актуальности сгенерированных gRPC-модулей для `scoring-service`,
  накатывает Liquibase-миграции на PostgreSQL через `compose.yaml` и проверяет
  заголовок PR на соответствие Conventional Commits.

### Changed

- **Тип требования вместо веса.** В `vacancy_requirement` удалена колонка
  `weight`, добавлены `skill_text`, `kind_source` и `llm_kind`; версия шаблона
  получила `is_confirmed`, `llm_model` и `dropped_requirements`. Веса, `critical`
  и профили весов в API шаблона не входят: `scoring-service` сопоставит `kind`
  со своими весами.
- **Структура репозитория.** Единый модуль `backend/` разделён на
  `resume-service/` и `vacancy-service/`; общая библиотека `text-extraction`
  подключена к `vacancy-service` через Gradle composite build. README и `AGENTS.md`
  обновлены под новую структуру и название `scoring-service`.
- **Сборка и запуск.** Каждый Java-сервис собирается отдельным Gradle-проектом;
  `docker-compose.yml` заменён на `compose.yaml`.

### Security

- **Проверка загрузок.** Тип файла определяется по сигнатуре, а не по имени или
  `Content-Type`; `.doc` отклоняется, несовпадение расширения и содержимого для
  PDF/DOCX считается ошибкой.
- **Ограничения ресурсов.** Лимиты 10 МБ, 50 страниц PDF, 200 000 символов и
  10 секунд на извлечение; для DOCX ограничены размер записи и коэффициент сжатия.
- **Ключ LLM.** Читается только из `OPENROUTER_API_KEY`/`.env`, не логируется и
  не отдаётся в `toString()`. Профиль `mock-llm` принудительно использует
  `test-key` и локальный хост заглушки, иначе приложение не стартует.
- **Логи и доступ.** Тексты документов и имена файлов не логируются;
  dev-эндпоинт по умолчанию отключён и при включении открывает без авторизации
  только `/api/v1/dev/**`.

