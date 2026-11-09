# Отчёт 003 — шаблон вакансии: MUST / NICE / OPTIONAL

| | |
|---|---|
| **Дата** | 2026-11-10 |
| **Основание** | `docs/rfc/RFC-002_skill-kinds-vacancy-template.md` |
| **Контур** | `vacancy-service`, `frontend`, `migrations`, `contracts`, `infra/mock-llm` |
| **Статус** | MVP реализован, реальная LLM не вызывалась |

## 1. Что сделано

### 1.1. Миграции

- `009-requirement-skills-and-kinds.sql`: удалена колонка `weight`, добавлены
  `skill_text`, `kind_source`, `llm_kind`; у версии появились `is_confirmed`,
  `llm_model`, `dropped_requirements`; обновлено ограничение субъекта требования.
- `010-skill-alias-and-seed.sql`: таблица `skill_alias`, бэкфилл существующих
  навыков и seed ~80 IT-навыков с синонимами (`js`→`javascript`, `k8s`→`kubernetes`,
  `питон`→`python` и т. п.).
- `011-skill-kind-stat.sql`: таблица счётчиков `MUST`/`NICE`/`OPTIONAL` по паре
  «специализация × уровень × навык».
- `012-seed-specializations.sql`: `backend`, `frontend`, `fullstack`, `mobile`,
  `data`, `devops`, `qa`.
- Старые changeset не редактировались; все новые include добавлены в
  `migrations/db.changelog-master.xml`.

### 1.2. Backend

- Модуль `ats.vacancy.llm`: интерфейс `LlmClient`, Spring AI-клиент OpenRouter,
  повторы, общий дедлайн, деградация `response_format` (schema → json_object → без
  формата), запасные модели и исключения `LLM_*`.
- Промпт `vacancy-template.v2` и JSON-схема ответа; текст вакансии передаётся в
  блоке `<vacancy_text>`, закрывающий тег экранируется без учёта регистра и
  пробелов.
- Разбор ответа: `strip`, снятие markdown-ограждения, строгая проверка
  `schema_version` и `kind`; невалидный ответ повторяется один раз.
- Детерминированная очистка: цитата как подстрока с нормализацией пробелов,
  отбрасывание бесполезных требований, лимит 50, дедупликация с приоритетом
  `MUST > NICE > OPTIONAL`.
- `SkillNormalizer` + `skill_alias`: NFKC, нижний регистр, схлопывание пробелов,
  сохранение `+`, `#`, `.NET`, снятие только завершающей точки.
- Подсказка из истории `skill_kind_stat` и атомарное обновление счётчиков при
  `PUT`; версия 1 из LLM не учитывается, повторное сохранение не меняет счётчики.
- Эндпоинты: `GET /specializations`, `GET /levels`, `GET /vacancies`,
  `POST /vacancies`, `GET /vacancies/{id}`, `PUT /vacancies/{id}/template`.
- Ошибки в формате `ApiStatusResponse`: HTTP-статус в `code`, машинный код в
  `status`.
- Публичный REST-контракт обновлён; Spring-интерфейсы и DTO генерируются из
  `contracts/openapi.yaml`.

### 1.2.1. Управление вакансиями после ревью

- `POST /api/v1/specializations` — добавление специализации; код необязателен и
  генерируется транслитерацией из названия.
- Уровень при создании вакансии необязателен. Для специализаций без грейдов
  используется скрытая запись `level.code = 'none'` («Без уровня»); история
  типов для неё изолирована от `junior/middle/senior`.
- `DELETE /api/v1/vacancies/{id}` — удаление вакансии; вклад последней
  подтверждённой версии вычитается из `skill_kind_stat`.
- В UI добавлены «Подтвердить черновик», удаление вакансии, форма добавления
  специализации и вариант «Без уровня».

### 1.3. Frontend

Каталога `frontend/` в репозитории не было. Добавлен MVP-интерфейс на
Vue 3 + TypeScript + Vite + Tailwind: список вакансий с поиском, фильтрами и
карточками для мобильных, создание через stepper с drag-and-drop файла,
редактор шаблона с тремя секциями, сегментированным переключателем типа,
бейджами `MATCHED`/`NEW` и источником `LLM`/`История`/`HR`, подсказкой истории,
дубликатами, обработкой `409`/`422`, защитой от ухода с несохранёнными
изменениями, тостами и модальными подтверждениями. Текст вакансии показан в
sticky-панели с поиском; на мобильных — сверху. Клиент генерируется из OpenAPI
(`npm run gen-api`, каталог `src/api/generated` не коммитится).

### 1.4. Инфраструктура и документация

- `infra/mock-llm`: детерминированный OpenAI-совместимый сервер и сценарии
  `ok`, `fenced`, `rate-limit-once`, `rate-limit-always`, `invalid-json`,
  `always-invalid`, `truncated`, `slow`, `unauthorized`, `quota`, `bad-quote`;
  синтетические примеры в `samples/`.
- Профиль `mock-llm`, `.env.example`, `.env` в `.gitignore`.
- `compose.yaml`: сервисы `mock-llm` и `frontend`; контекст сборки
  `vacancy-service` исправлен на корень репозитория из-за composite build
  `text-extraction`.
- Обновлены `README.md`, `AGENTS.md`, `docs/vacancy-data-model.md`, `CHANGELOG.md`.
- CI: добавлена job `frontend` (npm ci, тесты, сборка).

## 2. Что проверено

- `vacancy-service`: `./gradlew test spotlessCheck` — 44 теста зелёные, включая
  Testcontainers/PostgreSQL, разбор ответа, очистку, нормализацию, подсказку
  истории, счётчики, `PUT`/`GET`/`POST`, `409`, дубликаты и мок-клиент LLM.
- `text-extraction`: `gradle test spotlessCheck` — зелёные.
- `frontend`: `npm test` — 12 компонентных тестов; `npm run build` — `vue-tsc` и
  Vite-сборка проходят.
- Liquibase: все 13 changeset накатаны на PostgreSQL 18 через `compose`.
- `docker compose config --quiet` — успешно.
- Полный сценарий на профиле `mock-llm`: создание вакансии, сохранение правок,
  повторное сохранение без изменения счётчиков, создание второй вакансии и
  появление подсказки `История`; проверены также `fenced`, `bad-quote`,
  `invalid-json` и `rate-limit-once`.
- Frontend-образ собран; через nginx проверены SPA (`/vacancies`) и прокси
  `/api/v1/levels`.
- Реальная LLM **не вызывалась**, ключ нигде не записан.

## 3. Расхождения и принятые решения

1. **RFC §17.3 против §5.4.** В §5.4 по умолчанию LLM-ошибка возвращает `503` и
   вакансия не создаётся, а ответ на вопрос 17.3 требует создавать вакансию с
   пустым шаблоном. Реализован ответ 17.3: при `LLM_NOT_CONFIGURED`,
   `LLM_UNAVAILABLE`, `LLM_AUTH`/`LLM_QUOTA` создаётся `DRAFT` с пустым
   шаблоном; `LLM_INVALID_RESPONSE`/`LLM_TRUNCATED` остаются `502`.
2. **Frontend.** Готового фронтенда не было, поэтому UI сделан минимальным, без
   shadcn-vue и без полного дизайн-системного слоя. Это MVP для проверки сценария,
   а не финальный UI.
3. **Spring AI.** В версии 2.0.1 OpenAI-клиент построен на официальном
   `openai-java`; для работы нужен явный `openai-java-client-okhttp`. Подключён
   `spring-ai-openai` напрямую, а не starter: автоконфигурация starter требует
   ключ уже на старте и ломала запуск без ключа. `finish_reason`, `model` и
   `response_format` доступны, поэтому `RestClient` не понадобился.
4. **`WEIGHT_OBSERVATION`.** В реальной схеме такой таблицы не было; история
   реализована в `skill_kind_stat`, как требует RFC.
5. **`ApiStatusResponse`.** Отдельного формата ошибок в контракте не было;
   использован существующий: `code` — HTTP-статус, `status` — машинный код.
6. **Миграция алиасов.** Для уже существующих `skill.normalized_name` алиас
   создаётся из текущего значения; если формат отличается от кода, `SkillCatalog`
   дополнительно находит навык по `normalized_name`, чтобы не создать дубль.
7. **Пороги истории** вынесены в `application.yaml`
   (`vacancy.template.kind-history`); в профиле `mock-llm` стоят
   `min-observations: 1` для демонстрации подсказки.
8. **`critical`** остаётся в БД со значением `false`, в API и UI не отдаётся.
9. **`OPTIONAL` в скоринге** не реализован: это зона `scoring-service` и
   ML-инженера.

## 4. Что осталось человеку

- Проверить качество реальной модели на синтетических и публичных вакансиях:
  особенно различие `NICE`/`OPTIONAL`, `clarity_warning` и `dropped_requirements`.
- Подобрать `min-observations` и `min-share` на реальных сохранениях HR.
- Решить судьбу `OPTIONAL` в формуле скоринга.
- Проверить деградацию `response_format` и переключение запасных моделей на
  живом OpenRouter.
- Довести frontend до дизайн-системы проекта, если это требуется к демо.
