---
name: Conventional Branch
description: Использовать при создании, переименовании и ревью git-веток. Проверяет Conventional Branch 1.1.0 — тип, описание, допустимые символы, AI-префиксы и формат release-веток.
---

# Conventional Branch 1.1.0

Применяй этот скилл каждый раз, когда предлагаешь имя ветки, переименовываешь
существующую или ревьюишь ветку в Tracing ATS.

Формат:

```text
<type>/<description>
```

Trunk-ветки (`main`, `master`, `develop`) идут без префикса.

## Типы веток

| Префикс | Когда использовать |
|---|---|
| `feature/` или `feat/` | Новая функциональность |
| `bugfix/` или `fix/` | Исправление ошибки, не срочное |
| `hotfix/` | Срочное исправление, обычно в прод |
| `release/` | Подготовка релиза, в описании версия |
| `chore/` | Зависимости, документация, CI, рутина |
| `ai/` | Ветка любого AI-агента |
| `claude/` | Claude Code |
| `codex/` | OpenAI Codex |
| `copilot/` | GitHub Copilot |
| `cursor/` | Cursor |

Других типов не придумывай. Если задача не подходит ни под один тип — это сигнал
разбить её или уточнить намерение у человека.

## Правила описания

- Только нижний регистр: `a-z`, цифры `0-9`, дефисы и точки.
- Слова разделяются одним дефисом: `feature/add-login-page`.
- Точки допустимы для версий и номеров: `release/v1.2.0`, `chore/upgrade-node-v20.11`.
- Запрещены пробелы, подчёркивания, кириллица и спецсимволы.
- Запрещены два дефиса или точки подряд, а также дефис/точка в начале и конце описания.
- Описание короткое и конкретное: 2–5 слов.
- Если есть номер задачи, добавь его: `feature/issue-123-add-login`.
- Одна ветка — одна задача. Не смешивай рефакторинг, миграцию и фичу в одном имени.

## Формальная грамматика

```text
branch-name     = trunk-branch / prefixed-branch
trunk-branch    = "main" / "master" / "develop"
prefixed-branch = type "/" description
type            = "feature" / "feat" / "bugfix" / "fix"
                / "hotfix" / "release" / "chore"
                / "ai" / "copilot" / "cursor"
                / "claude" / "codex"
description     = desc-segment *("-" desc-segment)
desc-segment    = 1*(ALPHA / DIGIT) *("." 1*(ALPHA / DIGIT))
ALPHA           = %x61-7A   ; lowercase a-z
DIGIT           = %x30-39   ; 0-9
```

Практическая проверка (официальный источник — `spec.json` Conventional Branch):

```regex
^(main|master|develop)$|^(feature|feat|bugfix|fix|hotfix|release|chore|ai|claude|codex|copilot|cursor)/[a-z0-9]+(?:[.-][a-z0-9]+)*$
```

## Как выбирать тип в этом проекте

| Задача | Тип |
|---|---|
| Новый endpoint, сервис, поле контракта | `feature/` |
| Исправление скоринга, маскировки, парсинга | `fix/` или `bugfix/` |
| Срочная правка на проде | `hotfix/` |
| Только миграции как часть фичи | `feature/` |
| Только документация, README, скиллы | `chore/` |
| Обновление зависимостей, Docker, CI | `chore/` |
| Подготовка версии | `release/vX.Y.Z` |
| Ветка, созданная AI-агентом | `ai/` или префикс конкретного агента |

## Примеры

| Ветка | Валидна | Комментарий |
|---|---|---|
| `main` | да | trunk-ветка |
| `feature/add-login-page` | да | новая функциональность |
| `feat/add-login-page` | да | короткий алиас |
| `fix/header-bug` | да | исправление |
| `bugfix/header-bug` | да | полный алиас |
| `hotfix/security-patch` | да | срочно |
| `release/v1.2.0` | да | версия с точками |
| `chore/update-dependencies` | да | рутина |
| `feature/issue-123-new-login` | да | с номером задачи |
| `ai/refactor-auth-flow` | да | AI-агент |
| `Feature/Add-Login` | нет | верхний регистр |
| `feature/new--login` | нет | два дефиса подряд |
| `feature/-new-login` | нет | дефис в начале |
| `feature/new-login-` | нет | дефис в конце |
| `release/v1.-2.0` | нет | дефис рядом с точкой |
| `fix/header bug` | нет | пробел |
| `fix/header_bug` | нет | подчёркивание |
| `unknown/some-task` | нет | неизвестный тип |

## Переименование ветки

```bash
git branch -m <new-name>
git push -u origin <new-name>
```

Старую remote-ветку удаляй только после того, как новая запушена и PR переключён:

```bash
git push origin --delete <old-name>
git fetch --prune origin
```

Если по ветке открыт PR, безопаснее переименовать её через GitHub UI — PR и ревью
переедут автоматически.

## Чек-лист

- [ ] Тип из таблицы, в нижнем регистре.
- [ ] Описание на английском, короткое, через дефисы.
- [ ] Нет пробелов, подчёркиваний, кириллицы и верхнего регистра.
- [ ] Нет двух дефисов/точек подряд и разделителей по краям.
- [ ] Номер задачи добавлен, если он есть.
- [ ] Ветка содержит одну задачу.
- [ ] Имя не противоречит Conventional Commits в сообщениях коммитов.
