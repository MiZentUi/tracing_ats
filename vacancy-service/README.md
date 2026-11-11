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

## Логирование

Каждый HTTP-запрос получает идентификатор (входящий `X-Request-Id` или сгенерированные 8
символов), он попадает в MDC и печатается в каждой строке как `[req:xxxxxxxx]`. По нему видно
всю цепочку одного вызова: контроллер → разбор → LLM-клиент → сохранение.

Что искать при неудачном разборе (в порядке появления):

| Событие | Уровень | Что означает |
|---|---|---|
| `Create vacancy: specialization=..., textChars=...` | INFO | Параметры запроса до валидации |
| `Vacancy source file extracted: ...` | INFO | Экстракт файла: размер, страницы, предупреждения |
| `Template extraction started` | INFO | Начало разбора: длина текста, специализация, уровень, модель |
| `LLM chain started` / `LLM attempt started` | INFO | Какая модель и какая попытка пошла в сеть |
| `LLM attempt succeeded` | INFO | Успех: `elapsedMs`, токены, `finishReason` |
| `LLM rate limited` / `LLM server error` / `LLM transport error` | WARN | Ошибка провайдера, повтор или переход к следующей модели |
| `LLM timed out mid-response` | WARN | Сработал `read-timeout`, повтор той же модели не делается |
| `LLM model exhausted` | WARN | У модели кончились попытки, берётся следующая |
| `LLM chain exhausted` | ERROR | Все модели исчерпаны или истёк `total-timeout` |
| `LLM is unavailable, vacancy will be created with an empty template` | WARN | Деградация по RFC-002: вакансия создаётся без требований |
| `LLM template response is invalid, retrying once` | WARN | Невалидный JSON/схема: причина и первые 300 символов ответа |
| `Vacancy created` | INFO | Итог: id, версия, статус, число требований |
| `HTTP request completed` | INFO | Метод, путь, статус, длительность запроса |

Полный текст вакансии, промпта и ответа LLM в логи не пишется: только длины, причины и короткие
фрагменты ответа при ошибке разбора. PII в логи не попадает.

Для подробностей по CRUD-операциям включи DEBUG для пакета:

```bash
docker run -e LOGGING_LEVEL_ATS_VACANCY=DEBUG ...
```

