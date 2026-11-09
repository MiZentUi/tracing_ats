--liquibase formatted sql

--changeset tracing-ats:009-requirement-skills-and-kinds
-- В MVP нет числовой важности: тип требования (kind) - единственная разметка.
-- weight удаляется, добавляются поля источника типа и версии шаблона.
ALTER TABLE vacancy_requirement DROP CONSTRAINT IF EXISTS ck_requirement_weight;
ALTER TABLE vacancy_requirement DROP COLUMN IF EXISTS weight;

-- Название навыка для NEW-навыков до момента сохранения шаблона.
ALTER TABLE vacancy_requirement ADD COLUMN skill_text varchar(128);
ALTER TABLE vacancy_requirement ADD COLUMN kind_source varchar(8) NOT NULL DEFAULT 'LLM';
ALTER TABLE vacancy_requirement ADD COLUMN llm_kind varchar(8);

ALTER TABLE vacancy_requirement DROP CONSTRAINT IF EXISTS ck_requirement_subject;
ALTER TABLE vacancy_requirement ADD CONSTRAINT ck_requirement_kind_source
    CHECK (kind_source IN ('LLM', 'HISTORY', 'HR'));
ALTER TABLE vacancy_requirement ADD CONSTRAINT ck_requirement_llm_kind
    CHECK (llm_kind IS NULL OR llm_kind IN ('MUST', 'NICE', 'OPTIONAL'));
ALTER TABLE vacancy_requirement ADD CONSTRAINT ck_requirement_subject
    CHECK (
        skill_id IS NOT NULL
        OR btrim(coalesce(skill_text, '')) <> ''
        OR btrim(coalesce(raw_text, '')) <> ''
    );

-- Версия 1 приходит из LLM и не подтверждена; версии из PUT подтверждены.
ALTER TABLE vacancy_template_version ADD COLUMN is_confirmed boolean NOT NULL DEFAULT false;
ALTER TABLE vacancy_template_version ADD COLUMN llm_model varchar(128);
ALTER TABLE vacancy_template_version ADD COLUMN dropped_requirements integer NOT NULL DEFAULT 0;

--rollback ALTER TABLE vacancy_template_version DROP COLUMN dropped_requirements;
--rollback ALTER TABLE vacancy_template_version DROP COLUMN llm_model;
--rollback ALTER TABLE vacancy_template_version DROP COLUMN is_confirmed;
--rollback ALTER TABLE vacancy_requirement DROP CONSTRAINT IF EXISTS ck_requirement_subject;
--rollback ALTER TABLE vacancy_requirement ADD CONSTRAINT ck_requirement_subject CHECK (skill_id IS NOT NULL OR btrim(coalesce(raw_text, '')) <> '');
--rollback ALTER TABLE vacancy_requirement DROP CONSTRAINT IF EXISTS ck_requirement_llm_kind;
--rollback ALTER TABLE vacancy_requirement DROP CONSTRAINT IF EXISTS ck_requirement_kind_source;
--rollback ALTER TABLE vacancy_requirement DROP COLUMN llm_kind;
--rollback ALTER TABLE vacancy_requirement DROP COLUMN kind_source;
--rollback ALTER TABLE vacancy_requirement DROP COLUMN skill_text;
--rollback ALTER TABLE vacancy_requirement ADD COLUMN weight smallint NOT NULL DEFAULT 1;
--rollback ALTER TABLE vacancy_requirement ADD CONSTRAINT ck_requirement_weight CHECK (weight BETWEEN 1 AND 3);
