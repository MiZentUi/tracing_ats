# Отчёт 009 — генерация навыков по специальности через LLM

## 1. Задача

HR должен иметь возможность попросить LLM сгенерировать навыки и алиасы для новой специальности, например «электрик», просмотреть черновики и подтвердить только нужное. В боевой словарь ничего не должно попадать без подтверждения.

Дополнительно нужно было закрыть отсутствующую в UI возможность сменить канонический алиас: backend-метод уже существовал, но кнопки в интерфейсе не было.

## 2. Принятые решения

### 2.1. LLM вызывает `ats-service`

Выбран вариант, при котором LLM-вызов делает `ats-service`, а не `dictionary-service`.

Мотивы:

- в `ats-service` уже есть `LlmClient`, промпты, таймауты, повторы, fallback Cloudflare и mock-LLM;
- не нужен второй LLM-клиент и второй набор ключей;
- `dictionary-service` остаётся детерминированным: `Resolve` никогда не вызывает LLM;
- проще тестировать и логировать через существующий контур.

Следствие: staging-таблицы генерации живут в `public` и принадлежат `ats-service`, а не `dictionary-service`. Это админский staging, не боевой словарь.

### 2.2. Генерация по свободной специальности

Вход — строка `specialization` (например, `электрик`), необязательное примечание и `maxSkills`.

Мотивы:

- не требуется заранее заводить специализацию в справочнике;
- можно использовать для любой новой вакансии;
- примечание позволяет уточнить отрасль и ограничения.

### 2.3. Строгая JSON-схема и один повтор

LLM возвращает только JSON по схеме `skill-generation.v1`:

```json
{
  "schema_version": 1,
  "specialization": "электрик",
  "skills": [
    {
      "canonical": "Электромонтаж",
      "skill_type": "DOMAIN",
      "aliases": [
        {"text": "монтаж электрики", "lang": "ru", "category": "SYNONYM"}
      ]
    }
  ]
}
```

При невалидном ответе — один повтор с требованием вернуть только JSON, затем `FAILED`.

### 2.4. Черновики, а не автозапись

LLM пишет только в staging. Боевые `dictionary.skill` и `dictionary.skill_alias` меняются только по действию HR.

Мотивы:

- LLM может выдумать навык, опечатку или конфликтный алиас;
- нужна человеческая проверка;
- `dictionary_version` должен меняться контролируемо.

### 2.5. Профессии и квалификации не являются навыками

Промпт запрещает создавать как навыки:

- профессии (`электрик`, `бухгалтер`);
- квалификации и допуски (`III группа по электробезопасности`, разряд, права).

В словарь попадают только инструменты, технологии и предметные области.

### 2.6. Пакетное применение через `ApplySkills`

Добавлен gRPC-метод `ApplySkills`:

- принимает список навыков, алиасов и `target_skill_id`;
- проверяет конфликты;
- создаёт новые навыки или добавляет алиасы к существующим;
- делает один `dictionary_version++` на всё подтверждение.

Это лучше серии `CreateSkill`/`AddAlias`, потому что HR подтверждает одну пачку и версия должна вырасти один раз.

### 2.7. `skip_review` для проверки черновиков

`ResolveBatchRequest` получил `skip_review`. При проверке канонов и алиасов `ats-service` передаёт `true`, чтобы неизвестные алиасы не засоряли очередь NEW. Обычные вакансии и резюме по-прежнему создают записи в `review_item`.

### 2.8. Канонический алиас в UI

Использован уже существующий `POST /api/v1/admin/dictionary/skills/{id}/canonical`. В карточке навыка у неканонического алиаса появилась кнопка «сделать каноном».

## 3. Где что хранится

### 3.1. Staging в `ats-service`, схема `public`

Liquibase `022-skill-generation.sql`:

```text
skill_generation_job(
  id uuid PK,
  specialization varchar(256),
  note text,
  status {PENDING, RUNNING, DONE, FAILED, APPROVED, REJECTED},
  requested_by, model, prompt_version, max_skills,
  error, raw_response jsonb,
  created_at, started_at, finished_at
)

skill_generation_draft(
  id uuid PK,
  job_id uuid FK -> skill_generation_job ON DELETE CASCADE,
  position smallint,
  canonical varchar(256),
  skill_type,
  target_skill_id uuid,          -- существующий навык dictionary
  target_skill_name,
  conflict_skill_id uuid,
  conflict_skill_name,
  status {DRAFT, APPROVED, REJECTED}
)

skill_generation_alias(
  id uuid PK,
  draft_id uuid FK -> skill_generation_draft ON DELETE CASCADE,
  position smallint,
  alias_raw varchar(256),
  lang, category,
  conflict_skill_id, conflict_skill_name,
  status {DRAFT, APPROVED, REJECTED}
)
```

`raw_response` хранится как JSON-строка в `jsonb`, чтобы можно было разобрать спорный ответ LLM. В логи он не пишется.

### 3.2. Боевой словарь

После подтверждения данные пишутся в `dictionary-service`:

- `dictionary.skill`;
- `dictionary.skill_alias`;
- `dictionary.skill_alias_key`;
- `dictionary.dictionary_snapshot`;
- `dictionary.dictionary_meta` (`dictionary_version++`).

### 3.3. `ats`-зеркало

Как и раньше, `public.skill` и `public.skill_alias` остаются локальным зеркалом для FK и fallback. Боевой канон — в `dictionary-service`.

## 4. Как работает генерация

1. HR отправляет `POST /api/v1/admin/dictionary/generation-jobs`:
   ```json
   {"specialization":"электрик","note":"ЖКХ, без допусков","maxSkills":10}
   ```
2. `SkillGenerationService` создаёт `skill_generation_job` в `PENDING`, затем переводит в `RUNNING`.
3. `SkillGenerationPrompt` собирает system-промпт и user-промпт:
   ```text
   <specialization>электрик</specialization>
   <note>ЖКХ, без допусков</note>
   ```
4. `LlmClient` вызывает реальную модель или mock.
5. `SkillGenerationParser` проверяет `schema_version`, `skills`, `skill_type`.
6. Для каждого канона вызывается `dictionary.Resolve(..., skip_review=true)`:
   - найден существующий навык → `target_skill_id`;
   - не найден → будет создан новый.
7. Для каждого алиаса также вызывается `Resolve`:
   - алиас уже у target → дубль, пропускается;
   - алиас у другого навыка → `conflict_skill_id`;
   - новый → будет добавлен.
8. Черновики сохраняются в `skill_generation_draft`/`skill_generation_alias`, job → `DONE`.
9. HR отмечает нужные навыки и алиасы.
10. `POST .../{id}/approve` собирает `ApplySkills` и вызывает dictionary.
11. Dictionary применяет пачку, делает один `dictionary_version++`, job → `APPROVED`.
12. `POST .../{id}/reject` переводит job в `REJECTED`.

Генерация синхронная: HTTP-запрос ждёт LLM. С реальной моделью это может занимать десятки секунд; фронтенд показывает «Генерируем…».

## 5. API

```text
POST /api/v1/admin/dictionary/generation-jobs
GET  /api/v1/admin/dictionary/generation-jobs?limit=20
GET  /api/v1/admin/dictionary/generation-jobs/{id}
POST /api/v1/admin/dictionary/generation-jobs/{id}/approve
POST /api/v1/admin/dictionary/generation-jobs/{id}/reject
```

`approve` с пустым телом подтверждает все неконфликтные черновики. Можно передать выбранные:

```json
{
  "skills": [
    {
      "draftId": "...",
      "canonical": "ПУЭ",
      "skillType": "DOMAIN",
      "targetSkillId": null,
      "aliases": ["правила устройства электроустановок"]
    }
  ]
}
```

## 6. UI

`frontend/src/views/DictionaryView.vue`:

- третья вкладка «Генерация»;
- форма специальности, примечания и лимита;
- список прошлых задач;
- карточки черновиков:
  - чекбокс навыка;
  - чекбоксы алиасов;
  - пометка «к существующему»;
  - конфликтный алиас подсвечен и disabled;
- кнопки «Подтвердить выбранное» и «Отклонить черновик»;
- в «Навыках» — кнопка «сделать каноном» у алиаса.

## 7. Промпт

`ats-service/src/main/resources/prompts/skill-generation.v1.txt`:

- только навыки, инструменты и домены;
- запрет профессий, квалификаций, допусков, разрядов и прав;
- алиасы: синонимы, сокращения, транслитерация, опечатки, формулировки hh;
- без падежных форм — их закрывает лемматизация;
- без общих слов;
- только JSON по схеме.

Схема — `skill-generation.v1.schema.json`.

## 8. Проверка

- Mock-LLM: сценарий «электрик» вернул `Электромонтаж`, `Чтение схем`, `ПУЭ`, `Электроинструмент`; подтверждение добавило новые навыки, `ПУЭ` и `электроинструмент` резолвятся.
- Реальная LLM: генерация для `электрик` вернула `DONE` и валидные черновики:
  `Электромонтажные работы`, `Электропроводка`, `Щитовое оборудование`, `Системы освещения`, `Измерительные приборы`, `Ремонт и замена электрокомпонентов`, `Диагностика электросетей`.
- Liquibase `019` применился на пустой БД.
- `dictionary-service`: 22 теста, `ruff check`/`ruff format --check` чистые.
- `scoring-service`: 74 теста, ruff чистый.
- `ats-service`: `./gradlew test` — BUILD SUCCESSFUL.
- Frontend production build (`vue-tsc` + `vite build`) проходит.
- `docker compose config -q` — OK.

## 9. Ограничения

- Генерация синхронная; отдельной фоновой очереди и отмены job нет.
- `apply_skills` в dictionary проверяет конфликты до записи, но не является одной транзакцией на уровне Postgres для всех навыков; при редкой гонке возможна частичная запись.
- `generation_job.model` сохраняет сконфигурированную модель, а не фактическую модель из ответа LLM.
- Новые админские REST-эндпоинты не внесены в `contracts/openapi.yaml`; frontend использует ручной `api/dictionary.ts`.
- Аутентификации в проекте нет: админка открыта.
- Нет отдельного endpoint для правки черновика до approve; правки передаются в теле approve.
- `raw_response` хранится строкой в jsonb; для анализа нужен отдельный просмотр.
- `docs/materials/` (ESCO) остаётся вне репозитория.

## 10. Файлы

Новые:

- `migrations/changes/022-skill-generation.sql`;
- `ats-service/src/main/java/ats/vacancy/dictionary/generation/` — controller, service, store, prompt, parser, payload, models;
- `ats-service/src/main/resources/prompts/skill-generation.v1.txt` и `.schema.json`;
- `ats-service/src/test/java/ats/vacancy/dictionary/generation/SkillGenerationParserTest.java`.

Изменённые:

- `contracts/proto/dictionary_service/v1/dictionary.proto` — `ApplySkills`, `skip_review`;
- `dictionary-service/src/dictionary_service/service.py` — `apply_skills`, `skip_review`;
- `dictionary-service/src/dictionary_service/server.py` — gRPC-маппинг;
- `dictionary-service/tests/test_service.py` — тесты `apply_skills` и `skip_review`;
- `ats-service/.../DictionaryClient.java`, `DictionaryModels.java` — `applySkills`, `resolve(..., skipReview)`;
- `frontend/src/api/dictionary.ts`, `frontend/src/views/DictionaryView.vue` — генерация и канон;
- `migrations/db.changelog-master.yaml` — include `019`;
- `infra/mock-llm/server.py` — mock-ответ для `<specialization>`;
- `CHANGELOG.md`, `docs/demo-skill-dictionary.md`, ТЗ.

## 11. Коммиты

- `49d74bc` — `feat: добавить генерацию навыков по специальности через LLM`;
- отчёт — отдельным коммитом `docs(reports): добавить отчёт 009 по генерации навыков`.
