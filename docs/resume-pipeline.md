# Пайплайн resume-service

Документ описывает сквозной сценарий MVP: загрузка пачки резюме → обезличивание →
предварительный скор → шорт-лист → GitHub → итоговый скор → рейтинг и отчёт.
Арифметика считается в Python `scoring-service` по gRPC; политика шорт-листа
приходит из `vacancy-service` (ADR 0004).

## 1. Загрузка

`POST /api/v1/resumes` (`multipart/form-data`)

| Поле | Обязательно | Смысл |
|---|---|---|
| `vacancyId` | нет | вакансия для режима «кандидаты под вакансию»; без неё запускается обратный подбор |
| `files` | да | PDF, DOCX, TXT или MD; несколько файлов за запрос |
| `consentGithub` | нет | согласие кандидата на обогащение из GitHub, по умолчанию `false` |

Для каждого файла:

1. Тип определяется по сигнатуре, имя файла — только подсказка.
2. `text-extraction` извлекает и нормализует текст.
3. PII маскируется: regex + локальная NER (ADR 0001). Образование (вуз, специальность,
   годы) не маскируется.
4. Prompt injection удаляется детерминированно (ADR 0005).
5. Оригинал кладётся в MinIO, PII — в AES-256-GCM `pii_vault`, обезличенный текст —
   в `parsed_resume.masked_text`.
6. Создаются `candidate`, `resume` и `application` со статусом `MASKED`.
7. После коммита транзакции публикуется событие, и отдельный пул запускает
   предварительный скоринг. HTTP-ответ не ждёт LLM.

Ответ `202`:

```json
{
  "vacancyId": "00000000-0000-0000-0000-000000000001",
  "resumes": [
    { "resumeId": "…", "status": "MASKED", "warnings": [] }
  ]
}
```

Статусы файла: `MASKED`, `TEXT_EMPTY`, `MASKING_FAILED`, `FAILED`. Для `FAILED`
без `resumeId` запись всё равно присутствует, чтобы клиент увидел отказ по каждому
файлу. Текст резюме, имя файла и PII в логи не попадают.

## 1.1. Редактирование резюме

`PUT /api/v1/resumes/{resumeId}`

```json
{
  "title": "Java Engineer",
  "text": "полный текст резюме с PII",
  "reason": "кандидат прислал обновление"
}
```

- Без `text` меняется только название резюме.
- Если передан `text`, backend заново маскирует PII, обновляет `pii_vault` и
  `parsed_resume`, удаляет устаревший `resume_extraction`, сбрасывает указатели
  текущих скоров у откликов и запускает предварительный скоринг или обратный подбор.
- Для изменения текста обязательна причина; действие пишется в `audit_event`.
- В LLM по-прежнему уходит только обезличенный текст; PII в запросах к модели нет.
- `DELETE /api/v1/resumes/{resumeId}` удаляет резюме, связанные PII, разбор,
  скоры, отклики и оригинал в объектном хранилище. Действие аудируется.

## 1.2. Решение HR по отчёту

`PUT /api/v1/resumes/{resumeId}/vacancies/{vacancyId}/decision`

```json
{ "decision": "SHORTLISTED", "comment": "Пригласить на интервью" }
```

Решение хранится отдельно от `application.screening_decision` и не запускает
автоматических приглашений или отказов. Комментарий обязателен, действие пишется
в аудит. `ApplicationReport` возвращает `hrDecision` и `hrComment`.

## 2. Предварительный скор при загрузке

Асинхронный worker берёт `application` в статусе `MASKED`:

1. `GetTemplate(vacancyId)` в `vacancy-service` — требования, версия шаблона,
   веса, версии промптов и весов.
2. Вызывает `ScoringService.ScoreResume` по gRPC. В запрос уходят только
   обезличенный текст и шаблон; PII не передаётся.
3. Запуск пишется в `score_run` с `phase=PRELIMINARY`, `batch_id=NULL`,
   `template_version`, `scoring_version`, `prompt_version`, `model_version`,
   `weights_version`.
4. Статус отклика: `MASKED` → `SCORING` → `SCORED` или `SCORING_FAILED`.

`Score` и границы из gRPC приходят в шкале `0..1` и переводятся в `0..100` для API
и БД. Каждая цитата-доказательство проверяется как подстрока обезличенного текста;
непроверенная цитата отбрасывается, покрытие требования не повышается.

## 2.1. Режим «вакансии под резюме»

Тот же скоринг, обратное представление. `POST /api/v1/resumes` без `vacancyId`
принимает пачку резюме и не создаёт отклик.

1. После коммита `ExtractResume` разбирает резюме один раз; снимок кладётся в
   неизменяемую таблицу `resume_extraction`.
2. Все вакансии делятся на чанки по `ats.resume.match.batch-size`
   (`ATS_RESUME_MATCH_BATCH_SIZE`, по умолчанию 3) и уходят в
   `EvaluateResumeMatches`; на один вызов — один gRPC-запрос.
3. Запуски пишутся в `score_run` с `direction=RESUME_TO_VACANCIES`,
   `resume_id` и `vacancy_id`; отчёт по паре читается из тех же
   `requirement_result`.
4. `GET /api/v1/resumes/{resumeId}/ranking` — рейтинг вакансий;
   `GET /api/v1/resumes/{resumeId}/vacancies/{vacancyId}/report` — отчёт;
   `POST /api/v1/resumes/{resumeId}/match` — пересчёт по сохранённому
   `ParsedResume` без LLM.
5. Прогресс — `GET /api/v1/screening-batches/{batchId}`, как у скрининга.

## 3. Скрининг

`POST /api/v1/vacancies/{vacancyId}/screen`

```json
{ "applicationIds": ["…"] }
```

Если `applicationIds` пуст или отсутствует, берутся все отклики вакансии в статусах
`SCORED`, `SCORING` и `SCORING_FAILED`. Ответ — `202`:

```json
{ "batchId": "…", "status": "PENDING" }
```

Пустая пачка сразу получает `COMPLETED`.

Асинхронный worker:

1. `GetTemplate(vacancyId)` в `vacancy-service`.
2. Если отклик ещё в `SCORING`, ждёт до `ats.screening.scoring-wait`. По дедлайну
   отклик получает `FAILED`, попадает в `failed` пакета и не пропускается молча.
3. Если `PRELIMINARY` посчитан по другой `template_version` или его нет, запускает
   пересчёт по текущему шаблону. `SCORING_FAILED` считается ошибкой пакета.
4. Один вызов `DecideScreening(vacancyId, candidates[])` на всю пачку. Top-N нельзя
   решить по одному кандидату.
5. Для `SHORTLISTED` с `consent_github=true` — GitHub, только по конкретным навыкам
   (ADR 0003). Ошибка или отсутствие GitHub не уменьшают балл.
6. `Score_final` пишется отдельным запуском `phase=FINAL`. Пока в контракте
   `scoring-service` нет `Refine`, FINAL копирует готовые факты PRELIMINARY и не
   вызывает LLM повторно. `Score_pre` и `Score_final` не смешиваются.
7. `application.status` становится `REFINED` или `ENRICHED`.

Нормализованные факты GitHub сохраняются в `external_evidence` и показываются в
отчёте. До появления `Refine` они не меняют балл FINAL; это ограничение контракта,
а не штраф за отсутствие внешних данных.

Статус пакета: `GET /api/v1/screening-batches/{batchId}` → `PENDING`, `RUNNING`,
`COMPLETED` или `FAILED`, плюс `total`, `processed`, `failed`, `errorMessage`.
Если `vacancy-service` недоступен, пакет получает `FAILED`; локальная политика
молча не подставляется.

## 4. Рейтинг и отчёт

`GET /api/v1/vacancies/{vacancyId}/ranking?phase=PRELIMINARY|FINAL` — место,
релевантность, балл, вилка, cap, решение шорт-листа, флаги. По умолчанию `FINAL`.

`GET /api/v1/applications/{applicationId}/report?phase=PRELIMINARY|FINAL` — матрица
«требование → доказательство → пробел», внешние факты и флаги. В элементе требования
есть `relevance` (`R_i`), `cR` (покрытие `c_i`) и `tenure`. Поле `blind=true`
означает, что PII в ответе нет.

`POST /api/v1/applications/{applicationId}/reveal` с телом `{ "reason": "…" }` —
отдельное действие: расшифровывает vault, возвращает имя, контакты, GitHub и имя файла
и пишет `audit_event` с причиной. Образование в тексте не маскируется, поэтому в
`university` для новых резюме пусто.

## 5. Демо-данные

- Вакансия: `00000000-0000-0000-0000-000000000001`
- Версия шаблона: `00000000-0000-0000-0000-000000000002`
- Требования: Java (critical), Spring Boot, PostgreSQL, Kafka, Docker, Kubernetes.

## 6. Docker Compose

`docker compose up -d --wait` поднимает PostgreSQL, миграции, S3-совместимое
хранилище, `scoring-service` и оба Java-сервиса. Официальные community-образы MinIO
удалены из Docker Hub и Quay в 2026, поэтому compose использует API-совместимый форк
`pgsty/minio` и клиент `pgsty/mc` (версии закреплены, переопределяются через
`ATS_MINIO_IMAGE` и `ATS_MINIO_MC_IMAGE`). Для реальной NER-модели смонтируйте
`models/` и включите `ATS_PII_NER_PROVIDER=onnx`.

## 7. Ограничения MVP

- `scoring-service` считает детерминированную модель: сходство признаков `S`,
  убедительность `t`, стаж и свежесть `τ`, покрытие `c_i`, `Score` и границы.
  LLM вызывается только для извлечения признаков из обезличенного текста.
- gRPC-ответ `EvaluateResumeMatch` содержит `strengths`, `risks`, `questions` и
  `recruiter_note`; они собираются по каталогу `feedback-templates.v2` без LLM.
  Java сохраняет их в `score_run.parsed`, но REST-отчёт и UI пока не отдают эти
  поля.
- В контракте `scoring-service` нет `Refine`, весов и внешних фактов, поэтому FINAL
  копирует PRELIMINARY, а GitHub влияет только на отчёт.
- Реальная NER-модель не входит в репозиторий; CI использует fake-NER.
  Compose по умолчанию тоже запускается с `ATS_PII_NER_PROVIDER=fake`, поэтому
  маскирует только regex-PII, но не имена. Образование не маскируется ни в одном
  режиме. Для реальных данных скачайте
  модель (`scripts/download-ner.sh`), задайте `ATS_PII_NER_PROVIDER=onnx` и
  смонтируйте каталог `models/` в `/models` (это уже описано в `compose.yaml`).
- GitHub выключен по умолчанию (`ATS_GITHUB_ENABLED=false`).
- Нет авторизации, PDF-отчёта, письма отказа и импорта с hh.
