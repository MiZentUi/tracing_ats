# Отчёт: завершение gRPC-контракта `scoring-service`

| | |
|---|---|
| **Дата** | 2026-11-12 |
| **Ветка** | `feature/scoring-service` |
| **Контур** | `contracts/proto/scoring_service/`, `docs/` |
| **Статус** | контракт обновлён; Python-код и OpenAPI не менялись |

## 1. Что сделано

### 1.1. Контракт

- `ExtractResume` — извлечение `ParsedResume` без вакансии и без скоринга.
  В запрос добавлен `reference_date`, в ответ — `warnings` и `dropped`.
- `EvaluateResumeMatch` — оценка уже извлечённого резюме по одной вакансии.
  LLM не вызывается: используются `ParsedResume` и исходный обезличенный текст.
- `EvaluateResumeMatches` — пакетная оценка одного резюме по нескольким
  вакансиям. Резюме разбирается один раз, затем оценивается по каждой вакансии.
  Ошибка по одной вакансии не отменяет остальные результаты.
- `FeatureRequirement` получил `TextRequirement` отдельной веткой `oneof`.
  `source` остаётся на верхнем уровне требования.
- `gaps` заменены на `repeated Gap`, где `Gap` содержит `requirement_id` и
  `GapReason`. Детали пробела берутся из `matches[requirement_id]`.
- `FeatureMatch` содержит `status`, `relevance`, `coverage`,
  `resume_feature_key`, `evidence`, `comment` и `years_found`.
- `GeneralResumeScore` вынесен отдельным сообщением и используется в одиночной
  и пакетной оценке.
- `Correspondence` использует `CORRESPONDENCE_NOT_STATED` для статуса
  «в резюме нет сведений».

### 1.2. Документация

- `docs/rfc/rfc-feature-extraction.md` обновлён: зафиксированы новые RPC,
  форма `FeatureMatch`/`Gap`, решения по `TextRequirement` и batch-обработке.
- `hackathon-strategy.md` и `task-split-java-python.md` перенесены в `docs/`.
- Добавлен этот отчёт.

## 2. Принятые решения

1. **`TextRequirement` — отдельная ветка `oneof`.** Верхнеуровневый `category`
   убран, чтобы не было двух источников истины: тип требования определяется
   вариантом `oneof`, а `category` остаётся только у текстовых требований
   (`SOFT`/`OTHER`).
2. **`source` остаётся.** Это не тип фичи, а происхождение требования:
   `REQUIREMENT` или `DUTY`. Если Java начнёт передавать готовый вес, `source`
   можно заменить на `weight`.
3. **`gaps` нормализованы.** `Gap { requirement_id, reason }` ссылается на
   `matches`; статус, цитата, комментарий и стаж не дублируются.
4. **`risks`, `questions`, `recruiter_note` остаются строками.** Для текущего UI
   этого достаточно; структурировать риски имеет смысл, когда появится
   фильтрация по типу риска.
5. **Batch-RPC не вызывает LLM.** Один `ExtractResume`, затем
   `EvaluateResumeMatches` по многим вакансиям. Это соответствует инварианту
   «повторный расчёт без LLM».
6. **Ошибки пакета изолированы.** `EvaluateResumeMatchResult` — `oneof` из
   `match` и `error`, поэтому одна упавшая вакансия не скрывает остальные.

## 3. Проверки

- `grpc_tools.protoc` с `--descriptor_set_out` — успешно.
- По descriptor-set проверены методы:
  - `ScoreResume`
  - `EvaluateResumeMatch`
  - `EvaluateResumeMatches`
  - `ExtractResume`
- `clang-format --dry-run --Werror` — без замечаний.
- `git diff --check` — без ошибок.

`pytest` не запускался: контракт изменён без адаптации Python-кода. После
регенерации stubs текущая реализация потребует правок.

## 4. Последствия для кода

- `ParsedResume.skills`/`education`/`experience` теперь `map`, а не `repeated`.
  Текущий `_to_parsed_resume` использует `append` и требует переписывания.
- `ScoreResumeResponse` теперь содержит `general_score`; поля `relevance`,
  `score_low`, `score_high`, `score`, `refine` на верхнем уровне удалены.
- `EvaluateResumeMatch` и `EvaluateResumeMatches` ещё не реализованы в
  `ScoringService`; после генерации stubs сервер будет возвращать
  `UNIMPLEMENTED` для этих методов, пока код не добавлен.
- `CORRESPONDENCE_NOT_PRESENT` переименован в
  `CORRESPONDENCE_NOT_STATED`; клиенты и тесты нужно обновить.
- Сгенерированные protobuf/gRPC-модули не перегенерированы.

## 5. Открытые вопросы

- `BatchItemError.code` сейчас строка. Если нужен строгий контракт ошибок,
  заменить на enum или `google.rpc.Status`.
- `ScoreResumeResponse.features` всё ещё использует `FeatureAssessment` и не
  отдаёт `status`/`evidence`/`comment`. Решить: мигрировать legacy-вызов на
  `FeatureMatch` или оставить его одношаговым упрощённым путём.
- В контракте нет `extraction_meta` (провайдер, модель, версия промпта и
  словаря). Для воспроизводимости запусков это стоит добавить отдельным
  изменением.
- `FeatureCriticality` пока не связан с `kind` (`MUST`/`NICE`/`OPTIONAL`).
  Маппинг нужно зафиксировать в контракте или ADR.
- `source` можно заменить на `weight`, если Java возьмёт расчёт веса на себя.

## 6. Границы изменения

В коммит входят только `docs/` и `contracts/proto/scoring_service/`.
`contracts/openapi.yaml`, Python-код, `TASK.md`, uv workspace и остальные
файлы не менялись.
