# Документация Tracing ATS

Этот индекс описывает, что читать и какой документ считается актуальным. Если документ противоречит коду, контракту или `TASK.md`, действует следующий порядок истины:

1. `TASK.md` — продуктовые требования и критерии приёмки.
2. `contracts/` — OpenAPI и protobuf-контракты.
3. Код — фактическое поведение.
4. `README.md` и `docs/` — объяснение архитектуры, потоков и ограничений.
5. `docs/rfc/rfc-scoring-model.md` — формулы, переменные и открытые вопросы модели.

Документ, помеченный как исторический, не является текущей спецификацией. Не используй его для новых задач без сверки с кодом.

---

## Актуальные документы

| Документ | Назначение | Статус |
|---|---|---|
| [`../README.md`](../README.md) | Входная точка: запуск, архитектура, API, ограничения, use cases | Актуален |
| [`../AGENTS.md`](../AGENTS.md) | Правила работы AI-агента, границы сервисов, команды и DoD | Актуален |
| [`service-overview.md`](service-overview.md) | Текущая архитектура, компоненты, оба сквозных сценария | Актуален |
| [`resume-pipeline.md`](resume-pipeline.md) | Детальный пайплайн резюме: загрузка, PII, разбор, скоринг, отчёт | Актуален |
| [`data-model.md`](data-model.md) | Схемы `public` и `dictionary`, таблицы, инварианты, миграции | Актуален |
| [`known-issues.md`](known-issues.md) | Известные расхождения кода, контракта и документации | Актуален |
| [`demo-skill-dictionary.md`](demo-skill-dictionary.md) | Демо словаря и сквозного сценария | Актуален |
| [`deploy-vps.md`](deploy-vps.md) | Деплой на VPS через GitHub Actions и GHCR | Актуален |
| [`rfc/rfc-scoring-model.md`](rfc/rfc-scoring-model.md) | Формулы, открытые вопросы и статус реализации v0 | Draft; v0 реализован частично |
| [`rfc/rfc-feature-extraction.md`](rfc/rfc-feature-extraction.md) | Извлечение признаков резюме и проверка цитат | Draft; v0 реализован частично |
| [`rfc/rfc-feedback-generation.md`](rfc/rfc-feedback-generation.md) | Детерминированные объяснения и каталог шаблонов | Реализовано v1 |
| [`rfc/rfc-text-extraction.md`](rfc/rfc-text-extraction.md) | Извлечение текста из PDF, DOCX, TXT, MD | Реализовано |
| [`rfc/rfc-qualification-dictionary.md`](rfc/rfc-qualification-dictionary.md) | Квалификации и связи навыков | Реализовано v1 |
| [`technical_specification/tz-skill-dictionary-service.md`](technical_specification/tz-skill-dictionary-service.md) | Текущие решения по `dictionary-service` | Актуален |
| [`adr/`](adr/) | Принятые архитектурные решения | Исторические решения с примечаниями |

---

## Исторические документы

| Документ | Почему исторический |
|---|---|
| [`reports/`](reports/) | Отчёты о выполненных работах; не текущая спецификация |
| [`rfc/RFC-002_skill-kinds-vacancy-template.md`](rfc/RFC-002_skill-kinds-vacancy-template.md) | Дизайн ранней версии шаблона; реализовано в prompt v4/schema 3 |
| [`rfc/rfc-skill-dictionary.md`](rfc/rfc-skill-dictionary.md) | Обзор внешних источников; архитектурные варианты заменены `dictionary-service` |

Устаревшие планы и дубли (`hackathon-strategy.md`, `task-split-java-python.md`, `migration-info/`, `report/`) удалены; история остаётся в git.

---

## Как обновлять документацию

- Меняешь API или схему — обнови `contracts/`, `docs/data-model.md` и затронутые разделы README.
- Меняешь скоринг — сначала RFC/ADR, затем код и `docs/rfc/rfc-scoring-model.md`.
- Меняешь поведение сервиса — обнови `docs/service-overview.md` и компонентный README.
- Нашёл расхождение, но не исправляешь — добавь запись в [`known-issues.md`](known-issues.md).
- Не добавляй в активные документы команды, эндпоинты и поля, которых нет в коде или контракте.
