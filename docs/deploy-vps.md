# Деплой на VPS

Деплой выполняется GitHub Actions: CI проверяет `main`, затем workflow
`Deploy` собирает образы, публикует их в GHCR и обновляет контейнеры на VPS.

## 1. Что должно быть на VPS

- Docker Engine и Docker Compose plugin (`docker compose version`).
- Git, `curl` и `tar`.
- Открыты `22` (SSH), `80`/`443` для сайта и `8090` для UI `mock-hh`. Внутренние
  сервисы в `compose.prod.yaml` не публикуют порты на хост, кроме `mock-hh`.
- Клон репозитория, например `/opt/tracing-ats`:

```bash
git clone git@github.com:MiZentUi/tracing_ats.git /opt/tracing-ats
cd /opt/tracing-ats
```

Если репозиторий приватный, на VPS нужен deploy key или другой доступ на чтение.

## 2. Источники конфигурации

- **`.env.example`** — базовая конфигурация: порты, БД, S3, таймауты, флаги.
  Хранится в репозитории и не содержит реальных секретов.
- **`.env.production`** — генерируется на VPS при каждом деплое из GitHub
  Environment. Содержит только секреты и модельные настройки; права `600`,
  в git не попадает.
- **GitHub Environment `production`** — единственный источник секретов и
  модельных доменов/имён.

Workflow запускает compose так:

```bash
docker compose \
  --env-file .env.example \
  --env-file .env.production \
  -f compose.yaml -f compose.prod.yaml ...
```

Значения из `.env.production` перекрывают `.env.example`.

## 3. GitHub Environment `production`

Создай environment `production` и добавь protection rules: деплой только с
`main`, при необходимости — required reviewers.

### Secrets

| Секрет | Обязательно | Смысл |
|---|---|---|
| `VPS_HOST` | да | IP или домен VPS |
| `VPS_USER` | да | SSH-пользователь |
| `VPS_SSH_KEY` | да | приватный SSH-ключ для деплоя |
| `VPS_PORT` | нет | SSH-порт, по умолчанию `22` |
| `DEPLOY_PATH` | да | путь к клону, например `/opt/tracing-ats` |
| `GHCR_TOKEN` | нет | PAT с `read:packages`, если образы приватные |
| `GHCR_USER` | нет | пользователь для GHCR, по умолчанию автор workflow |
| `OPENROUTER_API_KEY` | да | основной ключ LLM |
| `VACANCY_LLM_API_KEY` | нет | отдельный ключ для ats-service |
| `SCORING_SERVICE_LLM_API_KEY` | нет | отдельный ключ для scoring-service |
| `CLOUDFLARE_AI_API_TOKEN` | нет | ключ fallback Cloudflare |
| `POSTGRES_PASSWORD` | да | пароль Postgres |
| `ATS_PII_ENCRYPTION_KEY` | да | base64-ключ AES-256-GCM |
| `ATS_S3_ACCESS_KEY` | да | доступ к MinIO/S3 |
| `ATS_S3_SECRET_KEY` | да | секрет MinIO/S3 |
| `DICTIONARY_SERVICE_TOKEN` | нет | зарезервирован; сейчас загружается, но не проверяется |
| `NER_MODEL_URL` | нет | URL tar-архива NER-модели, если её нет в `models/ner` |

Для `GHCR_TOKEN` достаточно classic PAT с правом `read:packages`. Если пакеты
GHCR публичные, секрет не нужен. Также можно один раз выполнить `docker login
ghcr.io` на VPS вручную.

### Variables

| Переменная | Пример | Смысл |
|---|---|---|
| `VACANCY_LLM_BASE_URL` | `https://openrouter.ai/api/v1` | домен модели вакансий |
| `VACANCY_LLM_MODEL` | `dots-studio/dots-3-note-preview:free` | модель вакансий |
| `SCORING_SERVICE_LLM_PROVIDER` | `openai` | провайдер scoring-service |
| `SCORING_SERVICE_LLM_BASE_URL` | `https://openrouter.ai/api/v1` | домен модели scoring |
| `SCORING_SERVICE_LLM_MODEL` | `<модель>` | модель scoring |
| `CLOUDFLARE_AI_ENABLED` | `false` | включить fallback |
| `CLOUDFLARE_AI_BASE_URL` | `https://api.cloudflare.com/client/v4/accounts/<ACCOUNT_ID>/ai/v1` | домен Cloudflare |
| `CLOUDFLARE_AI_MODEL` | `@cf/qwen/qwen3.8-27b` | модель fallback |
| `CLOUDFLARE_AI_REASONING_EFFORT` | `low` | reasoning effort |
| `FRONTEND_PORT` | `80` | порт сайта на VPS |
| `MOCK_HH_ENABLED` | `true` | запускать hh-заглушку в production; `false` отключает её |
| `MOCK_HH_PORT` | `8090` | порт mock-hh на VPS |
| `MOCK_HH_PUBLIC_URL` | `http://<VPS_HOST>:8090` | публичный адрес mock-hh для ссылки «Открыть на hh.ru»; если пусто, собирается из `VPS_HOST` |

Если `SCORING_SERVICE_LLM_PROVIDER` не задан, остаётся значение из
`.env.example` (`mock`). Для реальной модели задай `openai`.

### Пример `.env.production`

```dotenv
OPENROUTER_API_KEY=sk-or-...
VACANCY_LLM_BASE_URL=https://openrouter.ai/api/v1
VACANCY_LLM_MODEL=dots-studio/dots-3-note-preview:free
SCORING_SERVICE_LLM_PROVIDER=openai
SCORING_SERVICE_LLM_BASE_URL=https://openrouter.ai/api/v1
SCORING_SERVICE_LLM_MODEL=<модель>
CLOUDFLARE_AI_ENABLED=false
POSTGRES_PASSWORD=<сильный пароль>
ATS_PII_ENCRYPTION_KEY=<base64-ключ>
ATS_S3_ACCESS_KEY=<minio-user>
ATS_S3_SECRET_KEY=<minio-password>
FRONTEND_PORT=80
MOCK_HH_ENABLED=true
MOCK_HH_PORT=8090
MOCK_HH_PUBLIC_URL=http://<VPS_HOST>:8090
```

`VACANCY_LLM_API_KEY` и `SCORING_SERVICE_LLM_API_KEY` необязательны: если пусты,
используется `OPENROUTER_API_KEY`.

## 4. Как работает workflow

`.github/workflows/deploy.yml`:

1. Ждёт успешного завершения workflow `CI` для `main` (или запускается вручную
   через `workflow_dispatch`).
2. Собирает и публикует в GHCR образы:
   - `migrations`
   - `dictionary-service`
   - `scoring-service`
   - `ats-service`
   - `frontend`
   - `mock-hh`
3. Заходит на VPS по SSH.
4. Переключает клон на точный SHA, для которого собраны образы.
5. Создаёт `.env.production` из GitHub Secrets/Variables с правами `600`.
6. Делает `docker compose pull` и `docker compose up -d --no-build`.
7. Проверяет, что сайт отвечает на `http://127.0.0.1:80`, а `mock-hh` — на
   `http://127.0.0.1:8090` при `MOCK_HH_ENABLED=true`.

Используются `compose.yaml` + `compose.prod.yaml`. Production-override:

- подставляет GHCR-образы;
- убирает публикацию портов Postgres, MinIO, dictionary, scoring и ats-service;
- оставляет наружу frontend на `FRONTEND_PORT` (по умолчанию `80`) и `mock-hh`
  на `MOCK_HH_PORT` (по умолчанию `8090`);
- при `MOCK_HH_ENABLED=false` mock-hh не запускается, а `ats-service` получает
  `ATS_HH_ENABLED=false`.

`mock-hh` — демонстрационная заглушка без авторизации. Не оставляй её открытой
в интернет для реального production; отключи `MOCK_HH_ENABLED` или закрой порт
`8090` файрволом.

### NER-модель

Модель не коммитится в git: `models/` исключён из репозитория и Docker-контекста.
Deploy поддерживает два способа доставить её на VPS:

1. Положить tar-архив в `/opt/tracing-ats/models/ner` (внутри архива должны быть
   `model.onnx`, `tokenizer.json`, `config.json` и остальные файлы).
2. Задать GitHub Secret `NER_MODEL_URL` на URL этого tar-архива.

Deploy распакует архив в `models/ner/` и автоматически выставит:

```dotenv
ATS_PII_NER_PROVIDER=onnx
ATS_PII_NER_MODEL_PATH=/models/ner/model.onnx
ATS_PII_NER_TOKENIZER_PATH=/models/ner/tokenizer.json
ATS_PII_NER_CONFIG_PATH=/models/ner/config.json
ATS_PII_NER_FAIL_CLOSED=true
```

Если файлов модели нет, deploy оставляет `ATS_PII_NER_PROVIDER=fake` и пишет
`NER model not found; using fake provider`. Для локальной подготовки архива
используй `scripts/download-ner.sh`.

Модель большая (около 544 МБ), распаковка и загрузка ONNX требуют свободного
места и RAM на VPS. Если `ATS_PII_NER_FAIL_CLOSED=true` и модель повреждена,
`ats-service` не стартует — это защита от отправки немаскированного текста.

## 5. Ручной запуск

`Actions → Deploy → Run workflow` на `main`. Workflow соберёт образы из текущего
`main` и задеплоит их.

Если нужно поднять production-compose вручную, задай префикс, тег и env-файлы:

```bash
export IMAGE_PREFIX=ghcr.io/<owner>/tracing-ats
export IMAGE_TAG=<полный-sha>
docker compose --profile mock-hh \
  --env-file .env.example \
  --env-file .env.production \
  -f compose.yaml -f compose.prod.yaml up -d --no-build
```

Если при ручном запуске видны предупреждения
`The "IMAGE_PREFIX" variable is not set` и `The "IMAGE_TAG" variable is not set`,
значит не экспортированы переменные образов. В GitHub Actions они выставляются
автоматически; для ручного запуска экспортируй их, как показано выше.

## 6. TLS и домен

Workflow не настраивает TLS. Для production поставь перед frontend reverse proxy
(Caddy/Nginx) или используй внешний балансировщик. Сайт слушает `80`; TLS
терминируется на прокси.

## 7. Ограничения

- Образы собираются под `linux/amd64`. Для ARM VPS поменяй `platforms` в
  `deploy.yml` и проверь Dockerfile.
- Деплой делает `git checkout --detach --force <sha>`: локальные изменения
  отслеживаемых файлов на VPS будут перезаписаны. `.env.production` не
  отслеживается и пересоздаётся из GitHub.
- Если `GHCR_TOKEN` не задан и пакеты приватные, `docker compose pull` упадёт.
- `.env.example` не хранит реальные секреты: только локальные значения по
  умолчанию и пустые placeholders.
- NER-модель не входит в git. Без `models/ner` и без `NER_MODEL_URL` deploy
  работает с `fake`-провайдером, то есть имена не маскируются.

## 8. Если `migrations` завершился с ошибкой

Deploy теперь сам печатает `docker compose ps` и последние строки логов
`migrations`/`postgres`. Посмотреть вручную:

```bash
cd /opt/tracing-ats
docker compose \
  --env-file .env.example \
  --env-file .env.production \
  -f compose.yaml -f compose.prod.yaml logs --tail=100 migrations postgres
```

Самые частые причины:

- **`password authentication failed for user "ats"`** — том Postgres уже был
  создан с другим паролем. `POSTGRES_PASSWORD` не меняет пароль существующей
  базы. Укажи в GitHub Secret старый пароль или, если данные не нужны, удали
  только том Postgres и запусти деплой снова:

  ```bash
  cd /opt/tracing-ats
  docker compose \
    --env-file .env.example \
    --env-file .env.production \
    -f compose.yaml -f compose.prod.yaml down

  docker volume ls | grep ats-pgdata
  # проверь имя тома, затем:
  docker volume rm tracing-ats_ats-pgdata
  ```

  После удаления тома БД создастся заново с паролем из GitHub Secret. Все данные
  Postgres будут потеряны.

- **`Connection refused`** — Postgres не успел подняться или упал. Проверь
  `docker compose ps` и логи `postgres`.

- **Пароль с `$`, `#`, кавычками или переводом строки** может неверно разобраться
  в `.env`. Для `POSTGRES_PASSWORD` безопаснее hex-пароль:

  ```bash
  openssl rand -hex 24
  ```

## 9. Если frontend поднялся на 3000 вместо 80

В `docker compose ps` видно `0.0.0.0:3000->80/tcp`. Workflow по умолчанию ставит
`FRONTEND_PORT=80`, но GitHub Environment variable может переопределить его.

Проверь `Settings → Environments → production → Environment variables`:
- `FRONTEND_PORT` должен быть `80` или отсутствовать;
- если там `3000`, исправь на `80` и перезапусти `Deploy`.

На VPS также не должно быть старого `.env`, который задаёт `FRONTEND_PORT=3000`:
deploy использует `.env.example` + `.env.production`, но старый `.env` лучше
убрать, чтобы не путаться.
