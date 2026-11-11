# Отчёт 004 — scoring-service: извлечение признаков и LLM-клиент на SDK openai

| | |
|---|---|
| **Дата** | 2026-11-11 |
| **Основание** | `docs/rfc/rfc-feature-extraction.md`, §5.7 |
| **Ветка** | `feature/scoring-service-impl` от `main` (`b4c8ba5`) |
| **Контур** | `scoring-service`, `docs/rfc`, `CHANGELOG.md`, `.env.example`, `compose.yaml` |
| **Статус** | v0 пайплайна работает на mock-LLM; реальный провайдер не проверялся вручную |

## 1. Что сделано

### 1.1. LLM-клиент на официальном SDK `openai`

- `scoring-service/src/api/llm/client.py` переписан с самописного `urllib` на
  `openai.OpenAI`. `base_url` настраивается, поэтому один клиент подходит для
  OpenRouter и локальных OpenAI-совместимых серверов.
- Таймаут и повторы транспорта/429/5xx делегированы SDK: `timeout` и
  `max_retries` из настроек. Для локальных серверов без ключа передаётся
  `api_key="not-needed"`, иначе SDK требует `OPENAI_API_KEY`.
- Ошибки SDK маппятся в ошибки сервиса:
  - `APITimeoutError` → `LlmUnavailable`;
  - `APIConnectionError` → `LlmUnavailable`;
  - `APIStatusError` (401/400/429/5xx после повторов) → `LlmUnavailable`;
  - `APIError`/`ValueError` (битый JSON, невалидный конверт) →
    `LlmInvalidResponse`.
- Ответ, пришедший с `content-type: text/plain`, SDK отдаёт строкой; клиент
  распознаёт это как `LlmInvalidResponse`, а не падает с `AttributeError`.

### 1.2. Подробное логирование внешних вызовов

Логируются только метаданные, без текста резюме, цитат, промптов и ответов:

- `llm request start` — модель, `base_url`, таймаут, `max_retries`;
- `llm request ok` — `response_id`, `response_model`, `finish_reason`,
  `elapsed_ms`, `prompt_tokens`, `completion_tokens`, `total_tokens`,
  `content_chars`;
- `llm request failed` — причина (`timeout`, `connection`, `http`,
  `invalid-response`), класс ошибки, HTTP-статус, `request_id`, `elapsed_ms`;
- `llm request invalid` — `no_choices` или `empty_content` с `response_id`
  и `elapsed_ms`.

`request_id` берётся из `x-request-id`; ключ LLM и тела запросов/ответов
в логи не попадают.

### 1.3. Извлечение признаков резюме

- Строгая Pydantic-схема `ParsedResume` (`skills`, `education`, `experience`)
  с `schema_version = 1`; лишние поля отбрасываются, неизвестные enum —
  ошибка схемы. Модель возвращает только факты, цитаты и категории, без баллов.
- Промпт `resume-extraction.v1` в `src/api/prompts/resume_extraction.py`:
  текст резюме передаётся в блоке `<resume_text>` как данные, закрывающий тег
  внутри текста экранируется. Невалидный ответ — один повтор с
  `RETRY_HINT`, затем `ExtractionError`.
- `QuoteIndex` проверяет цитату как подстроку после схлопывания пробелов
  (`[\s\u00A0\u202F]+` → один пробел), сохраняя offsets в исходном тексте.
  Признак без валидной цитаты отбрасывается и не повышает покрытие.
- `normalize_key` повторяет Java `SkillNormalizer`: NFKC, схлопывание
  пробелов, trim, lower, снятие краевой пунктуации (кроме `+`, `#` и ведущей
  точки).
- Встроенная `MockLlmClient` детерминированно вытаскивает известные навыки,
  строки образования и опыта из текста. Она используется по умолчанию и не
  требует сети и ключей.

### 1.4. Сборка ответа и gRPC

- `ScoringService` принимает `ResumeExtractor`; `create_server` собирает
  клиент через `build_llm_client(settings)`.
- Для каждого требования запроса собирается `FeatureAssessment`:
  `relevance`/`coverage` из заглушки `S^{ij}`, evidence с
  `start_index`/`end_index`/`text`, для навыка — `SkillPerResume`, для
  образования — `EducationPerResume`, для опыта — `PriorExperiencePerResume`.
- Если признака нет или цитата не подтвердилась, требование получает
  `relevance = 0`, `coverage = 0` и `refine = true`. Поле `parsed` в ответ
  не добавлялось: контракт gRPC в v0 не менялся.
- Ошибки: `INVALID_ARGUMENT` — пустой текст, `UNAVAILABLE` — LLM недоступна,
  `INTERNAL` — ответ не прошёл схему после повтора. Вместо «0 баллов» клиент
  получает ошибку.

### 1.5. Заглушка `S^{ij}`

- `src/api/scoring/similarity.py`: детерминированная псевдослучайная матрица,
  seed — SHA-256 текста резюме (не встроенный `hash()`).
- По требованию выбирается `argmax`, поэтому `coverage = relevance`
  (внешних источников нет, `λ = 0`).
- Агрегаты `Score`, `Score_l`, `Score_h` остаются заглушкой
  `0.5`/`0.4`/`0.6`; реальные `S`, `t_i`, `τ_i`, `Wv`, `F` не реализованы
  до фиксации в `docs/rfc/rfc-scoring-model.md`.

### 1.6. Конфигурация, инфраструктура и документация

- `Settings`: `llm_provider` (`mock`/`openai`), `llm_base_url`, `llm_api_key`
  (`SecretStr`), `llm_model`, `llm_timeout_seconds`, `llm_max_retries`.
  Для `openai` обязательны `base_url` и `model`; проверка — в
  `model_validator`.
- `pyproject.toml`: добавлена зависимость `openai>=3.49.0`; `uv.lock`
  обновлён. `openai==3.49.0` использует `httpx2`, поэтому тесты клиента
  работают через `httpx2.MockTransport`.
- `.env.example` и `compose.yaml`: переменные провайдера, `base_url`, ключ
  из `OPENROUTER_API_KEY`, модель.
- `scoring-service/README.md`, `CHANGELOG.md` и
  `docs/rfc/rfc-feature-extraction.md` §5.7 обновлены под SDK `openai`.

## 2. Как подключается моковая LLM

Моковая LLM — это **встроенный в процесс клиент** `MockLlmClient`
(`src/api/llm/mock.py`), а не HTTP-сервер. `build_llm_client(settings)`
возвращает его при `SCORING_SERVICE_LLM_PROVIDER=mock`; это значение по
умолчанию, поэтому CI и демо работают без сети и ключей.

`infra/mock-llm` — отдельный HTTP-сервер для `vacancy-service`; `scoring-service`
его не вызывает. Если понадобится проверять реальный HTTP-путь без внешнего
провайдера, можно поднять `infra/mock-llm` и включить
`SCORING_SERVICE_LLM_PROVIDER=openai` с его `base_url`, но его сценарии
сейчас не покрывают разбор резюме.

## 3. Проверка

- `uv run pytest -q` → **43 passed** (0.65 s на сбор, ~1.4 s прогон).
  - `test_extraction.py` — 17;
  - `test_llm_client.py` — 8;
  - `test_scoring.py` — 7;
  - `test_config.py` — 5;
  - `test_similarity.py` — 5;
  - `test_server.py` — 1.
- `uv run ruff check .` → `All checks passed!`
- `uv run ruff format --check .` → все файлы отформатированы.
- `uv run sh protoc.sh` → без diff в `src/scoring_service` (контракт не менялся).
- `uv sync --locked` → lock согласован, 29 пакетов проверено.
- Тесты клиента используют реальный `OpenAI` с `httpx2.MockTransport`:
  успех, повтор 500 → 200, 401, connection error, timeout, пустой `content`,
  битый JSON. Реальная сеть и реальная модель в тестах не вызываются.

## 4. Решения и допущения

1. **SDK вместо своего HTTP-клиента.** Повторы 429/5xx и сетевые ошибки делает
   `openai` через `max_retries`; невалидный JSON/схему по-прежнему повторяет
   `ResumeExtractor` один раз. Двойных повторов нет.
2. **`mock` по умолчанию.** Реальная LLM не обязательна для зелёного CI.
   Переключение — только переменной окружения, без изменения кода.
3. **`LlmInvalidResponse` для битого конверта.** Сюда попадают пустой
   `content`, отсутствие `choices`, `text/plain` вместо JSON и
   `JSONDecodeError`; это позволяет экстрактору повторить запрос.
4. **`LlmUnavailable` для HTTP/сети.** После исчерпания повторов SDK сервис
   возвращает gRPC `UNAVAILABLE`, а не «0 баллов».
5. **Контракт не менялся.** `parsed` и `missing_requirements` в ответе
   отсутствуют; отсутствие признака выражается `relevance = 0` и
   `refine = true`.
6. **Заглушка `S^{ij}`.** Числа не претендуют на модель: это способ проверить
   пайплайн и контракт до реализации формулы из RFC.

## 5. Не сделано / открытые вопросы

- Реальная LLM (OpenRouter или локальный сервер) вручную не проверялась;
  качество промпта `resume-extraction.v1` на реальных резюме не измерено.
- `S^{ij}`, `t_i`, `τ_i`, `Wv`, `F`, `Score` и границы остаются заглушкой.
  Значения фиксируются только после правки `docs/rfc/rfc-scoring-model.md`.
- `FeatureCriticality.CRITICAL` и комментарий про cap в `common.proto`
  расходятся с моделью «критичность через вес `Wv`»; контракт пока не
  менялся.
- Нет интеграционного теста с поднятым `infra/mock-llm` для scoring-service:
  его сценарии не покрывают разбор резюме.
- Docker Compose с `SCORING_SERVICE_LLM_PROVIDER=openai` не запускался.

## 6. Требует решения пользователя

- Проверять ли реальную модель на синтетическом резюме до merge и какой
  провайдер/модель считать эталоном.
- Оставлять ли `infra/mock-llm` только для вакансии или расширять его
  сценариями резюме для сквозного HTTP-теста.
- Когда фиксировать `F`/`Wv`/`T_{1/2}` и остальные открытые параметры
  скоринга в RFC, чтобы заменить заглушку `0.5`/`0.4`/`0.6`.
