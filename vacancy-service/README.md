# vacancy-service

Java 21, Spring Boot 4, Gradle. Принимает и обрабатывает выгруженные вакансии.

## Сборка и запуск

```bash
./gradlew test
./gradlew bootRun
```

Порт по умолчанию — `8080`. PostgreSQL передаётся через `SPRING_DATASOURCE_URL`,
`SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`.

## Docker

```bash
docker build -t ats-vacancy-service .
```

Сервис собирается независимо от `resume-service`.
