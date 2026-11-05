# Отчёт: backend, миграции и модуль извлечения текста

| | |
|---|---|
| **Дата** | 2026-11-05 |
| **Ветка** | `feature/shared-text-extraction` |
| **Контур** | Java, `resume-service/`, `vacancy-service/`, `text-extraction/` |
| **Статус** | Реализовано |

## 1. Кратко

Сервисы вынесены в корень репозитория и собираются отдельными Gradle-проектами. Добавлены
Liquibase-миграции с отдельной джобой наката, описана модель данных вакансий и реализован
общий модуль извлечения текста из PDF, DOCX, TXT и MD. Для ручной проверки добавлен
временный dev-эндпоинт, который по умолчанию выключен.

## 2. Что добавлено

### 2.1. Структура проекта

```text
tracing_ats/
├── docs/
├── migrations/
├── docker-compose.yml
├── resume-service/
├── vacancy-service/
├── text-extraction/
└── scoring-service/
```

- `resume-service` и `vacancy-service` — отдельные Spring Boot-приложения на Gradle.
- `text-extraction` — обычная Java-библиотека, не сервис и не приложение.
- `text-extraction` подключается к `vacancy-service` через Gradle composite build.

### 2.2. Миграции и модель данных

- `migrations/db.changelog-master.xml` и `changes/001-vacancy-schema.sql`.
- Схема: `scoring_weights`, `specialization`, `level`, `skill`, `vacancy`,
  `vacancy_template_version`, `vacancy_requirement`.
- Ограничения целостности, внешние ключи, уникальные индексы и триггеры неизменяемости
  версии шаблона.
- `docs/vacancy-data-model.md` — ER-диаграмма и описание ограничений.

### 2.3. Накат схемы отдельной джобой

- `migrations/Dockerfile` на базе `liquibase/liquibase:4.29`.
- `docker-compose.yml`: PostgreSQL и одноразовая джоба `migrations`.
- Сервисы не запускают Liquibase и не меняют схему при старте.

### 2.4. Модуль `text-extraction`

Реализованы:

- определение формата по содержимому: PDF, DOCX, TXT, MD;
- отказ для старого `.doc` (OLE2) с подсказкой сохранить как DOCX;
- проверка ZIP-каталога DOCX через Commons Compress;
- проверка расхождения расширения `.pdf`/`.docx` с реальным содержимым;
- PDFBox 3: текст, число страниц, лимит страниц, пароль, повреждённый файл;
- POI XWPF: абзацы и таблицы, защита от zip-бомб;
- TXT/MD: BOM, строгий UTF-8, fallback windows-1251 с предупреждением;
- нормализация текста;
- лимиты размера, страниц, длины текста и времени;
- пул потоков и таймаут извлечения;
- доменные ошибки с кодами и будущими HTTP-статусами;
- конфигурация `ats.ingestion.extraction.*` с валидацией;
- логи без текста и имени файла.

### 2.5. Временный dev-эндпоинт

`POST /api/v1/dev/extraction` в `vacancy-service`:

- принимает `multipart/form-data` с полем `file`;
- возвращает `type`, `pageCount`, `warnings`, `charCount`, `text`;
- выключен по умолчанию;
- включается флагом:

```yaml
ats:
  dev:
    extraction-endpoint:
      enabled: true
```

- при включённом флаге открывает без авторизации только `/api/v1/dev/**`;
- при выключенном флаге контроллер и dev-security не создаются.

### 2.6. Скиллы агента

- `conventional-commits` — правила сообщений коммитов;
- `code-review` — быстрое ревью с приоритетом инвариантов проекта;
- `conventional-branch` — правила именования веток.

## 3. Технические решения

| Решение | Почему так |
|---|---|
| Maven multi-module | Два сервиса и общая библиотека собираются одной командой, версии и зависимости централизованы |
| `text-extraction` — библиотека, не сервис | Нет сетевого хопа, лишнего контейнера, контракта и точки отказа; RFC прямо исключает HTTP-контроллер |
| Обычная библиотека, не Spring Boot starter | Для двух внутренних сервисов достаточно явного `@Import`; starter добавил бы автоконфигурацию и лишнюю магию |
| `RawExtraction` вместо `String` | Позволяет вернуть `pageCount` и предупреждение о кодировке, не привязывая сервис к конкретному парсеру |
| Определение типа по содержимому | Имя файла и `Content-Type` подделываются; это требование AGENTS.md |
| Расширение только для MD и проверки расхождений | `.md` и `.txt` обрабатываются одинаково, но формат нужно различать; `.pdf`/`.docx` с чужим содержимым отклоняются |
| PDFBox 3 + POI 5 | В README уже выбраны PDFBox и POI; Tika тяжелее и не нужен |
| Commons Compress для ZIP-каталога | `ZipInputStream` распаковывает предыдущую запись при переходе к следующей; центральный каталог безопаснее |
| TXT: UTF-8 → windows-1251 | Русские тексты часто приходят в CP1251; fallback возвращает предупреждение, а не молча портит текст |
| Отклонять слишком длинный текст | Тихая обрезка описания вакансии даёт неполный шаблон навыков |
| Лимиты и пул потоков | Защита от больших файлов, зависших парсеров и исчерпания ресурсов |
| Dev-эндпоинт по флагу | Позволяет проверить библиотеку руками, не открывая endpoint в production |
| Liquibase отдельной джобой | Схема не зависит от старта приложений и не меняется ими |

## 4. Проверка

### 4.1. Тесты

```bash
(cd text-extraction && gradle test)
(cd resume-service && ./gradlew test)
(cd vacancy-service && ./gradlew test)
```

Результат:

- `text-extraction` — 45 тестов;
- `vacancy-service` — 3 теста, включая проверку флага dev-эндпоинта;
- `resume-service` — 1 тест;
- всего 49 тестов, все зелёные.

### 4.2. Миграции

```bash
POSTGRES_PORT=55432 docker compose -p ats-review up --build --abort-on-container-exit --exit-code-from migrations migrations
```

Применены все 8 changesets, джоба завершилась с кодом `0`. Откат всех changesets также
проверен.

### 4.3. Ручная проверка PDF

```bash
curl -sS -X POST http://localhost:8080/api/v1/dev/extraction \
  -F 'file=@temp/docs-test/test.pdf;type=application/pdf'
```

Результат: `HTTP 200`, `type=PDF`, `pageCount=14`, `warnings=[]`,
`charCount=13913`. Без флага endpoint возвращает `404`.

## 5. Ограничения и технический долг

1. **Нет production-контроллера.** `POST /vacancies` и `POST /resumes` пока не используют
   библиотеку; это следующий шаг после RFC.
2. **Dev-эндпоинт временный.** Его нужно удалить или заменить нормальным контрактом.
3. **Нет PDF-теста с кириллицей.** Для него нужен встроенный TTF-шрифт; сейчас PDF-тесты
   на ASCII.
4. **`text-extraction` подключается через composite build.** При публикации библиотеки в Maven
   понадобится отдельная версия и репозиторий; сейчас это локальный includeBuild.
5. **Префикс конфигурации `ats.ingestion.extraction`** не совпадает с пакетом
   `ats.extraction`. Оставлен по RFC; при желании можно переименовать отдельным изменением.
6. **Нет OCR.** Сканы без текстового слоя дают `TEXT_EMPTY` — это осознанное ограничение MVP.

## 6. Следующие шаги

1. Подключить `text-extraction` к `vacancy-service` в `POST /vacancies` и к
   `resume-service` в `POST /resumes`.
2. Добавить контракт загрузки в `contracts/openapi.yaml`.
3. Удалить dev-эндпоинт после проверки.
4. Добавить PDF-фикстуру с кириллицей.
5. Вынести общие зависимости из родительского POM, чтобы библиотека не тянула web/JPA/security.
6. Добавить contract-тест Java ↔ `scoring-service` на обезличенный текст.

## 7. Связанные коммиты

Уже закоммичено:

- `1946810` — сервисы, `docs` и `migrations` в корне, возврат Gradle;
- `1642a95` — библиотека `text-extraction` и dev-эндпоинт;
- `64d8e57` — схема данных и джоба Liquibase;
- `eed061a` — модель данных вакансий;
- `03ab647` — скиллы Conventional Commits и code review;
- `0015f45` — переименование Python-сервиса в документации;
- `2cb78e1` — скилл Conventional Branch.

Исторический Maven-коммит `d0f23c5` заменён структурой на Gradle из `1946810`.
