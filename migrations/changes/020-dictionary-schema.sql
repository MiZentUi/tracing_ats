--liquibase formatted sql

--changeset tracing-ats:020-dictionary-schema
-- Схема dictionary принадлежит dictionary-service. Отдельная схема, потому что
-- public.skill и public.skill_alias уже заняты ats-service.
CREATE SCHEMA IF NOT EXISTS dictionary;

CREATE TABLE IF NOT EXISTS dictionary.skill (
    id uuid PRIMARY KEY,
    status text NOT NULL DEFAULT 'ACTIVE',
    skill_type text NOT NULL DEFAULT 'OTHER',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS dictionary.skill_alias (
    id uuid PRIMARY KEY,
    skill_id uuid NOT NULL REFERENCES dictionary.skill (id) ON DELETE CASCADE,
    alias_raw text NOT NULL,
    is_canonical boolean NOT NULL DEFAULT false,
    lang text NOT NULL DEFAULT 'mixed',
    source text NOT NULL DEFAULT 'HR',
    lemma_mode text NOT NULL DEFAULT 'AUTO',
    created_by text NOT NULL DEFAULT '',
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_dictionary_alias_canonical
    ON dictionary.skill_alias (skill_id) WHERE is_canonical;

CREATE TABLE IF NOT EXISTS dictionary.skill_alias_key (
    key text PRIMARY KEY,
    alias_id uuid NOT NULL REFERENCES dictionary.skill_alias (id) ON DELETE CASCADE,
    kind text NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_dictionary_alias_key_alias
    ON dictionary.skill_alias_key (alias_id);

CREATE TABLE IF NOT EXISTS dictionary.review_item (
    id uuid PRIMARY KEY,
    key text NOT NULL UNIQUE,
    sample_masked text[] NOT NULL DEFAULT '{}',
    occurrences bigint NOT NULL DEFAULT 1,
    status text NOT NULL DEFAULT 'PENDING',
    suggested_skill_id uuid,
    suggested_confidence double precision,
    candidates jsonb NOT NULL DEFAULT '[]'::jsonb,
    resolved_skill_id uuid,
    resolved_by text,
    resolved_at timestamptz,
    first_seen timestamptz NOT NULL DEFAULT now(),
    last_seen timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_dictionary_review_status
    ON dictionary.review_item (status, first_seen);

CREATE TABLE IF NOT EXISTS dictionary.dictionary_meta (
    id smallint PRIMARY KEY CHECK (id = 1),
    dictionary_version bigint NOT NULL DEFAULT 1,
    embedding_model_id text NOT NULL DEFAULT '',
    config_hash text NOT NULL DEFAULT '',
    pymorphy_version text NOT NULL DEFAULT 'none',
    homoglyph_rules_version text NOT NULL DEFAULT 'v1'
);
INSERT INTO dictionary.dictionary_meta (id)
VALUES (1)
ON CONFLICT (id) DO NOTHING;

CREATE TABLE IF NOT EXISTS dictionary.dictionary_snapshot (
    resolver_version text PRIMARY KEY,
    payload jsonb NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
--rollback DROP SCHEMA IF EXISTS dictionary CASCADE;
