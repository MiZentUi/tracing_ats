# scoring-service

Python 3.14, `uv`, gRPC, Pydantic. Онлайн-скоринг: разбор обезличенного резюме через LLM и детерминированный расчёт модели.

Сервис stateless: не читает файлы, не ходит в PostgreSQL, MinIO, hh, GitHub и не получает PII. LLM вызывается только с обезличенным текстом резюме.

## Текущее состояние

Реализованы четыре RPC:

| RPC | Вход | Выход | LLM |
|---|---|---|---|
| `ExtractResume` | обезличенный текст, `reference_date` | `ParsedResume`, `dropped`, `warnings` | да |
| `EvaluateResumeMatch` | `ParsedResume`, исходный текст, одна вакансия | `GeneralResumeScore`, `FeatureMatch` по требованиям, `Gap`, объяснения | нет |
| `EvaluateResumeMatches` | `ParsedResume`, исходный текст, набор вакансий | результаты по каждой вакансии; ошибка одной не отменяет остальные | нет |
| `ScoreResume` | текст + вакансия | `GeneralResumeScore` + `FeatureAssessment`; legacy-путь | да |

`ExtractResume` и `ScoreResume` вызывают один и тот же экстрактор. `EvaluateResumeMatch` и `EvaluateResumeMatches` работают по уже разобранному резюме и LLM не вызывают.

## Извлечение признаков

Промпт — `src/api/prompts/resume_extraction.py`, версия `resume-extraction.v4`, схема `ParsedResume` `schema_version = 3`.

Извлекаются:

- навыки: `name`, `years`, `quote`;
- квалификации: `label`, `quote`;
- образование: `institution`, `field`, `level`, `graduation_year`, `quote`;
- опыт: `company`, `position`, `category`, `begin_year`, `end_year`, `quote`, `metrics`.

Каждая цитата проверяется как подстрока текста после схлопывания пробелов. Признаки с невалидной цитатой отбрасываются и считаются в `dropped`. Ответ модели валидируется Pydantic; невалидный JSON — один повтор, затем ошибка, а не «0 баллов».

## Скоринг v0

### `S^{ij}`

- **Навык:** точное совпадение `skill_id` или нормализованного ключа — `1.0`; класс связи словаря `SAME_GROUP`/`RELATED`/`PARENT`/`CHILD` — `0.5`; иначе `0.0`. `DeterministicSimilarity` для навыков не используется.
- **Квалификация:** бинарно; совпадение канонического ключа — `1.0`, иначе `NOT_STATED`.
- **Образование:** `0.7 · сходство направления + 0.3 · соответствие уровня`.
- **Опыт:** сходство категории.
- **Текст:** доля значимых токенов требования в лучшей строке резюме.

`R_i` — лучшее сходство по проверенным признакам. `Rv` — взвешенное среднее `R_i`.

### `t_i`, `τ_i`, `c_i`

- `t_i` для навыков — regex-эвристика: результат/метрика `0.8`, обязанность `0.5`, остальное `0.3`. Регулярное выражение результата реагирует на любую цифру, поэтому «Python — 3 года» может получить `0.8`.
- `τ_i = 0.5·(1+tanh(years/required−1)) · 2^(−recency/2)`, `A=B=1`, `T½=2`. Неизвестные стаж и давность расширяют вилку.
- `recency` матчер поддерживает, но экстрактор его не заполняет: в обычном `ExtractResume`/`ScoreResume` давность всегда неизвестна.
- `c_i`: навык — `S·t·τ`, опыт — `S·τ`, образование/текст — `S`, квалификация — `1.0`.

### `Wv` и `Score`

`Wv^i` выводится из критичности:

| Criticality | Wv |
|---|---:|
| `OPTIONAL` | 0.25 |
| `NICE` | 0.5 |
| `REQUIRED` | 0.75 |
| `CRITICAL` | 1.0 |
| `UNSPECIFIED` | 0.5 |

`Score` — взвешенное покрытие `Σ Wv^i c_i / Σ Wv^i`. В текущей v0 `F = 0`, `𝓜 = 1`, внешних `q` и `λ` нет, поэтому `Score = R'`. Границы `Score_l`/`Score_h` получаются подстановкой граничных `τ`; для `NOT_STATED` верхняя граница `1.0`.

Критичность не вводит отдельный cap. `critical` в требованиях Java всегда `false`, поэтому `CRITICAL` на практике не используется.

### Статусы

- `FULL`/`COVERED` — подтверждено;
- `PARTIAL` — частично или недостаточно стажа;
- `ABSENT`/`GAP` — есть близкий опыт, но требование не подтверждено;
- `NOT_STATED` — в резюме нет сведений; это не «навыка нет».

Пороги: `0.85`/`0.5` для структурированных признаков, `0.75`/`0.35` для текстовых.

## Словарь и квалификации

- `SkillResolver` делает один batch `Resolve` на запуск.
- Сравнение навыков — по `skill_id` или точному ключу; нечёткого матчинга нет.
- `GetRelations` даёт классы связей; значения `S^{ij}` для классов — `0.5`.
- `ResolveQualifications` предпочтителен; fallback — `QualificationCatalog` из запроса или точный ключ.
- При недоступности словаря: `dictionary_mode=EXACT_FALLBACK`, `resolver_version=local-exact-v1`.
- `resolver_version` и `dictionary_mode` возвращаются в ответе, но Java пока не сохраняет их в `score_run`.

## Детерминированные объяснения

`src/api/scoring/feedback.py` собирает `strengths`, `risks`, `questions`, `recruiter_note` по уже посчитанным `RequirementScore`. LLM не вызывается; `EvaluateResumeMatch` и `EvaluateResumeMatches` остаются детерминированными.

- Каталог `feedback-templates.v2`: 32 шаблона — 6 сильных сторон, 8 рисков, 10 вопросов, 6 элементов рекомендации, 2 fallback.
- Вариант шаблона выбирается стабильным хешем `fact_id`.
- `NOT_STATED` формулируется как «в резюме не указано», `ABSENT` — как «есть близкий опыт, но требование не подтверждено».
- Лимиты: 5 сильных сторон, 5 рисков, 5 вопросов, 500 символов на `recruiter_note`.
- `gaps` остаются структурированными; отдельного `weaknesses` в v1 нет.

Правила — [`docs/rfc/rfc-feedback-generation.md`](../docs/rfc/rfc-feedback-generation.md).

## LLM

- Клиент — `src/api/llm/client.py` на официальном SDK `openai`; `base_url` настраивается, подходит OpenRouter и локальные OpenAI-совместимые серверы.
- Провайдер по умолчанию — `mock` (детерминированный, без сети). Реальный включается `SCORING_SERVICE_LLM_PROVIDER=openai`.
- Повторы на 429/5xx и сетевые ошибки делает SDK (`max_retries`); один повтор на невалидный JSON/схему — на уровне экстрактора.
- В LLM уходит только обезличенный текст резюме. Тексты, цитаты и ответы модели не логируются; логируются метаданные: модель, задержка, статус, `request_id`, токены, `finish_reason`.
- Ошибки: `INVALID_ARGUMENT` — пустой текст, `UNAVAILABLE` — LLM недоступна, `INTERNAL` — ответ не прошёл схему после повтора.

## Конфигурация

Префикс `SCORING_SERVICE_`.

| Переменная | По умолчанию | Назначение |
|---|---|---|
| `SCORING_SERVICE_GRPC_HOST` | `0.0.0.0` | Адрес gRPC |
| `SCORING_SERVICE_GRPC_PORT` | `8082` | Порт gRPC |
| `SCORING_SERVICE_LOG_LEVEL` | `INFO` | Логирование |
| `SCORING_SERVICE_MAX_WORKERS` | `10` | Пул gRPC |
| `SCORING_SERVICE_LLM_PROVIDER` | `mock` | `mock` или `openai` |
| `SCORING_SERVICE_LLM_BASE_URL` | — | Обязателен для `openai` |
| `SCORING_SERVICE_LLM_API_KEY` | — | Не логируется |
| `SCORING_SERVICE_LLM_MODEL` | — | Обязателен для `openai` |
| `SCORING_SERVICE_LLM_TIMEOUT_SECONDS` | `30` | Таймаут запроса |
| `SCORING_SERVICE_LLM_MAX_RETRIES` | `1` | Повторы SDK |
| `SCORING_SERVICE_LLM_MAX_OUTPUT_TOKENS` | `4096` | Лимит ответа |
| `SCORING_SERVICE_DICTIONARY_ENABLED` | `true` | Ходить в словарь |
| `SCORING_SERVICE_DICTIONARY_TARGET` | `localhost:8084` | Адрес словаря |
| `SCORING_SERVICE_DICTIONARY_DEADLINE_SECONDS` | `2` | Таймаут словаря |
| `SCORING_SERVICE_DICTIONARY_AS_OF_VERSION` | — | Зафиксировать версию словаря |
| `SCORING_SERVICE_IGNORE_INVALID_QUOTES` | `false` | Заявлен, но в обычном пути не отключает проверку |

`SCORING_SERVICE_PORT` — это compose-переменная для публикации порта, а не настройка сервиса.

## Логирование

- gRPC-интерсептор пишет `grpc request start` и `grpc request end` с методом, статусом и `elapsed_ms`.
- Тела запросов и ответов не логируются.
- При ошибке пишется класс исключения, но не его сообщение.

## Проверки

```bash
uv sync --locked
uv run sh protoc.sh
uv run pytest -q
uv run ruff check .
uv run ruff format --check .
```

CI перегенерирует protobuf-модули через `protoc.sh` и проверяет diff. Сгенерированные модули не коммитятся целиком; `src/scoring_service/` и `src/dictionary_service/v1/` игнорируются, кроме отдельных файлов.

## Docker

Из корня репозитория:

```bash
docker compose up --build scoring-service
```

Образ собирается на `python:3.14-slim`, зависимости ставятся из `uv.lock` через `uv sync --locked`; protobuf-модули генерируются на этапе сборки.

## Структура

```text
scoring-service/
├── pyproject.toml
├── uv.lock
├── Dockerfile
├── protoc.sh
├── src/api/
│   ├── config.py
│   ├── server.py
│   ├── extraction/           # схема, нормализация, проверка цитат
│   ├── llm/                  # OpenAI-совместимый клиент и mock
│   ├── prompts/              # промпт resume-extraction.v4
│   ├── scoring/              # gRPC, matcher, policies, calculator, feedback
│   ├── dictionary/           # клиент dictionary-service
│   └── healthcheck.py
├── src/scoring_service/v1/   # сгенерированные protobuf/gRPC-модули
├── src/dictionary_service/v1/
└── tests/
```

## Ограничения

- `F = 0`, `𝓜 = 1`; внешних `q`, `λ` и `Refine` нет.
- `recency` не извлекается; общий `τ_total` не считается.
- `t` реагирует на цифры; уровень образования сравнивается по ordinal.
- `score_run` не хранит `resolver_version`/`dictionary_mode`.
- `ignore_invalid_quotes` не действует в обычном `build_scoring_service`.
- `ScoreResume` не возвращает `parsed` и не отдаёт объяснения.
- Нет golden-фикстур для полного примера `Score_l`/`Score_h`.

Подробнее — [`../docs/known-issues.md`](../docs/known-issues.md).
