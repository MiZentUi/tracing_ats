# Отчёт 002 — Lombok в text-extraction и чистка Maven-упоминаний

### Что сделано
- `docs/report/2026-11-05-backend-text-extraction.md` — строка про «Maven multi-module» заменена на Gradle composite build; Maven убран из ограничения и исторической справки.
- `.opencode/skills/conventional-commits/SKILL.md` — тип `build` описан как Gradle/Docker, пример breaking change больше не ссылается на Maven POM.
- `text-extraction/build.gradle` — добавлены `compileOnly` и `annotationProcessor` для Lombok.
- `text-extraction/src/main/java/ats/extraction/infrastructure/ExtractionProperties.java` — `@Getter`/`@Setter` на класс и вложенный `Docx`; ручные getters/setters удалены.
- `text-extraction/src/main/java/ats/extraction/infrastructure/DefaultTextExtractionService.java` — `@Slf4j` вместо `LoggerFactory`.
- `text-extraction/src/main/java/ats/extraction/infrastructure/PdfTextExtractor.java` — `@RequiredArgsConstructor` вместо ручного конструктора.
- `vacancy-service/src/main/java/ats/vacancy/dev/DevExtractionController.java` — `@RequiredArgsConstructor` вместо ручного конструктора.
- `vacancy-service/settings.gradle` — уже содержит `includeBuild('../text-extraction')`, правка не потребовалась.
- `text-extraction` и `vacancy-service` — применён `spotlessApply`: переформатировано 17 и 3 файла соответственно, изменения только форматирование.

### Проверка
- `./gradlew -p ../text-extraction test --no-daemon` → `BUILD SUCCESSFUL`, 45 тестов, 0 failures, 0 errors.
- `cd vacancy-service && ./gradlew test --no-daemon` → `BUILD SUCCESSFUL`, 3 теста, 0 failures, 0 errors.
- `spotlessCheck` в `text-extraction`, `vacancy-service` и `resume-service` → exit 0.
- Не запускалось: реальный `docker build`.

### Не сделано / проблемы
- `DocxExtractor` не переведён на Lombok: его конструктор выполняет side effects (`ZipSecureFile.setMinInflateRatio` / `setMaxEntrySize`), автогенерация конструктора здесь не подходит.
- `ErrorCode` и `ExtractionException` оставлены без Lombok: публичные методы `httpStatus()` и `errorCode()` имеют нестандартные имена, `@Getter` сгенерировал бы `get...` и сломал API.

### Требует решения пользователя
- Добавлять `includeBuild('../text-extraction')` в `resume-service` сейчас или вместе с подключением извлечения к `POST /resumes`?
