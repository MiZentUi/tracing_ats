--liquibase formatted sql

--changeset tracing-ats:resume-021-resume-title
-- Название резюме задаёт HR. В отличие от имени файла, оно не считается PII
-- и может показываться в списках и карточках.
ALTER TABLE resume ADD COLUMN title varchar(255);
--rollback ALTER TABLE resume DROP COLUMN title;
