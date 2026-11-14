--liquibase formatted sql

--changeset tracing-ats:025-dictionary-expansion
-- Пополнение словаря по provided/txt: frontend, образование, учёт ОС и документы,
-- автоматизация, аналитика и soft skills. Миграция идемпотентна.

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('6656725f-186d-5046-9976-ca3d8db47e49'::uuid, 'ACTIVE', 'TECH') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('26f4e75e-848d-5965-bf2a-3cdd51153cbf'::uuid, '6656725f-186d-5046-9976-ca3d8db47e49'::uuid, 'HTML', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('html', '26f4e75e-848d-5965-bf2a-3cdd51153cbf'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('edb7ac22-c735-57b0-9be5-5bca816151cd'::uuid, '6656725f-186d-5046-9976-ca3d8db47e49'::uuid, 'html5', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('html5', 'edb7ac22-c735-57b0-9be5-5bca816151cd'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('0750cb9f-32f7-584e-bd18-d9f2dd8725b2'::uuid, '6656725f-186d-5046-9976-ca3d8db47e49'::uuid, 'семантическая верстка', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('семантическая верстка', '0750cb9f-32f7-584e-bd18-d9f2dd8725b2'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('aa852c4d-7b1d-50e8-85df-f74c2a98fe6f'::uuid, '6656725f-186d-5046-9976-ca3d8db47e49'::uuid, 'semantic html', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('semantic html', 'aa852c4d-7b1d-50e8-85df-f74c2a98fe6f'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('64087a55-ae1d-567c-a8ba-9db6018ac868'::uuid, 'ACTIVE', 'TECH') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('0a75f8f9-9460-5767-92b8-3e0f0387722c'::uuid, '64087a55-ae1d-567c-a8ba-9db6018ac868'::uuid, 'CSS', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('css', '0a75f8f9-9460-5767-92b8-3e0f0387722c'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('18270262-eba5-54f8-8ef8-87d3838bfe1b'::uuid, '64087a55-ae1d-567c-a8ba-9db6018ac868'::uuid, 'css3', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('css3', '18270262-eba5-54f8-8ef8-87d3838bfe1b'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('f15024ea-a9eb-50e6-9258-e03b453ee4e3'::uuid, '64087a55-ae1d-567c-a8ba-9db6018ac868'::uuid, 'каскадные таблицы стилей', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('каскадные таблицы стилей', 'f15024ea-a9eb-50e6-9258-e03b453ee4e3'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('7c04a069-c1a5-50fa-bb0e-45d41c17ca45'::uuid, 'ACTIVE', 'TECH') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('dcd2d85c-89a2-5b96-8108-2bb6993de753'::uuid, '7c04a069-c1a5-50fa-bb0e-45d41c17ca45'::uuid, 'Flexbox', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('flexbox', 'dcd2d85c-89a2-5b96-8108-2bb6993de753'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('e63b02df-cf51-57b5-9f44-98f70e38fd4b'::uuid, '7c04a069-c1a5-50fa-bb0e-45d41c17ca45'::uuid, 'flex', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('flex', 'e63b02df-cf51-57b5-9f44-98f70e38fd4b'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('212bc73b-0550-5f74-81aa-fff037c75029'::uuid, 'ACTIVE', 'TECH') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('2e43ec48-f8df-5e24-bee2-324d3ec776a3'::uuid, '212bc73b-0550-5f74-81aa-fff037c75029'::uuid, 'CSS Grid', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('css grid', '2e43ec48-f8df-5e24-bee2-324d3ec776a3'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('3390403c-cce6-52f4-8406-cdab4ca3734b'::uuid, '212bc73b-0550-5f74-81aa-fff037c75029'::uuid, 'grid', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('grid', '3390403c-cce6-52f4-8406-cdab4ca3734b'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('dc6eb339-658b-56be-969c-437e88bb8de0'::uuid, '212bc73b-0550-5f74-81aa-fff037c75029'::uuid, 'css grid layout', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('css grid layout', 'dc6eb339-658b-56be-969c-437e88bb8de0'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('bb212d7c-40f5-5c47-a5d8-11b4d9c1d087'::uuid, 'ACTIVE', 'TECH') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('3d1d419e-8f50-5a80-a28b-884877ca8b5d'::uuid, 'bb212d7c-40f5-5c47-a5d8-11b4d9c1d087'::uuid, 'Адаптивная верстка', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('адаптивная верстка', '3d1d419e-8f50-5a80-a28b-884877ca8b5d'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('341c11bc-a575-57ee-a612-0c3d84710724'::uuid, 'bb212d7c-40f5-5c47-a5d8-11b4d9c1d087'::uuid, 'responsive design', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('responsive design', '341c11bc-a575-57ee-a612-0c3d84710724'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('aceda363-f240-57ea-9299-ca6d83ab8730'::uuid, 'bb212d7c-40f5-5c47-a5d8-11b4d9c1d087'::uuid, 'adaptive design', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('adaptive design', 'aceda363-f240-57ea-9299-ca6d83ab8730'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('fd18dde7-7a33-58ec-947d-5460e6d40001'::uuid, 'bb212d7c-40f5-5c47-a5d8-11b4d9c1d087'::uuid, 'адаптивность', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('адаптивность', 'fd18dde7-7a33-58ec-947d-5460e6d40001'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('699ea138-219d-56f9-94db-0fcf11764bc4'::uuid, 'ACTIVE', 'TECH') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('833f947a-1689-5b6b-872e-38084cfb46cf'::uuid, '699ea138-219d-56f9-94db-0fcf11764bc4'::uuid, 'Кроссбраузерная верстка', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('кроссбраузерная верстка', '833f947a-1689-5b6b-872e-38084cfb46cf'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('76791a7e-ee38-5240-ae47-860533218eb9'::uuid, '699ea138-219d-56f9-94db-0fcf11764bc4'::uuid, 'cross-browser', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('cross-browser', '76791a7e-ee38-5240-ae47-860533218eb9'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('1a2bbc91-6e39-508c-88c8-8b845b2b4e39'::uuid, '699ea138-219d-56f9-94db-0fcf11764bc4'::uuid, 'кроссбраузерность', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('кроссбраузерность', '1a2bbc91-6e39-508c-88c8-8b845b2b4e39'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('c92ed806-abb1-51dc-8cb4-5fab6c0714b4'::uuid, 'ACTIVE', 'TECH') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('2ce364b9-7246-5089-8619-77cf86bf202a'::uuid, 'c92ed806-abb1-51dc-8cb4-5fab6c0714b4'::uuid, 'Доступность интерфейсов', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('доступность интерфейсов', '2ce364b9-7246-5089-8619-77cf86bf202a'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('8cd1bc66-8116-58bd-a03d-fd371824dd1f'::uuid, 'c92ed806-abb1-51dc-8cb4-5fab6c0714b4'::uuid, 'a11y', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('a11y', '8cd1bc66-8116-58bd-a03d-fd371824dd1f'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('78c15567-ac70-5900-a991-7b7e9bcd44c4'::uuid, 'c92ed806-abb1-51dc-8cb4-5fab6c0714b4'::uuid, 'accessibility', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('accessibility', '78c15567-ac70-5900-a991-7b7e9bcd44c4'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('b435f340-fe5d-57b2-ba28-f1df5b5220f3'::uuid, 'ACTIVE', 'TECH') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('1a63e3ff-06a9-5f6d-8577-bcf361015775'::uuid, 'b435f340-fe5d-57b2-ba28-f1df5b5220f3'::uuid, 'Tailwind CSS', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('tailwind css', '1a63e3ff-06a9-5f6d-8577-bcf361015775'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('f357a546-1aee-5e6e-99fb-c7317770c095'::uuid, 'b435f340-fe5d-57b2-ba28-f1df5b5220f3'::uuid, 'tailwind', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('tailwind', 'f357a546-1aee-5e6e-99fb-c7317770c095'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('e031d674-e67d-5b30-bdb8-07ad843fcd78'::uuid, 'b435f340-fe5d-57b2-ba28-f1df5b5220f3'::uuid, 'tailwindcss', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('tailwindcss', 'e031d674-e67d-5b30-bdb8-07ad843fcd78'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('264e6b4e-ec6f-5742-800b-e066bbc04590'::uuid, 'ACTIVE', 'TECH') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('8ef69251-83ff-555f-8e25-f475255d3fcc'::uuid, '264e6b4e-ec6f-5742-800b-e066bbc04590'::uuid, 'Vue Router', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('vue router', '8ef69251-83ff-555f-8e25-f475255d3fcc'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('b43bcab4-7b80-537f-8976-b52182ffed92'::uuid, '264e6b4e-ec6f-5742-800b-e066bbc04590'::uuid, 'vue-router', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('vue-router', 'b43bcab4-7b80-537f-8976-b52182ffed92'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('ce2f3f45-48ab-5786-b52d-dfdbcac7251f'::uuid, '264e6b4e-ec6f-5742-800b-e066bbc04590'::uuid, 'vuerouter', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('vuerouter', 'ce2f3f45-48ab-5786-b52d-dfdbcac7251f'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('5a7f5090-2f22-5ed2-8e89-00d0cd76695b'::uuid, 'ACTIVE', 'TECH') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('a5cf465b-2407-5a85-ae61-3cd8852b3c52'::uuid, '5a7f5090-2f22-5ed2-8e89-00d0cd76695b'::uuid, 'Pinia', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('pinia', 'a5cf465b-2407-5a85-ae61-3cd8852b3c52'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('be9001ff-a457-5ea7-888f-0b365f6c33a3'::uuid, 'ACTIVE', 'TECH') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('b4807e4c-4c66-53d2-8e38-ce7dd65dd277'::uuid, 'be9001ff-a457-5ea7-888f-0b365f6c33a3'::uuid, 'VueUse', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('vueuse', 'b4807e4c-4c66-53d2-8e38-ce7dd65dd277'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('34ffdb95-c5b9-599f-ab4a-22092c734abf'::uuid, 'ACTIVE', 'TECH') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('9909644b-6b49-5266-a054-d91735829915'::uuid, '34ffdb95-c5b9-599f-ab4a-22092c734abf'::uuid, 'vue-i18n', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('vue-i18n', '9909644b-6b49-5266-a054-d91735829915'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('85c61640-c2d9-5ea2-abce-aab5baa9c412'::uuid, '34ffdb95-c5b9-599f-ab4a-22092c734abf'::uuid, 'vue i18n', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('vue i18n', '85c61640-c2d9-5ea2-abce-aab5baa9c412'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('3a6710b1-7b9b-5431-a0e9-b1d68c8b7572'::uuid, 'ACTIVE', 'TECH') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('4cafb472-0d9c-5688-a8b2-ec8231f3c623'::uuid, '3a6710b1-7b9b-5431-a0e9-b1d68c8b7572'::uuid, 'Nuxt', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('nuxt', '4cafb472-0d9c-5688-a8b2-ec8231f3c623'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('fb5e7802-e2a5-53b7-a56a-ed43a589c177'::uuid, '3a6710b1-7b9b-5431-a0e9-b1d68c8b7572'::uuid, 'nuxt.js', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('nuxt.js', 'fb5e7802-e2a5-53b7-a56a-ed43a589c177'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('c9186d05-a18f-5091-a98d-b8bbd05197af'::uuid, '3a6710b1-7b9b-5431-a0e9-b1d68c8b7572'::uuid, 'nuxtjs', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('nuxtjs', 'c9186d05-a18f-5091-a98d-b8bbd05197af'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('bc2a4ac8-d019-59cd-b68c-32038a1f1904'::uuid, 'ACTIVE', 'TECH') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('bd6d0951-c37f-5e81-b828-691d29c0a5b0'::uuid, 'bc2a4ac8-d019-59cd-b68c-32038a1f1904'::uuid, 'Composition API', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('composition api', 'bd6d0951-c37f-5e81-b828-691d29c0a5b0'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('11da15ae-2a69-56e8-8f29-6d1e6adc19e0'::uuid, 'bc2a4ac8-d019-59cd-b68c-32038a1f1904'::uuid, 'vue composition api', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('vue composition api', '11da15ae-2a69-56e8-8f29-6d1e6adc19e0'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('756f6bce-3536-540b-a99a-4566d5f6b6af'::uuid, 'ACTIVE', 'TECH') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('61738cbf-f17a-5150-838f-bc87097dde72'::uuid, '756f6bce-3536-540b-a99a-4566d5f6b6af'::uuid, 'SPA', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('spa', '61738cbf-f17a-5150-838f-bc87097dde72'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('0cd9d6d8-b993-577a-8864-4b84c2c0ed89'::uuid, '756f6bce-3536-540b-a99a-4566d5f6b6af'::uuid, 'single page application', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('single page application', '0cd9d6d8-b993-577a-8864-4b84c2c0ed89'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('081eff49-24b6-536b-a356-36a64ca1abc7'::uuid, '756f6bce-3536-540b-a99a-4566d5f6b6af'::uuid, 'одностраничное приложение', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('одностраничное приложение', '081eff49-24b6-536b-a356-36a64ca1abc7'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('839088c0-3c3c-5f15-98f7-24b78100f70d'::uuid, 'ACTIVE', 'TECH') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('ac3f3ccf-85a3-5d74-b315-ff8ba3efd870'::uuid, '839088c0-3c3c-5f15-98f7-24b78100f70d'::uuid, 'SSR', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('ssr', 'ac3f3ccf-85a3-5d74-b315-ff8ba3efd870'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('7fece731-64b3-5d4d-9cdf-c2fd41ddca86'::uuid, '839088c0-3c3c-5f15-98f7-24b78100f70d'::uuid, 'server-side rendering', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('server-side rendering', '7fece731-64b3-5d4d-9cdf-c2fd41ddca86'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('cf736f98-b495-5ad0-9deb-177abdd10f82'::uuid, '839088c0-3c3c-5f15-98f7-24b78100f70d'::uuid, 'серверный рендеринг', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('серверный рендеринг', 'cf736f98-b495-5ad0-9deb-177abdd10f82'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('cbdeb2ee-e0b6-5d0d-a94a-9e4ba54967e4'::uuid, 'ACTIVE', 'TOOL') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('b1a9c5db-59be-574a-ac8e-e351489fbdac'::uuid, 'cbdeb2ee-e0b6-5d0d-a94a-9e4ba54967e4'::uuid, 'Monaco Editor', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('monaco editor', 'b1a9c5db-59be-574a-ac8e-e351489fbdac'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('dee56b49-28f0-5a67-9e73-48091011715d'::uuid, 'cbdeb2ee-e0b6-5d0d-a94a-9e4ba54967e4'::uuid, 'monaco', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('monaco', 'dee56b49-28f0-5a67-9e73-48091011715d'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('7bdca04e-bb84-5fa7-b99c-297252420434'::uuid, 'ACTIVE', 'TOOL') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('82dc7eac-7ae6-5ce4-b1b2-b595571d0852'::uuid, '7bdca04e-bb84-5fa7-b99c-297252420434'::uuid, 'Vite', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('vite', '82dc7eac-7ae6-5ce4-b1b2-b595571d0852'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('fb9181a1-fda4-514b-8dce-d5e5af8a5926'::uuid, 'ACTIVE', 'TOOL') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('e1512f97-5ed2-5155-b68b-42b53422b757'::uuid, 'fb9181a1-fda4-514b-8dce-d5e5af8a5926'::uuid, 'Webpack', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('webpack', 'e1512f97-5ed2-5155-b68b-42b53422b757'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('da9c44d3-e874-59ff-b2d3-3be080675487'::uuid, 'ACTIVE', 'TOOL') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('dd39b0f5-e9e3-5bb1-b2ca-ab6bda026f7c'::uuid, 'da9c44d3-e874-59ff-b2d3-3be080675487'::uuid, 'Gulp', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('gulp', 'dd39b0f5-e9e3-5bb1-b2ca-ab6bda026f7c'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('a8bec381-19a4-5857-8205-5bced1676052'::uuid, 'ACTIVE', 'TOOL') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('775f22f6-7182-554d-a006-e789f7dd7c79'::uuid, 'a8bec381-19a4-5857-8205-5bced1676052'::uuid, 'npm', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('npm', '775f22f6-7182-554d-a006-e789f7dd7c79'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('a93ab924-4e40-53bd-8fd2-f0e349c80506'::uuid, 'ACTIVE', 'TOOL') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('feac66c6-883c-5bc2-a6f3-0f0a2d3327c4'::uuid, 'a93ab924-4e40-53bd-8fd2-f0e349c80506'::uuid, 'Vitest', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('vitest', 'feac66c6-883c-5bc2-a6f3-0f0a2d3327c4'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('a84ad921-249b-50d5-a36f-761fedf0993e'::uuid, 'ACTIVE', 'TOOL') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('fd8f05e7-9db8-5000-b420-0ab2208d8e93'::uuid, 'a84ad921-249b-50d5-a36f-761fedf0993e'::uuid, 'Jest', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('jest', 'fd8f05e7-9db8-5000-b420-0ab2208d8e93'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('76457970-eb66-592a-bea8-eea5a006bb74'::uuid, 'ACTIVE', 'TECH') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('e1256566-2298-5c0d-ad34-a4ef391bee89'::uuid, '76457970-eb66-592a-bea8-eea5a006bb74'::uuid, 'Sass', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('sass', 'e1256566-2298-5c0d-ad34-a4ef391bee89'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('3c20f5c4-c9d1-5732-bff4-7b904c274944'::uuid, '76457970-eb66-592a-bea8-eea5a006bb74'::uuid, 'scss', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('scss', '3c20f5c4-c9d1-5732-bff4-7b904c274944'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('7fbc8fbe-57fe-5e35-8ff1-eda424cc5166'::uuid, 'ACTIVE', 'TECH') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('95b5db77-ee89-5bba-90d6-d485f8624813'::uuid, '7fbc8fbe-57fe-5e35-8ff1-eda424cc5166'::uuid, 'CSS Modules', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('css modules', '95b5db77-ee89-5bba-90d6-d485f8624813'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('000851ea-05d2-5e92-aba0-3af60591fad8'::uuid, 'ACTIVE', 'TECH') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('01dc4a9f-5b73-5b32-b02c-97aab6eb0878'::uuid, '000851ea-05d2-5e92-aba0-3af60591fad8'::uuid, 'Pug', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('pug', '01dc4a9f-5b73-5b32-b02c-97aab6eb0878'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('cc48245c-f94f-5108-9f56-434b169bc23a'::uuid, '000851ea-05d2-5e92-aba0-3af60591fad8'::uuid, 'jade', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('jade', 'cc48245c-f94f-5108-9f56-434b169bc23a'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('aa5e070d-4623-55dd-a2d6-0ac0b2b94f07'::uuid, 'ACTIVE', 'TECH') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('48b31767-0d82-5623-8fad-42679ffbc848'::uuid, 'aa5e070d-4623-55dd-a2d6-0ac0b2b94f07'::uuid, 'WordPress', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('wordpress', '48b31767-0d82-5623-8fad-42679ffbc848'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('2dfcc2fb-3d81-56fe-9714-8efec36c728a'::uuid, 'aa5e070d-4623-55dd-a2d6-0ac0b2b94f07'::uuid, 'вордпресс', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('вордпресс', '2dfcc2fb-3d81-56fe-9714-8efec36c728a'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('2e8c7a42-d49c-59ef-af43-1ca37e0271e8'::uuid, 'ACTIVE', 'TECH') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('a97e7917-0e65-528d-b189-99fd9b712ef0'::uuid, '2e8c7a42-d49c-59ef-af43-1ca37e0271e8'::uuid, '1C-Битрикс', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('1c-битрикс', 'a97e7917-0e65-528d-b189-99fd9b712ef0'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('1c-биtpиkc', 'a97e7917-0e65-528d-b189-99fd9b712ef0'::uuid, 'FOLDED') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('c049ce08-ea45-591a-9973-b09354ccd350'::uuid, '2e8c7a42-d49c-59ef-af43-1ca37e0271e8'::uuid, 'bitrix', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('bitrix', 'c049ce08-ea45-591a-9973-b09354ccd350'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('3c445a17-221c-5f96-98e0-266b3691adbf'::uuid, '2e8c7a42-d49c-59ef-af43-1ca37e0271e8'::uuid, 'битрикс', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('битрикс', '3c445a17-221c-5f96-98e0-266b3691adbf'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('891cbaf4-473a-59b7-b6fb-748bc58bfb30'::uuid, '2e8c7a42-d49c-59ef-af43-1ca37e0271e8'::uuid, '1c-bitrix', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('1c-bitrix', '891cbaf4-473a-59b7-b6fb-748bc58bfb30'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('906e8d27-aa29-55b9-b6a6-01fc655ba1cc'::uuid, 'ACTIVE', 'TECH') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('028f0bdf-b871-50ea-9e39-f876b1f34ab9'::uuid, '906e8d27-aa29-55b9-b6a6-01fc655ba1cc'::uuid, 'WooCommerce', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('woocommerce', '028f0bdf-b871-50ea-9e39-f876b1f34ab9'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('17dc68a3-f782-5803-9a77-a1a0b60b5751'::uuid, 'ACTIVE', 'TOOL') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('7845e0f8-b807-5d3d-8561-379639199c28'::uuid, '17dc68a3-f782-5803-9a77-a1a0b60b5751'::uuid, 'ACF', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('acf', '7845e0f8-b807-5d3d-8561-379639199c28'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('90c730c7-2452-5530-a453-81ed2618d813'::uuid, '17dc68a3-f782-5803-9a77-a1a0b60b5751'::uuid, 'advanced custom fields', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('advanced custom fields', '90c730c7-2452-5530-a453-81ed2618d813'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('cdf652aa-ce46-5b9b-a911-d6eaff2de027'::uuid, 'ACTIVE', 'TOOL') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('33156628-f840-5110-9235-aaf877bf596a'::uuid, 'cdf652aa-ce46-5b9b-a911-d6eaff2de027'::uuid, 'Yoast SEO', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('yoast seo', '33156628-f840-5110-9235-aaf877bf596a'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('e80c1ae6-7d81-5c81-9a0b-84e5a863961f'::uuid, 'cdf652aa-ce46-5b9b-a911-d6eaff2de027'::uuid, 'yoast', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('yoast', 'e80c1ae6-7d81-5c81-9a0b-84e5a863961f'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('59a97f22-7cb7-5650-a11b-f3e13e256eb9'::uuid, 'ACTIVE', 'SOFT') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('bb598698-1e4b-504c-afe5-e56d82e445ed'::uuid, '59a97f22-7cb7-5650-a11b-f3e13e256eb9'::uuid, 'Код-ревью', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('код-ревью', 'bb598698-1e4b-504c-afe5-e56d82e445ed'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('c0cf7a69-779e-5b29-8d34-7ceac5393f60'::uuid, '59a97f22-7cb7-5650-a11b-f3e13e256eb9'::uuid, 'code review', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('code review', 'c0cf7a69-779e-5b29-8d34-7ceac5393f60'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('ad4b2b88-6f9b-57e0-b352-2f95861fe25e'::uuid, '59a97f22-7cb7-5650-a11b-f3e13e256eb9'::uuid, 'ревью кода', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('ревью кода', 'ad4b2b88-6f9b-57e0-b352-2f95861fe25e'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('f488a62b-ca1d-598f-b0c0-c5f05ba8c9e4'::uuid, 'ACTIVE', 'TECH') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('f08652fe-c4c6-506d-abd4-0dd821ded39d'::uuid, 'f488a62b-ca1d-598f-b0c0-c5f05ba8c9e4'::uuid, 'Тестирование интерфейсов', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('тестирование интерфейсов', 'f08652fe-c4c6-506d-abd4-0dd821ded39d'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('2c21cc9e-5e3b-51a8-8861-a247d8702f83'::uuid, 'f488a62b-ca1d-598f-b0c0-c5f05ba8c9e4'::uuid, 'frontend testing', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('frontend testing', '2c21cc9e-5e3b-51a8-8861-a247d8702f83'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('f023022f-4fe0-51b9-b7b9-6dc66888b097'::uuid, 'f488a62b-ca1d-598f-b0c0-c5f05ba8c9e4'::uuid, 'ui testing', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('ui testing', 'f023022f-4fe0-51b9-b7b9-6dc66888b097'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('fa63810c-5aa0-51c4-ae3a-59dff5463c2b'::uuid, 'ACTIVE', 'SOFT') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('bc961ab3-0d7e-5f4e-a176-822ff85432ac'::uuid, 'fa63810c-5aa0-51c4-ae3a-59dff5463c2b'::uuid, 'Чистота кода', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('чистота кода', 'bc961ab3-0d7e-5f4e-a176-822ff85432ac'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('f24349bf-b423-5cba-881e-cb999b84bd03'::uuid, 'fa63810c-5aa0-51c4-ae3a-59dff5463c2b'::uuid, 'читаемость кода', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('читаемость кода', 'f24349bf-b423-5cba-881e-cb999b84bd03'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('03dfb23d-0af9-5128-9272-69c823fedfec'::uuid, 'fa63810c-5aa0-51c4-ae3a-59dff5463c2b'::uuid, 'аккуратность в коде', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('аккуратность в коде', '03dfb23d-0af9-5128-9272-69c823fedfec'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('c8416470-eb74-5ba9-8f79-701938c35046'::uuid, 'fa63810c-5aa0-51c4-ae3a-59dff5463c2b'::uuid, 'clean code', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('clean code', 'c8416470-eb74-5ba9-8f79-701938c35046'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('5ec71905-8354-504d-8887-d90d453b6be3'::uuid, 'ACTIVE', 'TOOL') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('c98f0353-884d-509a-af56-accf418b5a71'::uuid, '5ec71905-8354-504d-8887-d90d453b6be3'::uuid, 'Google Sheets', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('google sheets', 'c98f0353-884d-509a-af56-accf418b5a71'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('4f45f2ef-a452-519a-90c7-38eb3a086d3f'::uuid, '5ec71905-8354-504d-8887-d90d453b6be3'::uuid, 'гугл таблицы', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('гугл таблицы', '4f45f2ef-a452-519a-90c7-38eb3a086d3f'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('c8788f7e-5d3e-533d-b99f-85e8fe327bda'::uuid, '5ec71905-8354-504d-8887-d90d453b6be3'::uuid, 'google таблицы', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('google таблицы', 'c8788f7e-5d3e-533d-b99f-85e8fe327bda'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('e64ad4e3-f9b1-5750-9144-ae18f769f18d'::uuid, 'ACTIVE', 'TOOL') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('ca10ea0c-4ac9-5251-8d6b-2c85ca9a512f'::uuid, 'e64ad4e3-f9b1-5750-9144-ae18f769f18d'::uuid, 'Google Workspace', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('google workspace', 'ca10ea0c-4ac9-5251-8d6b-2c85ca9a512f'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('dd52f482-5b41-5232-b564-7873b6618111'::uuid, 'e64ad4e3-f9b1-5750-9144-ae18f769f18d'::uuid, 'gsuite', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('gsuite', 'dd52f482-5b41-5232-b564-7873b6618111'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('a7df3b5f-ea67-54be-8e63-80c97204260a'::uuid, 'ACTIVE', 'DOMAIN') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('849519f1-9179-5289-897e-6bc6d6b6c35d'::uuid, 'a7df3b5f-ea67-54be-8e63-80c97204260a'::uuid, 'Отчётность', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('отчетность', '849519f1-9179-5289-897e-6bc6d6b6c35d'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('3747d101-e47c-51f2-9ba8-c3b7be4516f3'::uuid, 'a7df3b5f-ea67-54be-8e63-80c97204260a'::uuid, 'reporting', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('reporting', '3747d101-e47c-51f2-9ba8-c3b7be4516f3'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('908ba0b3-8477-5fcc-bfb0-307977f64e7d'::uuid, 'a7df3b5f-ea67-54be-8e63-80c97204260a'::uuid, 'подготовка отчетности', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('подготовка отчетности', '908ba0b3-8477-5fcc-bfb0-307977f64e7d'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('0564663d-0069-5bd1-a3d0-24152f2b07d5'::uuid, 'ACTIVE', 'SOFT') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('35c71533-cdf8-58b0-829a-451729dd3c33'::uuid, '0564663d-0069-5bd1-a3d0-24152f2b07d5'::uuid, 'Работа с большим объёмом информации', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('работа с большим объемом информации', '35c71533-cdf8-58b0-829a-451729dd3c33'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('98265411-4894-57d1-bd27-7776bf03589c'::uuid, '0564663d-0069-5bd1-a3d0-24152f2b07d5'::uuid, 'большой объем информации', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('большой объем информации', '98265411-4894-57d1-bd27-7776bf03589c'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('2284e561-369e-54ec-9750-fd27464134b3'::uuid, 'ACTIVE', 'DOMAIN') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('6d1fbccf-56f9-56d4-9880-38c34002276b'::uuid, '2284e561-369e-54ec-9750-fd27464134b3'::uuid, 'Договорная работа', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('договорная работа', '6d1fbccf-56f9-56d4-9880-38c34002276b'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('3cd5c90e-872e-560e-8f1d-fb02d3a3bc01'::uuid, '2284e561-369e-54ec-9750-fd27464134b3'::uuid, 'договоры', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('договоры', '3cd5c90e-872e-560e-8f1d-fb02d3a3bc01'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('9bda5307-d527-5c4d-be87-f28030384360'::uuid, '2284e561-369e-54ec-9750-fd27464134b3'::uuid, 'договорная документация', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('договорная документация', '9bda5307-d527-5c4d-be87-f28030384360'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('b465a8c2-b0ee-5952-9b75-dfd13aef58da'::uuid, '2284e561-369e-54ec-9750-fd27464134b3'::uuid, 'заключение договоров', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('заключение договоров', 'b465a8c2-b0ee-5952-9b75-dfd13aef58da'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('16dfa6ad-c151-55ff-a9ee-ac6cd56a8a48'::uuid, 'ACTIVE', 'DOMAIN') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('9c5125ce-229e-50d8-866c-b3e39e294c5f'::uuid, '16dfa6ad-c151-55ff-a9ee-ac6cd56a8a48'::uuid, 'Контроль комплектности документов', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('контроль комплектности документов', '9c5125ce-229e-50d8-866c-b3e39e294c5f'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('e84e1bb9-3f66-5393-b3eb-758588a1f7b9'::uuid, '16dfa6ad-c151-55ff-a9ee-ac6cd56a8a48'::uuid, 'проверка документов', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('проверка документов', 'e84e1bb9-3f66-5393-b3eb-758588a1f7b9'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('909519e3-e5dd-5489-9f37-f519d07b99a1'::uuid, '16dfa6ad-c151-55ff-a9ee-ac6cd56a8a48'::uuid, 'комплектность документов', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('комплектность документов', '909519e3-e5dd-5489-9f37-f519d07b99a1'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('61cd7beb-8ece-52ee-9e9f-fb04e5b52e6c'::uuid, 'ACTIVE', 'DOMAIN') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('01c6bd71-4852-547c-a5ac-8a7456e6a1e0'::uuid, '61cd7beb-8ece-52ee-9e9f-fb04e5b52e6c'::uuid, 'Сбор статистики', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('сбор статистики', '01c6bd71-4852-547c-a5ac-8a7456e6a1e0'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('8ec08658-69e3-5d57-8eb6-a2ab576b54c3'::uuid, '61cd7beb-8ece-52ee-9e9f-fb04e5b52e6c'::uuid, 'статистика', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('статистика', '8ec08658-69e3-5d57-8eb6-a2ab576b54c3'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('ae91ff35-de5d-5afe-ac6f-f19bc72841b8'::uuid, '61cd7beb-8ece-52ee-9e9f-fb04e5b52e6c'::uuid, 'сбор данных', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('сбор данных', 'ae91ff35-de5d-5afe-ac6f-f19bc72841b8'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('a4127dcd-6bae-5ac2-a30c-fb0b2bbe5e08'::uuid, 'ACTIVE', 'DOMAIN') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('cff47a19-6e82-5def-a761-6ae2252c03b3'::uuid, 'a4127dcd-6bae-5ac2-a30c-fb0b2bbe5e08'::uuid, 'Контроль успеваемости', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('контроль успеваемости', 'cff47a19-6e82-5def-a761-6ae2252c03b3'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('f3e691f2-8f2a-5612-832a-64b550465db0'::uuid, 'a4127dcd-6bae-5ac2-a30c-fb0b2bbe5e08'::uuid, 'успеваемость', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('успеваемость', 'f3e691f2-8f2a-5612-832a-64b550465db0'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('912d2d18-3495-5ae9-95e8-920506dd6b30'::uuid, 'a4127dcd-6bae-5ac2-a30c-fb0b2bbe5e08'::uuid, 'контрольные срезы', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('контрольные срезы', '912d2d18-3495-5ae9-95e8-920506dd6b30'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('bc7f364f-134e-5113-b89d-7452023b1b11'::uuid, 'ACTIVE', 'DOMAIN') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('31c4c201-f2e7-59bc-9a85-f0ccc05ce738'::uuid, 'bc7f364f-134e-5113-b89d-7452023b1b11'::uuid, 'Образовательный процесс', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('образовательный процесс', '31c4c201-f2e7-59bc-9a85-f0ccc05ce738'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('daa3d7be-a59e-5499-8c4f-0644936b6ea7'::uuid, 'ACTIVE', 'DOMAIN') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('b06a764a-b2be-5584-bcf6-412e68ef9b1e'::uuid, 'daa3d7be-a59e-5499-8c4f-0644936b6ea7'::uuid, 'Работа с образовательными организациями', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('работа с образовательными организациями', 'b06a764a-b2be-5584-bcf6-412e68ef9b1e'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('2b5395e0-a154-514c-aef0-697241f958c2'::uuid, 'daa3d7be-a59e-5499-8c4f-0644936b6ea7'::uuid, 'образовательные организации', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('образовательные организации', '2b5395e0-a154-514c-aef0-697241f958c2'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('28aaf137-37f1-5f82-a907-ad8bd194a3ac'::uuid, 'daa3d7be-a59e-5499-8c4f-0644936b6ea7'::uuid, 'взаимодействие с организациями', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('взаимодействие с организациями', '28aaf137-37f1-5f82-a907-ad8bd194a3ac'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('e270fc28-9053-548d-80d4-aa038285b38b'::uuid, 'ACTIVE', 'DOMAIN') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('58929e97-972d-53d2-891e-8e687a94e8df'::uuid, 'e270fc28-9053-548d-80d4-aa038285b38b'::uuid, 'Инвентаризация', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('инвентаризация', '58929e97-972d-53d2-891e-8e687a94e8df'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('db844f1c-7227-5c89-97e5-5d1597399736'::uuid, 'e270fc28-9053-548d-80d4-aa038285b38b'::uuid, 'инвентаризация ос', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('инвентаризация ос', 'db844f1c-7227-5c89-97e5-5d1597399736'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('1d259e79-c414-5af7-972e-e0556fe443a3'::uuid, 'e270fc28-9053-548d-80d4-aa038285b38b'::uuid, 'инвентаризация основных средств', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('инвентаризация основных средств', '1d259e79-c414-5af7-972e-e0556fe443a3'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('e0065c50-261d-5a66-a053-510f45193282'::uuid, 'ACTIVE', 'DOMAIN') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('bdf85efc-e09d-53c7-948b-ab8497f992a8'::uuid, 'e0065c50-261d-5a66-a053-510f45193282'::uuid, 'Бухгалтерский учёт', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('бухгалтерский учет', 'bdf85efc-e09d-53c7-948b-ab8497f992a8'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('e391d378-81bc-56f3-9f37-0ab5cfbd8502'::uuid, 'e0065c50-261d-5a66-a053-510f45193282'::uuid, 'бухгалтерия', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('бухгалтерия', 'e391d378-81bc-56f3-9f37-0ab5cfbd8502'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('a60e01aa-adb3-508d-b5f7-a4a45d165230'::uuid, 'e0065c50-261d-5a66-a053-510f45193282'::uuid, 'бухучет', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('бухучет', 'a60e01aa-adb3-508d-b5f7-a4a45d165230'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('a9650585-4b37-51a5-ba0f-8f83269a4706'::uuid, 'ACTIVE', 'SOFT') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('40e62621-e564-57c8-91dc-f711904d2504'::uuid, 'a9650585-4b37-51a5-ba0f-8f83269a4706'::uuid, 'Внимательность к деталям', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('внимательность к деталям', '40e62621-e564-57c8-91dc-f711904d2504'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('2dddb079-d903-51d4-a4a0-3dbc69ec6a71'::uuid, 'ACTIVE', 'SOFT') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('f3928335-a8f5-550d-82da-f027e540acae'::uuid, '2dddb079-d903-51d4-a4a0-3dbc69ec6a71'::uuid, 'Системность', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('системность', 'f3928335-a8f5-550d-82da-f027e540acae'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('044d40aa-e136-53e2-8c20-df6f726ac8be'::uuid, 'ACTIVE', 'SOFT') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('8a6aa73b-2ea8-55ab-968b-bea58ff104bb'::uuid, '044d40aa-e136-53e2-8c20-df6f726ac8be'::uuid, 'Стрессоустойчивость', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('стрессоустойчивость', '8a6aa73b-2ea8-55ab-968b-bea58ff104bb'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('97440e27-2eb3-55cb-a297-111f7101c024'::uuid, 'ACTIVE', 'SOFT') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('213e2a65-300a-54b4-b57c-d450780e7226'::uuid, '97440e27-2eb3-55cb-a297-111f7101c024'::uuid, 'Ответственность', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('ответственность', '213e2a65-300a-54b4-b57c-d450780e7226'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('cc72b304-08a2-52a3-bc1c-b5f05a993d71'::uuid, 'ACTIVE', 'SOFT') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('435a180b-2795-52d4-9070-46cb7bb19802'::uuid, 'cc72b304-08a2-52a3-bc1c-b5f05a993d71'::uuid, 'Самоорганизация', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('самоорганизация', '435a180b-2795-52d4-9070-46cb7bb19802'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('390ae581-5f2b-5de1-9edf-315985432349'::uuid, 'ACTIVE', 'SOFT') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('d7ecfd16-94e7-53a1-981a-e20b12d11120'::uuid, '390ae581-5f2b-5de1-9edf-315985432349'::uuid, 'Концентрация', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('концентрация', 'd7ecfd16-94e7-53a1-981a-e20b12d11120'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('45ef2a77-3e84-55c2-9d8c-530a11b9ce46'::uuid, '390ae581-5f2b-5de1-9edf-315985432349'::uuid, 'концентрация внимания', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('концентрация внимания', '45ef2a77-3e84-55c2-9d8c-530a11b9ce46'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('d1b67233-11c9-57b3-a015-f0843ac0f510'::uuid, 'ACTIVE', 'SOFT') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('832f9232-8ef0-5419-9cec-6f6fd158841a'::uuid, 'd1b67233-11c9-57b3-a015-f0843ac0f510'::uuid, 'Монотонная работа', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('монотонная работа', '832f9232-8ef0-5419-9cec-6f6fd158841a'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('37f41e69-2600-53ac-908e-e0367464244f'::uuid, 'ACTIVE', 'SOFT') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('d34ed939-df55-5240-b3f9-b36352162b07'::uuid, '37f41e69-2600-53ac-908e-e0367464244f'::uuid, 'Готовность работать в небольшой команде', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('готовность работать в небольшой команде', 'd34ed939-df55-5240-b3f9-b36352162b07'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('62e7d81c-19ec-587f-b858-8638087ef6dd'::uuid, '37f41e69-2600-53ac-908e-e0367464244f'::uuid, 'небольшая команда', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('небольшая команда', '62e7d81c-19ec-587f-b858-8638087ef6dd'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('83f6a8f4-18b9-53df-b028-fc15814b833f'::uuid, '37f41e69-2600-53ac-908e-e0367464244f'::uuid, 'small team', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('small team', '83f6a8f4-18b9-53df-b028-fc15814b833f'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('63c9c78f-c8e1-5d7d-94b3-e180ce3bfcd2'::uuid, 'ACTIVE', 'SOFT') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('c13bb205-a931-5cf8-9143-6fe2b7768697'::uuid, '63c9c78f-c8e1-5d7d-94b3-e180ce3bfcd2'::uuid, 'Организация встреч', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('организация встреч', 'c13bb205-a931-5cf8-9143-6fe2b7768697'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('9b700959-a8d5-5d56-8f90-51009c988f3a'::uuid, 'ACTIVE', 'SOFT') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('701eda91-a29c-5b7b-9be7-a6e76eeda0a4'::uuid, '9b700959-a8d5-5d56-8f90-51009c988f3a'::uuid, 'Планирование задач', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('планирование задач', '701eda91-a29c-5b7b-9be7-a6e76eeda0a4'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('93815266-fb77-5ae6-9053-3cff38df973d'::uuid, 'ACTIVE', 'SOFT') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('a299c4f5-c77c-5319-b9d9-df12d7439c5a'::uuid, '93815266-fb77-5ae6-9053-3cff38df973d'::uuid, 'Подготовка презентаций', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('подготовка презентаций', 'a299c4f5-c77c-5319-b9d9-df12d7439c5a'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('d31062fa-503c-5601-925e-fbd2693676ee'::uuid, '93815266-fb77-5ae6-9053-3cff38df973d'::uuid, 'презентации', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('презентации', 'd31062fa-503c-5601-925e-fbd2693676ee'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('177c3aad-9709-54d9-9698-540395df354d'::uuid, 'ACTIVE', 'DOMAIN') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('c17c38b0-2b94-5af4-b366-12fef771659d'::uuid, '177c3aad-9709-54d9-9698-540395df354d'::uuid, 'Автоматизация бизнес-процессов', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('автоматизация бизнес-процессов', 'c17c38b0-2b94-5af4-b366-12fef771659d'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('370924b8-2f6b-5116-b167-77e09a2400a3'::uuid, 'ACTIVE', 'TECH') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('d30ccfcb-939c-5564-ab19-01a2be49899e'::uuid, '370924b8-2f6b-5116-b167-77e09a2400a3'::uuid, 'JWT', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('jwt', 'd30ccfcb-939c-5564-ab19-01a2be49899e'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('b76ba4cd-bc3f-5298-b682-d60999a02652'::uuid, '370924b8-2f6b-5116-b167-77e09a2400a3'::uuid, 'json web token', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('json web token', 'b76ba4cd-bc3f-5298-b682-d60999a02652'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('f6bbece4-20b6-5959-9781-5336998d2591'::uuid, 'ACTIVE', 'TECH') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('822b4aa0-934f-5039-946d-9326dd8a654b'::uuid, 'f6bbece4-20b6-5959-9781-5336998d2591'::uuid, 'Chrome Extensions', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('chrome extensions', '822b4aa0-934f-5039-946d-9326dd8a654b'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('b2e59173-07e8-5c5a-9d7b-30a7a7ce249d'::uuid, 'f6bbece4-20b6-5959-9781-5336998d2591'::uuid, 'chrome extension', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('chrome extension', 'b2e59173-07e8-5c5a-9d7b-30a7a7ce249d'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('a163eaa3-c996-5a33-9f69-9bd5da2b4cfd'::uuid, 'f6bbece4-20b6-5959-9781-5336998d2591'::uuid, 'расширения chrome', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('расширения chrome', 'a163eaa3-c996-5a33-9f69-9bd5da2b4cfd'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('c9584917-8ebb-52a4-bc2c-3ab317733ceb'::uuid, 'ACTIVE', 'TECH') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('64544ff7-1b52-5f02-86ea-ec42a0547419'::uuid, 'c9584917-8ebb-52a4-bc2c-3ab317733ceb'::uuid, 'Manifest V3', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('manifest v3', '64544ff7-1b52-5f02-86ea-ec42a0547419'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('ac39ca3f-988c-5aec-b457-a80bf8ca2b92'::uuid, 'ACTIVE', 'TECH') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('0f2f0942-f94d-5dfc-9490-96a7bb164727'::uuid, 'ac39ca3f-988c-5aec-b457-a80bf8ca2b92'::uuid, 'Telegram Bot API', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('telegram bot api', '0f2f0942-f94d-5dfc-9490-96a7bb164727'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('a127ea94-315d-58ab-ad97-0dbe165c265f'::uuid, 'ac39ca3f-988c-5aec-b457-a80bf8ca2b92'::uuid, 'telegram bot', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('telegram bot', 'a127ea94-315d-58ab-ad97-0dbe165c265f'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('b1f785eb-4a96-56bb-b1a1-79216c0d1d50'::uuid, 'ac39ca3f-988c-5aec-b457-a80bf8ca2b92'::uuid, 'telegram bots', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('telegram bots', 'b1f785eb-4a96-56bb-b1a1-79216c0d1d50'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('a04a3379-0c64-530b-9f1e-0939125b2138'::uuid, 'ACTIVE', 'TECH') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('cebada77-9ce2-583a-9334-0f4e5cd4da5e'::uuid, 'a04a3379-0c64-530b-9f1e-0939125b2138'::uuid, 'Telegram Mini App', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('telegram mini app', 'cebada77-9ce2-583a-9334-0f4e5cd4da5e'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('6cd46d38-7db6-54ce-8467-972f3f23a642'::uuid, 'a04a3379-0c64-530b-9f1e-0939125b2138'::uuid, 'telegram web app', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('telegram web app', '6cd46d38-7db6-54ce-8467-972f3f23a642'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('3350788c-289c-50f2-8b6c-d28ee19c1984'::uuid, 'ACTIVE', 'TECH') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('f68e9414-d323-5eab-a09f-5e396c7f3010'::uuid, '3350788c-289c-50f2-8b6c-d28ee19c1984'::uuid, 'SQLite', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('sqlite', 'f68e9414-d323-5eab-a09f-5e396c7f3010'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('4438ac85-12f7-5ed0-81cc-22d403299353'::uuid, 'ACTIVE', 'TECH') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('bc75f5a6-cda3-5166-bac6-f9e5551543d7'::uuid, '4438ac85-12f7-5ed0-81cc-22d403299353'::uuid, 'FTS5', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('fts5', 'bc75f5a6-cda3-5166-bac6-f9e5551543d7'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('7942900b-28cd-57f4-8f6a-21355ef357cb'::uuid, '4438ac85-12f7-5ed0-81cc-22d403299353'::uuid, 'sqlite fts5', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('sqlite fts5', '7942900b-28cd-57f4-8f6a-21355ef357cb'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('32483364-5790-59b8-96bd-8ba6abdbea1e'::uuid, 'ACTIVE', 'TOOL') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('8542a548-ca87-5c9a-9c68-8a6cd5156866'::uuid, '32483364-5790-59b8-96bd-8ba6abdbea1e'::uuid, 'Playwright', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('playwright', '8542a548-ca87-5c9a-9c68-8a6cd5156866'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('b26b1467-9aec-587c-bf50-bdd60c541623'::uuid, 'ACTIVE', 'TOOL') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('328a788e-9785-53a8-ac4b-bf4bd24d522e'::uuid, 'b26b1467-9aec-587c-bf50-bdd60c541623'::uuid, 'BeautifulSoup', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('beautifulsoup', '328a788e-9785-53a8-ac4b-bf4bd24d522e'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('b0da6c54-bc58-59e2-b081-1f3140766aa7'::uuid, 'b26b1467-9aec-587c-bf50-bdd60c541623'::uuid, 'bs4', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('bs4', 'b0da6c54-bc58-59e2-b081-1f3140766aa7'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('bf42d346-c7dd-563a-b04f-01cbe870022c'::uuid, 'ACTIVE', 'TOOL') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('56212252-b616-5478-a59b-0472a840fd10'::uuid, 'bf42d346-c7dd-563a-b04f-01cbe870022c'::uuid, 'ScreenCaptureKit', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('screencapturekit', '56212252-b616-5478-a59b-0472a840fd10'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('a8386700-165f-5180-9ec4-322daf0a6b18'::uuid, 'ACTIVE', 'TOOL') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('b5411825-3962-557e-9e96-4a7390d5689e'::uuid, 'a8386700-165f-5180-9ec4-322daf0a6b18'::uuid, 'whisper.cpp', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('whisper.cpp', 'b5411825-3962-557e-9e96-4a7390d5689e'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('8e8db982-c540-53ac-b280-d03b7db889e8'::uuid, 'a8386700-165f-5180-9ec4-322daf0a6b18'::uuid, 'whisper', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('whisper', '8e8db982-c540-53ac-b280-d03b7db889e8'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('2a7b39a6-92ff-5ed4-b9ef-21e4164cdedc'::uuid, 'ACTIVE', 'TECH') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('c9a5c97f-601b-5064-92f3-69bfe24912cd'::uuid, '2a7b39a6-92ff-5ed4-b9ef-21e4164cdedc'::uuid, 'Metal', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('metal', 'c9a5c97f-601b-5064-92f3-69bfe24912cd'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('5d510273-4c39-5236-874c-cbf6e01e1627'::uuid, 'ACTIVE', 'TOOL') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('cb962510-1838-57d5-b4e3-91cc8a55fd47'::uuid, '5d510273-4c39-5236-874c-cbf6e01e1627'::uuid, 'systemd', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('systemd', 'cb962510-1838-57d5-b4e3-91cc8a55fd47'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('3cb42643-00fe-5f12-8282-9a089adb7640'::uuid, 'ACTIVE', 'TOOL') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('8f39e117-0929-51e8-9e45-3f6eea857b67'::uuid, '3cb42643-00fe-5f12-8282-9a089adb7640'::uuid, 'VPS', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('vps', '8f39e117-0929-51e8-9e45-3f6eea857b67'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('ac4056fc-0c90-5fd4-b36f-cf153d4fa2ba'::uuid, '3cb42643-00fe-5f12-8282-9a089adb7640'::uuid, 'виртуальный сервер', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('виртуальный сервер', 'ac4056fc-0c90-5fd4-b36f-cf153d4fa2ba'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('a1fb2661-52c8-59d1-91b2-83afb5afbab4'::uuid, 'ACTIVE', 'TECH') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('e146d108-613b-5228-aa13-5ffaee91f1e3'::uuid, 'a1fb2661-52c8-59d1-91b2-83afb5afbab4'::uuid, 'MCP', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('mcp', 'e146d108-613b-5228-aa13-5ffaee91f1e3'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('bd2d2908-e5ac-59e4-a4a6-eeb4d7325593'::uuid, 'a1fb2661-52c8-59d1-91b2-83afb5afbab4'::uuid, 'model context protocol', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('model context protocol', 'bd2d2908-e5ac-59e4-a4a6-eeb4d7325593'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('0625e5f1-68ce-5386-9faf-0441108ff432'::uuid, 'ACTIVE', 'TECH') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('cb6852c8-7c83-51c1-b4cd-fe4cbc51b217'::uuid, '0625e5f1-68ce-5386-9faf-0441108ff432'::uuid, 'LLM', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('llm', 'cb6852c8-7c83-51c1-b4cd-fe4cbc51b217'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('89a4e5a9-c35a-55ca-a59c-f7a40140103c'::uuid, '0625e5f1-68ce-5386-9faf-0441108ff432'::uuid, 'large language model', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('large language model', '89a4e5a9-c35a-55ca-a59c-f7a40140103c'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('332aebe6-23ef-5a8b-bfeb-857936b670cf'::uuid, '0625e5f1-68ce-5386-9faf-0441108ff432'::uuid, 'большие языковые модели', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('большие языковые модели', '332aebe6-23ef-5a8b-bfeb-857936b670cf'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('5277bd0a-3d64-5e2b-b844-048c68fb4201'::uuid, 'ACTIVE', 'TECH') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('09ef511c-237f-524d-a8c3-4879b33462c1'::uuid, '5277bd0a-3d64-5e2b-b844-048c68fb4201'::uuid, 'Prompt Engineering', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('prompt engineering', '09ef511c-237f-524d-a8c3-4879b33462c1'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('359ccc23-c784-5f8c-86de-1539fe954768'::uuid, '5277bd0a-3d64-5e2b-b844-048c68fb4201'::uuid, 'промпт-инжиниринг', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('промпт-инжиниринг', '359ccc23-c784-5f8c-86de-1539fe954768'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('2c37d583-68cc-5540-a4d4-3ad7f126cfc6'::uuid, 'ACTIVE', 'DOMAIN') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('dda32d8d-51e8-5c5f-a79d-9770ae4e0138'::uuid, '2c37d583-68cc-5540-a4d4-3ad7f126cfc6'::uuid, 'P&L', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('p&l', 'dda32d8d-51e8-5c5f-a79d-9770ae4e0138'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('e3524939-18aa-5214-9b4f-1a7491dc16b1'::uuid, '2c37d583-68cc-5540-a4d4-3ad7f126cfc6'::uuid, 'profit and loss', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('profit and loss', 'e3524939-18aa-5214-9b4f-1a7491dc16b1'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('fe99bebc-bdf5-53b2-98a0-4e762ebbff50'::uuid, '2c37d583-68cc-5540-a4d4-3ad7f126cfc6'::uuid, 'ОПиУ', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('опиу', 'fe99bebc-bdf5-53b2-98a0-4e762ebbff50'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('644afe50-3aca-5d31-82d7-0ecc8a9372e4'::uuid, 'ACTIVE', 'DOMAIN') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('5ba5b41a-303e-524a-9d66-641863100a96'::uuid, '644afe50-3aca-5d31-82d7-0ecc8a9372e4'::uuid, 'Unit-экономика', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('unit-экономика', '5ba5b41a-303e-524a-9d66-641863100a96'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('unit-эkohomиka', '5ba5b41a-303e-524a-9d66-641863100a96'::uuid, 'FOLDED') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('fba021ce-4b0c-5782-86e5-ed09891b9277'::uuid, '644afe50-3aca-5d31-82d7-0ecc8a9372e4'::uuid, 'unit economics', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('unit economics', 'fba021ce-4b0c-5782-86e5-ed09891b9277'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('ed063c68-e692-51b4-aefc-301cef9b6d95'::uuid, '644afe50-3aca-5d31-82d7-0ecc8a9372e4'::uuid, 'юнит-экономика', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('юнит-экономика', 'ed063c68-e692-51b4-aefc-301cef9b6d95'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('88d77ace-5a0d-5faa-baaa-614ba48b2c73'::uuid, 'ACTIVE', 'DOMAIN') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('f26916b1-275b-5664-b6ad-009a49bfc58a'::uuid, '88d77ace-5a0d-5faa-baaa-614ba48b2c73'::uuid, 'CAC/LTV', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('cac/ltv', 'f26916b1-275b-5664-b6ad-009a49bfc58a'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('cf730630-8c15-5edf-a895-b9222374dc6d'::uuid, '88d77ace-5a0d-5faa-baaa-614ba48b2c73'::uuid, 'cac', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('cac', 'cf730630-8c15-5edf-a895-b9222374dc6d'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('403cc93f-6ffe-5b6c-be55-87631a5ef0de'::uuid, '88d77ace-5a0d-5faa-baaa-614ba48b2c73'::uuid, 'ltv', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('ltv', '403cc93f-6ffe-5b6c-be55-87631a5ef0de'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('98aa5804-5ca4-5e76-8864-18011ee66fe0'::uuid, 'ACTIVE', 'DOMAIN') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('7edc31a2-8a8a-5bb2-9035-714165fa31cc'::uuid, '98aa5804-5ca4-5e76-8864-18011ee66fe0'::uuid, 'WBS', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('wbs', '7edc31a2-8a8a-5bb2-9035-714165fa31cc'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('f762737f-6045-5e1b-8766-e7afd971bea8'::uuid, '98aa5804-5ca4-5e76-8864-18011ee66fe0'::uuid, 'work breakdown structure', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('work breakdown structure', 'f762737f-6045-5e1b-8766-e7afd971bea8'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('8fec0c77-a49a-5ed0-8215-2faf4722946b'::uuid, 'ACTIVE', 'DOMAIN') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('bb6a7d87-935c-5beb-a4af-e525d253f151'::uuid, '8fec0c77-a49a-5ed0-8215-2faf4722946b'::uuid, 'ABC/XYZ', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('abc/xyz', 'bb6a7d87-935c-5beb-a4af-e525d253f151'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('790ed8aa-980d-59d5-ad7b-338e88f621d2'::uuid, '8fec0c77-a49a-5ed0-8215-2faf4722946b'::uuid, 'abc-xyz', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('abc-xyz', '790ed8aa-980d-59d5-ad7b-338e88f621d2'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('78acdb19-4e15-53fe-ba21-2ca20079e0ee'::uuid, '8fec0c77-a49a-5ed0-8215-2faf4722946b'::uuid, 'abc xyz', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('abc xyz', '78acdb19-4e15-53fe-ba21-2ca20079e0ee'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('8ab0bc4e-5178-52ed-8023-249d9cda0245'::uuid, 'ACTIVE', 'DOMAIN') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('7d4feb3e-95ad-5f30-9476-e0381a7e508e'::uuid, '8ab0bc4e-5178-52ed-8023-249d9cda0245'::uuid, 'PEST', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('pest', '7d4feb3e-95ad-5f30-9476-e0381a7e508e'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('5403d030-2384-59f3-878a-814c3c62d96e'::uuid, '8ab0bc4e-5178-52ed-8023-249d9cda0245'::uuid, 'pest-анализ', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('pest-анализ', '5403d030-2384-59f3-878a-814c3c62d96e'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('pest-ahaлиз', '5403d030-2384-59f3-878a-814c3c62d96e'::uuid, 'FOLDED') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('65cef4f3-900f-5460-ab34-2e6aacc96f1a'::uuid, 'ACTIVE', 'DOMAIN') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('807ff345-6a1e-5e06-9eca-4af7cd33fb02'::uuid, '65cef4f3-900f-5460-ab34-2e6aacc96f1a'::uuid, 'SWOT', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('swot', '807ff345-6a1e-5e06-9eca-4af7cd33fb02'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('6bfd513f-2793-562d-ae7b-435f2555910d'::uuid, '65cef4f3-900f-5460-ab34-2e6aacc96f1a'::uuid, 'swot-анализ', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('swot-анализ', '6bfd513f-2793-562d-ae7b-435f2555910d'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('swot-ahaлиз', '6bfd513f-2793-562d-ae7b-435f2555910d'::uuid, 'FOLDED') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('4637805f-4267-58b1-a68f-d07b939050fe'::uuid, 'ACTIVE', 'DOMAIN') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('87f56870-827d-59af-a1c1-afec62134951'::uuid, '4637805f-4267-58b1-a68f-d07b939050fe'::uuid, 'Финансовое моделирование', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('финансовое моделирование', '87f56870-827d-59af-a1c1-afec62134951'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('93630e22-8647-5df0-b939-1eaf517d10aa'::uuid, 'ACTIVE', 'DOMAIN') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('2ccc83b5-f18d-5b8d-a9ff-f136df81cab0'::uuid, '93630e22-8647-5df0-b939-1eaf517d10aa'::uuid, 'Бизнес-анализ', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('бизнес-анализ', '2ccc83b5-f18d-5b8d-a9ff-f136df81cab0'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill (id, status, skill_type) VALUES ('25ed8bd6-db5c-5913-adca-fae536027b8b'::uuid, 'ACTIVE', 'DOMAIN') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('aa9864f5-fc8b-53d8-bd12-5274cd6a7169'::uuid, '25ed8bd6-db5c-5913-adca-fae536027b8b'::uuid, 'Анализ данных', true, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('анализ данных', 'aa9864f5-fc8b-53d8-bd12-5274cd6a7169'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('1a441736-dfc0-5c72-b9be-aff484eadc9d'::uuid, '0ba808de-ad85-582b-a5c4-c76f9f2ea674'::uuid, 'es6', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('es6', '1a441736-dfc0-5c72-b9be-aff484eadc9d'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('a9db41e0-fc6d-58a1-aa86-d5507ebcb859'::uuid, '0ba808de-ad85-582b-a5c4-c76f9f2ea674'::uuid, 'es6+', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('es6+', 'a9db41e0-fc6d-58a1-aa86-d5507ebcb859'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('584ef6e3-2717-55a6-b525-3053b0b0b8c8'::uuid, '0ba808de-ad85-582b-a5c4-c76f9f2ea674'::uuid, 'ecmascript', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('ecmascript', '584ef6e3-2717-55a6-b525-3053b0b0b8c8'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('f0e5da30-139d-5622-9d4e-a538dc3ee4c1'::uuid, 'e4545bb5-a299-558f-92bd-38f08f4b99ed'::uuid, 'основные средства', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('основные средства', 'f0e5da30-139d-5622-9d4e-a538dc3ee4c1'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('658ba801-1afc-511a-aace-74ca4e6aa8d3'::uuid, 'e4545bb5-a299-558f-92bd-38f08f4b99ed'::uuid, 'учет ос', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('учет ос', '658ba801-1afc-511a-aace-74ca4e6aa8d3'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('315c31ce-ae91-5ce0-965b-03e04bea44d8'::uuid, 'e4545bb5-a299-558f-92bd-38f08f4b99ed'::uuid, 'движение ос', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('движение ос', '315c31ce-ae91-5ce0-965b-03e04bea44d8'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('29635722-aeb6-56dc-80e3-c898f04277ba'::uuid, 'e4545bb5-a299-558f-92bd-38f08f4b99ed'::uuid, 'списание ос', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('списание ос', '29635722-aeb6-56dc-80e3-c898f04277ba'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('29788cf8-c9e9-533e-b6b7-40c355f252b1'::uuid, 'e4545bb5-a299-558f-92bd-38f08f4b99ed'::uuid, 'инвентарные номера', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('инвентарные номера', '29788cf8-c9e9-533e-b6b7-40c355f252b1'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('503e7c72-e5bb-5789-853f-05e4497410a4'::uuid, 'e4545bb5-a299-558f-92bd-38f08f4b99ed'::uuid, 'тмц', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('тмц', '503e7c72-e5bb-5789-853f-05e4497410a4'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('56b1aa85-e7ce-58eb-98f3-af7b84bfc9a8'::uuid, '637a91f0-9faf-5acd-96f7-9ff1b3407710'::uuid, 'склад', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('склад', '56b1aa85-e7ce-58eb-98f3-af7b84bfc9a8'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('72ebe025-4f29-59dd-b408-a359de75d9dd'::uuid, 'a51ac0a6-96f7-5425-8c3f-67a4258684c8'::uuid, 'документооборот', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('документооборот', '72ebe025-4f29-59dd-b408-a359de75d9dd'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('1d3db23f-55f1-5100-8df5-ac1699621645'::uuid, 'a51ac0a6-96f7-5425-8c3f-67a4258684c8'::uuid, 'документоведение', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('документоведение', '1d3db23f-55f1-5100-8df5-ac1699621645'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('2e192f87-9aa5-579c-9653-30541a7d16a5'::uuid, 'a51ac0a6-96f7-5425-8c3f-67a4258684c8'::uuid, 'документовед', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('документовед', '2e192f87-9aa5-579c-9653-30541a7d16a5'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('9c8abaa2-f6a6-55d5-8311-ce5a058d7b7b'::uuid, 'a51ac0a6-96f7-5425-8c3f-67a4258684c8'::uuid, 'делопроизводитель', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('делопроизводитель', '9c8abaa2-f6a6-55d5-8311-ce5a058d7b7b'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('668a22dc-5c52-50d5-bc3d-2eecdfda956d'::uuid, '1bb3df5c-f93d-5e31-b97d-8dcfae91d045'::uuid, 'коммуникативные навыки', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('коммуникативные навыки', '668a22dc-5c52-50d5-bc3d-2eecdfda956d'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;
INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('2c94d230-48f9-58ac-a9f3-a084b81d6962'::uuid, '1bb3df5c-f93d-5e31-b97d-8dcfae91d045'::uuid, 'деловая коммуникация', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('деловая коммуникация', '2c94d230-48f9-58ac-a9f3-a084b81d6962'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill_alias (id, skill_id, alias_raw, is_canonical, lang, source, lemma_mode, created_by) VALUES ('471d3b69-242a-57d1-af57-d79198c4c58e'::uuid, 'c647b05a-d6d9-57ec-91d1-1bceab7523ed'::uuid, 'командная работа', false, 'mixed', 'SEED', 'AUTO', 'seed') ON CONFLICT (id) DO NOTHING;
INSERT INTO dictionary.skill_alias_key (key, alias_id, kind) VALUES ('командная работа', '471d3b69-242a-57d1-af57-d79198c4c58e'::uuid, 'RAW') ON CONFLICT (key) DO NOTHING;

INSERT INTO dictionary.skill_relation (left_skill_id, right_skill_id, relation_kind) VALUES ('12a891ce-9936-547a-95c6-baa7c8c21535'::uuid, 'e79a6edf-b6b4-5a4e-85fe-b98d386e2e1e'::uuid, 'RELATED') ON CONFLICT DO NOTHING;
INSERT INTO dictionary.skill_relation (left_skill_id, right_skill_id, relation_kind) VALUES ('e5d48528-ef6a-545f-a196-629cfa99636e'::uuid, 'ec89e0a5-a0a9-5953-9729-6e2ad3065e0e'::uuid, 'SAME_GROUP') ON CONFLICT DO NOTHING;
INSERT INTO dictionary.skill_relation (left_skill_id, right_skill_id, relation_kind) VALUES ('99f9a910-4c20-551c-8f35-aa42f258829d'::uuid, 'a55a0160-f383-598b-acab-ecc3103b9f44'::uuid, 'SAME_GROUP') ON CONFLICT DO NOTHING;
INSERT INTO dictionary.skill_relation (left_skill_id, right_skill_id, relation_kind) VALUES ('2df9a597-cd55-5eb4-91ec-f95b258343d3'::uuid, '3a6710b1-7b9b-5431-a0e9-b1d68c8b7572'::uuid, 'RELATED') ON CONFLICT DO NOTHING;
INSERT INTO dictionary.skill_relation (left_skill_id, right_skill_id, relation_kind) VALUES ('264e6b4e-ec6f-5742-800b-e066bbc04590'::uuid, '2df9a597-cd55-5eb4-91ec-f95b258343d3'::uuid, 'RELATED') ON CONFLICT DO NOTHING;
INSERT INTO dictionary.skill_relation (left_skill_id, right_skill_id, relation_kind) VALUES ('2df9a597-cd55-5eb4-91ec-f95b258343d3'::uuid, '5a7f5090-2f22-5ed2-8e89-00d0cd76695b'::uuid, 'RELATED') ON CONFLICT DO NOTHING;
INSERT INTO dictionary.skill_relation (left_skill_id, right_skill_id, relation_kind) VALUES ('2df9a597-cd55-5eb4-91ec-f95b258343d3'::uuid, 'be9001ff-a457-5ea7-888f-0b365f6c33a3'::uuid, 'RELATED') ON CONFLICT DO NOTHING;
INSERT INTO dictionary.skill_relation (left_skill_id, right_skill_id, relation_kind) VALUES ('2df9a597-cd55-5eb4-91ec-f95b258343d3'::uuid, '34ffdb95-c5b9-599f-ab4a-22092c734abf'::uuid, 'RELATED') ON CONFLICT DO NOTHING;
INSERT INTO dictionary.skill_relation (left_skill_id, right_skill_id, relation_kind) VALUES ('5a7f5090-2f22-5ed2-8e89-00d0cd76695b'::uuid, 'cb130713-9764-5aec-8dc2-8965aa11645c'::uuid, 'SAME_GROUP') ON CONFLICT DO NOTHING;
INSERT INTO dictionary.skill_relation (left_skill_id, right_skill_id, relation_kind) VALUES ('64087a55-ae1d-567c-a8ba-9db6018ac868'::uuid, '6656725f-186d-5046-9976-ca3d8db47e49'::uuid, 'RELATED') ON CONFLICT DO NOTHING;
INSERT INTO dictionary.skill_relation (left_skill_id, right_skill_id, relation_kind) VALUES ('64087a55-ae1d-567c-a8ba-9db6018ac868'::uuid, '7c04a069-c1a5-50fa-bb0e-45d41c17ca45'::uuid, 'RELATED') ON CONFLICT DO NOTHING;
INSERT INTO dictionary.skill_relation (left_skill_id, right_skill_id, relation_kind) VALUES ('212bc73b-0550-5f74-81aa-fff037c75029'::uuid, '64087a55-ae1d-567c-a8ba-9db6018ac868'::uuid, 'RELATED') ON CONFLICT DO NOTHING;
INSERT INTO dictionary.skill_relation (left_skill_id, right_skill_id, relation_kind) VALUES ('64087a55-ae1d-567c-a8ba-9db6018ac868'::uuid, 'b435f340-fe5d-57b2-ba28-f1df5b5220f3'::uuid, 'RELATED') ON CONFLICT DO NOTHING;
INSERT INTO dictionary.skill_relation (left_skill_id, right_skill_id, relation_kind) VALUES ('64087a55-ae1d-567c-a8ba-9db6018ac868'::uuid, '76457970-eb66-592a-bea8-eea5a006bb74'::uuid, 'RELATED') ON CONFLICT DO NOTHING;
INSERT INTO dictionary.skill_relation (left_skill_id, right_skill_id, relation_kind) VALUES ('64087a55-ae1d-567c-a8ba-9db6018ac868'::uuid, '7fbc8fbe-57fe-5e35-8ff1-eda424cc5166'::uuid, 'RELATED') ON CONFLICT DO NOTHING;
INSERT INTO dictionary.skill_relation (left_skill_id, right_skill_id, relation_kind) VALUES ('000851ea-05d2-5e92-aba0-3af60591fad8'::uuid, '64087a55-ae1d-567c-a8ba-9db6018ac868'::uuid, 'RELATED') ON CONFLICT DO NOTHING;
INSERT INTO dictionary.skill_relation (left_skill_id, right_skill_id, relation_kind) VALUES ('212bc73b-0550-5f74-81aa-fff037c75029'::uuid, '7c04a069-c1a5-50fa-bb0e-45d41c17ca45'::uuid, 'SAME_GROUP') ON CONFLICT DO NOTHING;
INSERT INTO dictionary.skill_relation (left_skill_id, right_skill_id, relation_kind) VALUES ('a84ad921-249b-50d5-a36f-761fedf0993e'::uuid, 'a93ab924-4e40-53bd-8fd2-f0e349c80506'::uuid, 'SAME_GROUP') ON CONFLICT DO NOTHING;
INSERT INTO dictionary.skill_relation (left_skill_id, right_skill_id, relation_kind) VALUES ('7bdca04e-bb84-5fa7-b99c-297252420434'::uuid, 'fb9181a1-fda4-514b-8dce-d5e5af8a5926'::uuid, 'SAME_GROUP') ON CONFLICT DO NOTHING;
INSERT INTO dictionary.skill_relation (left_skill_id, right_skill_id, relation_kind) VALUES ('7bdca04e-bb84-5fa7-b99c-297252420434'::uuid, 'da9c44d3-e874-59ff-b2d3-3be080675487'::uuid, 'RELATED') ON CONFLICT DO NOTHING;
INSERT INTO dictionary.skill_relation (left_skill_id, right_skill_id, relation_kind) VALUES ('906e8d27-aa29-55b9-b6a6-01fc655ba1cc'::uuid, 'aa5e070d-4623-55dd-a2d6-0ac0b2b94f07'::uuid, 'RELATED') ON CONFLICT DO NOTHING;
INSERT INTO dictionary.skill_relation (left_skill_id, right_skill_id, relation_kind) VALUES ('17dc68a3-f782-5803-9a77-a1a0b60b5751'::uuid, 'aa5e070d-4623-55dd-a2d6-0ac0b2b94f07'::uuid, 'RELATED') ON CONFLICT DO NOTHING;
INSERT INTO dictionary.skill_relation (left_skill_id, right_skill_id, relation_kind) VALUES ('aa5e070d-4623-55dd-a2d6-0ac0b2b94f07'::uuid, 'cdf652aa-ce46-5b9b-a911-d6eaff2de027'::uuid, 'RELATED') ON CONFLICT DO NOTHING;
INSERT INTO dictionary.skill_relation (left_skill_id, right_skill_id, relation_kind) VALUES ('2e8c7a42-d49c-59ef-af43-1ca37e0271e8'::uuid, 'aa5e070d-4623-55dd-a2d6-0ac0b2b94f07'::uuid, 'SAME_GROUP') ON CONFLICT DO NOTHING;
INSERT INTO dictionary.skill_relation (left_skill_id, right_skill_id, relation_kind) VALUES ('c9584917-8ebb-52a4-bc2c-3ab317733ceb'::uuid, 'f6bbece4-20b6-5959-9781-5336998d2591'::uuid, 'RELATED') ON CONFLICT DO NOTHING;
INSERT INTO dictionary.skill_relation (left_skill_id, right_skill_id, relation_kind) VALUES ('a04a3379-0c64-530b-9f1e-0939125b2138'::uuid, 'ac39ca3f-988c-5aec-b457-a80bf8ca2b92'::uuid, 'RELATED') ON CONFLICT DO NOTHING;
INSERT INTO dictionary.skill_relation (left_skill_id, right_skill_id, relation_kind) VALUES ('3350788c-289c-50f2-8b6c-d28ee19c1984'::uuid, '4438ac85-12f7-5ed0-81cc-22d403299353'::uuid, 'RELATED') ON CONFLICT DO NOTHING;
INSERT INTO dictionary.skill_relation (left_skill_id, right_skill_id, relation_kind) VALUES ('5ec71905-8354-504d-8887-d90d453b6be3'::uuid, '8f23fad1-9ab1-5f54-88e6-3c7e349488a2'::uuid, 'SAME_GROUP') ON CONFLICT DO NOTHING;
INSERT INTO dictionary.skill_relation (left_skill_id, right_skill_id, relation_kind) VALUES ('e270fc28-9053-548d-80d4-aa038285b38b'::uuid, 'e4545bb5-a299-558f-92bd-38f08f4b99ed'::uuid, 'RELATED') ON CONFLICT DO NOTHING;
INSERT INTO dictionary.skill_relation (left_skill_id, right_skill_id, relation_kind) VALUES ('637a91f0-9faf-5acd-96f7-9ff1b3407710'::uuid, 'e4545bb5-a299-558f-92bd-38f08f4b99ed'::uuid, 'RELATED') ON CONFLICT DO NOTHING;
INSERT INTO dictionary.skill_relation (left_skill_id, right_skill_id, relation_kind) VALUES ('e0065c50-261d-5a66-a053-510f45193282'::uuid, 'e4545bb5-a299-558f-92bd-38f08f4b99ed'::uuid, 'RELATED') ON CONFLICT DO NOTHING;
INSERT INTO dictionary.skill_relation (left_skill_id, right_skill_id, relation_kind) VALUES ('a514807b-427f-59f2-9b62-009e7ec269b7'::uuid, 'a51ac0a6-96f7-5425-8c3f-67a4258684c8'::uuid, 'RELATED') ON CONFLICT DO NOTHING;
INSERT INTO dictionary.skill_relation (left_skill_id, right_skill_id, relation_kind) VALUES ('2284e561-369e-54ec-9750-fd27464134b3'::uuid, 'a51ac0a6-96f7-5425-8c3f-67a4258684c8'::uuid, 'RELATED') ON CONFLICT DO NOTHING;
INSERT INTO dictionary.skill_relation (left_skill_id, right_skill_id, relation_kind) VALUES ('2c37d583-68cc-5540-a4d4-3ad7f126cfc6'::uuid, '644afe50-3aca-5d31-82d7-0ecc8a9372e4'::uuid, 'RELATED') ON CONFLICT DO NOTHING;
INSERT INTO dictionary.skill_relation (left_skill_id, right_skill_id, relation_kind) VALUES ('2c37d583-68cc-5540-a4d4-3ad7f126cfc6'::uuid, '4637805f-4267-58b1-a68f-d07b939050fe'::uuid, 'RELATED') ON CONFLICT DO NOTHING;
INSERT INTO dictionary.skill_relation (left_skill_id, right_skill_id, relation_kind) VALUES ('151a3761-08e2-5675-8d91-73d8e468f7a6'::uuid, '2c37d583-68cc-5540-a4d4-3ad7f126cfc6'::uuid, 'RELATED') ON CONFLICT DO NOTHING;
INSERT INTO dictionary.skill_relation (left_skill_id, right_skill_id, relation_kind) VALUES ('644afe50-3aca-5d31-82d7-0ecc8a9372e4'::uuid, '88d77ace-5a0d-5faa-baaa-614ba48b2c73'::uuid, 'RELATED') ON CONFLICT DO NOTHING;
INSERT INTO dictionary.skill_relation (left_skill_id, right_skill_id, relation_kind) VALUES ('98aa5804-5ca4-5e76-8864-18011ee66fe0'::uuid, 'd5113ed2-0e26-59c6-8d3c-a465557558d6'::uuid, 'RELATED') ON CONFLICT DO NOTHING;
INSERT INTO dictionary.skill_relation (left_skill_id, right_skill_id, relation_kind) VALUES ('0625e5f1-68ce-5386-9faf-0441108ff432'::uuid, '5277bd0a-3d64-5e2b-b844-048c68fb4201'::uuid, 'RELATED') ON CONFLICT DO NOTHING;
INSERT INTO dictionary.skill_relation (left_skill_id, right_skill_id, relation_kind) VALUES ('0625e5f1-68ce-5386-9faf-0441108ff432'::uuid, '2f2fdaa9-6938-5e63-8f5a-ac7cb24f3556'::uuid, 'RELATED') ON CONFLICT DO NOTHING;
INSERT INTO dictionary.skill_relation (left_skill_id, right_skill_id, relation_kind) VALUES ('4e4db277-a725-5296-a176-a0120f07b612'::uuid, 'caf96ff0-f935-52fc-9f36-31a9dd3e7563'::uuid, 'SAME_GROUP') ON CONFLICT DO NOTHING;
INSERT INTO dictionary.skill_relation (left_skill_id, right_skill_id, relation_kind) VALUES ('07849fe4-5bca-563f-b26e-7850bb178192'::uuid, '0cf9c7ab-5cef-55da-a4a0-8b44295ec4f1'::uuid, 'RELATED') ON CONFLICT DO NOTHING;
INSERT INTO dictionary.skill_relation (left_skill_id, right_skill_id, relation_kind) VALUES ('0cf9c7ab-5cef-55da-a4a0-8b44295ec4f1'::uuid, '43542236-6edc-5e3e-9872-94358f3508fa'::uuid, 'RELATED') ON CONFLICT DO NOTHING;

UPDATE dictionary.dictionary_meta
SET dictionary_version = dictionary_version + 1,
    relation_version = relation_version + 1
WHERE id = 1;

--rollback -- Полный откат пополнения требует отдельного снимка словаря.
