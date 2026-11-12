# Отчёт 007 — обратный подбор и новый контракт scoring

Ветка `feature/resume-to-vacancy-matching`, 12 коммитов от `main` (`6e444d2`).

## Что сделано

### Java-сервис
- `ats-service/.../scoring/GrpcScoringClient.java` — переведён с legacy `ScoreResume` на `ExtractResume` + `EvaluateResumeMatch`/`EvaluateResumeMatches`; маппинг `general_score`, `FeatureMatch`, `gaps`.
- `ats-service/.../domain/scoring/ScoringResult.java` — статусы `COVERED`, `PARTIAL`, `GAP`, `NOT_STATED`, `ABSENT`; добавлены strengths/risks/questions/recruiterNote.
- `ats-service/.../entity/ResumeExtractionEntity.java`, `.../screening/PreliminaryScoringService.java`, `.../screening/ResumeMatchService.java` — разбор резюме один раз, снимок `ParsedResume` в неизменяемой `resume_extraction`.
- `ats-service/.../screening/ResumeMatchService.java`, `ResumeMatchListener.java` — обратный режим: все вакансии чанками по `ats.resume.match.batch-size` (3).
- `ats-service/.../ingestion/ResumeIngestionService.java` — `vacancyId` необязателен; без него отклик не создаётся.
- `ats-service/.../api/ResumeRankingController.java` — `GET /resumes/{id}/ranking`, `POST /resumes/{id}/match`.
- `ats-service/.../api/ResumeController.java`, `ResumeQueryService.java` — `GET /resumes` (история), `GET /resumes/{id}/text`, `POST /resumes/{id}/reveal-text`.
- `ats-service/.../report/ReportService.java`, `RevealService.java` — отчёт отдаёт `resumeId`/`vacancyId`, раскрытие текста пишет `REVEAL_RESUME_TEXT` в `audit_event`.
- `ats-service/.../pii/RegexPiiDetector.java`, `ner/NerPiiDetector.java` — образование больше не маскируется; `EducationPiiDetector.java` удалён.

### Миграции и контракты
- `migrations/changes/016-resume-vacancy-matching.sql` — `direction`, nullable-владельцы в `screening_batch`/`score_run`, таблица `resume_extraction`, статусы требований и резюме.
- `contracts/openapi.yaml` — новые эндпоинты и схемы: ranking/match/report по паре, текст резюме, reveal-text, история, batchId в загрузке.
- `AGENTS.md`, `docs/adr/0001-local-pii-masking-with-ner.md`, `docs/resume-pipeline.md` — зафиксировано решение не маскировать образование.

### Frontend
- `frontend/src/views/MatchView.vue` — вставка текста резюме (`pasted-resume-N.txt`), автопереход в разборы.
- `frontend/src/views/AnalysesView.vue` — новая вкладка «Разборы» со статусами и автообновлением.
- `frontend/src/views/ResumeRankingView.vue`, `VacancyCandidatesView.vue` — рейтинги и прогресс пакета.
- `frontend/src/views/ReportView.vue`, `CompareView.vue`, `ResumeTextView.vue` — отчёт, две панели «вакансия/резюме», подсветка подтверждённых навыков, возврат к разбору.
- `frontend/src/router/index.ts`, `App.vue` — маршруты и вкладки.

### Мок и инфраструктура
- `infra/mock-scoring-service/**` — заглушка всех 4 RPC, сценарии, тесты, Dockerfile, README.
- `compose.yaml`, `.env.example` — профиль `mock-scoring`, `SCORING_GRPC_TARGET`, параметры LLM скоринга и `ATS_SCORING_DEADLINE`.
- `.github/workflows/ci.yml` — джоба `mock-scoring`.
- `scoring-service/src/api/extraction/extractor.py` — исправлено `droppereturnd` → `dropped`, удалён `print(raw)`.

## Проверка
- `ats-service`: `./gradlew test spotlessCheck --no-daemon` → BUILD SUCCESSFUL; `ResumePipelineIntegrationTest` проходит оба режима, нарезку 4 вакансий по 2.
- `scoring-service`: `tests/test_extraction.py` → 19 passed. Полный набор → 67 passed, 3 failed (до наших изменений).
- `infra/mock-scoring-service`: pytest → 22 passed; Docker-образ собран, контейнер healthy, gRPC-вызов и healthcheck прошли.
- `frontend`: Docker-сборка `vue-tsc -b && vite build` → успешно.
- E2E на реальной модели OpenRouter: обратный режим — 24 вакансии, 8 вызовов `EvaluateResumeMatches`, `MATCHED 24/24`; прямой — `SCORED`, скрининг `COMPLETED 1/1`, `FINAL`-рейтинг; `reveal-text` → 200 и запись аудита.
- `docker compose config --quiet` → ok.
- Не запускалось: frontend `vitest`, полный зелёный прогон `scoring-service`.

## Не сделано / проблемы
- `OPTIONAL` в требованиях оставлен по указанию «пока не удаляем».
- Математика скоринга в `scoring-service` остаётся заглушкой (`coverage = relevance`, `Score` 0.5/0.4/0.6), поэтому баллы ~24 при шкале 100.
- `Score_final` копирует `PRELIMINARY`: в контракте нет refine, GitHub-факты в расчёт не уходят.
- 3 падающих теста `scoring-service`: обращение к `coverage_high`, которого нет в контракте, и `KeyError` в `test_llm_client.py`.
- Обратный режим сравнивает все вакансии без фильтров; шаблоны тянутся по одному `GetTemplate` на вакансию.
- Оригинальный текст резюме с PII доступен только через reveal с причиной и аудитом.

## Требует решения пользователя
- Удалять ли `OPTIONAL` из контракта, БД, промпта и UI.
- Нужен ли фильтр вакансий в обратном режиме (специализация/уровень) и какое значение `batch-size` считать основным.
- Когда подключать реальную модель скоринга вместо заглушки в `scoring-service`.
