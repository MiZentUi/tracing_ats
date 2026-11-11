--liquibase formatted sql

--changeset tracing-ats:012-skill-kind-stat
-- Память расстановки HR: сколько раз навык сохранён как MUST, NICE и OPTIONAL
-- для пары «специализация × уровень».
CREATE TABLE skill_kind_stat (
    specialization_id uuid        NOT NULL REFERENCES specialization (id) ON DELETE RESTRICT,
    level_id          uuid        NOT NULL REFERENCES level (id) ON DELETE RESTRICT,
    skill_id          uuid        NOT NULL REFERENCES skill (id) ON DELETE RESTRICT,
    must_count        integer     NOT NULL DEFAULT 0,
    nice_count        integer     NOT NULL DEFAULT 0,
    optional_count    integer     NOT NULL DEFAULT 0,
    updated_at        timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (specialization_id, level_id, skill_id),
    CONSTRAINT ck_skill_kind_stat_nonneg
        CHECK (must_count >= 0 AND nice_count >= 0 AND optional_count >= 0)
);
CREATE INDEX ix_skill_kind_stat_skill ON skill_kind_stat (skill_id);
--rollback DROP TABLE skill_kind_stat;
