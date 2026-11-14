# Пайплайн резюме

Документ описывает путь резюме в `ats-service`: загрузка → извлечение текста → маскировка → разбор → скоринг → рейтинг и отчёт. Оба направления используют один и тот же разбор и одну модель; различаются только входные данные и представление результата.

---

## 1. Загрузка

`POST /api/v1/resumes` — `multipart/form-data`.

| Поле | Обязательно | Смысл |
|---|---|---|
| `files` | да | PDF, DOCX, TXT или MD; несколько файлов за запрос |
| `vacancyId` | нет | режим «кандидаты под вакансию»; без него запускается обратный подбор |
| `consentGithub` | нет | согласие на GitHub-обогащение, по умолчанию `false` |

Для каждого файла:

1. Тип определяется по сигнатуре; имя файла — только подсказка.
2. `text-extraction` извлекает и нормализует текст.
3. PII маскируется: regex + опциональная ONNX NER.
4. Prompt injection удаляется детерминированным sanitizer.
5. Оригинал кладётся в MinIO; PII шифруется в `pii_vault`; обезличенный текст — в `parsed_resume.masked_text`.
6. Создаются `candidate`, `resume` и, если передан `vacancyId`, `application`.
7. После коммита публикуется событие: предварительный скоринг или обратный подбор.

Ответ `202`:

```json
{
  "vacancyId": "00000000-0000-0000-0000-000000000001",
  "resumes": [
    { "resumeId": "…", "status": "MASKED", "warnings": [] }
  ]
}
```

Статусы файла: `MASKED`, `TEXT_EMPTY`, `MASKING_FAILED`, `FAILED`. Для `FAILED` без `resumeId` запись всё равно остаётся, чтобы клиент видел отказ по каждому файлу. Тексты и PII в логи не попадают.

### 1.1. Редактирование и удаление

`PUT /api/v1/resumes/{resumeId}`:

```json
{
  "title": "Java Engineer",
  "text": "полный текст резюме с PII",
  "reason": "кандидат прислал обновление"
}
```

- Без `text` меняется только название.
- С `text` backend заново маскирует PII, обновляет vault и `parsed_resume`, удаляет устаревший `resume_extraction`, сбрасывает указатели скоров и запускает пересчёт.
- Для изменения текста обязательна причина; действие пишется в `audit_event`.
- `DELETE /api/v1/resumes/{resumeId}` удаляет резюме, PII, разбор, скоры, отклики и оригинал; действие аудируется.

### 1.2. Решение HR

`PUT /api/v1/resumes/{resumeId}/vacancies/{vacancyId}/decision`:

```json
{ "decision": "SHORTLISTED", "comment": "Пригласить на интервью" }
```

Решение хранится отдельно от `application.screening_decision` и не запускает автоматических приглашений или отказов. Комментарий обязателен, действие пишется в аудит. `ApplicationReport` возвращает `hrDecision` и `hrComment`.

---

## 2. Разбор резюме

### 2.1. Режим «вакансия → кандидаты»

При загрузке с `vacancyId` worker вызывает `ExtractResume`, затем
`EvaluateResumeMatch`:

```text
обезличенный текст
  → ExtractResume: LLM извлекает навыки, квалификации, образование, опыт
  → проверка цитат
  → EvaluateResumeMatch: детерминированный скоринг по шаблону вакансии
```

Запуск пишется в `score_run` с `phase=PRELIMINARY`, `batch_id=NULL`, `template_version`, `scoring_version`, `prompt_version`, `model_version`, `weights_version`. Статус отклика: `MASKED` → `SCORING` → `SCORED` или `SCORING_FAILED`.

`Score` и границы приходят из gRPC в шкале `0..1` и переводятся в `0..100` для API и БД. Каждая цитата проверяется как подстрока обезличенного текста; непроверенная цитата отбрасывается, покрытие не повышается.

### 2.2. Режим «вакансии под резюме»

`POST /api/v1/resumes` без `vacancyId` не создаёт отклик.

1. `ExtractResume` разбирает резюме один раз; снимок кладётся в `resume_extraction`.
2. Вакансии делятся на чанки по `ATS_RESUME_MATCH_BATCH_SIZE` (по умолчанию `3`) и уходят в `EvaluateResumeMatches`.
3. Запуски пишутся в `score_run` с `direction=RESUME_TO_VACANCIES`, `resume_id`, `vacancy_id`.
4. `GET /resumes/{resumeId}/ranking` — рейтинг вакансий.
5. `GET /resumes/{resumeId}/vacancies/{vacancyId}/report` — отчёт по паре.
6. `POST /resumes/{resumeId}/match` — пересчёт по сохранённому `ParsedResume` без LLM.
7. Прогресс — `GET /screening-batches/{batchId}`.

---

## 3. Скрининг «кандидаты под вакансию»

`POST /api/v1/vacancies/{vacancyId}/screen`:

```json
{ "applicationIds": ["…"] }
```

Если `applicationIds` пуст или отсутствует, берутся все отклики вакансии в статусах `SCORED`, `SCORING`, `SCORING_FAILED`. Ответ `202`:

```json
{ "batchId": "…", "status": "PENDING" }
```

Worker:

1. Получает текущий шаблон вакансии.
2. Если отклик ещё в `SCORING`, ждёт до `ats.screening.scoring-wait`; по дедлайну — `FAILED` и ошибка пакета.
3. Если `PRELIMINARY` посчитан по другой версии шаблона или отсутствует, пересчитывает.
4. Один раз вызывает `DecideScreening(vacancyId, candidates[])` на всю пачку.
5. Для `SHORTLISTED` с `consent_github=true` запускает GitHub только по конкретным навыкам.
6. Пишет `FINAL` отдельным `score_run`. Сейчас это копия `PRELIMINARY`, потому что в контракте нет `Refine`.
7. `application.status` становится `REFINED` или `ENRICHED`.

GitHub-факты сохраняются в `external_evidence` и показываются в отчёте. До появления `Refine` они не меняют `Score`; это ограничение контракта, а не штраф за отсутствие внешних данных.

Статус пакета: `GET /api/v1/screening-batches/{batchId}` → `PENDING`, `RUNNING`, `COMPLETED`, `FAILED`, плюс `total`, `processed`, `failed`, `errorMessage`.

---

## 4. Рейтинг и отчёт

`GET /api/v1/vacancies/{vacancyId}/ranking?phase=PRELIMINARY|FINAL` — место, релевантность, балл, вилка, cap, решение шорт-листа, флаги. По умолчанию `FINAL`.

`GET /api/v1/applications/{applicationId}/report?phase=PRELIMINARY|FINAL` — отчёт «требование → доказательство → пробел». В элементе требования есть `relevance` (`R_i`), `cR` (покрытие `c_i`), `tenure`, статус, цитата и комментарий. `blind=true` означает, что PII в ответе нет.

Отчёт также содержит:

- `strengths`, `risks`, `questions`, `recruiterNote` — из детерминированного каталога `feedback-templates.v2`;
- `gaps` — структурированные пробелы;
- `externalEvidence` — факты GitHub и флаги;
- `hrDecision`, `hrComment` — решение HR.

`GET /api/v1/resumes/{resumeId}/vacancies/{vacancyId}/report` — парный отчёт обратного направления; всегда читает `PRELIMINARY`.

### 4.1. Раскрытие PII

`POST /api/v1/applications/{applicationId}/reveal` с телом `{ "reason": "…" }` расшифровывает vault и возвращает имя, контакты, GitHub и имя файла; пишет `audit_event`. Образование в тексте не маскируется, поэтому `university` для новых резюме пусто.

`POST /api/v1/resumes/{resumeId}/reveal-text` раскрывает полный текст резюме с причиной и аудитом.

### 4.2. Письма кандидату

`GET /api/v1/applications/{applicationId}/candidate-messages?phase=...` — последние черновики по каждому типу.

`POST /api/v1/applications/{applicationId}/candidate-messages`:

```json
{ "kind": "INTERVIEW_INVITATION" }
```

или

```json
{ "kind": "REJECTION" }
```

- Для приглашения используются подтверждённые требования.
- Для отказа — непокрытые требования; если их нет, API возвращает `422 NO_UNCOVERED_REQUIREMENTS`.
- В LLM уходят только название вакансии и тексты требований; PII и текст резюме не передаются.
- Черновик сохраняется в `candidate_message` и не отправляется автоматически.
- В окне «Решение по отчёту» под комментарием находится блок «Письмо»: две
  вкладки, одна кнопка «Сгенерировать» / «Сгенерировать заново» и копирование.
  Для отказа без непокрытых требований генерация недоступна.

---

## 5. Демо-данные

- Вакансия: `00000000-0000-0000-0000-000000000001`.
- Версия шаблона: `00000000-0000-0000-0000-000000000002`.
- Требования: Java (critical), Spring Boot, PostgreSQL, Kafka, Docker, Kubernetes.

---

## 6. Ограничения

## 7. Ограничения MVP

- `scoring-service` считает детерминированную модель: сходство признаков `S`,
  убедительность `t`, стаж и свежесть `τ`, покрытие `c_i`, `Score` и границы.
  LLM вызывается только для извлечения признаков из обезличенного текста.
- gRPC-ответ `EvaluateResumeMatch` содержит `strengths`, `risks`, `questions` и
  `recruiter_note`; они собираются по каталогу `feedback-templates.v3` без LLM.
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
- Нет авторизации, PDF-отчёта и импорта с hh.
- `recency` не извлекается из резюме, поэтому фактор свежести в обычном сценарии всегда неизвестен.
- `score_run` не хранит `resolver_version` и `dictionary_mode`.

Подробнее — [`known-issues.md`](known-issues.md).
