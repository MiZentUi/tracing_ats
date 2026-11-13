# Демо: словарь навыков и алиасы

Сценарии показывают, что изменилось для пользователя: `js` в вакансии и резюме
сводится к `JavaScript`, новые написания попадают в очередь HR, а решение HR
сразу влияет на следующий скоринг.

## Запуск

```bash
# Без ключа LLM: ats использует mock-llm, scoring — детерминированную заглушку.
export ATS_PROFILES=mock-llm
export SCORING_SERVICE_LLM_PROVIDER=mock
docker compose up -d --build

./scripts/demo-skill-dictionary.sh
```

Адреса:

- UI: <http://localhost:3000>
- Словарь: <http://localhost:3000/dictionary>
- REST: <http://localhost:8080/api/v1>
- Swagger UI: <http://localhost:8080/swagger-ui.html>

Скрипт создаёт вакансию с `JS`, `Tauri`, `PostgreSQL`, показывает очередь NEW,
подтверждает подсказку для `Tauri`, загружает резюме и печатает рейтинг и отчёт.

## Сценарий 1. Алиас в вакансии

Создайте вакансию с текстом:

```text
Требования:
- JS от 2 лет
- Tauri
- PostgreSQL
```

В ответе `POST /api/v1/vacancies`:

- `JS` → `skill=JavaScript`, `normalization_status=MATCHED`;
- `PostgreSQL` → `MATCHED`;
- `Tauri` → `normalization_status=NEW`, `skill_id=null`.

Это и есть исправление асимметрии: раньше `js` не находил `javascript`.

## Сценарий 2. Очередь NEW и решение HR

1. Откройте <http://localhost:3000/dictionary>.
2. На вкладке «Очередь NEW» найдите `tauri`.
3. Нажмите «Похоже на Tauri» (подсказка) или «Создать навык».
4. Создайте ещё одну вакансию с `Tauri` — теперь требование будет `MATCHED`.

Проверка через API:

```bash
curl -s 'http://localhost:8080/api/v1/admin/dictionary/review-items?status=PENDING'
```

После подтверждения очередь пуста, а `Tauri` есть в списке навыков.

## Сценарий 3. Резюме и отчёт

Загрузите `scripts/demo-resume.txt`:

```bash
curl -s -X POST http://localhost:8080/api/v1/resumes \
  -F 'files=@scripts/demo-resume.txt'
```

Через несколько секунд:

```bash
curl -s http://localhost:8080/api/v1/resumes/<resumeId>/ranking
curl -s http://localhost:8080/api/v1/resumes/<resumeId>/vacancies/<vacancyId>/report
```

В отчёте:

- `JavaScript` — `COVERED`, цитата `JS — 3 года`;
- `PostgreSQL` — `COVERED`;
- `Tauri` — `COVERED`, если HR уже подтвердил навык.

## Сценарий 4. Деградация без словаря

Остановите словарь и пересчитайте рейтинг:

```bash
docker compose stop dictionary-service
curl -s -X POST http://localhost:8080/api/v1/resumes/<resumeId>/match
```

Скоринг не падает: `dictionary_mode=EXACT_FALLBACK`, `JS` больше не равен
`JavaScript`. Верните словарь:

```bash
docker compose start dictionary-service
```

## Сценарий 5. Управление алиасами и конфликт

```bash
# добавить алиас
curl -s -X POST http://localhost:8080/api/v1/admin/dictionary/skills/<skillId>/aliases \
  -H 'Content-Type: application/json' \
  -d '{"raw":"js-разработчик","lang":"ru","lemmaMode":"AUTO"}'

# конфликт: алиас уже принадлежит другому навыку -> 409 ALIAS_OWNED_BY_OTHER
```

Удаление используемого навыка возвращает `409 SKILL_IN_USE`. Merge нет:
удалите алиас и привяжите его к нужному навыку заново.

## Сценарий 6. Генерация навыков по специальности

1. Откройте <http://localhost:3000/dictionary> → вкладка **«Генерация»**.
2. Введите специальность `электрик`, примечание `ЖКХ, без допусков`, максимум `10`.
3. Нажмите **«Сгенерировать»**. LLM вернёт черновики навыков и алиасов.
4. Проверьте:
   - `Электромонтаж` может быть привязан к существующему навыку;
   - `ПУЭ`, `Электроинструмент`, `Щитовое оборудование` — новые;
   - конфликтные алиасы подсвечены и недоступны для подтверждения.
5. Снимите галочки с лишнего и нажмите **«Подтвердить выбранное»**.
6. В «Навыках» появятся новые каноны и алиасы; `resolver_version` вырастет.
7. Создайте вакансию с `ПУЭ` или `электроинструмент` — требование будет
   `MATCHED`, а не `NEW`.

Через API:

```bash
curl -s -X POST http://localhost:8080/api/v1/admin/dictionary/generation-jobs \
  -H 'Content-Type: application/json' \
  -d '{"specialization":"электрик","note":"ЖКХ, без допусков","maxSkills":10}'
```

Ответ `DONE` содержит черновики. Подтверждение — `POST .../approve`.
LLM вызывает только `ats-service`; `dictionary-service.Resolve` LLM не вызывает.

## Ограничения mock-LLM

Mock-LLM извлекает только известные ему навыки и по одному на строку. Поэтому
в демо-резюме каждый навык — отдельная строка. Реальная LLM извлекает свободнее.

## Что смотреть в логах

```bash
docker compose logs -f dictionary-service
docker compose logs -f scoring-service | grep -E 'Resolve|dictionary'
docker compose logs -f ats-service | grep -E 'dictionary|scoring'
```

В логах есть `resolver_version`, `dictionary_mode`, размер батча и счётчики
методов, но нет текстов резюме, цитат и PII.
