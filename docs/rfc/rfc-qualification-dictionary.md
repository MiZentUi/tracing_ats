| | |
|---|---|
| **Статус** | Реализовано v1; открытые вопросы ниже не блокируют v1 |
| **Автор** | DeepSeek |
| **Дата** | 2026-11-13 |
| **Контур** | `dictionary-service`, `ats-service`, `scoring-service` |
| **Ветка** | `main` |
| **Модуль** | справочник квалификаций и отношений навыков |
| **Связанные документы** | `rfc-skill-dictionary.md`, `rfc-feature-extraction.md`, `rfc-scoring-model.md` |

# RFC: квалификации и отношения похожести в dictionary-service

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
- новый словарь навыков (`dictionary-service`) пока не знает о квалификациях;
- у словаря нет отношений похожести (`Kafka`/`RabbitMQ`), поэтому `S^{ij}`
  для близких, но не равных навыков остаётся `0`.

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
dictionary.skill_relation
```

Квалификации не складываются в `dictionary.skill` и не получают
`skill_type = OTHER`. Это разные сущности с разной семантикой, разными
очередями ревью и разными правилами матчинга.

Отношения похожести навыков входят в схему v1: `skill_relation` хранит
типизированные связи `SAME_GROUP`, `PARENT`, `CHILD`, `RELATED`. Словарь
возвращает класс связи, а не число `S^{ij}`; числа фиксируются в
`rfc-scoring-model.md`.

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
- таблица `skill_relation` и RPC `GetRelations`;
- классы отношений `SAME_GROUP`, `PARENT`, `CHILD`, `RELATED`;
- использование отношений в матчинге навыков через `scoring-service`;
- fallback на текущий `QualificationCatalog` при недоступности словаря;
- версия словаря в запуске скоринга;
- seed для демонстрационных квалификаций и отношений.

### Не входит в v1

- графовая БД, Neo4j, RDF, SPARQL;
- иерархия и похожесть квалификаций;
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

Отношения навыков в v1 хранятся в обычной таблице `skill_relation`.
Это уже даёт `Kafka`/`RabbitMQ` без графовой БД; переход на Neo4j/RDF
понадобится только для сложных обходов и запросов, а не для самих связей.

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

### 5.5. `dictionary.skill_relation`

Отношения похожести входят в схему v1 вместе с квалификациями.

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

Правила:

- для симметричных связей пара хранится в каноническом порядке
  (`left_skill_id < right_skill_id`);
- `PARENT`/`CHILD` хранятся явно и проверяются при записи;
- `relation_kind` принимает значения `SAME_GROUP`, `PARENT`, `CHILD`,
  `RELATED`;
- связь не заменяет алиас: `Kafka` и `RabbitMQ` остаются разными навыками.

Группы (`skill_group`, `skill_group_member`) можно добавить позже, если
понадобится хранить группы явно. Для v1 достаточно `SAME_GROUP`-пар.

### 5.6. Версия

В `dictionary.dictionary_meta` добавляются отдельные счётчики:

```sql
ALTER TABLE dictionary.dictionary_meta
    ADD COLUMN qualification_version bigint NOT NULL DEFAULT 1,
    ADD COLUMN relation_version bigint NOT NULL DEFAULT 1;
```

Композитный `resolver_version` расширяется:

```text
d<skills_version>-q<qualification_version>-r<relation_version>-...-h<homoglyph_rules_version>
```

Любое изменение `qualification`/`qualification_alias` увеличивает
`qualification_version`, изменение `skill_relation` — `relation_version`.
Это позволяет не инвалидировать навыки при изменении только квалификаций
или только связей.

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
  rpc DeleteQualification(DeleteQualificationRequest)
      returns (google.protobuf.Empty);
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

Ответ `GetVersion` расширяется полями `qualification_version`,
`relation_version`, `qualification_resolver_version` и
`relation_resolver_version`.

### 6.1. Отношения навыков (v1)

Отношения `Kafka`/`RabbitMQ` входят в v1. Таблица описана в разделе 5.5.
Словарь возвращает типизированную связь, а не число похожести.

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
5. Если словарь недоступен, отношения не применяются: `EXACT_FALLBACK`,
   сравниваются только точные ключи.

Значения `S` для классов — часть v1: они фиксируются в
`rfc-scoring-model.md` до включения отношений в расчёт. Словарь не возвращает
числа и не знает про формулу.

Такой подход сохраняет главное: **граф связей принадлежит словарю,
а числа формулы — `scoring-service` и RFC**.

## 7. Матчинг

### 7.1. Навыки

Словарь навыков — источник канонических ключей и отношений похожести.
Для v1:

- `Resolve` вызывается один раз на запуск;
- сравнение — по `skill_id` или точному ключу;
- для несовпавших пар вызывается `GetRelations` (один batch на запуск);
- классы `SAME_GROUP`/`PARENT`/`CHILD`/`RELATED` маппятся в `S^{ij}`
  в `scoring-service` по значениям из `rfc-scoring-model.md`;
- при недоступности словаря — `EXACT_FALLBACK`, отношения не применяются;
- `Kafka`/`RabbitMQ` не становятся одним навыком: они остаются разными
  `skill_id` со связью `RELATED`/`SAME_GROUP`.

Числа `S` для классов не хранятся в словаре и не приходят из него.

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

- `023-dictionary-qualifications-and-relations.sql` — `qualification`,
  `qualification_alias`, `qualification_alias_key`,
  `qualification_review_item`, `skill_relation`, счётчики версий.

Include добавляется в `migrations/db.changelog-master.yaml` после
`022-skill-generation.sql`. Демонстрационные квалификации и связи
загружаются `dictionary-service` при старте, если соответствующие таблицы пусты.

Seed v1 — только демонстрационный набор:

- группы электробезопасности I–IV;
- водительские права B/C (и алиасы `права категории B`,
  `водительские права категории B`);
- аттестат профессионального бухгалтера;
- несколько аттестатов/допусков для демо;
- связи `Kafka ↔ RabbitMQ` (`RELATED`), `Docker ↔ Podman` (`SAME_GROUP`),
  `PostgreSQL ↔ MySQL` (`SAME_GROUP`).

Seed не является закрытым перечнем. Новые квалификации и связи добавляются
через админку или импорт.

## 10. Версионирование и деградация

- `resolver_version` включает версии навыков, отношений и квалификаций;
- `EvaluateResumeMatch`/`EvaluateResumeMatches` возвращают `resolver_version` и
  `dictionary_mode`; Java пока **не сохраняет** их в `score_run` — это известное
  ограничение, см. [`../known-issues.md`](../known-issues.md);
- повторный расчёт без LLM передаёт сохранённый `as_of_resolver_version` (когда
  версия сохранена клиентом);
- при недоступности словаря скоринг не падает:
  - навыки — `EXACT_FALLBACK`, отношения не применяются;
  - квалификации — `QualificationCatalog` или точное совпадение label;
- результат явно помечается `dictionary_mode=EXACT_FALLBACK`;
- старые запуски не пересчитываются.

## 11. Альтернативы

| Вариант | Решение |
|---|---|
| Графовая БД (Neo4j/RDF) сейчас | Отложено. Отношения v1 хранятся в Postgres (`skill_relation`); сложные обходы графа пока не нужны. Модель оставляем граф-совместимой. |
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
- **Курирование связей.** `Kafka`/`RabbitMQ` — не синонимы; связь `RELATED`
  должна быть проверена, иначе она начнёт завышать `S^{ij}`.
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
4. Включать ли `qualification_version` и `relation_version` в тот же
   `resolver_version`, что и навыки, или выдавать отдельные версии?
5. Какие значения `S^{ij}` соответствуют `SAME_GROUP`, `PARENT`, `CHILD`,
   `RELATED`? Значения фиксируются в `rfc-scoring-model.md` до включения
   отношений в расчёт.
6. Когда переходить на графовую БД: после появления сложных обходов
   иерархии или только для навыков?

## 14. Критерии готовности

- `ResolveQualifications` возвращает `qualification_id` и канонический ключ
  для алиасов и `NEW` для неизвестных label.
- «3 группа по электробезопасности» и «III группа по электробезопасности»
  сводятся к одной квалификации через алиасы.
- «права категории B» и «водительские права категории B» сводятся к одной
  квалификации; B и C не совпадают.
- Квалификация в резюме и вакансии сравнивается бинарно; отсутствие даёт
  `NOT_STATED`, а не `ABSENT`.
- `GetRelations` возвращает `RELATED` для `Kafka`/`RabbitMQ` и
  `SAME_GROUP` для `Docker`/`Podman`; `scoring-service` применяет класс
  через значения `S`, зафиксированные в `rfc-scoring-model.md`.
- `GetRelations` не вызывается для точных совпадений и не раздувает
  `n × m`: пары батчатся и ограничиваются.
- При недоступном словаре скоринг продолжает работать через
  `QualificationCatalog` или точный ключ; отношения не применяются.
- gRPC-ответ содержит версию словаря и режим деградации; сохранение версии в
  `score_run` остаётся отдельной задачей (сейчас не реализовано).
- Цитата остаётся дословной подстрокой текста; label может быть нормализован.
- В логах нет PII, текстов резюме, цитат и номеров документов.
