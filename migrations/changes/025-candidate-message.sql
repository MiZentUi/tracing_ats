--liquibase formatted sql

--changeset tracing-ats:resume-025-candidate-message
-- Черновики писем кандидату. Каждая генерация — отдельная неизменяемая строка.
-- Текст не содержит PII: имя и контакты добавляет HR после проверки.
CREATE TABLE candidate_message (
    id             uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id uuid         NOT NULL REFERENCES application (id) ON DELETE CASCADE,
    score_run_id   uuid         NOT NULL REFERENCES score_run (id) ON DELETE CASCADE,
    phase          varchar(16)  NOT NULL,
    kind           varchar(32)  NOT NULL,
    subject        varchar(200) NOT NULL,
    body           text         NOT NULL,
    prompt_version varchar(64)  NOT NULL,
    model_version  varchar(128),
    created_at     timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_candidate_message_phase CHECK (phase IN ('PRELIMINARY', 'FINAL')),
    CONSTRAINT ck_candidate_message_kind CHECK (kind IN ('INTERVIEW_INVITATION', 'REJECTION')),
    CONSTRAINT ck_candidate_message_subject CHECK (length(subject) BETWEEN 3 AND 200),
    CONSTRAINT ck_candidate_message_body CHECK (length(body) BETWEEN 80 AND 3000)
);
CREATE INDEX ix_candidate_message_run
    ON candidate_message (score_run_id, kind, created_at DESC);
--rollback DROP TABLE candidate_message;

--changeset tracing-ats:resume-025-candidate-message-immutable splitStatements:false
CREATE TRIGGER trg_candidate_message_immutable
    BEFORE UPDATE ON candidate_message
    FOR EACH ROW EXECUTE FUNCTION forbid_update();
--rollback DROP TRIGGER IF EXISTS trg_candidate_message_immutable ON candidate_message;
