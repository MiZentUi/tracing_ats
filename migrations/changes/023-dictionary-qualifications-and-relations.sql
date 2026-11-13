--liquibase formatted sql

--changeset tracing-ats:023-dictionary-qualifications-and-relations
-- Квалификации и отношения навыков входят в dictionary v1. Это отдельные
-- сущности: qualification не ссылается на skill и не участвует в skill-очереди.
CREATE TABLE IF NOT EXISTS dictionary.qualification (
    id uuid PRIMARY KEY,
    canonical_name text NOT NULL,
    status text NOT NULL DEFAULT 'ACTIVE',
    qualification_type text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS dictionary.qualification_alias (
    id uuid PRIMARY KEY,
    qualification_id uuid NOT NULL
        REFERENCES dictionary.qualification (id) ON DELETE CASCADE,
    alias_raw text NOT NULL,
    is_canonical boolean NOT NULL DEFAULT false,
    lang text NOT NULL DEFAULT 'mixed',
    source text NOT NULL DEFAULT 'HR',
    lemma_mode text NOT NULL DEFAULT 'AUTO',
    created_by text NOT NULL DEFAULT '',
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_dictionary_qualification_alias_canonical
    ON dictionary.qualification_alias (qualification_id) WHERE is_canonical;

CREATE TABLE IF NOT EXISTS dictionary.qualification_alias_key (
    key text PRIMARY KEY,
    alias_id uuid NOT NULL
        REFERENCES dictionary.qualification_alias (id) ON DELETE CASCADE,
    kind text NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_dictionary_qualification_alias_key_alias
    ON dictionary.qualification_alias_key (alias_id);

CREATE TABLE IF NOT EXISTS dictionary.qualification_review_item (
    id uuid PRIMARY KEY,
    key text NOT NULL UNIQUE,
    sample_masked text[] NOT NULL DEFAULT '{}',
    occurrences bigint NOT NULL DEFAULT 1,
    status text NOT NULL DEFAULT 'PENDING',
    suggested_qualification_id uuid,
    suggested_confidence double precision,
    candidates jsonb NOT NULL DEFAULT '[]'::jsonb,
    resolved_qualification_id uuid,
    resolved_by text,
    resolved_at timestamptz,
    first_seen timestamptz NOT NULL DEFAULT now(),
    last_seen timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_dictionary_qualification_review_status
    ON dictionary.qualification_review_item (status, first_seen);

CREATE TABLE IF NOT EXISTS dictionary.skill_relation (
    left_skill_id uuid NOT NULL REFERENCES dictionary.skill (id) ON DELETE CASCADE,
    right_skill_id uuid NOT NULL REFERENCES dictionary.skill (id) ON DELETE CASCADE,
    relation_kind text NOT NULL,
    source text NOT NULL DEFAULT 'HR',
    reviewed boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (left_skill_id, right_skill_id),
    CHECK (left_skill_id <> right_skill_id)
);
CREATE INDEX IF NOT EXISTS ix_dictionary_skill_relation_right
    ON dictionary.skill_relation (right_skill_id);

ALTER TABLE dictionary.dictionary_meta
    ADD COLUMN IF NOT EXISTS qualification_version bigint NOT NULL DEFAULT 1;
ALTER TABLE dictionary.dictionary_meta
    ADD COLUMN IF NOT EXISTS relation_version bigint NOT NULL DEFAULT 1;

--rollback DROP TABLE IF EXISTS dictionary.skill_relation;
--rollback DROP TABLE IF EXISTS dictionary.qualification_review_item;
--rollback DROP TABLE IF EXISTS dictionary.qualification_alias_key;
--rollback DROP TABLE IF EXISTS dictionary.qualification_alias;
--rollback DROP TABLE IF EXISTS dictionary.qualification;
--rollback ALTER TABLE dictionary.dictionary_meta DROP COLUMN IF EXISTS relation_version;
--rollback ALTER TABLE dictionary.dictionary_meta DROP COLUMN IF EXISTS qualification_version;
