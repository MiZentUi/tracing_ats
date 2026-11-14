# mock-hh

Детерминированная заглушка hh.ru на Rust + Axum. Нужна, чтобы проверить публикацию
вакансии, подписку на отклики и вебхук `NEW_NEGOTIATION_VACANCY` без реального
токена работодателя и платного доступа к резюме.

## Что эмулируется

| Метод | Поведение |
|---|---|
| `GET /` | HTML-фронтенд mock hh.ru: список опубликованных вакансий. |
| `GET /vacancy/{id}` | HTML-страница вакансии с формой отклика. |
| `POST /vacancy/{id}/respond` | Отправляет вебхук отклика. Принимает текст резюме или TXT/MD-файл. |
| `POST /vacancies/drafts` | Создаёт черновик, возвращает `id` и `publication_ready=true`. |
| `POST /vacancies/drafts/{id}/publish` | Публикует черновик, возвращает `vacancy_ids`. |
| `GET /vacancies/{id}` | Отдаёт вакансию, `alternate_url` и `employer.id`. |
| `GET /resumes/{id}` | Отдаёт синтетическое резюме. `*forbidden*` → 403, `*missing*` → 404. |
| `POST /webhook/subscriptions` | Сохраняет подписку и возвращает `id`. |
| `DELETE /webhook/subscriptions/{id}` | Удаляет подписку. |
| `PUT /employers/{employer}/vacancies/archived/{id}` | Помечает вакансию архивной. |

Админские точки:

- `POST /__admin/respond` — отправить вебхук на `ATS_HH_WEBHOOK_URL`;
- `POST /__admin/reset` — вернуть демо-состояние;
- `GET /__admin/state` — счётчики и идентификаторы без PII;
- `GET /health`.

## Демо-резюме

- `resume-strong` — Java, Spring Boot, PostgreSQL, Kafka, Docker.
- `resume-weak` — Python без опыта.
- `resume-empty` — пустое резюме.

На странице вакансии вставьте текст резюме или загрузите TXT/MD: готовые
демо-резюме в UI не выбираются, чтобы отклик выглядел как настоящий. Свой текст
уходит в ATS как обычное резюме и проходит тот же пайплайн маскировки и скоринга.
PDF/DOCX в mock не разбираются — для них есть ручная загрузка в разделе «Резюме»
Tracing ATS. Демо-резюме остались только в админском `POST /__admin/respond` для
скриптов и тестов.

В резюме есть имя и контакты: это проверяет, что маскировка PII работает и в
hh-пайплайне. В логи текст резюме не попадает.

## Переменные окружения

| Переменная | По умолчанию | Назначение |
|---|---|---|
| `MOCK_HH_PORT` | `8090` | порт сервиса |
| `MOCK_HH_PUBLIC_URL` | `http://localhost:8090` | публичный адрес mock-hh для ссылки «Открыть на hh.ru» |
| `MOCK_HH_STATE_FILE` | — | файл состояния, чтобы вакансии переживали рестарт |
| `MOCK_HH_WEBHOOK_URL` | `http://localhost:8080/api/v1/integrations/hh/webhook?token=mock-hh-token` | куда отправлять вебхук |
| `MOCK_HH_EMPLOYER_ID` | `999` | идентификатор работодателя |

## Запуск

```bash
docker compose --profile mock-hh up --build
```

Проверка публикации и отклика:

```bash
# 1. Опубликовать вакансию платформы (нужен подтверждённый шаблон)
curl -X POST http://localhost:8080/api/v1/vacancies/<vacancy_id>/hh-publication

# 2. Отправить отклик
curl -X POST http://localhost:8090/__admin/respond \
  -H 'Content-Type: application/json' \
  -d '{"vacancy_id":"<hh_vacancy_id>","resume_id":"resume-strong"}'

# 3. Посмотреть состояние заглушки
curl http://localhost:8090/__admin/state
```

## Ограничения

- Это не полная реализация hh.ru: только поля, которые нужны нашему пайплайну.
- Нет OAuth, rate limit, платных услуг и проверки подписи вебхука.
- Тела запросов и резюме не логируются.
