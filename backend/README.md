# Backend

Java 21, Spring Boot 4, Maven multi-module.

```text
backend/
├── pom.xml                    # родительский POM
├── migrations/                # общие Liquibase-миграции (SQL) + Dockerfile
│   ├── Dockerfile
│   ├── db.changelog-master.xml
│   └── changes/
├── resume-service/            # приём и обработка выгруженных резюме
└── vacancy-service/           # приём и обработка выгруженных вакансий
```

## Сервисы

| Модуль | Ответственность | Порт |
|---|---|---|
| `resume-service` | Загрузка резюме (PDF, DOCX, TXT), извлечение текста, маскировка PII, разбор резюме | 8081 |
| `vacancy-service` | Загрузка вакансии, извлечение текста, шаблон требований, веса и их история | 8080 |

## Сборка и запуск

```bash
cd backend
mvn clean verify
mvn -pl resume-service spring-boot:run
mvn -pl vacancy-service spring-boot:run
```

## Миграции

Схему накатывает отдельная джоба `migrations` из `infra/docker-compose.yml`. Сервисы Liquibase
не запускают, поэтому приложение никогда не меняет схему само.

```bash
# поднять PostgreSQL и применить все миграции
docker compose -f infra/docker-compose.yml up migrations

# только повторить джобу
docker compose -f infra/docker-compose.yml run --rm migrations
```

Dockerfile джобы собирается из этого каталога: `backend/migrations` копируется целиком в
`/liquibase/changelog` образа `liquibase/liquibase:4.29`, а `LIQUIBASE_COMMAND_CHANGELOG_FILE`
указывает на `db.changelog-master.xml`.
