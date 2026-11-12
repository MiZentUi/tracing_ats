# mock-scoring-service

Детерминированная заглушка контракта `scoring_service.v1.ScoringService`.
Нужна, пока модель скоринга разрабатывают отдельно: `ats-service` и frontend
могут ходить по настоящему gRPC-контракту, не ожидая ML-часть.

Заглушка **не реализует модель скоринга**. Числа синтетические, но правило
одно и то же для любого входа: один и тот же запрос даёт один и тот же ответ.

## Что реализовано

Контракт — `contracts/proto/scoring_service/v1/scoring.proto`.

| RPC | Поведение заглушки |
|---|---|
| `ExtractResume` | Извлекает навыки, образование и опыт по фиксированному словарю и шаблонам дат. Каждая цитата — точный срез переданного текста. |
| `EvaluateResumeMatch` | Сопоставляет `ParsedResume` клиента с требованиями вакансии, возвращает `matches`, `gaps`, `strengths`, `risks`, `questions`, `recruiter_note`. |
| `EvaluateResumeMatches` | То же для нескольких вакансий. Ошибка одной вакансии возвращается в `BatchItemError` и не отменяет остальные. |
| `ScoreResume` | `ExtractResume` + `EvaluateResumeMatch` за один вызов (legacy-путь). |

## Правила сопоставления

- **Навык.** Совпадение по нормализованному названию. Если требование задаёт
  стаж, найденный стаж сравнивается с требуемым: меньше — `PARTIAL` и
  `GAP_REASON_INSUFFICIENT_TENURE`, не указан — `PARTIAL`.
- **Явное отсутствие.** Формулировки «не работал с …», «без опыта …»,
  «нет опыта …» дают `ABSENT` и `GAP_REASON_ABSENT`.
- **Образование.** Сравнивается уровень; ниже требуемого — `PARTIAL`.
- **Опыт.** Суммарный стаж по диапазонам дат; меньше требуемого — `PARTIAL`.
- **Текстовое требование.** Ищется значимое слово из формулировки; найдено —
  `PARTIAL` с цитатой, иначе `NOT_STATED`.
- **Нет сведений.** Всё, что не подтверждено и не опровергнуто, — `NOT_STATED`,
  а не «отсутствует» (ТЗ, критерий «отсутствие сведений ≠ отсутствие навыка»).

`Score` считается как взвешенное покрытие требований: вес задаёт
`FeatureCriticality` (`OPTIONAL`/`UNSPECIFIED` = 1, `NICE` = 2, `REQUIRED` = 3,
`CRITICAL` = 4). Вилка — `score ± 0.05` в шкале `0..1`. Это заглушка, а не
формула из `docs/rfc/rfc-scoring-model.md`.

## Ограничения

- Числа нельзя использовать для реального ранжирования и решений о найме.
- Месяц и день в `google.type.Date` всегда `1`: из текста извлекается только год.
- Словарь навыков фиксированный (`src/mock_scoring/dictionary.py`), без
  `skill_alias` из БД: сервис stateless.
- В `EducationPerResume` и `PriorExperiencePerResume` не выдумываются вуз,
  компания и должность: подтверждение передаётся только в `evidence`.
- LLM, БД, файлы и внешние API не используются. PII не поступает и не логируется.

## Запуск

В составе compose (профиль `mock-scoring`, порт хоста `8083`):

```bash
SCORING_GRPC_TARGET=mock-scoring:8082 \
  docker compose --profile mock-scoring up --build
```

Отдельно:

```bash
cd infra/mock-scoring-service
uv sync
uv run sh protoc.sh
uv run mock-scoring-service
```

Проверка:

```bash
cd infra/mock-scoring-service
uv run pytest -q
uv run ruff check .
uv run ruff format --check .
```

## Переменные окружения

| Переменная | По умолчанию | Назначение |
|---|---|---|
| `MOCK_SCORING_GRPC_HOST` | `0.0.0.0` | адрес прослушивания |
| `MOCK_SCORING_GRPC_PORT` | `8082` | порт gRPC |
| `MOCK_SCORING_LOG_LEVEL` | `INFO` | уровень логирования |
| `MOCK_SCORING_MAX_WORKERS` | `10` | размер пула потоков |
| `MOCK_SCORING_SLOW_SECONDS` | `1` | задержка сценария `slow` |

## Сценарии для тестов

Маркер в тексте резюме или metadata вызывающего:

```text
MOCK_SCORING_SCENARIO: unavailable
```

| Сценарий | Результат |
|---|---|
| `unavailable` | `UNAVAILABLE` |
| `invalid-argument` | `INVALID_ARGUMENT` |
| `internal` | `INTERNAL` |
| `slow` | задержка `MOCK_SCORING_SLOW_SECONDS` |
| `empty` | пустой `ParsedResume` без признаков |

Сценарий также читается из metadata `x-mock-scenario`. Для пакетного вызова
маркер `MOCK_VACANCY_ERROR` в `Vacancy.text_description` возвращает
`BatchItemError` только по этой вакансии.

## Логирование

- `inbound request received` / `inbound response sent` / `inbound request failed`
  — из gRPC-интерсептора, с `method`, `status`, `request_id`, `elapsed_ms`.
- `extract_resume_completed`, `score_resume_completed`,
  `evaluate_resume_match_completed`, `evaluate_resume_matches_completed` —
  бизнес-итоги со счётчиками.
- `request_id` читается из metadata `x-request-id`; если его нет — `-`.
- Текст резюме, цитаты, названия требований и тела сообщений не логируются.
- Health-check и reflection не логируются на `INFO`.
