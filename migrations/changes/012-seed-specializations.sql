--liquibase formatted sql

--changeset tracing-ats:013-seed-specializations
-- Стартовый набор IT-специализаций. Уровни засеяны в changeset 008.
INSERT INTO specialization (code, name) VALUES
    ('backend', 'Backend'),
    ('frontend', 'Frontend'),
    ('fullstack', 'Fullstack'),
    ('mobile', 'Mobile'),
    ('data', 'Data'),
    ('devops', 'DevOps'),
    ('qa', 'QA')
ON CONFLICT (code) DO NOTHING;
--rollback DELETE FROM specialization WHERE code IN ('backend', 'frontend', 'fullstack', 'mobile', 'data', 'devops', 'qa');
