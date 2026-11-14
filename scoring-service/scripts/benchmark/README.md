# Пакетный прогон scoring-service

Набор для ручной проверки матчинга на локальных `txt`-файлах.

## Запуск

```bash
cd scoring-service
uv run python scripts/benchmark.py \
  --resumes-dir scripts/benchmark/examples/resumes \
  --vacancies-dir scripts/benchmark/examples/vacancies
```

По умолчанию используются каталоги:

- `scripts/benchmark/resumes/` — резюме;
- `scripts/benchmark/vacancies/` — вакансии;
- `scripts/benchmark/cache/` — кэш разбора;
- `scripts/benchmark/results/` — результаты прогонов.

gRPC-target берётся из `SCORING_GRPC_TARGET` или `--target`, по умолчанию
`127.0.0.1:8082`.

## Формат вакансии

Вакансия — структурированный `txt`, а не сырое описание: scoring-service не
вызывает LLM для вакансий. Один параметр на строку, комментарии начинаются с
`#`.

```text
tenure: 3
position: middle
category: backend

skill: required | Python | 3
skill: nice | Kafka | 2
skill: nice | Docker
qualification: required | III группа по электробезопасности
education: nice | bachelor | Прикладная математика
experience: required | backend | 3
text: nice | soft | Умение работать в команде
```

Поля:

| Ключ | Формат | Значение |
|---|---|---|
| `tenure` | целое | требуемый общий стаж в годах |
| `position` | `intern`/`junior`/`middle`/`senior` | уровень позиции |
| `category` | текст | категория работ |
| `skill` | `criticality \| name [\| tenure]` | навык; стаж по умолчанию `0` |
| `qualification` | `criticality \| label` | квалификация |
| `education` | `criticality \| level \| specialty` | образование |
| `experience` | `criticality \| category [\| tenure]` | требование к опыту |
| `text` | `criticality \| soft\|other \| text` | текстовое требование |

`criticality`: `critical`, `required`, `nice`, `optional`.

Уровни образования: `secondary`, `secondary_specialized`, `bachelor`,
`master`, `doctor`, `courses`.

Резюме — обычный текст. Оно один раз отправляется в `ExtractResume`, результат
кэшируется.

## Кэш и повторные запуски

- Кэш резюме: `cache/resumes/<stem>.json`.
- Кэш вакансий: `cache/vacancies/<stem>.json`.
- В кэше хранится SHA-256 исходного файла. Если файл изменился, он парсится
  заново.
- `--force-parse` принудительно пересобирает кэш.
- Результаты каждого прогона лежат в отдельном каталоге
  `results/<timestamp>/`:
  - `manifest.json` — входные файлы, кэш и ошибки;
  - `summary.json` — матрица `резюме × вакансия`;
  - `resumes/<stem>.json` — полный `EvaluateResumeMatches` по всем вакансиям.

## Ограничения

- Каталоги не обходятся рекурсивно: берутся только `*.txt` верхнего уровня.
- Для вакансий используется локальный структурированный формат. Сырые тексты
  вакансий парсит Java-сервис, а не scoring-service.
- Квалификационный каталог в запрос не передаётся: квалификации сравниваются
  по точному label или правилам scoring-service по умолчанию.
