# dictionary-service

Словарь навыков и квалификаций: нормализация, алиасы, очередь NEW, отношения похожести и подсказки для HR.

Владелец схемы `dictionary` в PostgreSQL: `skill`, `skill_alias`, `skill_alias_key`, `qualification`, `qualification_alias`, `qualification_alias_key`, `skill_relation`, `review_item`, `qualification_review_item`, `dictionary_meta`, `dictionary_snapshot`.

Квалификации — отдельная сущность, не `skill_type=OTHER`. Отношения навыков хранят класс связи (`SAME_GROUP`, `PARENT`, `CHILD`, `RELATED`), а не число похожести: числа `S^{ij}` считает `scoring-service`.

Контракт — [`contracts/proto/dictionary_service/v1/dictionary.proto`](../contracts/proto/dictionary_service/v1/dictionary.proto). Админский REST живёт в `ats-service` (BFF): только он знает ссылки на `skill_id`.

## Запуск

Схему `dictionary` и seed накатывает Liquibase (`migrations/changes/020`–`023`) в составе джобы `migrations`. Демонстрационные квалификации и связи догружаются сервисом при старте, если соответствующие таблицы пусты.

```bash
DICTIONARY_STORE=memory python -m dictionary_service   # без Postgres, для отладки
DICTIONARY_DATABASE_URL=postgresql://ats:ats@localhost:5432/ats python -m dictionary_service
```

Порт по умолчанию — `8084`.

## Конфигурация

| Переменная | По умолчанию | Назначение |
|---|---|---|
| `DICTIONARY_GRPC_HOST` | `0.0.0.0` | Адрес gRPC |
| `DICTIONARY_GRPC_PORT` | `8084` | Порт gRPC |
| `DICTIONARY_DATABASE_URL` | `postgresql://ats:ats@localhost:5432/ats` | PostgreSQL |
| `DICTIONARY_STORE` | `postgres` | `postgres` или `memory` |
| `DICTIONARY_LOG_LEVEL` | `INFO` | Логирование |
| `DICTIONARY_SERVICE_TOKEN` | — | Загружается, но не проверяется |
| `DICTIONARY_SUGGESTION_THRESHOLD` | `0.6` | Загружается, но фактически не используется |
| `DICTIONARY_MAX_BATCH` | `2000` | Лимит batch-запросов |
| `DICTIONARY_EMBEDDING_MODEL_ID` | — | Только для версии; embeddings не реализованы |

## Открытый seed

`src/dictionary_service/data/open_seed.json` — курируемая подборка навыков и
квалификаций из открытых таксономий и каталогов (ESCO, Wikidata, публичные
подборки). Она расширяет предметные области за пределами базового IT-набора:
финансы, HR, продажи, логистика, строительство, дизайн, право, образование,
медицина и управление проектами.

- При пустой базе сервис загружает базовый и открытый seed.
- На существующей базе `seed_missing` идемпотентно добавляет только недостающие
  канонические имена и алиасы; повторный запуск не меняет количество записей.
- Источник зафиксирован в `provenance` JSON-файла. Это стартовая подборка, а не
  полная выгрузка ESCO/Wikidata; внешние идентификаторы пока не хранятся.
- Данные не влияют на скоринг напрямую: словарь только нормализует навыки и
  квалификации.

## Нормализация

1. NFKC, пробелы, lowercase, краевая пунктуация, `ё → е`.
2. Свёртка гомоглифов только при наличии ASCII-сигнала: `1С → 1c`, `С++ → c++`, `сок` не меняется.
3. Лемматизация кириллицы (`кубернетесом → кубернетес`).

Свёрнутый ключ используется только для точного поиска по алиасу. `кубы` хранится как `RAW_ONLY`, чтобы не совпасть с `куб`.

`Resolve` не делает нечёткий матчинг и не вызывает LLM. Неизвестное имя получает `NEW` и попадает в очередь ревью.

## Контракт

- `Resolve` — навыки;
- `ResolveQualifications` — квалификации;
- `GetRelations` — классы отношений для пар `skill_id`;
- `GetVersion` — версия навыков, квалификаций и отношений;
- `ListReviewItems`/`ResolveReviewItem` — очередь NEW;
- `ListSkills`/`CreateSkill`/`AddAlias`/`SetCanonicalAlias`/`DeleteAlias`/`DeprecateSkill`/`DeleteSkill` — навыки;
- `ListQualifications`/`CreateQualification`/`AddQualificationAlias`/`SetCanonicalQualificationAlias`/`DeleteQualificationAlias`/`DeleteQualification` — квалификации;
- `ListRelations`/`CreateRelation`/`DeleteRelation` — связи;
- `ApplySkills` — пакетное применение подтверждённых черновиков.

Для квалификаций `3 группа по электробезопасности` и `III группа по электробезопасности` сводятся через алиасы, а `B` и `C` остаются разными квалификациями.

## Версии и деградация

- `resolver_version` собирается из версий словаря, квалификаций, связей, конфигурации, `pymorphy` и правил гомоглифов.
- `Resolve`/`ResolveQualifications`/`GetRelations` принимают `as_of_resolver_version`; неизвестная версия — `NOT_FOUND`.
- При недоступности словаря `scoring-service` переходит на `EXACT_FALLBACK`; `ats-service` использует локальные `public.skill`/`public.skill_alias`.
- Админские endpoints BFF не имеют fallback: если словарь недоступен, возвращается `503 DICTIONARY_UNAVAILABLE`.

## Ограничения v1

- Очередь новых квалификаций доступна в админке, но не поддерживает автоматические подсказки-кандидаты: HR выбирает существующую квалификацию вручную или создаёт новую.
- Нет merge квалификаций и связей. Ошибочный алиас можно удалить и привязать заново; для квалификаций доступно удаление целиком.
- У квалификации пока нет `qualification_id` в `vacancy_requirement`, поэтому удаление записи словаря не проверяет ссылки из шаблонов: в шаблоне остаётся снимок label.
- Админка не имеет аутентификации и ролей, как и остальной API проекта.
- Граф связей в UI использует d3 force-layout; для очень больших словарей может потребоваться отдельная визуализация.
- Значения `S^{ij}` для классов `SAME_GROUP`/`RELATED`/`PARENT`/`CHILD` пока `0.5` и требуют калибровки.
- Embeddings и `RESOLVE_METHOD_EMBEDDING` зарезервированы в proto, но не реализованы.
- Нет отдельного CI job; нет `uv.lock`.
- Три источника seed (Liquibase, Python и `open_seed.json`) требуют синхронизации; автоматической проверки нет. Открытый seed применяется при старте сервиса идемпотентно.

## Проверки

```bash
python -m pytest
ruff check .
ruff format --check .
```

Тесты используют `InMemoryStore`; `PostgresStore` и gRPC-сервер отдельными тестами не покрыты.

## Логирование

Один inbound-запрос — одно событие с `method`, `request_id`, `status`, `elapsed_ms`. Тела запросов, тексты резюме и PII не логируются. Контексты очереди NEW должны приходить уже обезличенными; сервис не маскирует их сам.

## Связанные документы

- [`../docs/technical_specification/tz-skill-dictionary-service.md`](../docs/technical_specification/tz-skill-dictionary-service.md)
- [`../docs/data-model.md`](../docs/data-model.md)
- [`../docs/rfc/rfc-qualification-dictionary.md`](../docs/rfc/rfc-qualification-dictionary.md)
