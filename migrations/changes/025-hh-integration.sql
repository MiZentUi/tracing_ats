--liquibase formatted sql

--changeset tracing-ats:hh-001-publication
-- Публикация вакансии на hh.ru. Одна платформенная вакансия связана с одной hh-вакансией.
-- hh_draft_id сохраняется до публикации, чтобы повторный запуск не создавал дубль.
CREATE TABLE vacancy_hh_publication (
    vacancy_id     uuid         PRIMARY KEY REFERENCES vacancy (id) ON DELETE CASCADE,
    status         varchar(16)  NOT NULL DEFAULT 'PENDING',
    hh_draft_id    varchar(64),
    hh_vacancy_id  varchar(64),
    hh_url         varchar(512),
    hh_employer_id varchar(64),
    error_code     varchar(64),
    requested_at   timestamptz  NOT NULL DEFAULT now(),
    published_at   timestamptz,
    updated_at     timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_vacancy_hh_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED', 'ARCHIVED')),
    CONSTRAINT ck_vacancy_hh_published CHECK (
        status <> 'PUBLISHED' OR hh_vacancy_id IS NOT NULL
    )
);
CREATE UNIQUE INDEX uq_vacancy_hh_vacancy_id
    ON vacancy_hh_publication (hh_vacancy_id) WHERE hh_vacancy_id IS NOT NULL;
--rollback DROP TABLE vacancy_hh_publication;

--changeset tracing-ats:hh-002-subscription
-- Подписка на события hh. Одна активная подписка на работодателя.
CREATE TABLE hh_subscription (
    id              uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    subscription_id varchar(64)  NOT NULL,
    url             varchar(1024) NOT NULL,
    employer_id     varchar(64),
    status          varchar(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at      timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_hh_subscription_external UNIQUE (subscription_id),
    CONSTRAINT ck_hh_subscription_status CHECK (status IN ('ACTIVE', 'DELETED'))
);
CREATE UNIQUE INDEX uq_hh_subscription_single_active
    ON hh_subscription ((true)) WHERE status = 'ACTIVE';
--rollback DROP TABLE hh_subscription;

--changeset tracing-ats:hh-003-webhook-event
-- Входящее событие hh. Тело запроса не храним: только идентификаторы и статус.
CREATE TABLE hh_webhook_event (
    id                uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    external_event_id varchar(128) NOT NULL,
    subscription_id   varchar(64),
    action_type       varchar(64)  NOT NULL,
    hh_vacancy_id     varchar(64),
    hh_resume_id      varchar(64),
    hh_topic_id       varchar(128),
    hh_employer_id    varchar(64),
    status            varchar(16)  NOT NULL,
    error_code        varchar(64),
    application_id    uuid         REFERENCES application (id) ON DELETE SET NULL,
    received_at       timestamptz  NOT NULL DEFAULT now(),
    processed_at      timestamptz,
    CONSTRAINT uq_hh_webhook_event_external UNIQUE (external_event_id),
    CONSTRAINT ck_hh_webhook_event_status CHECK (status IN (
        'RECEIVED', 'PROCESSING', 'PROCESSED', 'UNMATCHED', 'IGNORED', 'FAILED', 'DUPLICATE'
    ))
);
CREATE INDEX ix_hh_webhook_event_status ON hh_webhook_event (status);
CREATE INDEX ix_hh_webhook_event_vacancy ON hh_webhook_event (hh_vacancy_id);
--rollback DROP TABLE hh_webhook_event;

--changeset tracing-ats:hh-004-application-source
-- Источник отклика и внешний идентификатор переговоров для идемпотентности.
ALTER TABLE application ADD COLUMN source varchar(16) NOT NULL DEFAULT 'MANUAL';
ALTER TABLE application ADD COLUMN external_ref varchar(128);
ALTER TABLE application ADD CONSTRAINT ck_application_source
    CHECK (source IN ('MANUAL', 'HH'));
CREATE UNIQUE INDEX uq_application_source_external
    ON application (source, external_ref) WHERE external_ref IS NOT NULL;
--rollback DROP INDEX uq_application_source_external;
--rollback ALTER TABLE application DROP CONSTRAINT ck_application_source;
--rollback ALTER TABLE application DROP COLUMN external_ref;
--rollback ALTER TABLE application DROP COLUMN source;

--changeset tracing-ats:hh-005-candidate-source
-- Один и тот же hh-кандидат может откликаться на несколько вакансий: не дублируем candidate.
ALTER TABLE candidate ADD COLUMN source varchar(16) NOT NULL DEFAULT 'MANUAL';
ALTER TABLE candidate ADD COLUMN external_ref varchar(128);
ALTER TABLE candidate ADD CONSTRAINT ck_candidate_source
    CHECK (source IN ('MANUAL', 'HH'));
CREATE UNIQUE INDEX uq_candidate_source_external
    ON candidate (source, external_ref) WHERE external_ref IS NOT NULL;
--rollback DROP INDEX uq_candidate_source_external;
--rollback ALTER TABLE candidate DROP CONSTRAINT ck_candidate_source;
--rollback ALTER TABLE candidate DROP COLUMN external_ref;
--rollback ALTER TABLE candidate DROP COLUMN source;

--changeset tracing-ats:hh-006-resume-source
ALTER TABLE resume ADD COLUMN source varchar(16) NOT NULL DEFAULT 'MANUAL';
ALTER TABLE resume ADD COLUMN external_ref varchar(128);
ALTER TABLE resume ADD CONSTRAINT ck_resume_source
    CHECK (source IN ('MANUAL', 'HH'));
CREATE INDEX ix_resume_source_external ON resume (source, external_ref);
--rollback DROP INDEX ix_resume_source_external;
--rollback ALTER TABLE resume DROP CONSTRAINT ck_resume_source;
--rollback ALTER TABLE resume DROP COLUMN external_ref;
--rollback ALTER TABLE resume DROP COLUMN source;

--changeset tracing-ats:hh-007-notification-outbox
-- Outbox защищает алерты от потери при рестарте и от дублей.
CREATE TABLE notification_outbox (
    id             uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    dedupe_key     varchar(160) NOT NULL,
    event_type     varchar(32)  NOT NULL,
    vacancy_id     uuid,
    application_id uuid,
    payload        text         NOT NULL DEFAULT '{}',
    status         varchar(16)  NOT NULL DEFAULT 'PENDING',
    attempts       integer      NOT NULL DEFAULT 0,
    next_attempt_at timestamptz NOT NULL DEFAULT now(),
    last_error     varchar(128),
    created_at     timestamptz  NOT NULL DEFAULT now(),
    sent_at        timestamptz,
    CONSTRAINT uq_notification_outbox_dedupe UNIQUE (dedupe_key),
    CONSTRAINT ck_notification_outbox_status CHECK (status IN (
        'PENDING', 'SENT', 'FAILED', 'SKIPPED'
    )),
    CONSTRAINT ck_notification_outbox_attempts CHECK (attempts >= 0)
);
CREATE INDEX ix_notification_outbox_pending
    ON notification_outbox (status, next_attempt_at);
--rollback DROP TABLE notification_outbox;
