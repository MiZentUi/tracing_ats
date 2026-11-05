--liquibase formatted sql

--changeset tracing-ats:001-scoring-weights
-- Веса итогового балла: Score = 100 * (w_m*M + w_n*N + w_f*F).
-- Строки неизменяемы по смыслу: другие веса = новая версия (новая строка).
-- Таблицей владеет Java-бэкенд, в ml-service значения уходят в gRPC-запросе.
CREATE TABLE scoring_weights (
    id         uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    version    integer      NOT NULL,
    status     varchar(16)  NOT NULL DEFAULT 'CANDIDATE',
    w_m        numeric(4,3) NOT NULL,
    w_n        numeric(4,3) NOT NULL,
    w_f        numeric(4,3) NOT NULL,
    created_at timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_scoring_weights_version UNIQUE (version),
    CONSTRAINT ck_scoring_weights_version CHECK (version >= 1),
    CONSTRAINT ck_scoring_weights_status  CHECK (status IN ('CANDIDATE', 'ACTIVE')),
    CONSTRAINT ck_scoring_weights_sum CHECK (
        w_m >= 0 AND w_n >= 0 AND w_f >= 0
        AND abs(w_m + w_n + w_f - 1) < 0.0001
    )
);
-- Одновременно активна ровно одна запись или ни одной.
CREATE UNIQUE INDEX uq_scoring_weights_single_active
    ON scoring_weights ((true)) WHERE status = 'ACTIVE';
--rollback DROP TABLE scoring_weights;

--changeset tracing-ats:002-specialization-and-level
-- Справочники. Строки не удаляются, если на них есть ссылки (RESTRICT);
-- «удаление» из UI = is_active = false.
CREATE TABLE specialization (
    id         uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    code       varchar(64)  NOT NULL,
    name       varchar(128) NOT NULL,
    is_active  boolean      NOT NULL DEFAULT true,
    created_at timestamptz  NOT NULL DEFAULT now(),
    updated_at timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_specialization_code UNIQUE (code),
    CONSTRAINT ck_specialization_code CHECK (code ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT ck_specialization_name CHECK (btrim(name) <> '')
);
CREATE UNIQUE INDEX uq_specialization_name_lower ON specialization (lower(name));

CREATE TABLE level (
    id            uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    code          varchar(64)  NOT NULL,
    name          varchar(128) NOT NULL,
    sort_order    integer      NOT NULL DEFAULT 0,
    -- λ: насколько внешние доказательства (GitHub) влияют на уверенность E.
    -- HR задаёт ползунком при создании уровня. 0 = не учитывать, 1 = полный вес.
    github_weight numeric(4,3) NOT NULL,
    is_active     boolean      NOT NULL DEFAULT true,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_level_code UNIQUE (code),
    CONSTRAINT ck_level_code CHECK (code ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT ck_level_name CHECK (btrim(name) <> ''),
    CONSTRAINT ck_level_github_weight CHECK (github_weight BETWEEN 0 AND 1)
);
CREATE UNIQUE INDEX uq_level_name_lower ON level (lower(name));
--rollback DROP TABLE level;
--rollback DROP TABLE specialization;

--changeset tracing-ats:003-skill
-- Справочник навыков: один навык = одна строка.
CREATE TABLE skill (
    id              uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    name            varchar(128) NOT NULL,
    normalized_name varchar(128) NOT NULL,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_skill_normalized_name UNIQUE (normalized_name),
    CONSTRAINT ck_skill_normalized CHECK (normalized_name = lower(btrim(normalized_name)) AND normalized_name <> '')
);
--rollback DROP TABLE skill;

--changeset tracing-ats:004-vacancy
CREATE TABLE vacancy (
    id              uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    title           varchar(256) NOT NULL,
    status          varchar(16)  NOT NULL DEFAULT 'DRAFT',
    source_type     varchar(8)   NOT NULL,
    source_ref      varchar(512),
    source_text     text         NOT NULL,
    current_version integer      NOT NULL DEFAULT 1,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_vacancy_title  CHECK (btrim(title) <> ''),
    CONSTRAINT ck_vacancy_status CHECK (status IN ('DRAFT', 'ACTIVE', 'CLOSED', 'ARCHIVED')),
    CONSTRAINT ck_vacancy_source CHECK (source_type IN ('TEXT', 'FILE', 'HH')),
    CONSTRAINT ck_vacancy_current_version CHECK (current_version >= 1)
);
CREATE INDEX ix_vacancy_status ON vacancy (status);
--rollback DROP TABLE vacancy;

--changeset tracing-ats:005-template-version
-- Версия шаблона: снимок специализации, уровня, веса GitHub и весов балла. Неизменяема.
CREATE TABLE vacancy_template_version (
    id                 uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    vacancy_id         uuid         NOT NULL REFERENCES vacancy (id) ON DELETE CASCADE,
    version_no         integer      NOT NULL,
    specialization_id  uuid         NOT NULL REFERENCES specialization (id) ON DELETE RESTRICT,
    level_id           uuid         NOT NULL REFERENCES level (id) ON DELETE RESTRICT,
    -- Копия level.github_weight на момент создания версии (уровень потом могут изменить).
    github_weight      numeric(4,3) NOT NULL,
    scoring_weights_id uuid         NOT NULL REFERENCES scoring_weights (id) ON DELETE RESTRICT,
    llm_prompt_version varchar(32),
    created_at         timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_template_version UNIQUE (vacancy_id, version_no),
    CONSTRAINT ck_template_version_no CHECK (version_no >= 1),
    CONSTRAINT ck_template_github_weight CHECK (github_weight BETWEEN 0 AND 1)
);
CREATE INDEX ix_template_version_spec_level ON vacancy_template_version (specialization_id, level_id);
CREATE INDEX ix_template_version_level      ON vacancy_template_version (level_id);
CREATE INDEX ix_template_version_weights    ON vacancy_template_version (scoring_weights_id);

-- Указатель на текущую версию; проверяется в конце транзакции,
-- поэтому вакансию и её первую версию можно вставить в одной транзакции.
ALTER TABLE vacancy
    ADD CONSTRAINT fk_vacancy_current_version
    FOREIGN KEY (id, current_version)
    REFERENCES vacancy_template_version (vacancy_id, version_no)
    DEFERRABLE INITIALLY DEFERRED;
--rollback ALTER TABLE vacancy DROP CONSTRAINT fk_vacancy_current_version;
--rollback DROP TABLE vacancy_template_version;

--changeset tracing-ats:006-requirement
-- Требование = навык + тип (kind) + вес. Набор kind фиксирован и не расширяется.
CREATE TABLE vacancy_requirement (
    id                  uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    template_version_id uuid         NOT NULL REFERENCES vacancy_template_version (id) ON DELETE CASCADE,
    position            smallint     NOT NULL,
    skill_id            uuid         REFERENCES skill (id) ON DELETE RESTRICT,
    raw_text            text,
    kind                varchar(8)   NOT NULL,
    weight              smallint     NOT NULL,
    critical            boolean      NOT NULL DEFAULT false,
    years_required      numeric(4,1) NOT NULL DEFAULT 0,
    half_life_years     numeric(4,1),
    clarity_warning     text,
    CONSTRAINT uq_requirement_position UNIQUE (template_version_id, position),
    CONSTRAINT uq_requirement_skill    UNIQUE (template_version_id, skill_id),
    CONSTRAINT ck_requirement_kind     CHECK (kind IN ('MUST', 'NICE', 'OPTIONAL')),
    CONSTRAINT ck_requirement_weight   CHECK (weight BETWEEN 1 AND 3),
    CONSTRAINT ck_requirement_position CHECK (position >= 1),
    CONSTRAINT ck_requirement_years    CHECK (years_required >= 0),
    CONSTRAINT ck_requirement_half     CHECK (half_life_years IS NULL OR half_life_years > 0),
    CONSTRAINT ck_requirement_subject  CHECK (skill_id IS NOT NULL OR btrim(coalesce(raw_text, '')) <> ''),
    CONSTRAINT ck_requirement_critical CHECK (NOT critical OR kind = 'MUST')
);
CREATE INDEX ix_requirement_skill ON vacancy_requirement (skill_id);
--rollback DROP TABLE vacancy_requirement;

--changeset tracing-ats:007-immutability splitStatements:false
CREATE FUNCTION forbid_update() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION '% rows are immutable: create a new template version instead', TG_TABLE_NAME;
END;
$$;

CREATE TRIGGER trg_template_version_immutable
    BEFORE UPDATE ON vacancy_template_version
    FOR EACH ROW EXECUTE FUNCTION forbid_update();

CREATE TRIGGER trg_requirement_immutable
    BEFORE UPDATE ON vacancy_requirement
    FOR EACH ROW EXECUTE FUNCTION forbid_update();
--rollback DROP FUNCTION IF EXISTS forbid_update() CASCADE;

--changeset tracing-ats:008-seed-weights-and-levels
-- Стартовые значения: 0.70 / 0.15 / 0.15 из scoring_model.pdf, λ по уровням из README §7.
-- Это гипотезы для MVP. Стажёр взят с тем же λ, что junior.
INSERT INTO scoring_weights (version, status, w_m, w_n, w_f) VALUES (1, 'ACTIVE', 0.70, 0.15, 0.15);

INSERT INTO level (code, name, sort_order, github_weight) VALUES
    ('intern', 'Стажёр', 10, 1.0),
    ('junior', 'Junior', 20, 1.0),
    ('middle', 'Middle', 30, 0.8),
    ('senior', 'Senior', 40, 0.5);
--rollback DELETE FROM level WHERE code IN ('intern', 'junior', 'middle', 'senior');
--rollback DELETE FROM scoring_weights WHERE version = 1;
