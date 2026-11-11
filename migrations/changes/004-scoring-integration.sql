--liquibase formatted sql

--changeset tracing-ats:resume-017-score-run-per-upload
-- Предварительный запуск считается сразу после загрузки, когда пакета ещё нет.
ALTER TABLE score_run ALTER COLUMN batch_id DROP NOT NULL;
--rollback ALTER TABLE score_run ALTER COLUMN batch_id SET NOT NULL;

--changeset tracing-ats:resume-018-score-run-template-and-relevance
-- Версия шаблона позволяет отличить устаревший PRELIMINARY от актуального.
ALTER TABLE score_run ADD COLUMN template_version integer;
ALTER TABLE score_run ADD COLUMN relevance numeric(4,3);
ALTER TABLE score_run ADD CONSTRAINT ck_score_run_relevance
    CHECK (relevance IS NULL OR relevance BETWEEN 0 AND 1);
--rollback ALTER TABLE score_run DROP CONSTRAINT ck_score_run_relevance;
--rollback ALTER TABLE score_run DROP COLUMN relevance;
--rollback ALTER TABLE score_run DROP COLUMN template_version;

--changeset tracing-ats:resume-019-requirement-result-scoring-fields
ALTER TABLE requirement_result ADD COLUMN relevance numeric(4,3);
ALTER TABLE requirement_result ADD COLUMN tenure integer;
ALTER TABLE requirement_result ALTER COLUMN m DROP NOT NULL;
ALTER TABLE requirement_result ALTER COLUMN t DROP NOT NULL;
ALTER TABLE requirement_result ALTER COLUMN rho DROP NOT NULL;
ALTER TABLE requirement_result ADD CONSTRAINT ck_requirement_result_relevance
    CHECK (relevance IS NULL OR relevance BETWEEN 0 AND 1);
ALTER TABLE requirement_result ADD CONSTRAINT ck_requirement_result_tenure
    CHECK (tenure IS NULL OR tenure >= 0);
--rollback ALTER TABLE requirement_result DROP CONSTRAINT ck_requirement_result_tenure;
--rollback ALTER TABLE requirement_result DROP CONSTRAINT ck_requirement_result_relevance;
--rollback ALTER TABLE requirement_result ALTER COLUMN rho SET NOT NULL;
--rollback ALTER TABLE requirement_result ALTER COLUMN t SET NOT NULL;
--rollback ALTER TABLE requirement_result ALTER COLUMN m SET NOT NULL;
--rollback ALTER TABLE requirement_result DROP COLUMN tenure;
--rollback ALTER TABLE requirement_result DROP COLUMN relevance;

--changeset tracing-ats:resume-020-application-scoring-status
-- Скоринг при загрузке идёт асинхронно, поэтому у отклика появляются промежуточные статусы.
ALTER TABLE application DROP CONSTRAINT ck_application_status;
ALTER TABLE application ADD CONSTRAINT ck_application_status CHECK (status IN (
    'UPLOADED', 'MASKED', 'SCORING', 'SCORED', 'SCORING_FAILED',
    'TEXT_EMPTY', 'MASKING_FAILED', 'SCREENED', 'ENRICHED', 'REFINED', 'FAILED'
));
--rollback ALTER TABLE application DROP CONSTRAINT ck_application_status;
--rollback ALTER TABLE application ADD CONSTRAINT ck_application_status CHECK (status IN (
--rollback     'UPLOADED', 'MASKED', 'TEXT_EMPTY', 'MASKING_FAILED',
--rollback     'SCREENED', 'ENRICHED', 'REFINED', 'FAILED'
--rollback ));
