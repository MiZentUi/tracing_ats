# Отчёт 001 — Исправления по ревью Java-сервисов

### Что сделано
- `lombok.config` — перенесён из корня в `resume-service/lombok.config`, копия добавлена в `vacancy-service/lombok.config`; корневой файл удалён.
- `contracts/.openapi-generator-ignore` — перенесён в `resume-service/.openapi-generator-ignore`, копия добавлена в `vacancy-service/.openapi-generator-ignore`; из `contracts/` удалён.
- `docker-compose.yml` — переименован в `compose.yaml`.
- `README.md`, `docs/vacancy-data-model.md`, `.opencode/skills/code-review/SKILL.md` — ссылки на compose-файл обновлены на `compose.yaml`.
- `resume-service/Dockerfile`, `vacancy-service/Dockerfile` — сборка разбита на слои, добавлены BuildKit cache mount (`resume-service-gradle`, `vacancy-service-gradle`), копирование `lombok.config` до сборки, рантайм `eclipse-temurin:21-jre-alpine` с `curl`; порты 8081 и 8080 сохранены.
- `.opencode/skills/work-report/SKILL.md` — зафиксировано сохранение отчётов в `docs/reports/NNN-<тема>.md` с поиском последнего номера и автоинкрементом.

### Проверка
- `resume-service/src/test/java/ats/resume/TestcontainersConfiguration.java` и `vacancy-service/src/test/java/ats/vacancy/TestcontainersConfiguration.java` — уже используют `postgres:18`, совпадает с `compose.yaml`; правка не потребовалась.
- `docker compose config` → exit 0.
- `docker build --check` для обоих Dockerfile → `Check complete, no warnings found`.
- `./gradlew compileJava --no-daemon` в `resume-service` → `BUILD SUCCESSFUL` (6m 44s).
- `./gradlew compileJava --no-daemon` в `vacancy-service` → `BUILD SUCCESSFUL` (5s).
- Не запускалось: полный `./gradlew test`, реальный `docker build` обоих образов.
