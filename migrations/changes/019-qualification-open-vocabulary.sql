--liquibase formatted sql

--changeset tracing-ats:vacancy-021-qualification
-- Открытая квалификация хранится в существующем skill_text как label.
-- Отдельного закрытого перечня групп и категорий нет.
ALTER TABLE vacancy_requirement
    ADD COLUMN qualification boolean NOT NULL DEFAULT false;
--rollback ALTER TABLE vacancy_requirement DROP COLUMN qualification;
