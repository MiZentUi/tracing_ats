# ТЗ: dictionary-service и нормализация навыков

Статус: реализовано v1. Документ описывает текущие решения и границы; исторические варианты поставки словаря остались в git и в [`../rfc/rfc-skill-dictionary.md`](../rfc/rfc-skill-dictionary.md).

---

## 1. Цель

Словарь решает три задачи:

1. **Нормализация** — свести `js`, `JavaScript`, `питон`, `Python`, `1С`, `1C`, `кубернетесом`, `kubernetes` к одному ключу.
2. **Идентичность** — выдать `skill_id` и канонический ключ, чтобы Java и Python сравнивали навыки одинаково.
3. **Курирование** — складывать неизвестные написания в очередь NEW, давать HR привязать их к существующему навыку или создать новый.

Квалификации — отдельная сущность, не `skill_type=OTHER`. Связи навыков хранят класс (`SAME_GROUP`, `PARENT`, `CHILD`, `RELATED`), а не число похожести: числа `S^{ij}` считает `scoring-service`.

---

## 2. Границы

### Входит

- отдельный Python 3.12 gRPC-сервис;
- схема `dictionary` в PostgreSQL;
- нормализация, алиасы, очередь NEW;
- квалификации и алиасы квалификаций;
- связи навыков;
- версия словаря и снапшоты;
- админские RPC;
- BFF-админка в `ats-service`.

### Не входит

- вызовы LLM;
- расчёт `S^{ij}` и скоринга;
- хранение PII;
- чтение резюме и вакансий;
- embeddings и нечёткий матчинг в `Resolve`;
- merge навыков и квалификаций;
- роли и аутентификация (общая проблема проекта).

---

## 3. Архитектура

```text
ats-service ──gRPC──► dictionary-service ──SQL──► dictionary.*
scoring-service ──gRPC──► dictionary-service
frontend ──REST──► ats-service /admin/dictionary ──gRPC──► dictionary-service
```

| Аспект | Решение |
|---|---|
| Язык | Python 3.12 |
| Транспорт | gRPC, plaintext, health/reflection |
| Хранилище | PostgreSQL, схема `dictionary` |
| Миграции | Liquibase `020`, `021`, `023`; runtime DDL нет |
| Админка | REST BFF в `ats-service`, только он знает ссылки на `skill_id` |
| Версия | `resolver_version` и снапшоты |
| LLM | Не используется |

---

## 4. gRPC-контракт

Контракт — [`contracts/proto/dictionary_service/v1/dictionary.proto`](../../contracts/proto/dictionary_service/v1/dictionary.proto).

### Чтение и разрешение

- `Resolve` — навыки;
- `ResolveQualifications` — квалификации;
- `GetRelations` — классы связей для пар `skill_id`;
- `GetVersion` — версии словаря, квалификаций, связей и конфигурации;
- `GetSkills` — список навыков.

### Очередь NEW

- `ListReviewItems`, `ResolveReviewItem`;
- `ListQualificationReviewItems`, `ResolveQualificationReviewItem`.

### Администрирование

- навыки: `ListSkills`, `CreateSkill`, `AddAlias`, `SetCanonicalAlias`, `DeleteAlias`, `DeprecateSkill`, `DeleteSkill`;
- квалификации: `ListQualifications`, `CreateQualification`, `AddQualificationAlias`, `SetCanonicalQualificationAlias`, `DeleteQualificationAlias`, `DeleteQualification`;
- связи: `ListRelations`, `CreateRelation`, `DeleteRelation`;
- пакетное применение: `ApplySkills`.

`Resolve`/`ResolveQualifications` принимают `as_of_resolver_version`; неизвестная версия — `NOT_FOUND`.

---

## 5. Нормализация

Пайплайн — `src/dictionary_service/normalization.py`:

1. NFKC.
2. Схлопывание пробелов, включая NBSP/NNBSP.
3. Trim, lowercase, `ё → е`.
4. Снятие краевой пунктуации `,;:!?\"'()[]«»` и завершающей точки; `+`, `#`, ведущая точка сохраняются.
5. Свёртка гомоглифов только при наличии ASCII-сигнала: `1С → 1c`, `С++ → c++`, `Еxcel → excel`; чисто кириллические слова не меняются (`сок` остаётся `сок`).
6. Лемматизация полностью кириллических токенов через `pymorphy3`; латиница и смешанные токены не лемматизируются.
7. Ключи в порядке приоритета: `RAW`, `FOLDED`, `LEMMA`, `LEMMA_FOLDED`.

`RAW_ONLY` (например, `кубы`) хранит только `RAW`, чтобы не совпасть с `куб`.

`Resolve` не делает нечёткий матчинг и не вызывает LLM. Неизвестное имя получает `NEW`, попадает в очередь ревью и не разрешается автоматически.

---

## 6. Модель данных

Подробная схема — [`../data-model.md`](../data-model.md). Основные таблицы:

- `dictionary.skill`, `dictionary.skill_alias`, `dictionary.skill_alias_key`;
- `dictionary.review_item`;
- `dictionary.dictionary_meta`, `dictionary.dictionary_snapshot`;
- `dictionary.qualification`, `dictionary.qualification_alias`, `dictionary.qualification_alias_key`;
- `dictionary.qualification_review_item`;
- `dictionary.skill_relation`.

`dictionary.skill_alias_key` уникален: один нормализованный ключ не может вести на два навыка. Канонический алиас — `skill_alias.is_canonical` с partial unique index.

---

## 7. Seed и версия

- `migrations/changes/021` содержит 79 навыков, 129 алиасов и 144 ключа.
- Python seed (`seed.py`) содержит те же 79 навыков, 7 квалификаций и 3 связи; используется, если таблицы пусты.
- Стартовое заполнение идемпотентно: навыки, квалификации и связи заполняются только при пустых таблицах.
- `resolver_version` включает версии словаря, квалификаций, связей, конфигурации, `pymorphy` и правил гомоглифов.
- Версии неизменяемы; `Resolve` может читать снапшот `as_of`.
- Два источника seed (Liquibase и Python) требуют синхронизации; автоматической проверки пока нет.

---

## 8. Интеграция

### `ats-service`

- вызывает `Resolve` при сохранении требований вакансии;
- BFF `/api/v1/admin/dictionary` проксирует админские операции;
- при недоступности словаря использует локальные `public.skill`/`public.skill_alias` и точный ключ;
- админские endpoints не имеют fallback: ошибка словаря возвращает `503 DICTIONARY_UNAVAILABLE`.

### `scoring-service`

- делает один batch `Resolve` на запуск;
- сравнивает навыки по `skill_id` или точному ключу;
- при недоступности словаря переходит на `EXACT_FALLBACK`;
- возвращает `resolver_version` и `dictionary_mode`;
- Java пока не сохраняет эти поля в `score_run`.

### Квалификации

- `ResolveQualifications` вызывается из `scoring-service`;
- при недоступности словаря используется `QualificationCatalog` из запроса или точный ключ;
- квалификация в резюме и вакансии сравнивается бинарно; отсутствие даёт `NOT_STATED`, а не `ABSENT`.

---

## 9. Админка

Экран `/dictionary` во frontend:

- навыки: поиск, тип, статус, алиасы, канонический алиас, создание;
- квалификации: создание, алиасы, канон, удаление;
- связи: граф и таблица, создание/удаление;
- генерация: задание по специальности, черновики, подтверждение;
- очередь NEW: привязка к существующему навыку, создание нового, отклонение.

Ограничения:

- нет удаления/депрекации навыка в UI (API есть);
- нет добавления квалификационного требования в редакторе вакансии;
- нет аутентификации;
- генерация синхронная, хотя выглядит как job.

---

## 10. Ограничения и открытые вопросы

- `DICTIONARY_SERVICE_TOKEN` загружается, но не проверяется.
- `DICTIONARY_SUGGESTION_THRESHOLD` не используется: порог подсказок захардкожен (`0.6`).
- Нет CI job для `dictionary-service`, нет `uv.lock`.
- Нет merge навыков и квалификаций.
- Нет `qualification_id` в `vacancy_requirement`: удаление квалификации не проверяет ссылки из шаблонов.
- Эмбеддинги и `RESOLVE_METHOD_EMBEDDING` зарезервированы в proto, но не реализованы.
- Значения `S^{ij}` для классов связей (`0.5`) — стартовая политика, требует калибровки.
- Контексты очереди NEW должны приходить уже обезличенными; сервис не маскирует их сам.
- В логах только `method`, `request_id`, `status`, `elapsed_ms`; тела не логируются.

---

## 11. Проверки

```bash
cd dictionary-service
python -m pytest
ruff check .
ruff format --check .
```

Тесты используют `InMemoryStore`; `PostgresStore` и gRPC-сервер отдельными тестами не покрыты. В CI отдельного job пока нет.
