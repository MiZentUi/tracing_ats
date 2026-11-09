--liquibase formatted sql

--changeset tracing-ats:014-optional-level
-- «Без уровня» — скрытый уровень для специализаций без деления на грейды.
-- В справочник /levels не попадает (is_active = false), но используется версиями
-- шаблона и ключом skill_kind_stat.
INSERT INTO level (code, name, sort_order, github_weight, is_active)
VALUES ('none', 'Без уровня', 0, 0.5, false)
ON CONFLICT (code) DO NOTHING;
--rollback DELETE FROM level WHERE code = 'none';
