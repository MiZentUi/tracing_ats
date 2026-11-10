# Пайплайн resume-service

Документ описывает сквозной сценарий MVP: загрузка пачки резюме → обезличивание →
предварительный скор → шорт-лист → GitHub → итоговый скор → рейтинг и отчёт.
Арифметика пока считается временным Java-портом (ADR 0002); политика шорт-листа
приходит из `vacancy-service` (ADR 0004).

## 1. Загрузка

`POST /api/v1/resumes` (`multipart/form-data`)

| Поле | Обязательно | Смысл |
|---|---|---|
| `vacancyId` | да | вакансия, к которой относятся отклики |
| `files` | да | PDF, DOCX, TXT или MD; несколько файлов за запрос |
| `consentGithub` | нет | согласие кандидата на обогащение из GitHub, по умолчанию `false` |

Для каждого файла:

1. Тип определяется по сигнатуре, имя файла — только подсказка.
2. `text-extraction` извлекает и нормализует текст.
3. PII маскируется: regex + локальная NER + контекст образования (ADR 0001).
4. Prompt injection удаляется детерминированно (ADR 0005).
5. Оригинал кладётся в MinIO, PII — в AES-256-GCM `pii_vault`, обезличенный текст —
   в `parsed_resume.masked_text`.
6. Создаются `candidate`, `resume` и `application` со статусом `MASKED`.

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

## 2. Скрининг

`POST /api/v1/vacancies/{vacancyId}/screen`

```json
{ "applicationIds": ["…"] }
```

Если `applicationIds` пуст или отсутствует, берутся все `application` вакансии в
статусе `MASKED`. Ответ — `202`:

```json
{ "batchId": "…", "status": "PENDING" }
```

Пустая пачка сразу получает `COMPLETED`.

Асинхронный worker:

1. `GetTemplate(vacancyId)` в `vacancy-service` — требования, веса, версии промптов
   и версия весов (`scoring_weights.version`).
2. `Score_pre` по обезличенному тексту для каждого кандидата; запуск пишется в
   `score_run` с `phase=PRELIMINARY`, `scoring_version`, `prompt_version`,
   `model_version`, `weights_version`.
3. Один вызов `DecideScreening(vacancyId, candidates[])` на всю пачку. Top-N нельзя
   решить по одному кандидату.
4. Для `SHORTLISTED` с `consent_github=true` — GitHub, только по конкретным навыкам
   (ADR 0003). Ошибка или отсутствие GitHub не уменьшают балл.
5. `Score_final` пишется отдельным запуском `phase=FINAL`. `Score_pre` и `Score_final`
   не смешиваются.
6. `application.status` становится `REFINED` или `ENRICHED`.

Нормализованные факты об образовании (уровень, степень, специальность, годы) передаются
в `ScoringRequest` отдельно от текста, но текущая модель их не использует: годы обучения
не считаются рабочим стажем.

Статус пакета: `GET /api/v1/screening-batches/{batchId}` → `PENDING`, `RUNNING`,
`COMPLETED` или `FAILED`, плюс `total`, `processed`, `failed`, `errorMessage`.
Если `vacancy-service` недоступен, пакет получает `FAILED`; локальная политика
молча не подставляется.

## 3. Рейтинг и отчёт

`GET /api/v1/vacancies/{vacancyId}/ranking?phase=PRELIMINARY|FINAL` — место, балл,
вилка, cap, решение шорт-листа, флаги. По умолчанию `FINAL`.

`GET /api/v1/applications/{applicationId}/report?phase=PRELIMINARY|FINAL` — матрица
«требование → доказательство → пробел», внешние факты и флаги. Поле `blind=true`
означает, что PII в ответе нет.

`POST /api/v1/applications/{applicationId}/reveal` с телом `{ "reason": "…" }` —
отдельное действие: расшифровывает vault, возвращает имя, контакты, вуз, год выпуска,
GitHub и имя файла и пишет `audit_event` с причиной.

## 4. Демо-данные

- Вакансия: `00000000-0000-0000-0000-000000000001`
- Версия шаблона: `00000000-0000-0000-0000-000000000002`
- Требования: Java (critical), Spring Boot, PostgreSQL, Kafka, Docker, Kubernetes.

## 5. Docker Compose

`docker compose up -d --wait` поднимает PostgreSQL, миграции, S3-совместимое
хранилище и оба Java-сервиса. Официальные community-образы MinIO удалены из
Docker Hub и Quay в 2026, поэтому compose использует API-совместимый форк
`pgsty/minio` и клиент `pgsty/mc` (версии закреплены, переопределяются через
`ATS_MINIO_IMAGE` и `ATS_MINIO_MC_IMAGE`). Для реальной NER-модели смонтируйте
`models/` и включите `ATS_PII_NER_PROVIDER=onnx`.

## 6. Ограничения MVP

- `LocalScoringClient` — детерминированная заглушка вместо `scoring-service`.
- Реальная NER-модель не входит в репозиторий; CI использует fake-NER.
  Compose по умолчанию тоже запускается с `ATS_PII_NER_PROVIDER=fake`, поэтому
  маскирует только regex-PII, но не имена и вуз. Для реальных данных скачайте
  модель (`scripts/download-ner.sh`), задайте `ATS_PII_NER_PROVIDER=onnx` и
  смонтируйте каталог `models/` в `/models` (это уже описано в `compose.yaml`).
- GitHub выключен по умолчанию (`ATS_GITHUB_ENABLED=false`).
- Нет авторизации, PDF-отчёта, письма отказа и импорта с hh.
