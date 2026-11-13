# dictionary-service

Словарь навыков и квалификаций: нормализация, алиасы, очередь NEW, отношения
похожести и подсказки для HR. Владелец таблиц `skill`, `skill_alias`,
`skill_alias_key`, `qualification`, `qualification_alias`,
`qualification_alias_key`, `skill_relation`, `review_item`,
`qualification_review_item`.

Квалификации — отдельная сущность, не `skill_type=OTHER`. Отношения навыков
хранят класс связи (`SAME_GROUP`, `PARENT`, `CHILD`, `RELATED`), а не число
похожести: числа `S^{ij}` считает `scoring-service`.

Контракт — `contracts/proto/dictionary_service/v1/dictionary.proto`.
Админский REST живёт в `ats-service` (BFF): только он знает ссылки на `skill_id`.

## Запуск

Схему `dictionary` и seed накатывает Liquibase (`migrations/changes/020`–`023`)
в составе джобы `migrations`. Демонстрационные квалификации и связи
догружаются сервисом при старте, если соответствующие таблицы пусты.

```bash
DICTIONARY_STORE=memory python -m dictionary_service   # без Postgres, для отладки
DICTIONARY_DATABASE_URL=postgresql://ats:ats@localhost:5432/ats python -m dictionary_service
```

## Нормализация

1. NFKC, пробелы, lower, краевая пунктуация, `ё -> е`.
2. Свёртка гомоглифов только при наличии ASCII-сигнала: `1С -> 1c`, `С++ -> c++`,
   `сок` не меняется.
3. Лемматизация кириллицы (`кубернетесом -> кубернетес`).

Свёрнутый ключ используется только для точного поиска по алиасу. `кубы` хранится
как `RAW_ONLY`, чтобы не совпасть с `куб`.

## Контракт

- `Resolve` — навыки;
- `ResolveQualifications` — квалификации;
- `GetRelations` — классы отношений для пар `skill_id`;
- `GetVersion` — версия навыков, квалификаций и отношений.

Для квалификаций `3 группа по электробезопасности` и
`III группа по электробезопасности` сводятся через алиасы, а `B` и `C`
остаются разными квалификациями.
