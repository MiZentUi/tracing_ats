# Отчёт 003 — шаблон вакансии: MUST / NICE / OPTIONAL

| | |
|---|---|
| **Дата** | 2026-11-10 |
| **Основание** | `docs/rfc/RFC-002_skill-kinds-vacancy-template.md` |
| **Ветка** | `feature/vacancy-skill-kinds` от `main` (`d5155a5`) |
| **Контур** | `vacancy-service`, `frontend`, `migrations`, `contracts`, `infra/mock-llm` |
| **Статус** | MVP реализован; реальная LLM проверена вручную, качество требует доработки промпта |
| **Правки по ревью** | 2026-11-11, 8 коммитов |

## 1. Что сделано

### 1.1. Контракт и БД

- `contracts/openapi.yaml` — справочники, создание специализации, список/создание/чтение/удаление вакансий, сохранение шаблона, `source_text`, необязательный уровень и коды ошибок.
- `009-requirement-skills-and-kinds.sql` — удалён `weight`, добавлены `skill_text`, `kind_source`, `llm_kind`, `is_confirmed`, `llm_model`, `dropped_requirements`.
- `010-skill-alias-and-seed.sql` — таблица `skill_alias`, бэкфилл существующих навыков и seed ~80 IT-навыков с синонимами.
- `011-skill-kind-stat.sql` — счётчики `MUST`/`NICE`/`OPTIONAL` по паре «специализация × уровень × навык».
- `012-seed-specializations.sql` — стартовые IT-специализации.
- `013-optional-level.sql` — скрытый уровень `none` («Без уровня»).
- `014-seed-more-skills.sql` — пропущенные навыки: Rust-фреймворки `Axum`, `Actix Web`, `Rocket`, а также `UNIX`, `LaTeX`, `Typst` с алиасами.
- Новые changeset `009–014` добавлены в `db.changelog-master.xml`; старые не редактировались. Всего в проекте 15 changeset.

### 1.2. vacancy-service

- LLM-модуль на Spring AI/OpenRouter: повторы, запасные модели, общий дедлайн, деградация `response_format`, ошибки `LLM_*`.
- `prompts/vacancy-template.v2.txt` и JSON-схема ответа; текст вакансии передаётся как данные, закрывающий тег экранируется.
- `strip`, снятие markdown-ограждения, строгая проверка схемы, очистка цитат, дедупликация `MUST > NICE > OPTIONAL`, лимит 50 требований.
- `SkillNormalizer` и `SkillCatalog` — точная нормализация по словарю без LLM.
- `KindHistoryService` и `SkillKindStatWriter` — подсказка типа из истории и атомарное обновление счётчиков через `SkillKindStatRepository` (`INSERT ... ON CONFLICT ... RETURNING`).
- Маппинг API разбит на MapStruct-мапперы `VacancyApiMapper`, `RequirementApiMapper`, `ReferenceApiMapper`; `CommandMapper` собирает доменные команды из запроса.
- Сущности `VacancyEntity`, `TemplateVersionEntity`, `RequirementEntity`, `SpecializationEntity` собираются через Lombok `@Builder`.
- Список вакансий читается JPQL-фрагментом `VacancyPageRepositoryImpl` с точным `offset`, без native query.
- Эндпоинты: `GET/POST /specializations`, `GET /levels`, `GET/POST /vacancies`, `GET/DELETE /vacancies/{id}`, `PUT /vacancies/{id}/template`.
- Создание специализации: код необязателен и генерируется транслитерацией из названия.
- Удаление вакансии вычитает вклад последней подтверждённой версии из `skill_kind_stat`.
- Уровень необязателен: для специализаций без грейдов используется скрытый `none`; история типов изолирована от `junior/middle/senior`.
- Ошибки отдаются в `ApiStatusResponse`: HTTP-статус в `code`, машинный код в `status`.

### 1.3. frontend

- Добавлен Vue 3 + TypeScript + Vite + Tailwind интерфейс: список, создание, редактор шаблона.
- Список: поиск, фильтры, проценты подтверждённых/черновиков, таблица на десктопе и карточки на мобильных, удаление.
- Создание: компактный прогресс, stepper-логика, drag-and-drop файла, добавление специализации в модальном окне, «Без уровня».
- Редактор: параметры шаблона, три секции требований, сегментированные типы, история HR в процентах, sticky-панель действий, подтверждение черновика, поиск и копирование текста вакансии.
- При смене типа и добавлении требования карточка подсвечивается, страница прокручивается к ней, новый навык получает фокус.
- Добавлены тосты, модальные подтверждения, скелетоны, пустые состояния и адаптивность.
- Клиент генерируется из OpenAPI; каталог `src/api/generated` не коммитится.
- Иконки переведены с deprecated `lucide-vue-next` на `@lucide/vue`.

### 1.4. Инфраструктура и документация

- `infra/mock-llm` — детерминированная OpenAI-совместимая заглушка со сценариями `ok`, `fenced`, `rate-limit-once`, `rate-limit-always`, `invalid-json`, `always-invalid`, `truncated`, `slow`, `unauthorized`, `quota`, `bad-quote`.
- `compose.yaml` — профиль `mock-llm`, сервисы `mock-llm` и `frontend`, передача `OPENROUTER_API_KEY`, исправлен контекст сборки `vacancy-service`.
- CI — добавлена job frontend: генерация клиента docker-образом `openapi-generator-cli:7.22.0`, `npm ci`, компонентные тесты и сборка без `setup-java`.
- `frontend/Dockerfile` — multi-stage: генератор OpenAPI, сборка на Node 24 и раздача через nginx; JRE в образе не нужен.
- Обновлены `README.md`, `AGENTS.md`, `docs/vacancy-data-model.md`, `CHANGELOG.md`.

## 2. Проверка

- `vacancy-service`: `./gradlew test spotlessCheck` → 44 теста, 0 failures.
- `text-extraction`: `gradle test spotlessCheck` → успешно.
- `frontend`: `npm test` → 13 тестов, 0 failures.
- `frontend`: `npm run build` → `vue-tsc` и Vite-сборка проходят.
- Liquibase: 15 changeset накатаны на PostgreSQL 18 через `compose`.
- `docker compose config --quiet` → успешно; образы `vacancy-service` и `frontend` собираются.
- `frontend`: сборка образа `docker build -f frontend/Dockerfile .` → успешно; `npm ci --ignore-scripts && npm test --ignore-scripts` в `node:24-alpine` → 13 тестов.
- `vacancy-service`: changelog для тестов копируется Gradle в test resources и читается из classpath; путь не зависит от рабочей директории.
- Сценарий на `mock-llm`: создание, правка типов, подтверждение, повторное сохранение, вторая вакансия с подсказкой истории, удаление, вакансия без уровня, добавление специализации.
- Ручная проверка реальной LLM: получен ответ OpenRouter с моделью `dots-studio/dots-3-note-preview:free`. На синтетической вакансии модель вернула 3 требования из 5 ожидаемых: Kafka попала в обязательные, Docker и расплывчатая формулировка отсутствовали.

## 3. Расхождения и принятые решения

1. **RFC §17.3 против §5.4.** Реализован ответ 17.3: при `LLM_NOT_CONFIGURED`, `LLM_UNAVAILABLE`, `LLM_AUTH`/`LLM_QUOTA` создаётся `DRAFT` с пустым шаблоном; `LLM_INVALID_RESPONSE`/`LLM_TRUNCATED` остаются `502`.
2. **Spring AI.** В версии 2.0.1 OpenAI-клиент построен на официальном `openai-java`; нужен явный `openai-java-client-okhttp`. Подключён `spring-ai-openai` напрямую, а не starter: автоконфигурация требует ключ на старте и ломает запуск без него.
3. **`WEIGHT_OBSERVATION`.** В реальной схеме такой таблицы не было; история реализована в `skill_kind_stat`, как требует RFC.
4. **`ApiStatusResponse`.** Отдельного формата ошибок в контракте не было; использован существующий.
5. **Миграция алиасов.** Для существующих `skill.normalized_name` алиас создаётся из текущего значения; `SkillCatalog` дополнительно находит навык по `normalized_name`, чтобы не создать дубль.
6. **Пороги истории** вынесены в `application.yaml`; в профиле `mock-llm` стоит `min-observations: 1` для демонстрации.
7. **`critical`** остаётся в БД со значением `false`, в API и UI не отдаётся.
8. **`OPTIONAL` в скоринге** не реализован: это зона `scoring-service`.
9. **Frontend.** Готового фронтенда не было; сделан MVP на Tailwind без полного shadcn-vue-слоя.

## 4. Не сделано / проблемы

- Качество реальной модели на списках «Обязательно / Будет плюсом / Научим» требует `vacancy-template.v3`: наследование заголовка раздела, обязательное извлечение `OPTIONAL` и расплывчатых требований.
- Карточки сгруппированы по типу; при смене типа карточка перемещается, но пользователь возвращается к ней прокруткой и подсветкой.
- Нет авторизации, серверной пагинации, экрана истории версий и управления уровнями.
- Реальная LLM не проверяется автоматическими тестами; ручная оценка качества остаётся за человеком.

## 5. Правки по ревью

| Замечание | Что сделано |
|---|---|
| В seed нет веб-фреймворка на Rust | Changeset `014`: `Axum`, `Actix Web`, `Rocket` и алиасы (`axum`, `actix`, `actix-web`, `rocket`) |
| В seed нет `UNIX`, `LaTeX`, `Typst` | Там же: навыки и алиасы (`unix`, `latex`, `tex`, `typst`) |
| Зачем JDK для OpenAPI-генератора | `frontend/Dockerfile` — multi-stage с `openapi-generator-cli:7.22.0`, JRE убран; CI генерирует клиент docker-образом, `setup-java` удалён; версия в `openapitools.json` — 7.22.0 |
| `lucide-vue-next` deprecated | Заменён на `@lucide/vue@^1.72.0`; все используемые иконки в пакете есть |
| `ApiMapper` разбить, использовать MapStruct | `VacancyApiMapper`, `RequirementApiMapper`, `ReferenceApiMapper`; `CommandMapper` — явные команды (`base_version` → -1) |
| Добавить `@Builder` на сущности | `@Builder` + `@AllArgsConstructor` на четырёх сущностях, конструкции переведены на билдеры |
| Есть ли библиотека транслитерации | Оставлена своя таблица: 33 символа, детерминированно, без зависимости. ICU4J (`Russian-Latin/BGN`) — вариант при мультиязычности, но это ~14 МБ |
| Native query в `VacancyRepository` | Заменён JPQL-фрагментом `VacancyPageRepositoryImpl` с `setFirstResult/setMaxResults`: API задаёт `offset`, `Pageable` изменил бы выборку |
| Почему `JdbcTemplate`, если есть JPA | Атомарный `INSERT ... ON CONFLICT ... DO UPDATE ... RETURNING` не выражается в JPA; SQL вынесен в `SkillKindStatRepository` с комментарием, лишний `SELECT` убран |
| Liquibase в тестах через миграции уровня compose | Приложение не зависит от compose-джобы: тесты поднимают Postgres в Testcontainers и применяют тот же changelog; Gradle копирует его в test resources, чтение — `classpath:db/changelog/...` без привязки к рабочей директории |

## 6. Требует решения пользователя

- Выпускать ли `vacancy-template.v3` с исправлением разбора списков и `OPTIONAL`.
- Показывать ли `dropped_requirements` в UI и добавлять экран истории версий.
- Достаточен ли стартовый набор специализаций или нужен другой список.
