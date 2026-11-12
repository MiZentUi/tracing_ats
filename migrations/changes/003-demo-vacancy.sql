--liquibase formatted sql

--changeset tracing-ats:resume-101-demo-vacancy
-- Синтетические демо-данные для сквозного сценария. Не содержат PII.
-- Вакансия и её первая версия шаблона — в одной транзакции: циклический
-- внешний ключ vacancy.current_version проверяется отложенно в конце транзакции.
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
    (SELECT id FROM specialization WHERE code = 'backend'),
    (SELECT id FROM level WHERE code = 'middle'),
    0.8,
    (SELECT id FROM scoring_weights WHERE version = 1),
    'vacancy-v1'
);
--rollback DELETE FROM vacancy_template_version WHERE id = '00000000-0000-0000-0000-000000000002';
--rollback DELETE FROM vacancy WHERE id = '00000000-0000-0000-0000-000000000001';

--changeset tracing-ats:resume-102-demo-requirements
-- Навыки берём из справочника (010-skill-alias-and-seed), чтобы не дублировать
-- нормализованные имена и не зависеть от конкретных UUID навыков.
INSERT INTO vacancy_requirement (
    id, template_version_id, position, skill_id, raw_text, kind,
    critical, years_required, half_life_years
) VALUES
    ('00000000-0000-0000-0000-000000000201', '00000000-0000-0000-0000-000000000002', 1,
     (SELECT id FROM skill WHERE normalized_name = 'java'), 'Java', 'MUST', true, 3.0, 5.0),
    ('00000000-0000-0000-0000-000000000202', '00000000-0000-0000-0000-000000000002', 2,
     (SELECT id FROM skill WHERE normalized_name = 'spring boot'), 'Spring Boot', 'MUST', false, 2.0, 3.0),
    ('00000000-0000-0000-0000-000000000203', '00000000-0000-0000-0000-000000000002', 3,
     (SELECT id FROM skill WHERE normalized_name = 'postgresql'), 'PostgreSQL', 'MUST', false, 2.0, 5.0),
    ('00000000-0000-0000-0000-000000000204', '00000000-0000-0000-0000-000000000002', 4,
     (SELECT id FROM skill WHERE normalized_name = 'kafka'), 'Kafka', 'NICE', false, 1.0, 3.0),
    ('00000000-0000-0000-0000-000000000205', '00000000-0000-0000-0000-000000000002', 5,
     (SELECT id FROM skill WHERE normalized_name = 'docker'), 'Docker', 'NICE', false, 1.0, 3.0),
    ('00000000-0000-0000-0000-000000000206', '00000000-0000-0000-0000-000000000002', 6,
     (SELECT id FROM skill WHERE normalized_name = 'kubernetes'), 'Kubernetes', 'OPTIONAL', false, 0.0, NULL);
--rollback DELETE FROM vacancy_requirement WHERE template_version_id = '00000000-0000-0000-0000-000000000002';
