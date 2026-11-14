--liquibase formatted sql

--changeset tracing-ats:resume-027-vacancy-experience
-- Общий требуемый стаж вакансии. Справочное поле для UI; в скоринге не участвует.
ALTER TABLE vacancy ADD COLUMN experience_years integer;
ALTER TABLE vacancy
    ADD CONSTRAINT ck_vacancy_experience CHECK (
        experience_years IS NULL OR experience_years BETWEEN 0 AND 50
    );
--rollback ALTER TABLE vacancy DROP CONSTRAINT ck_vacancy_experience;
--rollback ALTER TABLE vacancy DROP COLUMN experience_years;
