--liquibase formatted sql

--changeset tracing-ats:015-specialization-it
-- Признак IT-специализации: для IT уровень (грейд) обязателен, для остальных
-- специализаций шаблон создаётся со скрытым уровнем «none».
-- Существующие пользовательские специализации остаются не-IT (DEFAULT false),
-- засеянные IT-направления помечаются явно.
ALTER TABLE specialization ADD COLUMN is_it boolean NOT NULL DEFAULT false;

UPDATE specialization
SET is_it = true
WHERE code IN ('backend', 'frontend', 'fullstack', 'mobile', 'data', 'devops', 'qa');
--rollback ALTER TABLE specialization DROP COLUMN is_it;
