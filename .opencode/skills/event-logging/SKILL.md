---
name: event-logging
description: Использовать при добавлении, изменении или ревью логирования событий в Tracing ATS — Java, Python, frontend, gRPC/HTTP, межсервисные вызовы, ошибки и фоновые задачи. Проверяет границы, парные события caller/callee, correlation id, отсутствие PII в релизе и тесты. Загружать перед созданием или изменением любой системы, сервиса, эндпоинта, интеграции или фоновой задачи, даже если в задаче нет слова «логирование».
---

# Event Logging в Tracing ATS

## Когда применять

- создание или изменение системы, сервиса, эндпоинта, интеграции или фоновой задачи — даже если логирование в задаче не упомянуто;
- добавить или изменить логирование запроса, ответа, внешнего вызова, ошибки, фоновой задачи;
- сделать gRPC/HTTP interceptor или middleware;
- связать логи caller/callee через `request_id`;
- проверить логи на PII, секреты и дублирование.

Не применять для метрик и трейсинга — это отдельная задача. Correlation id входит сюда, полноценный OpenTelemetry — нет.

Охват: `resume-service`, `vacancy-service`, `text-extraction` (Java), `scoring-service`, `ml-trainer` (Python), `frontend`, `infra/mock-llm`.

## 0. Главные правила

1. **Один факт — одно событие — одна запись на сервис.** Соседние слои не дублируют событие.
2. **При межсервисном вызове логируют обе стороны:**
   - caller — отправку запроса и получение ответа;
   - callee — получение запроса и отправку ответа.
3. **Внутри сервиса** событие логируется один раз на своём слое: либо middleware/interceptor, либо application service, либо client wrapper.
4. **В логи не попадают** PII, секреты, тела запросов/ответов, промпты и ответы LLM, текст резюме и цитаты.
5. **Чувствительные данные не логируются ни в каком виде** — включая debug, временную отладку и локальные запуски.
6. **Формат** — стабильное имя события + `key=value`; Python `%s`, Java `{}`, без f-strings и конкатенации.
7. **Ошибку со stack trace** логирует один слой — тот, который превращает её в ответ или решение.
8. **Correlation id** (`request_id`/`trace_id`) обязателен для входящих и исходящих вызовов.

## 1. Границы логирования

У каждого события есть слой-владелец. Он логирует событие один раз.

| Граница | Кто логирует | Что пишет |
|---|---|---|
| Входящий HTTP | `OncePerRequestFilter` / `HandlerInterceptor` | `inbound request received`, `inbound response sent` |
| Входящий gRPC | `ServerInterceptor` | `inbound request received`, `inbound response sent` |
| Исходящий HTTP/LLM | client wrapper / `ClientInterceptor` | `outbound request sent`, `outbound response received`, `outbound request failed` |
| Бизнес-операция | application service | один итог: `score_resume`, `vacancy_created`, `shortlist_built` |
| Ошибка | обработчик ошибок / boundary interceptor | один `ERROR` со stack trace, если ошибка не обработана |
| Фоновая задача | job runner | `job started`, `job finished`, counts, elapsed |

Пример для `ScoreResume`:

- `RequestLoggingInterceptor` — граница входящего запроса;
- `ScoringService` — граница бизнес-операции;
- `OpenAiCompatibleClient` — граница внешнего вызова;
- обработчик ошибок — граница ошибки.

Это **четыре разных события**, а не четыре лога одного события.

### Плохо

```java
// controller
log.info("scoring started");
// service
log.info("scoring started");
// repository
log.info("scoring started");
```

```java
try {
    ...
} catch (Exception e) {
    log.error("failed", e);
    throw e;
}
```

Если так сделать на трёх слоях, на одну ошибку будет три stack trace.

### Хорошо

- controller/interceptor — только lifecycle запроса;
- service — только бизнес-итог;
- client — только внешний вызов;
- ошибку со stack trace логирует один слой.

Если внутреннему слою нужен контекст, он **оборачивает исключение**, а не логирует его повторно:

```java
throw new ScoringException("scoring failed for runId=" + runId, cause);
```

## 2. Межсервисные вызовы: логируют обе стороны

Для наших сервисов caller и callee логируют парные события с одним `request_id`.

| Сторона | Событие | Когда |
|---|---|---|
| Caller | `outbound request sent` | перед вызовом |
| Caller | `outbound response received` | после ответа, включая ошибку статуса |
| Caller | `outbound request failed` | транспортная ошибка, ответа нет |
| Callee | `inbound request received` | в начале обработки |
| Callee | `inbound response sent` | перед отправкой ответа, включая ошибку статуса |
| Callee | `inbound request failed` | обработчик упал до ответа |

Обязательные поля: `request_id`, `method`/`path`, `status`, `elapsed_ms`, `attempt` для повторов. Тела запросов и ответов не логируются.

```python
# caller
logger.info(
    "outbound request sent target=%s method=%s request_id=%s",
    target, method, request_id,
)
try:
    response = call(...)
except Exception as exc:
    logger.warning(
        "outbound request failed target=%s method=%s request_id=%s error=%s elapsed_ms=%.1f",
        target, method, request_id, type(exc).__name__, elapsed_ms,
    )
    raise
logger.info(
    "outbound response received target=%s method=%s status=%s request_id=%s elapsed_ms=%.1f",
    target, method, status, request_id, elapsed_ms,
)
```

```python
# callee
logger.info("inbound request received method=%s request_id=%s", method, request_id)
try:
    ...
finally:
    logger.info(
        "inbound response sent method=%s status=%s request_id=%s elapsed_ms=%.1f",
        method, status, request_id, elapsed_ms,
    )
```

Правила:

- `request_id` генерируется на входе, если его нет; передаётся в metadata/header исходящего вызова; callee читает его и логирует.
- Если вторая сторона — внешний сервис (OpenRouter, GitHub, hh), логирует только наша сторона. Правило «обе стороны» — для наших сервисов.
- Повтор логируется как `attempt`, но финальная ошибка — одна, без спама на каждую попытку.
- Health-check — `DEBUG` или исключить, чтобы не шуметь.

## 3. Уровни

| Уровень | Когда |
|---|---|
| `ERROR` | операция не выполнена, нужен человек; неожиданное исключение со stack trace |
| `WARN` | деградация, повтор, fallback, ожидаемый отказ внешнего источника, 4xx |
| `INFO` | старт/конец запроса, бизнес-вехи, импорт, активация профиля |
| `DEBUG` | диагностика, по умолчанию выключена; без PII |

Ожидаемая валидация — не `ERROR`. Отсутствие необязательного GitHub — не `ERROR`. Health-check — не `INFO`.

## 4. Формат сообщения

- Стабильное имя события + `key=value`: `llm request ok model=... elapsed_ms=...`.
- Python: ленивый `%s`, не f-string.
- Java: параметризованный `{}`, не конкатенация.
- Не логировать `toString()`/`str()` сущностей и DTO.
- Не менять имя события без причины: на него опираются тесты и алерты.
- Если formatter не печатает `extra`/MDC (сейчас в проекте явной настройки нет), либо `key=value` в сообщении, либо обнови pattern логирования и тесты.

Обязательные поля, когда применимы:

`request_id`, `trace_id`, `method`, `path`, `status`, `elapsed_ms`, `attempt`, `provider`, `model`, `http_status`, `prompt_version`, `scoring_version`, `resume_id`, `vacancy_id`, `run_id`, `batch_id`, `requirements`, `skills`, `dropped`.

Только ID и технические метаданные. Баллы и решения — только если действительно нужны; лучше `run_id` и версия.

## 5. Python

- `logger = logging.getLogger(__name__)`.
- Ленивый `%s`; f-strings в логах запрещены.
- `logger.exception("...")` — только на границе, где ошибка превращается в ответ.
- Не `print`.
- Дорогие аргументы (`json.dumps`, `repr`, большие срезы) не передавать в выключенный `DEBUG` без проверки.
- В цикле — агрегаты, не лог на каждый элемент.
- gRPC: `grpc.ServerInterceptor` / `grpc.ClientInterceptor`.
- HTTP-клиент: обёртка или event hooks; не логировать тело.

## 6. Java

- `@Slf4j`, SLF4J, параметризованные `{}`.
- Не `System.out`, не `printStackTrace`.
- `log.error("...", ex)` — один раз на границе.
- MDC (`request_id`) — только если pattern логирования его печатает; иначе `key=value` в сообщении.
- MDC ставить и очищать в `finally`.
- `log.isDebugEnabled()` для дорогих вычислений.
- Не логировать `toString()` сущностей и DTO.
- HTTP: `OncePerRequestFilter` или `HandlerInterceptor`.
- gRPC: `ServerInterceptor` / `ClientInterceptor`.
- Исходящий HTTP: интерсептор `RestClient`/`WebClient`, без тел.

## 7. Frontend

- Общий logger-модуль, не `console.log` по коду.
- В production — только `warn`/`error` для неожиданных ошибок; debug-логи выключены.
- Не логировать токены, `Authorization`, текст резюме, PII кандидата и ответы API с PII.
- Слепой режим: PII не должна попадать в console до `reveal`.
- `request_id` из заголовка ответа/запроса логировать можно.
- `import.meta.env.DEV` — дополнительная защита, не единственная: проверяй release-bundle.

## 8. Фоновые задачи и инфраструктура

- `ml-trainer`: `job started` / `job finished`, dataset ID, seed, метрики, версия артефакта. Без строк датасета и PII.
- `text-extraction`: MIME, размер, число страниц, длительность. Без содержимого и полных путей с username.
- `infra/mock-llm`: сценарий, статус, задержка. Без тел запросов.
- Audit-события (`reveal`, доступ к PII) — отдельный поток/таблица, не обычный лог. В обычный лог — только `audit_id` и технические метаданные.
- Миграции и инфраструктурные скрипты: не логировать секреты из env.

## 9. Тесты

**Python (`caplog`):**

```python
with caplog.at_level(logging.INFO, logger="api.interceptors"):
    ...
assert "inbound request received method=..." in caplog.text
assert "resume text" not in caplog.text
```

**Java (`ListAppender<ILoggingEvent>`):**

- проверить имя события и поля;
- проверить, что PII/секретов нет;
- проверить error/retry-путь;
- не проверять точные timestamps и длительности.

**Межсервисный тест:**

- caller и callee пишут парные события;
- в обоих логах один и тот же `request_id`;
- статус и `elapsed_ms` присутствуют.

**Release-проверка:**

- `jar tf` / `unzip -l` / содержимое wheel: нет кода, который логирует PII или секреты;
- `rg` по release-исходникам не находит логов PII/секретов;
- prod-профиль не включает debug-логирование.

## 10. Чек-лист

- [ ] Событие названо стабильно.
- [ ] Одна запись на событие внутри сервиса.
- [ ] Caller логирует `sent`/`received`/`failed`.
- [ ] Callee логирует `received`/`sent`/`failed`.
- [ ] Один `request_id` на обоих концах.
- [ ] Уровень соответствует событию.
- [ ] Нет PII, секретов, тел запросов/ответов — включая debug и временную отладку.
- [ ] Нет f-strings, конкатенации, `toString()`.
- [ ] Нет дублирующего stack trace.
- [ ] Есть тест на событие и отсутствие PII.

## 11. Антипаттерны

- Логировать только caller или только callee.
- Логировать `request received` в controller, service и repository.
- `catch { log.error; throw; }` на каждом слое.
- `log.info("resume: " + text)`, `logger.info(f"...")`.
- `System.out.println`, `print`.
- Health-check на `INFO`.
- Лог на каждый элемент пачки.
- Полный URL с токеном в query.
- Логировать PII/секреты под видом debug или временной отладки.
- Логировать `Authorization`, cookies, токены, пароли.
- Логировать `toString()` сущности, в которой есть PII.

## 12. Команды проверки

```bash
(cd scoring-service && uv run pytest -q && uv run ruff check .)
(cd ml-trainer && uv run pytest -q && uv run ruff check .)
(cd vacancy-service && ./gradlew test spotlessCheck)
(cd resume-service && ./gradlew test spotlessCheck)
(cd frontend && npm test && npm run build)
```

```bash
rg -n "print\\(|System\\.out|logger\\.(info|debug)\\(f\"|log\\.(info|debug)\\(\".*\\+" <files>
rg -n "password|secret|token|Authorization|BEGIN PRIVATE KEY" <files>
```

## 13. Формат ответа агента

1. Список добавленных событий: файл, событие, сторона (caller/callee), уровень, поля.
2. Как связаны caller и callee через `request_id`.
3. Что сознательно не логируется.
4. Что проверено: тесты, lint, release-артефакт.
5. Что осталось неясным или требует ADR.
