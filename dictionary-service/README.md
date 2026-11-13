# dictionary-service

Словарь навыков: нормализация, алиасы, очередь NEW и подсказки для HR.
Владелец таблиц `skill`, `skill_alias`, `skill_alias_key`, `review_item`.

Контракт — `contracts/proto/dictionary_service/v1/dictionary.proto`.
Админский REST живёт в `ats-service` (BFF): только он знает ссылки на `skill_id`.

## Запуск

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
