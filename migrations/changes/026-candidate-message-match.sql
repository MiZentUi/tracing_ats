--liquibase formatted sql

--changeset tracing-ats:resume-026-candidate-message-match
-- Письма доступны и для отчёта по паре «резюме × вакансия», где отклика нет.
ALTER TABLE candidate_message ALTER COLUMN application_id DROP NOT NULL;
ALTER TABLE candidate_message ADD COLUMN resume_id uuid REFERENCES resume (id) ON DELETE CASCADE;
ALTER TABLE candidate_message ADD COLUMN vacancy_id uuid;

UPDATE candidate_message cm
SET resume_id = a.resume_id,
    vacancy_id = a.vacancy_id
FROM application a
WHERE cm.application_id = a.id
  AND (cm.resume_id IS NULL OR cm.vacancy_id IS NULL);

ALTER TABLE candidate_message ALTER COLUMN resume_id SET NOT NULL;
ALTER TABLE candidate_message ALTER COLUMN vacancy_id SET NOT NULL;
ALTER TABLE candidate_message
    ADD CONSTRAINT ck_candidate_message_owner CHECK (
        application_id IS NOT NULL OR (resume_id IS NOT NULL AND vacancy_id IS NOT NULL)
    );
CREATE INDEX ix_candidate_message_match
    ON candidate_message (resume_id, vacancy_id, kind, created_at DESC);
--rollback DROP INDEX ix_candidate_message_match;
--rollback ALTER TABLE candidate_message DROP CONSTRAINT ck_candidate_message_owner;
--rollback ALTER TABLE candidate_message ALTER COLUMN resume_id DROP NOT NULL;
--rollback ALTER TABLE candidate_message ALTER COLUMN vacancy_id DROP NOT NULL;
--rollback ALTER TABLE candidate_message DROP COLUMN vacancy_id;
--rollback ALTER TABLE candidate_message DROP COLUMN resume_id;
--rollback ALTER TABLE candidate_message ALTER COLUMN application_id SET NOT NULL;
