--liquibase formatted sql

--changeset tracing-ats:resume-101-demo-specialization
-- Синтетические демо-данные для сквозного сценария. Не содержат PII.
INSERT INTO specialization (id, code, name)
VALUES ('00000000-0000-0000-0000-000000000010', 'java-developer', 'Java Developer');
--rollback DELETE FROM specialization WHERE id = '00000000-0000-0000-0000-000000000010';

--changeset tracing-ats:resume-102-demo-skills
INSERT INTO skill (id, name, normalized_name) VALUES
    ('00000000-0000-0000-0000-000000000101', 'Java', 'java'),
    ('00000000-0000-0000-0000-000000000102', 'Spring Boot', 'spring boot'),
    ('00000000-0000-0000-0000-000000000103', 'PostgreSQL', 'postgresql'),
    ('00000000-0000-0000-0000-000000000104', 'Kafka', 'kafka'),
    ('00000000-0000-0000-0000-000000000105', 'Docker', 'docker'),
    ('00000000-0000-0000-0000-000000000106', 'Kubernetes', 'kubernetes');
--rollback DELETE FROM skill WHERE id IN (
--rollback     '00000000-0000-0000-0000-000000000101',
--rollback     '00000000-0000-0000-0000-000000000102',
--rollback     '00000000-0000-0000-0000-000000000103',
--rollback     '00000000-0000-0000-0000-000000000104',
--rollback     '00000000-0000-0000-0000-000000000105',
--rollback     '00000000-0000-0000-0000-000000000106'
--rollback );

--changeset tracing-ats:resume-103-demo-vacancy
-- Вакансия и её первая версия шаблона — в одном changeset (одной транзакции): циклический
-- внешний ключ vacancy.current_version проверяется отложенно только в конце транзакции.
INSERT INTO vacancy (
    id, title, status, source_type, source_text, current_version
) VALUES (
    '00000000-0000-0000-0000-000000000001',
    'Java-разработчик (демо)',
    'ACTIVE',
    'TEXT',
    'Ищем Java-разработчика. Обязательно: Java, Spring Boot, PostgreSQL. Будет плюсом: Kafka, Docker. Kubernetes — опционально.',
    1
);

INSERT INTO vacancy_template_version (
    id, vacancy_id, version_no, specialization_id, level_id,
    github_weight, scoring_weights_id, llm_prompt_version
) VALUES (
    '00000000-0000-0000-0000-000000000002',
    '00000000-0000-0000-0000-000000000001',
    1,
    '00000000-0000-0000-0000-000000000010',
    (SELECT id FROM level WHERE code = 'middle'),
    0.8,
    (SELECT id FROM scoring_weights WHERE version = 1),
    'vacancy-v1'
);
--rollback DELETE FROM vacancy_template_version WHERE id = '00000000-0000-0000-0000-000000000002';
--rollback DELETE FROM vacancy WHERE id = '00000000-0000-0000-0000-000000000001';

--changeset tracing-ats:resume-105-demo-requirements
INSERT INTO vacancy_requirement (
    id, template_version_id, position, skill_id, raw_text, kind,
    weight, critical, years_required, half_life_years
) VALUES
    ('00000000-0000-0000-0000-000000000201', '00000000-0000-0000-0000-000000000002', 1,
     '00000000-0000-0000-0000-000000000101', 'Java', 'MUST', 3, true, 3.0, 5.0),
    ('00000000-0000-0000-0000-000000000202', '00000000-0000-0000-0000-000000000002', 2,
     '00000000-0000-0000-0000-000000000102', 'Spring Boot', 'MUST', 3, false, 2.0, 3.0),
    ('00000000-0000-0000-0000-000000000203', '00000000-0000-0000-0000-000000000002', 3,
     '00000000-0000-0000-0000-000000000103', 'PostgreSQL', 'MUST', 2, false, 2.0, 5.0),
    ('00000000-0000-0000-0000-000000000204', '00000000-0000-0000-0000-000000000002', 4,
     '00000000-0000-0000-0000-000000000104', 'Kafka', 'NICE', 2, false, 1.0, 3.0),
    ('00000000-0000-0000-0000-000000000205', '00000000-0000-0000-0000-000000000002', 5,
     '00000000-0000-0000-0000-000000000105', 'Docker', 'NICE', 1, false, 1.0, 3.0),
    ('00000000-0000-0000-0000-000000000206', '00000000-0000-0000-0000-000000000002', 6,
     '00000000-0000-0000-0000-000000000106', 'Kubernetes', 'OPTIONAL', 1, false, 0.0, NULL);
--rollback DELETE FROM vacancy_requirement WHERE template_version_id = '00000000-0000-0000-0000-000000000002';
