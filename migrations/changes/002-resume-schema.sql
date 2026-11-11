--liquibase formatted sql

--changeset tracing-ats:resume-001-candidate
-- Кандидат отделён от резюме: один человек может откликнуться несколькими файлами.
-- PII здесь не хранится, только техническая связка.
CREATE TABLE candidate (
    id         uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
--rollback DROP TABLE candidate;

--changeset tracing-ats:resume-002-resume
-- Оригинал лежит в объектном хранилище. Имя файла может содержать PII,
-- поэтому в открытом виде не хранится — только ключ объекта и хеш.
CREATE TABLE resume (
    id                  uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    candidate_id        uuid         NOT NULL REFERENCES candidate (id) ON DELETE CASCADE,
    original_object_key varchar(512) NOT NULL,
    original_sha256     varchar(64)  NOT NULL,
    original_size_bytes bigint       NOT NULL,
    file_type           varchar(16)  NOT NULL,
    status              varchar(24)  NOT NULL DEFAULT 'UPLOADED',
    warnings            text         NOT NULL DEFAULT '[]',
    created_at          timestamptz  NOT NULL DEFAULT now(),
    updated_at          timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_resume_object_key UNIQUE (original_object_key),
    CONSTRAINT ck_resume_status CHECK (status IN (
        'UPLOADED', 'TEXT_EXTRACTED', 'MASKED', 'TEXT_EMPTY', 'MASKING_FAILED', 'FAILED'
    )),
    CONSTRAINT ck_resume_size CHECK (original_size_bytes >= 0),
    CONSTRAINT ck_resume_sha CHECK (original_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_resume_file_type CHECK (file_type IN ('PDF', 'DOCX', 'TEXT', 'MARKDOWN'))
);
CREATE INDEX ix_resume_candidate ON resume (candidate_id);
--rollback DROP TABLE resume;

--changeset tracing-ats:resume-003-pii-vault
-- Шифротекст PII. Ключ и key_version живут вне БД; в строке только iv и ciphertext.
CREATE TABLE pii_vault (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    resume_id   uuid        NOT NULL REFERENCES resume (id) ON DELETE CASCADE,
    key_version varchar(32) NOT NULL,
    iv          bytea       NOT NULL,
    ciphertext  bytea       NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_pii_vault_resume UNIQUE (resume_id),
    CONSTRAINT ck_pii_vault_iv CHECK (octet_length(iv) = 12),
    CONSTRAINT ck_pii_vault_ciphertext CHECK (octet_length(ciphertext) > 16)
);
--rollback DROP TABLE pii_vault;

--changeset tracing-ats:resume-004-parsed-resume
-- Только обезличенный текст и нормализованные факты. Названия вуза и года
-- окончания здесь нет — они в pii_vault.
CREATE TABLE parsed_resume (
    id               uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    resume_id        uuid         NOT NULL REFERENCES resume (id) ON DELETE CASCADE,
    masked_text      text         NOT NULL,
    injection_flags  text         NOT NULL DEFAULT '[]',
    education_level  varchar(64),
    degree           varchar(128),
    specialty        varchar(256),
    education_years  numeric(4,1),
    pii_model        varchar(128),
    pii_model_hash   varchar(64),
    ner_used         boolean      NOT NULL DEFAULT false,
    created_at       timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_parsed_resume_resume UNIQUE (resume_id),
    CONSTRAINT ck_parsed_education_years CHECK (education_years IS NULL OR education_years >= 0)
);
--rollback DROP TABLE parsed_resume;

--changeset tracing-ats:resume-005-application
-- Отклик = резюме + вакансия + согласие на внешние источники.
-- vacancy_id без FK: таблицей владеет vacancy-service.
CREATE TABLE application (
    id                       uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    vacancy_id               uuid        NOT NULL,
    resume_id                uuid        NOT NULL REFERENCES resume (id) ON DELETE CASCADE,
    candidate_id             uuid        NOT NULL REFERENCES candidate (id) ON DELETE CASCADE,
    consent_github           boolean     NOT NULL DEFAULT false,
    status                   varchar(32) NOT NULL DEFAULT 'UPLOADED',
    screening_decision       varchar(16),
    preliminary_score_run_id uuid,
    final_score_run_id       uuid,
    created_at               timestamptz NOT NULL DEFAULT now(),
    updated_at               timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_application_vacancy_resume UNIQUE (vacancy_id, resume_id),
    CONSTRAINT ck_application_status CHECK (status IN (
        'UPLOADED', 'MASKED', 'TEXT_EMPTY', 'MASKING_FAILED', 'SCREENED', 'ENRICHED', 'REFINED', 'FAILED'
    )),
    CONSTRAINT ck_application_decision CHECK (
        screening_decision IS NULL OR screening_decision IN ('SHORTLISTED', 'BORDERLINE', 'NOT_SHORTLISTED')
    )
);
CREATE INDEX ix_application_vacancy ON application (vacancy_id);
CREATE INDEX ix_application_status ON application (status);
--rollback DROP TABLE application;

--changeset tracing-ats:resume-006-screening-batch
CREATE TABLE screening_batch (
    id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    vacancy_id    uuid        NOT NULL,
    status        varchar(16) NOT NULL DEFAULT 'PENDING',
    total         integer     NOT NULL DEFAULT 0,
    processed     integer     NOT NULL DEFAULT 0,
    failed        integer     NOT NULL DEFAULT 0,
    error_message text,
    created_at    timestamptz NOT NULL DEFAULT now(),
    started_at    timestamptz,
    finished_at   timestamptz,
    CONSTRAINT ck_screening_batch_status CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_screening_batch_counts CHECK (total >= 0 AND processed >= 0 AND failed >= 0)
);
CREATE INDEX ix_screening_batch_vacancy ON screening_batch (vacancy_id);
--rollback DROP TABLE screening_batch;

--changeset tracing-ats:resume-007-score-run
-- Запуск неизменяем: версия модели, промпта, формулы и весов фиксируются.
CREATE TABLE score_run (
    id              uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id  uuid         NOT NULL REFERENCES application (id) ON DELETE CASCADE,
    batch_id        uuid         NOT NULL REFERENCES screening_batch (id) ON DELETE CASCADE,
    phase           varchar(16)  NOT NULL,
    scoring_version varchar(32)  NOT NULL,
    prompt_version  varchar(32),
    model_version   varchar(128),
    weights_version varchar(32),
    score           numeric(5,2) NOT NULL,
    score_low       numeric(5,2) NOT NULL,
    score_high      numeric(5,2) NOT NULL,
    cap_applied     boolean      NOT NULL DEFAULT false,
    flags           text         NOT NULL DEFAULT '[]',
    parsed          text         NOT NULL DEFAULT '{}',
    created_at      timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_score_run_phase UNIQUE (batch_id, application_id, phase),
    CONSTRAINT ck_score_run_phase CHECK (phase IN ('PRELIMINARY', 'FINAL')),
    CONSTRAINT ck_score_run_score CHECK (score BETWEEN 0 AND 100),
    CONSTRAINT ck_score_run_range CHECK (
        score_low BETWEEN 0 AND 100
        AND score_high BETWEEN 0 AND 100
        AND score_low <= score
        AND score <= score_high
    )
);
CREATE INDEX ix_score_run_application ON score_run (application_id, phase, created_at DESC);
--rollback DROP TABLE score_run;

--changeset tracing-ats:resume-008-requirement-result
CREATE TABLE requirement_result (
    id               uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    score_run_id     uuid         NOT NULL REFERENCES score_run (id) ON DELETE CASCADE,
    requirement_id   varchar(128) NOT NULL,
    requirement_text text         NOT NULL,
    kind             varchar(8)   NOT NULL,
    weight           integer      NOT NULL,
    critical         boolean      NOT NULL DEFAULT false,
    c_r              numeric(4,3) NOT NULL,
    status           varchar(8)   NOT NULL,
    m                numeric(4,3) NOT NULL,
    t                numeric(4,3) NOT NULL,
    rho              numeric(4,3) NOT NULL,
    evidence_source  varchar(16),
    evidence_quote   text,
    gap              text,
    external         boolean      NOT NULL DEFAULT false,
    created_at       timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_requirement_result UNIQUE (score_run_id, requirement_id),
    CONSTRAINT ck_requirement_result_kind CHECK (kind IN ('MUST', 'NICE', 'OPTIONAL')),
    CONSTRAINT ck_requirement_result_status CHECK (status IN ('COVERED', 'PARTIAL', 'GAP')),
    CONSTRAINT ck_requirement_result_source CHECK (
        evidence_source IS NULL OR evidence_source IN ('RESUME', 'GITHUB')
    ),
    CONSTRAINT ck_requirement_result_values CHECK (
        c_r BETWEEN 0 AND 1 AND m BETWEEN 0 AND 1 AND t BETWEEN 0 AND 1 AND rho BETWEEN 0 AND 1
    )
);
--rollback DROP TABLE requirement_result;

--changeset tracing-ats:resume-009-external-evidence
-- Нормализованные факты внешнего источника. Сырые тексты и PII не хранятся.
CREATE TABLE external_evidence (
    id             uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id uuid        NOT NULL REFERENCES application (id) ON DELETE CASCADE,
    score_run_id   uuid        REFERENCES score_run (id) ON DELETE SET NULL,
    source         varchar(16) NOT NULL,
    skill          varchar(128) NOT NULL,
    facts          text        NOT NULL DEFAULT '{}',
    flags          text        NOT NULL DEFAULT '[]',
    created_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_external_evidence_source CHECK (source IN ('GITHUB'))
);
CREATE INDEX ix_external_evidence_application ON external_evidence (application_id);
--rollback DROP TABLE external_evidence;

--changeset tracing-ats:resume-010-audit-event
CREATE TABLE audit_event (
    id             uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id uuid        REFERENCES application (id) ON DELETE SET NULL,
    actor          varchar(128) NOT NULL DEFAULT 'system',
    action         varchar(64) NOT NULL,
    reason         text,
    metadata       text        NOT NULL DEFAULT '{}',
    created_at     timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_event_application ON audit_event (application_id, created_at DESC);
--rollback DROP TABLE audit_event;

--changeset tracing-ats:resume-011-pii-vault-immutable splitStatements:false
CREATE TRIGGER trg_pii_vault_immutable
    BEFORE UPDATE ON pii_vault
    FOR EACH ROW EXECUTE FUNCTION forbid_update();
--rollback DROP TRIGGER IF EXISTS trg_pii_vault_immutable ON pii_vault;

--changeset tracing-ats:resume-012-parsed-resume-immutable splitStatements:false
CREATE TRIGGER trg_parsed_resume_immutable
    BEFORE UPDATE ON parsed_resume
    FOR EACH ROW EXECUTE FUNCTION forbid_update();
--rollback DROP TRIGGER IF EXISTS trg_parsed_resume_immutable ON parsed_resume;

--changeset tracing-ats:resume-013-score-run-immutable splitStatements:false
CREATE TRIGGER trg_score_run_immutable
    BEFORE UPDATE ON score_run
    FOR EACH ROW EXECUTE FUNCTION forbid_update();
--rollback DROP TRIGGER IF EXISTS trg_score_run_immutable ON score_run;

--changeset tracing-ats:resume-014-requirement-result-immutable splitStatements:false
CREATE TRIGGER trg_requirement_result_immutable
    BEFORE UPDATE ON requirement_result
    FOR EACH ROW EXECUTE FUNCTION forbid_update();
--rollback DROP TRIGGER IF EXISTS trg_requirement_result_immutable ON requirement_result;

--changeset tracing-ats:resume-015-external-evidence-immutable splitStatements:false
CREATE TRIGGER trg_external_evidence_immutable
    BEFORE UPDATE ON external_evidence
    FOR EACH ROW EXECUTE FUNCTION forbid_update();
--rollback DROP TRIGGER IF EXISTS trg_external_evidence_immutable ON external_evidence;

--changeset tracing-ats:resume-016-audit-event-immutable splitStatements:false
CREATE TRIGGER trg_audit_event_immutable
    BEFORE UPDATE ON audit_event
    FOR EACH ROW EXECUTE FUNCTION forbid_update();
--rollback DROP TRIGGER IF EXISTS trg_audit_event_immutable ON audit_event;
