--liquibase formatted sql

--changeset tracing-ats:resume-016-matching-direction
-- Скоринг один и тот же для двух направлений. Чтобы не дублировать score_run
-- и requirement_result, запуск получает направление и nullable-владельца:
-- отклик (кандидаты под вакансию) или пару резюме × вакансия (вакансии под резюме).
ALTER TABLE screening_batch
    ADD COLUMN direction varchar(32) NOT NULL DEFAULT 'VACANCY_TO_CANDIDATES';
ALTER TABLE screening_batch
    ADD COLUMN resume_id uuid REFERENCES resume (id) ON DELETE CASCADE;
ALTER TABLE screening_batch ALTER COLUMN vacancy_id DROP NOT NULL;
ALTER TABLE screening_batch
    ADD CONSTRAINT ck_screening_batch_direction
    CHECK (direction IN ('VACANCY_TO_CANDIDATES', 'RESUME_TO_VACANCIES'));
ALTER TABLE screening_batch
    ADD CONSTRAINT ck_screening_batch_target
    CHECK (
        (direction = 'VACANCY_TO_CANDIDATES' AND vacancy_id IS NOT NULL)
        OR (direction = 'RESUME_TO_VACANCIES' AND resume_id IS NOT NULL)
    );
CREATE INDEX ix_screening_batch_resume ON screening_batch (resume_id);
--rollback DROP INDEX ix_screening_batch_resume;
--rollback ALTER TABLE screening_batch DROP CONSTRAINT ck_screening_batch_target;
--rollback ALTER TABLE screening_batch DROP CONSTRAINT ck_screening_batch_direction;
--rollback ALTER TABLE screening_batch ALTER COLUMN vacancy_id SET NOT NULL;
--rollback ALTER TABLE screening_batch DROP COLUMN resume_id;
--rollback ALTER TABLE screening_batch DROP COLUMN direction;

--changeset tracing-ats:resume-017-resume-extraction
-- ParsedResume из scoring-service хранится отдельными неизменяемыми снимками:
-- повторный расчёт берёт последний и LLM не вызывает. parsed_resume остаётся
-- неизменяемым, как и раньше.
CREATE TABLE resume_extraction (
    id                 uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    resume_id          uuid        NOT NULL REFERENCES resume (id) ON DELETE CASCADE,
    extracted_features text        NOT NULL,
    extraction_version varchar(32) NOT NULL,
    created_at         timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_resume_extraction_resume ON resume_extraction (resume_id, created_at DESC);
--rollback DROP TABLE resume_extraction;

--changeset tracing-ats:resume-018-score-run-direction
ALTER TABLE score_run ALTER COLUMN application_id DROP NOT NULL;
ALTER TABLE score_run
    ADD COLUMN resume_id uuid REFERENCES resume (id) ON DELETE CASCADE;
ALTER TABLE score_run
    ADD COLUMN vacancy_id uuid;
ALTER TABLE score_run
    ADD COLUMN vacancy_title varchar(512);
ALTER TABLE score_run
    ADD COLUMN direction varchar(32) NOT NULL DEFAULT 'VACANCY_TO_CANDIDATES';
ALTER TABLE score_run
    ADD CONSTRAINT ck_score_run_direction
    CHECK (direction IN ('VACANCY_TO_CANDIDATES', 'RESUME_TO_VACANCIES'));
ALTER TABLE score_run
    ADD CONSTRAINT ck_score_run_target
    CHECK (
        (direction = 'VACANCY_TO_CANDIDATES' AND application_id IS NOT NULL)
        OR (
            direction = 'RESUME_TO_VACANCIES'
            AND resume_id IS NOT NULL
            AND vacancy_id IS NOT NULL
        )
    );
CREATE UNIQUE INDEX uq_score_run_match_phase
    ON score_run (batch_id, resume_id, vacancy_id, phase)
    WHERE direction = 'RESUME_TO_VACANCIES';
CREATE INDEX ix_score_run_match
    ON score_run (resume_id, phase, created_at DESC)
    WHERE direction = 'RESUME_TO_VACANCIES';
--rollback DROP INDEX ix_score_run_match;
--rollback DROP INDEX uq_score_run_match_phase;
--rollback ALTER TABLE score_run DROP CONSTRAINT ck_score_run_target;
--rollback ALTER TABLE score_run DROP CONSTRAINT ck_score_run_direction;
--rollback ALTER TABLE score_run DROP COLUMN direction;
--rollback ALTER TABLE score_run DROP COLUMN vacancy_title;
--rollback ALTER TABLE score_run DROP COLUMN vacancy_id;
--rollback ALTER TABLE score_run DROP COLUMN resume_id;
--rollback ALTER TABLE score_run ALTER COLUMN application_id SET NOT NULL;

--changeset tracing-ats:resume-019-requirement-statuses
-- Статусы ТЗ: полное, частичное, отсутствие и «не указано».
ALTER TABLE requirement_result DROP CONSTRAINT ck_requirement_result_status;
ALTER TABLE requirement_result ALTER COLUMN status TYPE varchar(16);
ALTER TABLE requirement_result
    ADD CONSTRAINT ck_requirement_result_status
    CHECK (status IN ('COVERED', 'PARTIAL', 'GAP', 'NOT_STATED', 'ABSENT'));
--rollback ALTER TABLE requirement_result DROP CONSTRAINT ck_requirement_result_status;
--rollback ALTER TABLE requirement_result
--rollback     ADD CONSTRAINT ck_requirement_result_status
--rollback     CHECK (status IN ('COVERED', 'PARTIAL', 'GAP'));
--rollback ALTER TABLE requirement_result ALTER COLUMN status TYPE varchar(8);

--changeset tracing-ats:resume-020-resume-match-statuses
ALTER TABLE resume DROP CONSTRAINT ck_resume_status;
ALTER TABLE resume
    ADD CONSTRAINT ck_resume_status
    CHECK (status IN (
        'UPLOADED', 'TEXT_EXTRACTED', 'MASKED', 'TEXT_EMPTY', 'MASKING_FAILED', 'FAILED',
        'EXTRACTED', 'MATCHING', 'MATCHED', 'MATCH_FAILED'
    ));
--rollback ALTER TABLE resume DROP CONSTRAINT ck_resume_status;
--rollback ALTER TABLE resume
--rollback     ADD CONSTRAINT ck_resume_status
--rollback     CHECK (status IN (
--rollback         'UPLOADED', 'TEXT_EXTRACTED', 'MASKED', 'TEXT_EMPTY', 'MASKING_FAILED', 'FAILED'
--rollback     ));
