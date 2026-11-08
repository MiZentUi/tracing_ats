# Отчёт 002 — Скиллы и возврат Gradle-зависимостей

### Что сделано
- `.opencode/skills/frontend/SKILL.md` — ссылка на локальный `temp/frontend-example/fractal-flame` заменена на GitHub `https://github.com/MiZentUi/fractal-flame` (ветка `feature/add-frontend`).
- `.opencode/skills/work-report/SKILL.md` — лимит объёма отчёта изменён с 15–20 до 50 строк.
- `resume-service/build.gradle` — из `main:backend/build.gradle` возвращены `springdoc-openapi-starter-webmvc-ui:3.2.0`, `spring-ai-pdf-document-reader` + `spring-ai-bom:2.0.1`, `jackson-databind-nullable:0.2.10`, `mapstruct:1.6.3` + processor, `jjwt-api/impl/jackson:0.13.0`, `spring-boot-docker-compose`, `spring-ai-spring-boot-docker-compose`, тестовые стартеры (`data-jpa-test`, `security-test`, `webmvc-test`) и `spring-ai-spring-boot-testcontainers`.
- `vacancy-service/build.gradle` — тот же набор, вместо PDF-ридера возвращён `spring-grpc-client-spring-boot-starter` + `spring-grpc-dependencies:1.0.3`.

### Проверка
- `resume-service`: `./gradlew compileJava --no-daemon` → `BUILD SUCCESSFUL in 26s`.
- `vacancy-service`: `./gradlew compileJava --no-daemon` → `BUILD SUCCESSFUL in 24s`.
- `resume-service`: `./gradlew testClasses --no-daemon` → `BUILD SUCCESSFUL in 18s` (тестовые зависимости резолвятся).
- `vacancy-service`: `./gradlew testClasses --no-daemon` → `BUILD SUCCESSFUL in 18s`.
- Не запускалось: `./gradlew test` (нужен Docker/Testcontainers), frontend-сборка (каталога `frontend/` нет).

### Не сделано / допущения
- Распределение зависимостей по сервисам выбрано по ответственности: PDF — в `resume-service`, gRPC-клиент — в `vacancy-service`; остальные общие. Это допущение, в `main` был один модуль `backend/`.
- Плагины `com.google.protobuf` и `org.openapi.generator`, блоки `protobuf`/`openApiGenerate` и `sourceSets` не возвращены: `contracts/ml-internal.proto` отсутствует, генерация сломала бы сборку.
- `spring-boot-starter-validation` оставлен (был в текущих файлах, в `main` его не было).
