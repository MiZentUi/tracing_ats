# Модель данных: вакансии, специализации, уровни, навыки

Схема соответствует миграции `migrations/changes/001-vacancy-schema.sql` (MVP).
Корневой changelog Liquibase — `migrations/db.changelog-master.xml`; его накатывает не
приложение, а отдельная джоба `migrations` из корневого `compose.yaml` (см. `migrations/Dockerfile`).

## ER-диаграмма

```mermaid
erDiagram
    specialization ||--o{ vacancy_template_version : "задаёт"
    level ||--o{ vacancy_template_version : "задаёт"
    scoring_weights ||--o{ vacancy_template_version : "применяется"
    vacancy ||--|{ vacancy_template_version : "имеет версии"
    vacancy_template_version ||--o{ vacancy_requirement : "содержит"
    skill |o--o{ vacancy_requirement : "требуется как"

    specialization {
        uuid id PK
        varchar code UK "slug, java-developer"
        varchar name UK "без учёта регистра"
        boolean is_active "false вместо удаления"
        timestamptz created_at
        timestamptz updated_at
    }

    level {
        uuid id PK
        varchar code UK "slug, senior"
        varchar name UK "без учёта регистра"
        integer sort_order "порядок в списках"
        numeric github_weight "ползунок HR, от 0 до 1"
        boolean is_active "false вместо удаления"
        timestamptz created_at
        timestamptz updated_at
    }

    scoring_weights {
        uuid id PK
        integer version UK
        varchar status "CANDIDATE или ACTIVE, активна одна"
        numeric w_m "wM + wN + wF = 1"
        numeric w_n
        numeric w_f
        timestamptz created_at
    }

    skill {
        uuid id PK
        varchar name "как показывать"
        varchar normalized_name UK "нижний регистр, без пробелов по краям"
        timestamptz created_at
    }

    vacancy {
        uuid id PK
        varchar title
        varchar status "DRAFT ACTIVE CLOSED ARCHIVED"
        varchar source_type "TEXT FILE HH"
        varchar source_ref "id на hh или ключ файла"
        text source_text "извлечённый текст"
        integer current_version FK "вместе с id указывает на версию"
        timestamptz created_at
        timestamptz updated_at
    }

    vacancy_template_version {
        uuid id PK
        uuid vacancy_id FK "уникально вместе с version_no"
        integer version_no
        uuid specialization_id FK
        uuid level_id FK
        numeric github_weight "копия level.github_weight"
        uuid scoring_weights_id FK
        varchar llm_prompt_version
        timestamptz created_at "строка неизменяема"
    }

    vacancy_requirement {
        uuid id PK
        uuid template_version_id FK
        smallint position "уникально в версии"
        uuid skill_id FK "пусто для расплывчатых"
        text raw_text "исходная формулировка"
        varchar kind "MUST NICE OPTIONAL"
        smallint weight "от 1 до 3"
        boolean critical "только для MUST"
        numeric years_required "по умолчанию 0"
        numeric half_life_years "пусто означает значение из конфига"
        text clarity_warning
    }
```

## Как читать

- **`scoring_weights`** — веса итогового балла `Score = 100·(wM·M + wN·N + wF·F)`. В MVP одна активная строка 0.70 / 0.15 / 0.15. Новые веса — новая версия. Таблицей владеет Java, в Python-сервис значения передаются в gRPC-запросе.
- **`level.github_weight`** — λ, вес внешних доказательств (GitHub). HR выставляет ползунком при создании уровня. Это единственная связь уровня с весами.
- **Справочники** `specialization`, `level`, `skill` открыты для добавления. Удалить строку, на которую есть ссылки, нельзя, её отключают через `is_active`.
- **Версия шаблона** фиксирует специализацию, уровень, вес GitHub и ссылку на веса балла. Когда HR меняет веса требований, создаётся новая версия, старая не меняется (защищено триггером).
- **Текущая версия** вакансии задаётся парой `vacancy.id` + `vacancy.current_version` (составной внешний ключ, проверяется в конце транзакции).

## Ограничения целостности

| Таблица | Ограничение |
|---|---|
| `scoring_weights` | `wM + wN + wF = 1`; одновременно активна только одна запись |
| `level` | `github_weight` от 0 до 1 |
| `vacancy_requirement` | `kind` только `MUST`, `NICE`, `OPTIONAL`; `weight` от 1 до 3 |
| `vacancy_requirement` | нужен либо `skill_id`, либо непустой `raw_text` |
| `vacancy_requirement` | `critical = true` только при `kind = MUST` |
| `vacancy_requirement` | навык не повторяется в одной версии, `position` уникален в версии |
| все ссылки на справочники | `ON DELETE RESTRICT`, физически удалить используемую строку нельзя |
| `vacancy` → версии → требования | `ON DELETE CASCADE` |
