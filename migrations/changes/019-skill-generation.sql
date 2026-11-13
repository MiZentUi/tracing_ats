--liquibase formatted sql

--changeset tracing-ats:019-skill-generation
-- Черновики генерации навыков по специальности. Живут в public: это админский
-- staging ats-service, боевой словарь по-прежнему принадлежит dictionary-service.
CREATE TABLE IF NOT EXISTS skill_generation_job (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    specialization varchar(256) NOT NULL,
    note text,
    status varchar(16) NOT NULL DEFAULT 'PENDING',
    requested_by varchar(128),
    model varchar(128),
    prompt_version varchar(64),
    max_skills integer NOT NULL DEFAULT 30,
    error text,
    raw_response jsonb,
    created_at timestamptz NOT NULL DEFAULT now(),
    started_at timestamptz,
    finished_at timestamptz
);
CREATE INDEX IF NOT EXISTS ix_skill_generation_job_status
    ON skill_generation_job (status, created_at);

CREATE TABLE IF NOT EXISTS skill_generation_draft (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id uuid NOT NULL REFERENCES skill_generation_job (id) ON DELETE CASCADE,
    position smallint NOT NULL,
    canonical varchar(256) NOT NULL,
    skill_type varchar(16) NOT NULL DEFAULT 'OTHER',
    target_skill_id uuid,
    target_skill_name varchar(256),
    conflict_skill_id uuid,
    conflict_skill_name varchar(256),
    status varchar(16) NOT NULL DEFAULT 'DRAFT',
    CONSTRAINT uq_skill_generation_draft_position UNIQUE (job_id, position)
);
CREATE INDEX IF NOT EXISTS ix_skill_generation_draft_job
    ON skill_generation_draft (job_id);

CREATE TABLE IF NOT EXISTS skill_generation_alias (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    draft_id uuid NOT NULL REFERENCES skill_generation_draft (id) ON DELETE CASCADE,
    position smallint NOT NULL,
    alias_raw varchar(256) NOT NULL,
    lang varchar(16) NOT NULL DEFAULT 'mixed',
    category varchar(32),
    conflict_skill_id uuid,
    conflict_skill_name varchar(256),
    status varchar(16) NOT NULL DEFAULT 'DRAFT',
    CONSTRAINT uq_skill_generation_alias_position UNIQUE (draft_id, position)
);
CREATE INDEX IF NOT EXISTS ix_skill_generation_alias_draft
    ON skill_generation_alias (draft_id);

--rollback DROP TABLE IF EXISTS skill_generation_alias;
--rollback DROP TABLE IF EXISTS skill_generation_draft;
--rollback DROP TABLE IF EXISTS skill_generation_job;
