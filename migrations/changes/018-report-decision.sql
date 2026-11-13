--liquibase formatted sql

--changeset tracing-ats:resume-022-report-decision
-- Решение HR по паре резюме × вакансия. Не заменяет решение модели
-- (application.screening_decision) и не запускает автоматических действий.
CREATE TABLE report_decision (
    id         uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    resume_id  uuid        NOT NULL REFERENCES resume (id) ON DELETE CASCADE,
    vacancy_id uuid        NOT NULL,
    decision   varchar(16) NOT NULL,
    comment    text        NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_report_decision_pair UNIQUE (resume_id, vacancy_id),
    CONSTRAINT ck_report_decision CHECK (
        decision IN ('SHORTLISTED', 'BORDERLINE', 'NOT_SHORTLISTED')
    ),
    CONSTRAINT ck_report_decision_comment CHECK (length(comment) BETWEEN 3 AND 2000)
);
CREATE INDEX ix_report_decision_vacancy ON report_decision (vacancy_id, updated_at DESC);
--rollback DROP TABLE report_decision;
