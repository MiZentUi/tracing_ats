--liquibase formatted sql

--changeset tracing-ats:024-education-requirement
-- Требование к образованию хранит уровень отдельно, а специальность — в skill_text.
ALTER TABLE vacancy_requirement
    ADD COLUMN IF NOT EXISTS education_level varchar(32);

ALTER TABLE vacancy_requirement
    DROP CONSTRAINT IF EXISTS ck_requirement_education_level;

ALTER TABLE vacancy_requirement
    ADD CONSTRAINT ck_requirement_education_level
    CHECK (
        education_level IS NULL
        OR education_level IN (
            'SECONDARY',
            'SECONDARY_SPECIALIZED',
            'BACHELOR',
            'MASTER',
            'DOCTOR',
            'COURSES'
        )
    );
--rollback ALTER TABLE vacancy_requirement DROP CONSTRAINT IF EXISTS ck_requirement_education_level;
--rollback ALTER TABLE vacancy_requirement DROP COLUMN IF EXISTS education_level;
