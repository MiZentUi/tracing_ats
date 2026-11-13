| | |
|---|---|
| **Статус** | Предложено, требует согласования |
| **Автор** | DeepSeek |
| **Дата** | 2026-11-13 |
| **Контур** | `dictionary-service`, `ats-service`, `scoring-service` |
| **Ветка** | после мержа `feature/skill-dictionary-service` |
| **Модуль** | справочник квалификаций |
| **Связанные документы** | `rfc-skill-dictionary.md`, `rfc-feature-extraction.md`, `rfc-scoring-model.md` |

# RFC: справочник квалификаций в dictionary-service

## 1. Контекст

Квалификации уже участвуют в скоринге как бинарные требования: группа
по электробезопасности, категория водительских прав, аттестат, допуск,
сертификат. Сейчас они хранятся как открытый `label`:

- в вакансии — `vacancy_requirement.skill_text` + флаг
  `vacancy_requirement.qualification`;
- в резюме — `ParsedResume.qualifications`;
- в Java — временный `QualificationCatalog` со stub-алиасами;
- в `scoring-service` — `QualificationNormalizer`, который сравнивает
  канонические ключи.

Это работает, но словарь остаётся размазанным между Java и Python:

- Java передаёт снимок алиасов в gRPC-запросе;
- Python не может обновить словарь без нового запроса;
- у квалификаций нет общего владельца, админки и версии;
- новый словарь навыков (`dictionary-service`) пока не знает о квалификациях.

Первоначальная идея команды — хранить и навыки, и квалификации в одной
графовой БД (Neo4j или RDF-хранилище), которой владеет отдельный сервис.
Сейчас эта модель эмулируется через Postgres и таблицы `dictionary.skill` /
`dictionary.skill_alias`.

## 2. Решение

Согласовано: **не вводить графовую БД сейчас**. Вместо этого добавить
в существующий `dictionary-service` отдельную сущность квалификации по образу
и подобию навыков:

```text
dictionary.skill              dictionary.qualification
dictionary.skill_alias        dictionary.qualification_alias
dictionary.skill_alias_key    dictionary.qualification_alias_key
```

Квалификации не складываются в `dictionary.skill` и не получают
`skill_type = OTHER`. Это разные сущности с разной семантикой, разными
очередями ревью и разными правилами матчинга.

`dictionary-service` остаётся единственным владельцем справочника. Java
и `scoring-service` обращаются к нему по gRPC; никто не читает таблицы
напрямую.

## 3. Границы

### Входит в v1

- отдельные таблицы `qualification`, `qualification_alias`,
  `qualification_alias_key`;
- открытый `label` и каноническое имя без закрытого enum;
- алиасы и нормализация тех же форм, что у навыков;
- `ResolveQualifications` и версия словаря квалификаций;
- бинарное сравнение квалификаций в скоринге;
- fallback на текущий `QualificationCatalog` при недоступности словаря;
- версия словаря в запуске скоринга;
- seed для демонстрационных квалификаций.

### Не входит в v1

- графовая БД, Neo4j, RDF, SPARQL;
- иерархия и похожесть квалификаций;
- отношения похожести навыков (`Kafka`/`RabbitMQ`) — отдельный следующий этап;
- парсинг сроков действия документов и статусы `EXPIRED`;
- автоматические решения по кандидату;
- изменение формулы `Score`;
- UI-админка в этой же итерации (контракт и backend можно сделать раньше);
- миграция всех старых квалификаций в словарь задним числом.

## 4. Почему не графовая БД сейчас

Графовая модель хорошо подходит для будущих связей:

```text
Kafka --RELATED--> RabbitMQ
Docker --SAME_GROUP--> Podman
PostgreSQL --PARENT--> SQL
```

Но для v1 квалификаций нужны только:

- точное совпадение алиасов;
- канонический ключ;
- версия справочника.

Это уже эффективно решается Postgres:

- `qualification_alias_key (key text primary key)` — точный поиск;
- `qualification_alias.qualification_id` — связь алиаса с каноном;
- при необходимости иерархии — рекурсивные CTE;
- при необходимости графа — экспорт стабильных UUID и типизированных рёбер
  в Neo4j/RDF без изменения контрактов потребителей.

Поэтому сейчас важнее не выбрать хранилище, а зафиксировать **граф-совместимую
модель**: стабильные ID, отдельные типы сущностей, типизированные связи
в будущем. Переход на графовую БД остаётся возможным и не требует менять
`ResolveQualifications`.

## 5. Модель данных

Схема — в `dictionary`, рядом с навыками, но без общих таблиц.

### 5.1. `dictionary.qualification`

```sql
CREATE TABLE dictionary.qualification (
    id              uuid PRIMARY KEY,
    canonical_name  text NOT NULL,
    status          text NOT NULL DEFAULT 'ACTIVE'
                    CHECK (status IN ('ACTIVE', 'DEPRECATED')),
    qualification_type text,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);
```

`qualification_type` — необязательная метаинформация для админки
(`PERMIT`, `LICENSE`, `ATTESTATION`, `CATEGORY`, `OTHER`). Она **не участвует
в идентичности** и не является закрытым перечнем квалификаций. Основной
идентификатор — `id`, отображаемое имя — `canonical_name`.

### 5.2. `dictionary.qualification_alias`

```sql
CREATE TABLE dictionary.qualification_alias (
    id                uuid PRIMARY KEY,
    qualification_id  uuid NOT NULL
                      REFERENCES dictionary.qualification (id)
                      ON DELETE CASCADE,
    alias_raw         text NOT NULL,
    is_canonical      boolean NOT NULL DEFAULT false,
    lang              text NOT NULL DEFAULT 'mixed',
    source            text NOT NULL DEFAULT 'HR',
    lemma_mode        text NOT NULL DEFAULT 'AUTO',
    created_by        text NOT NULL DEFAULT '',
    created_at        timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_qualification_alias_canonical
    ON dictionary.qualification_alias (qualification_id)
    WHERE is_canonical;
```

Правила:

- у квалификации ровно один канонический алиас;
- alias-ключ глобально уникален: один алиас не может вести на две
  квалификации;
- конфликт при добавлении алиаса возвращает `409`, как у навыков;
- `lemma_mode = RAW_ONLY` доступен для формулировок, где лемматизация
  меняет смысл.

### 5.3. `dictionary.qualification_alias_key`

```sql
CREATE TABLE dictionary.qualification_alias_key (
    key       text PRIMARY KEY,
    alias_id  uuid NOT NULL
              REFERENCES dictionary.qualification_alias (id)
              ON DELETE CASCADE,
    kind      text NOT NULL
);

CREATE INDEX ix_qualification_alias_key_alias
    ON dictionary.qualification_alias_key (alias_id);
```

Ключи строятся тем же конвейером, что и для навыков:

1. NFKC, схлопывание пробелов, `trim`, lower, `ё → е`;
2. снятие краевой пунктуации и завершающей точки;
3. свёртка гомоглифов только при наличии ASCII-сигнала;
4. лемматизация кириллицы с учётом `lemma_mode`;
5. ключи `RAW`, `FOLDED`, `LEMMA`, `LEMMA_FOLDED`.

Важно: `III группа` и `3 группа` — это **алиасы**, а не магическая
нормализация. Автоматически римские цифры в арабские не переводятся.

### 5.4. Очередь ревью

Для NEW-квалификаций нужна отдельная очередь. Нельзя использовать
`dictionary.review_item` навыков: это смешает сущности и испортит
админский сценарий.

```sql
CREATE TABLE dictionary.qualification_review_item (
    id                        uuid PRIMARY KEY,
    key                       text NOT NULL UNIQUE,
    sample_masked             text[] NOT NULL DEFAULT '{}',
    occurrences               bigint NOT NULL DEFAULT 1,
    status                    text NOT NULL DEFAULT 'PENDING',
    suggested_qualification_id uuid,
    suggested_confidence      double precision,
    candidates                jsonb NOT NULL DEFAULT '[]'::jsonb,
    resolved_qualification_id uuid,
    resolved_by               text,
    resolved_at               timestamptz,
    first_seen                timestamptz NOT NULL DEFAULT now(),
    last_seen                 timestamptz NOT NULL DEFAULT now()
);
```

`sample_masked` содержит только обезличенные фрагменты, без резюме,
цитат и PII.

### 5.5. Версия

В `dictionary.dictionary_meta` добавляется отдельный счётчик:

```sql
ALTER TABLE dictionary.dictionary_meta
    ADD COLUMN qualification_version bigint NOT NULL DEFAULT 1;
```

Композитный `resolver_version` расширяется:

```text
d<skills_version>-q<qualification_version>-...-h<homoglyph_rules_version>
```

Любое изменение `qualification` или `qualification_alias` увеличивает
`qualification_version`. Это позволяет не инвалидировать навыки при изменении
только квалификаций.

## 6. gRPC-контракт

Существующий `Resolve` не меняется. Добавляются аддитивные методы.

```proto
service DictionaryService {
  // ... существующие методы

  rpc ResolveQualifications(ResolveQualificationsRequest)
      returns (ResolveQualificationsResponse);

  rpc GetRelations(GetRelationsRequest) returns (GetRelationsResponse);

  rpc ListQualifications(ListQualificationsRequest)
      returns (ListQualificationsResponse);
  rpc CreateQualification(CreateQualificationRequest) returns (Qualification);
  rpc AddQualificationAlias(AddQualificationAliasRequest)
      returns (QualificationAlias);
  rpc SetCanonicalQualificationAlias(SetCanonicalQualificationAliasRequest)
      returns (Qualification);
  rpc DeleteQualificationAlias(DeleteQualificationAliasRequest)
      returns (google.protobuf.Empty);
  rpc DeprecateQualification(DeprecateQualificationRequest)
      returns (Qualification);
}
```

Основной метод разрешения:

```proto
message RawQualification {
  string key = 1;
  string raw_label = 2;
  ResolveSource source = 3;
}

message ResolvedQualification {
  string key = 1;
  string raw_label = 2;
  string qualification_id = 3;
  string canonical_name = 4;
  string normalized_key = 5;
  string folded_key = 6;
  string method = 7;
  repeated Candidate candidates = 8;
}

message ResolveQualificationsRequest {
  repeated RawQualification items = 1;
  // Пусто — текущая версия. Для запуска скоринга версия фиксируется.
  string as_of_resolver_version = 2;
}

message ResolveQualificationsResponse {
  string resolver_version = 1;
  repeated ResolvedQualification items = 2;
}
```

`method` использует те же значения, что у навыков:
`ALIAS_EXACT`, `ALIAS_FOLDED`, `ALIAS_LEMMA`, `ALIAS_LEMMA_FOLDED`, `NEW`.

Ответ `GetVersion` расширяется полями `qualification_version` и
`qualification_resolver_version`.

### 6.1. Отношения навыков — следующий этап

Похожесть `Kafka`/`RabbitMQ` не входит в v1 квалификаций, но её нужно
заложить в ту же модель, чтобы не менять контракт `Resolve`.

Данные:

```sql
CREATE TABLE dictionary.skill_relation (
    left_skill_id   uuid NOT NULL REFERENCES dictionary.skill (id),
    right_skill_id  uuid NOT NULL REFERENCES dictionary.skill (id),
    relation_kind   text NOT NULL,
    source          text NOT NULL DEFAULT 'HR',
    reviewed        boolean NOT NULL DEFAULT false,
    created_at      timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (left_skill_id, right_skill_id),
    CHECK (left_skill_id <> right_skill_id)
);
```

Для симметричных связей приложение хранит пару в каноническом порядке
(`left_skill_id < right_skill_id`). Асимметричные `PARENT`/`CHILD` хранятся
явно и проверяются при записи.

Контракт:

```proto
enum RelationKind {
  RELATION_KIND_UNSPECIFIED = 0;
  RELATION_KIND_SAME_GROUP = 1;
  RELATION_KIND_PARENT = 2;
  RELATION_KIND_CHILD = 3;
  RELATION_KIND_RELATED = 4;
}

message SkillPair {
  string left_skill_id = 1;
  string right_skill_id = 2;
}

message SkillRelation {
  string left_skill_id = 1;
  string right_skill_id = 2;
  RelationKind kind = 3;
}

message GetRelationsRequest {
  repeated SkillPair pairs = 1;
  string as_of_resolver_version = 2;
}

message GetRelationsResponse {
  string resolver_version = 1;
  repeated SkillRelation relations = 2;
}
```

Порядок использования:

1. `Resolve` даёт канонические `skill_id`.
2. Точное совпадение `skill_id` или ключа → `S = 1`.
3. Для оставшихся пар `scoring-service` делает один batch `GetRelations`
   только по кандидатам.
4. `RELATED`/`SAME_GROUP`/`PARENT`/`CHILD` → класс, затем `S` по версии
   `rfc-scoring-model.md`.
5. Пока значения `S` для классов не зафиксированы, словарь возвращает класс,
   но `S` остаётся `0`, а класс сохраняется только в трассе/объяснении.

Такой подход сохраняет главное: **граф связей принадлежит словарю,
а числа формулы — `scoring-service` и RFC**.

## 7. Матчинг

### 7.1. Навыки

Словарь навыков теперь является источником канонических ключей и будущих
отношений похожести. Для v1:

- `Resolve` вызывается один раз на запуск;
- сравнение — по `skill_id` или точному ключу;
- при недоступности словаря — `EXACT_FALLBACK`;
- похожесть `Kafka`/`RabbitMQ` подключается отдельным этапом через
  `GetRelations` и классы отношений, а не через число из словаря.

Классы отношений должны маппиться в `S^{ij}` только в `scoring-service`
и только после фиксации значений в `rfc-scoring-model.md`.

### 7.2. Квалификации

Для квалификаций нет похожести. Сравнение бинарное:

- канонические `qualification_id` или ключи совпали → `FULL`,
  `relevance = coverage = 1`;
- не совпали → `NOT_STATED`, точка `0`, верхняя граница `1`;
- `t_i = 1`, `τ_i = 1`;
- вес берётся из критичности требования, отдельного cap нет.

`label` может быть нормализованной формой и не быть подстрокой `quote`.
Сама `quote` по-прежнему обязана быть дословной подстрокой обезличенного
текста.

### 7.3. Кто вызывает словарь

Рекомендуемый вариант:

- `scoring-service` вызывает `Resolve` и `ResolveQualifications` в рамках
  `RequirementMatcher.prepare`;
- Java продолжает хранить `qualification`-флаг и label в шаблоне вакансии;
- текущий `QualificationCatalog` остаётся fallback-снимком на переходный
  период;
- после стабилизации словаря Java перестаёт передавать полный каталог
  в каждом запросе, но поле контракта можно оставить на несколько релизов.

Это сохраняет `scoring-service` stateless: он не читает БД, а только вызывает
внешний сервис.

## 8. Хранение в вакансии

В v1 можно оставить текущую схему:

```text
vacancy_requirement.qualification boolean
vacancy_requirement.skill_text    -- label квалификации
```

Это минимальное изменение: матчинг получает label и разрешает его через
словарь.

Целевое расширение:

```sql
ALTER TABLE vacancy_requirement
    ADD COLUMN qualification_id uuid;
```

- `qualification_id` — nullable, зеркало ID из `dictionary-service`;
- для старых шаблонов остаётся только label;
- для новых и подтверждённых шаблонов ID заполняется после разрешения;
- `skill_text` остаётся снимком исходного/канонического label, чтобы шаблон
  не зависел от переименования в словаре.

Решение о добавлении `qualification_id` можно принять отдельно после
админки и миграции; для v1 он не обязателен.

## 9. Миграции и seed

Нумерация должна учитывать текущий YAML-changelog:

- `020-qualification-dictionary-schema.sql`;
- `021-qualification-dictionary-seed.sql`.

Include добавляются в `migrations/db.changelog-master.yaml` после
`019-qualification-open-vocabulary.sql`.

Seed v1 — только демонстрационный набор:

- группы электробезопасности I–IV;
- водительские права B/C (и алиасы `права категории B`,
  `водительские права категории B`);
- аттестат профессионального бухгалтера;
- несколько аттестатов/допусков для демо.

Seed не является закрытым перечнем. Новые квалификации добавляются через
админку или импорт.

## 10. Версионирование и деградация

- `score_run` хранит `resolver_version` и `dictionary_mode`;
- повторный расчёт без LLM передаёт сохранённый `as_of_resolver_version`;
- при недоступности словаря скоринг не падает:
  - навыки — `EXACT_FALLBACK`;
  - квалификации — `QualificationCatalog` или точное совпадение label;
- результат явно помечается `dictionary_mode=EXACT_FALLBACK`;
- старые запуски не пересчитываются.

## 11. Альтернативы

| Вариант | Решение |
|---|---|
| Графовая БД (Neo4j/RDF) сейчас | Отложено. Нет текущих запросов, которые требуют обхода графа; Postgres проще в эксплуатации. Модель оставляем граф-совместимой. |
| Отдельный `qualification-dictionary-service` | Отложено. Дублирует инфраструктуру, админку и транспорт. Отдельные таблицы в существующем сервисе дают почти ту же изоляцию. |
| Хранить квалификации в `dictionary.skill` с `skill_type=OTHER` | Отклонено. Смешивает семантику, очередь ревью и статистику. |
| Оставить только `QualificationCatalog` в Java | Допустимо как временный fallback, но не как целевое владение: нет общей версии, админки и доступа из Python. |
| Универсальная таблица `term` с `entity_type` | Отклонено для v1. Экономит немного кода, но усложняет валидацию и повышает риск снова смешать сущности. |

## 12. Риски

- **Синонимы квалификаций плохо курируются.** «Допуск на высоту» и
  «удостоверение верхолазных работ» могут означать одно и то же; без алиасов
  они не совпадут. Нужна очередь ревью.
- **Ложное объединение.** Нельзя автоматически сводить `B` и `C`, `I` и `II`
  или разные виды допусков. Это разные бинарные факты.
- **Лемматизация.** Для некоторых формулировок лемма меняет смысл; нужен
  `RAW_ONLY` и вычитка.
- **Версии.** Изменение только квалификаций не должно менять версию навыков;
  композитный `resolver_version` должен это учитывать.
- **Приватность.** Медицинские книжки, допуски и иные документы могут быть
  чувствительными. Словарь хранит только label и обезличенные примеры,
  без диагнозов, номеров документов и PII.
- **Переходный период.** Пока Java передаёт `QualificationCatalog`, возможна
  рассинхронизация с dictionary-service. Нужен явный `dictionary_mode` и
  версия.

## 13. Открытые вопросы

1. Добавлять ли `vacancy_requirement.qualification_id` сразу или после
   стабилизации админки?
2. Нужна ли отдельная очередь `qualification_review_item` в v1 или можно
   отложить до появления админки?
3. Использовать ли лемматизацию для квалификаций по умолчанию или только
   `RAW`-ключи с алиасами?
4. Включать ли `qualification_version` в тот же `resolver_version`, что и
   навыки, или выдавать отдельный `qualification_resolver_version`?
5. Когда переходить на графовую БД: после появления иерархии/похожести
   квалификаций или только для навыков?

## 14. Критерии готовности

- `ResolveQualifications` возвращает `qualification_id` и канонический ключ
  для алиасов и `NEW` для неизвестных label.
- «3 группа по электробезопасности» и «III группа по электробезопасности»
  сводятся к одной квалификации через алиасы.
- «права категории B» и «водительские права категории B» сводятся к одной
  квалификации; B и C не совпадают.
- Квалификация в резюме и вакансии сравнивается бинарно; отсутствие даёт
  `NOT_STATED`, а не `ABSENT`.
- При недоступном словаре скоринг продолжает работать через
  `QualificationCatalog` или точный ключ.
- `score_run` хранит версию словаря и режим деградации.
- Цитата остаётся дословной подстрокой текста; label может быть нормализован.
- В логах нет PII, текстов резюме, цитат и номеров документов.
