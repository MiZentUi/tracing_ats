--liquibase formatted sql

--changeset tracing-ats:014-seed-more-skills
-- Пропущенные в 011 навыки: веб-фреймворки Rust, UNIX и инструменты вёрстки.
INSERT INTO skill (name, normalized_name) VALUES
    ('Axum', 'axum'),
    ('Actix Web', 'actix web'),
    ('Rocket', 'rocket'),
    ('UNIX', 'unix'),
    ('LaTeX', 'latex'),
    ('Typst', 'typst')
ON CONFLICT (normalized_name) DO NOTHING;

INSERT INTO skill_alias (alias_normalized, skill_id, source)
SELECT v.alias, s.id, 'SEED'
FROM (VALUES
    ('axum', 'axum'),
    ('actix', 'actix web'),
    ('actix-web', 'actix web'),
    ('actix web', 'actix web'),
    ('rocket', 'rocket'),
    ('unix', 'unix'),
    ('latex', 'latex'),
    ('tex', 'latex'),
    ('typst', 'typst')
) AS v(alias, skill)
JOIN skill s ON s.normalized_name = v.skill
ON CONFLICT (alias_normalized) DO NOTHING;
--rollback DELETE FROM skill_alias WHERE alias_normalized IN ('axum', 'actix', 'actix-web', 'actix web', 'rocket', 'unix', 'latex', 'tex', 'typst');
--rollback DELETE FROM skill WHERE normalized_name IN ('axum', 'actix web', 'rocket', 'unix', 'latex', 'typst');
