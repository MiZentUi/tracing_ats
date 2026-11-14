# Обзор сервиса Tracing ATS

Документ описывает текущую архитектуру и сквозные сценарии. Источники истины: `TASK.md` (продукт), `contracts/` (API), код (фактическое поведение). Открытые вопросы и технический долг — в [`known-issues.md`](known-issues.md).

---

## 1. Кратко

Tracing ATS решает две задачи:

1. **Резюме → вакансии.** Для одного резюме строит рейтинг вакансий и отчёт по каждому требованию.
2. **Вакансия → кандидаты.** Для вакансии собирает шаблон требований, принимает резюме и строит рейтинг кандидатов.

В обоих сценариях результат объясним: требование, подтверждение в резюме, статус, комментарий, пробел. Сильные стороны, риски и вопросы формируются детерминированно по уже посчитанным фактам.

Текущее состояние:

| Блок | Статус |
|---|---|
| Загрузка и извлечение текста | Готово |
| Маскировка PII, vault, reveal | Готово; NER по умолчанию `fake` |
| Шаблон вакансии через LLM и правка HR | Готово |
| Словарь навыков и квалификаций | Готово |
| Разбор резюме через LLM | Готово |
| Детерминированный скоринг | Готово (v0) |
| Обратный подбор | Готово |
| Шорт-лист, рейтинг, отчёт | Готово |
| Детерминированные объяснения | Готово |
| GitHub | Частично: факты в отчёте, не в `Score` |
| Письма кандидату | Готово: API и блок «Письмо» в окне решения |
| hh, PDF, auth, `ml-trainer` | Не реализовано |

---

## 2. Что запускается

| Сервис | Порт | Назначение |
|---|---|---|
| `frontend` | 3000 → 80 | Vue 3 + nginx, проксирует `/api` в `ats-service` |
| `ats-service` | 8080 REST, 9090 gRPC | Вакансии, резюме, PII, оркестрация, GitHub, LLM, отчёты |
| `scoring-service` | 8082 gRPC | Разбор обезличенного резюме и скоринг |
| `dictionary-service` | 8084 gRPC | Словарь навыков и квалификаций |
| `postgres` | 5432 | Схемы `public` и `dictionary` |
| `minio` | 9000 API, 9001 консоль | Оригиналы резюме |
| `mock-llm` | 8089 | OpenAI-совместимая заглушка |
| `mock-scoring` | 8083 → 8082 | Заглушка скоринга, профиль `mock-scoring` |
| `migrations` | — | Liquibase, накатывает схему и завершается |

`migrations` — не сервис, а one-shot job. `ats-service` работает с `ddl-auto=validate` и не создаёт таблицы.

---

## 3. Компоненты

### 3.1 `ats-service`

Java 21 / Spring Boot 4. Пакеты:

- `ats.vacancy` — REST вакансий и справочников, LLM-шаблон, `kind`-история, словарь, screening-политика, gRPC-фасад `GetTemplate`/`DecideScreening`.
- `ats.resume` — загрузка, извлечение текста, PII, vault, разбор, скоринг, скрининг, GitHub, рейтинг, отчёт, решения HR, письма кандидату, reveal.
- `ats.support` — `request_id` и логирование.

Ключевые ограничения:

- контроллеры не содержат бизнес-логики;
- публичный REST генерируется из OpenAPI (`ats.api`, `ats.gateway.model`);
- арифметика скоринга не дублируется в Java;
- фоновые операции возвращают `202` и `batchId`.

### 3.2 `scoring-service`

Python 3.14, gRPC, Pydantic. Реализует четыре RPC:

| RPC | Что делает | LLM |
|---|---|---|
| `ExtractResume` | Обезличенный текст → `ParsedResume`, `dropped`, `warnings` | да |
| `EvaluateResumeMatch` | `ParsedResume` + исходный текст + одна вакансия → матчи, gaps, объяснения | нет |
| `EvaluateResumeMatches` | То же для набора вакансий; ошибка одной не отменяет остальные | нет |
| `ScoreResume` | Одношаговый legacy: извлечение + оценка по одной вакансии | да |

Сервис stateless: не читает файлы, БД, MinIO, GitHub и не получает PII. LLM вызывается только с обезличенным текстом резюме.

### 3.3 `dictionary-service`

Python 3.12, gRPC, PostgreSQL. Владеет схемой `dictionary`.

- `Resolve` — навыки;
- `ResolveQualifications` — квалификации;
- `GetRelations` — классы связей;
- `GetVersion` — версия словаря;
- админские RPC — навыки, алиасы, квалификации, связи, очередь NEW;
- `ApplySkills` — пакетное применение подтверждённых черновиков.

Нормализация: NFKC, пробелы, `ё → е`, краевая пунктуация, гомоглифы при ASCII-сигнале, лемматизация кириллицы. Нечёткого матчинга в `Resolve` нет: только точный ключ; подсказки кандидатов живут в очереди NEW.

### 3.4 `text-extraction`

Java-библиотека, не сервис. Поддерживает PDF (PDFBox), DOCX (POI), TXT и Markdown. Тип определяется по сигнатуре, имя файла — только подсказка. Есть лимиты на размер, страницы, текст и время. OCR нет.

### 3.5 `frontend`

Vue 3 + TypeScript. Экраны:

- вакансии: список, создание, просмотр, редактор требований;
- резюме: список, загрузка, карточка с извлечёнными фактами и masked text;
- словарь: навыки, квалификации, связи, генерация, очередь NEW;
- отчёт по паре «резюме × вакансия» или отклику.

В окне «Решение по отчёту» под комментарием есть блок «Письмо» с вкладками
«Приглашение на собеседование» и «Письмо отказа». Текст можно сгенерировать,
перегенерировать и скопировать; автоматической отправки нет. Отдельный экран
рейтинга, blind-mode toggle и PDF пока не реализованы в UI.

---

## 4. Сценарий «резюме → вакансии»

```text
POST /api/v1/resumes (files, без vacancyId)
  → text-extraction
  → regex/PII-маскировка + prompt-injection sanitizer
  → pii_vault + parsed_resume
  → ExtractResume (LLM один раз)
  → resume_extraction
  → EvaluateResumeMatches (чанки вакансий)
  → score_run(direction=RESUME_TO_VACANCIES)
  → GET /resumes/{id}/ranking
  → GET /resumes/{id}/vacancies/{vacancyId}/report
```

Детали:

- ответ на загрузку — `202` и статус по каждому файлу;
- `POST /resumes/{id}/match` пересчитывает по сохранённому `ParsedResume` без LLM;
- прогресс — `GET /screening-batches/{batchId}`;
- `GET /resumes/{id}/ranking` по умолчанию отдаёт `PRELIMINARY`;
- парный отчёт всегда читает `PRELIMINARY`;
- отчёт содержит требования, цитаты, статусы, пробелы, сильные стороны, риски, вопросы и рекомендацию.

---

## 5. Сценарий «вакансия → кандидаты»

```text
POST /api/v1/vacancies (text или file)
  → LLM: шаблон требований (vacancy-template.v4, schema 3)
  → HR: PUT /vacancies/{id}/template
  → POST /api/v1/resumes (files + vacancyId)
  → ExtractResume + EvaluateResumeMatch (предварительный скор)
  → POST /api/v1/vacancies/{id}/screen
  → ScreeningPolicy: threshold / top-N / borderline
  → GitHub только для SHORTLISTED + consent
  → FINAL: копия PRELIMINARY (Refine пока нет)
  → GET /vacancies/{id}/ranking
  → GET /applications/{id}/report
```

### Шаблон вакансии

- LLM возвращает требования с цитатами; цитата проверяется как подстрока после нормализации пробелов.
- Требования без subject, дубликаты и записи без валидной цитаты отбрасываются.
- `kind` — `MUST`, `NICE`, `OPTIONAL`; `kind_source` — `LLM`, `HISTORY`, `HR`.
- HR сохраняет новую неизменяемую версию шаблона.
- Числового `weight` и `critical` в API и UI нет; `critical` в БД всегда `false`.

### Скрининг

- `ScreeningPolicy` сортирует кандидатов по score и применяет порог, top-N и borderline.
- `PRELIMINARY` считается для всех при загрузке.
- GitHub запускается только для `SHORTLISTED` с `consent_github=true`.
- `FINAL` создаётся отдельным `score_run`, но пока копирует готовые факты `PRELIMINARY`.
- GitHub-факты сохраняются в `external_evidence` и показываются в отчёте, но не влияют на балл.

### Решения и письма

- `PUT /resumes/{resumeId}/vacancies/{vacancyId}/decision` сохраняет решение HR и комментарий.
- `GET/POST /applications/{applicationId}/candidate-messages` — черновики приглашения или отказа.
- Письмо отказа требует непокрытых требований; в LLM уходят только название вакансии и тексты требований, без PII.
- Ничего не отправляется автоматически.

---

## 6. Словарь

Словарь — отдельный контур:

```text
ats-service ──gRPC──► dictionary-service ──SQL──► dictionary.*
scoring-service ──gRPC──► dictionary-service
```

- Java вызывает `Resolve` при сохранении требований и в админке.
- Python вызывает `Resolve`, `ResolveQualifications`, `GetRelations` при каждом скоринге.
- При недоступности словаря Java и Python переходят на точный ключ (`EXACT_FALLBACK`), скоринг не падает.
- `resolver_version` и `dictionary_mode` возвращаются в gRPC-ответе, но Java пока не сохраняет их в `score_run`.
- `DICTIONARY_SERVICE_TOKEN` загружается, но не проверяется.

---

## 7. Данные

Две схемы в одном PostgreSQL:

- `public` — вакансии, шаблоны, требования, резюме, PII, разбор, отклики, запуски, результаты, аудит, решения, письма, staging словаря.
- `dictionary` — навыки, алиасы, ключи, очередь NEW, квалификации, связи, снапшоты.

Инварианты:

- версии шаблонов, запуски, результаты, PII, разбор и аудит неизменяемы;
- `candidate_message` создаётся только новой строкой; DB-триггера на неё нет;
- `parsed_resume` и `masked_text` не содержат PII;
- `score_run` хранит версии модели, промпта и шаблона;
- `candidate_message` не содержит PII и не отправляется сам.

Подробная схема — [`data-model.md`](data-model.md).

---

## 8. Приватность

- PII маскируется до передачи текста в Python и до внешней LLM.
- Regex-детекторы работают всегда; ONNX NER включается явно.
- По умолчанию `ATS_PII_NER_PROVIDER=fake`: имена не маскируются, только regex-PII.
- Образование не маскируется по решению команды.
- PII шифруется AES-256-GCM в `pii_vault`; ключ обязателен при старте.
- `reveal` требует причину и пишет аудит; в отчёте `blind=true`.
- Prompt injection удаляется детерминированно, флаги хранят только хэши.

---

## 9. Конфигурация и профили

- Основной профиль — `application.yaml`; ключи LLM и инфраструктуры — из env.
- `mock-llm` — заглушка LLM для `ats-service`.
- `mock` — провайдер `scoring-service` по умолчанию.
- `mock-scoring` — compose-профиль для заглушки скоринга.
- `ATS_STORAGE_TYPE=memory` — хранилище в памяти для тестов.
- Тестовый профиль использует fake-NER, in-memory storage и Liquibase из classpath.

Полный список переменных — в [`.env.example`](../.env.example) и `ats-service/src/main/resources/application.yaml`.

---

## 10. Проверки

| Модуль | Команды |
|---|---|
| `ats-service` | `./gradlew test`, `./gradlew spotlessCheck` |
| `text-extraction` | `gradle test`, `gradle spotlessCheck` |
| `scoring-service` | `uv run pytest -q`, `uv run ruff check .`, `uv run ruff format --check .` |
| `dictionary-service` | `python -m pytest`, `ruff check .`, `ruff format --check .` |
| `frontend` | `npm test`, `npm run build` |
| Compose | `docker compose config --quiet` |

CI jobs: Java, `scoring-service`, `mock-scoring`, frontend, migrations. Отдельного job для `dictionary-service` нет.

---

## 11. Текущие ограничения

Кратко; подробнее — [`known-issues.md`](known-issues.md).

- Нет авторизации; все endpoints открыты.
- Нет hh, PDF-отчёта, `ml-trainer`, профилей весов.
- GitHub не влияет на `Score`; `FINAL` копирует `PRELIMINARY`.
- Нет `Refine`, внешних `q`, `λ`, `F`, общего `τ_total`.
- Нет cap для критичных требований.
- NER по умолчанию `fake`.
- Нет OCR.
- Основной блок отчёта не показывает фазу, вилку и часть полей; блок письма подписывает фазу.
- `ATS_SCREENING_*` не привязаны к политике из-за пути в `application.yaml`.

---

## 12. Связанные документы

- [`../README.md`](../README.md) — запуск и обзор.
- [`resume-pipeline.md`](resume-pipeline.md) — детальный пайплайн резюме.
- [`data-model.md`](data-model.md) — схема данных.
- [`known-issues.md`](known-issues.md) — расхождения и технический долг.
- [`rfc/rfc-scoring-model.md`](rfc/rfc-scoring-model.md) — формулы.
- [`rfc/rfc-feature-extraction.md`](rfc/rfc-feature-extraction.md) — извлечение признаков.
- [`rfc/rfc-feedback-generation.md`](rfc/rfc-feedback-generation.md) — объяснения.
- [`technical_specification/tz-skill-dictionary-service.md`](technical_specification/tz-skill-dictionary-service.md) — словарь.
