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

`S^{ij}` считается детерминированно:

- для навыков точное совпадение `skill_id` или ключа даёт `1.0`; иначе
  version-aware matcher в `src/api/scoring/versioning.py` отделяет версию от
  базового имени и сравнивает базу (`Java 11/17` → `Java`, `C++17` → `C++`);
- хвост отрезается только если он состоит из версий; `Java EE`, `AngularJS`,
  `Spring Boot`, `Java developer` и `Java, Python` остаются разными именами;
- при несовпадении версий `VersionMatchPolicy` по умолчанию возвращает `1.0`;
  значение можно переопределить в коде, не меняя формулу;
- для образования сходство направления даёт 70%, соответствие уровня — 30%;
- для текстовых требований `S` — доля значимых токенов требования, найденных
  в тексте резюме; близость имён для образования и опыта считает
  `src/api/scoring/similarity.py`.

`R_i` — лучшее сходство по проверенным признакам резюме. `t_i` для навыков
выводится из цитаты секционными правилами: результат или метрика — `0.8`,
обязанность — `0.5`, остальное — `0.3`. Если тот же навык дополнительно найден
в проверенной цитате описания опыта или проекта, берётся
`t_i = max(t_i навыка, t_i опыта)`, а более сильная цитата становится
доказательством. `τ_i` — фактор стажа
`0.5·(1+tanh(years/required − 1))`; recency пока не учитывается и равен `1`.
Неизвестный стаж расширяет вилку, а не обнуляет требование. `c_i` для навыка —
`S·t·τ`, для опыта — `S·τ`, для образования и текста — `S`.

`Wv^i` пока выводится из критичности: `OPTIONAL 0.25`, `NICE 0.5`,
`REQUIRED 0.75`, `CRITICAL 1.0`, `UNSPECIFIED 0.5`. `R'` — взвешенное
покрытие `Σ Wv^i c_i / Σ Wv^i`. `Score` аддитивно складывает `R'`, домен и
общий стаж: `Score = 𝓜·R' + 𝓕·F + 𝒯·T_total`, где `𝓜 + 𝓕 + 𝒯 = 1`.
`T_total` — фактор соответствия общего стажа резюме `Vacancy.tenure`; при
`Vacancy.tenure = 0` слагаемое стажа отключено. Вес `𝒯` настраивается
`SCORING_SERVICE_TENURE_WEIGHT` (по умолчанию `0.1`). Для известных признаков
`Score_l`/`Score_h` получаются подстановкой граничных `τ` и `T_total`, для
`NOT_STATED` верхняя граница — `1.0`. Внешних источников в контракте нет,
поэтому `λ = 0`; `F = 0`, пока формула домена не зафиксирована.

Статус `FULL` требует не только найденного признака, но и достаточного
`coverage`: для навыка — `SCORING_SERVICE_SKILL_FULL_COVERAGE` (по умолчанию
`0.70`), для опыта — `0.85`, для образования — `0.85`, для текста — `0.75`.
Навык с точным названием, но слабой цитатой (`coverage = 0.3`) получает
`PARTIAL`, а не `FULL`. Если признак или проверенная цитата не найдены, ставится
`NOT_STATED` — «в резюме нет сведений», а не «навыка нет».

Значения `S` для нечётких совпадений, `t`, `Wv` и `F` остаются открытыми
вопросами `docs/rfc/rfc-scoring-model.md`; реализация вынесена в заменяемые
политики, поэтому их фиксация не требует правок сопоставления и арифметики.

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

- Каталог `feedback-templates.v4` содержит 60 шаблонов: 12 сильных сторон,
  14 рисков, 20 вопросов, 12 элементов рекомендации и 2 fallback.
- Факты выбираются по статусу, `gap_reason`, критичности, стажу, цитате и
  датам; шаблон только формулирует факт.
- Риски и вопросы дополняются контекстом требования или проверенной цитаты и
  следующим действием; `recruiter_note` перечисляет сильные стороны и
  ключевые темы для уточнения.
- Вариант шаблона выбирается стабильным хешем `fact_id`, поэтому один и тот же
  вход даёт один и тот же текст.
- `NOT_STATED` формулируется как «в резюме не указано», а не «нет навыка»;
  `ABSENT` — как «есть близкий опыт, но требование не подтверждено».
- Цитата в сильной стороне берётся только из проверенного `match.evidence`.
- `gaps` не меняются и остаются структурированными; отдельного `weaknesses`
  в v1 нет.
- Лимиты: 5 сильных сторон, 5 рисков, 5 вопросов, 500 символов на
  `recruiter_note`. Приоритеты и лимиты — политика отображения, не формула
  скоринга.

Правила — [`docs/rfc/rfc-feedback-generation.md`](../docs/rfc/rfc-feedback-generation.md).

## LLM

- Промпт и JSON-схема лежат в `src/api/prompts/resume_extraction.py`, версия
  промпта — `resume-extraction.v4`.
- Составные записи навыков разбиваются на отдельные: `Spring
  (Boot/Data/Security)` → `Spring`, `Spring Boot`, `Spring Data`, `Spring
  Security`; одна quote может подтверждать несколько навыков.
- Опытом считается только коммерческая работа с указанием места работы и дат;
  учебные, личные и волонтёрские проекты в `experience` не попадают.
- Клиент — `src/api/llm/client.py` на официальном SDK `openai`. `base_url`
  настраивается, поэтому подходит OpenRouter и локальные OpenAI-совместимые
  серверы. Повторы на 429/5xx и сетевые ошибки делает SDK (`max_retries`);
  один повтор на невалидный JSON/схему — на уровне экстрактора, затем ошибка,
  а не «0 баллов».
- В LLM уходит только обезличенный текст резюме. Тексты, цитаты и ответы модели
  не логируются; логируются только метаданные вызова: задержка, статус,
  `request_id`, токены и `finish_reason`.
- Ошибки: `INVALID_ARGUMENT` — пустой текст, `UNAVAILABLE` — LLM недоступна,
  `INTERNAL` — ответ не прошёл схему после повтора.

## Логирование

- gRPC-интерсептор `src/api/interceptors.py` пишет `grpc request start` и
  `grpc request end` с полным именем метода, статусом и `elapsed_ms`.
- Тела запросов и ответов не логируются: в них может быть текст резюме.
  При ошибке пишется класс исключения, но не его сообщение.

## Требования

- Python 3.14
- uv 0.13+

## Локальный запуск

```bash
uv sync
uv run scoring-service
```

Сервис слушает `0.0.0.0:8082`. Проверка здоровья:

```bash
uv run scoring-service-healthcheck
```

## Генерация gRPC

Сгенерированные protobuf/gRPC-модули лежат в `src/scoring_service/` и коммитятся вместе с контрактом. После изменения `.proto`:

```bash
uv run sh protoc.sh
```

## Конфигурация

Префикс `SCORING_SERVICE_`.

| Переменная | По умолчанию | Назначение |
|---|---|---|
| `SCORING_SERVICE_GRPC_HOST` | `0.0.0.0` | Адрес gRPC-сервера |
| `SCORING_SERVICE_GRPC_PORT` | `8082` | Порт gRPC-сервера |
| `SCORING_SERVICE_LOG_LEVEL` | `INFO` | Уровень логирования |
| `SCORING_SERVICE_MAX_WORKERS` | `10` | Размер пула обработчиков gRPC |
| `SCORING_SERVICE_TENURE_WEIGHT` | `0.1` | Вес общего стажа в `Score`; при `Vacancy.tenure = 0` слагаемое отключено |
| `SCORING_SERVICE_SKILL_FULL_COVERAGE` | `0.70` | Минимальный `coverage` навыка для статуса `FULL` |
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

## Пакетный прогон

Для ручной проверки на локальных `txt`-файлах есть
`scripts/benchmark.py`:

```bash
cd scoring-service
uv run python scripts/benchmark.py \
  --resumes-dir scripts/benchmark/examples/resumes \
  --vacancies-dir scripts/benchmark/examples/vacancies
```

Резюме один раз разбираются через `ExtractResume`, вакансии — из
структурированного txt в `Vacancy`. Результаты разбора кэшируются по SHA-256,
поэтому повторный запуск не вызывает LLM для неизменившихся резюме. Полные
результаты матчинга сохраняются в `scripts/benchmark/results/<run>/` в JSON.
Формат вакансии и структура каталогов — в `scripts/benchmark/README.md`.

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

Вся арифметика живёт в `src/api/scoring/`: `versioning.py` — версии навыков,
`similarity.py` — сходство, `matcher.py` — `R_i`, `c_i` и статусы,
`policies.py` — `Wv`, `τ`, `t` и `F`, `calculator.py` — агрегаты и границы,
`feedback.py` — объяснения. Формулы не дублируются в Java и других сервисах.
