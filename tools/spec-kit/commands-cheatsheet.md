# Spec Kit — шпаргалка по командам для DSH-сессий

> **Где**: `tools/spec-kit/commands-cheatsheet.md`
> **Для кого**: агенты, работающие в DeepSeek Harness (DSH), где нет
> автоподсказок slash-команд. Файл-источник: `.dsh/commands/speckit.*.md`
> (генерируется `bash tools/install-dsh-integration.sh`).

## Как вызвать команду в DSH-сессии

В чате напишите, например:

> `/speckit.specify добавить страницу "О проекте"`

Агент прочитает `.dsh/commands/speckit.specify.md` через `read` и начнёт
выполнять инструкции из этого файла. Это работает точно так же, как
нативная slash-команда в opencode.

## Команды (10 штук)

### Основной цикл (SDD)

| Команда | Что делает | Когда вызывать |
|---|---|---|
| `/speckit.specify` | Создаёт/обновляет `spec.md` из описания на естественном языке | Самая первая команда. Хук `before_specify` создаст feature-ветку `NNN-slug` |
| `/speckit.clarify` | Задаёт до 5 уточняющих вопросов по спеке | После specify, если есть неоднозначности |
| `/speckit.plan` | Генерирует `plan.md` из спеки | После specify/clarify |
| `/speckit.tasks` | Разбивает план на `tasks.md` | После plan |
| `/speckit.analyze` | Кросс-артефактный анализ spec+plan+tasks на согласованность | После tasks, перед implement |
| `/speckit.implement` | Выполняет задачи из `tasks.md` | После analyze |

### Дополнительные

| Команда | Что делает | Когда вызывать |
|---|---|---|
| `/speckit.checklist` | Генерирует кастомный чек-лист для фичи | Параллельно с plan/tasks |
| `/speckit.constitution` | Обновляет `.specify/memory/constitution.md` | При изменении NON-NEGOTIABLE принципов проекта |
| `/speckit.converge` | Сверяет текущий код с spec/plan/tasks и дописывает пропущенное в tasks | После implement, чтобы закрыть хвосты |
| `/speckit.taskstoissues` | Конвертирует tasks в GitHub Issues | После tasks, вместо implement |

## Hand-offs (последовательность)

```
specify ──► clarify ──► plan ──► tasks ──► analyze ──► implement
   │                       │         │
   │                       │         └─► taskstoissues ──► GitHub Issues
   │                       └─► checklist (параллельно)
   └─► plan (можно пропустить clarify)
```

В каждой команде есть `handoffs` в frontmatter — следующая логическая
команда. Агент может сам предложить переход, если вы попросите «иди по
цепочке».

## Хуки (auto, до/после команд)

Из `.specify/extensions.yml`:

| Хук | Команда | Эффект |
|---|---|---|
| `before_specify` | `tools/specify-bootstrap.sh` | Создаёт feature-ветку `NNN-slug` от master (NON-NEGOTIABLE — нельзя работать в master) |

Остальные хуки (`after_*`, `before_clarify`, `after_clarify`) сейчас пусты.

## Замечание про Karaoke-специфику

Перед implement агент ОБЯЗАН пройти 6 шагов из AGENTS.md (NON-NEGOTIABLE):

1. `./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel`
2. `./gradlew :karaoke-web:ktlintCheck` + линтеры фронта
3. `./gradlew :karaoke-web:bootJar --parallel`
4. `cd webvue3 && npm run build && npm run format:check` + то же для `karaoke-public`
5. Docker-образы: `cd deploy && bash do.sh build_webvue3` (и `build_public` если менялся)
6. Только после 5/5 OK — сообщать «готово к деплою»

## Запросить шпаргалку в чате

Скажите агенту в DSH-сессии:

> «Покажи команды spec-kit»

И он выведет эту таблицу в чат (без чтения файла — для скорости).

## Где лежат исходники

- **Шаблоны** (канонические): `.specify/templates/*.md`
- **Opencode-команды** (генерируются): `.opencode/commands/speckit.*.md` (в `.gitignore`)
- **DSH-команды** (генерируются): `.dsh/commands/speckit.*.md` (в `.gitignore`)
- **Манифесты** (в репо): `.specify/integrations/{opencode,dsh,speckit}.manifest.json`
- **Установщик DSH**: `tools/install-dsh-integration.sh`
- **Этот файл**: `tools/spec-kit/commands-cheatsheet.md`
