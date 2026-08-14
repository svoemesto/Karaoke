# commands/ — AI-agent slash-commands

> Slash-команды для AI-агентов (OpenCode, Claude Code, Cursor, и др.).
> Аналог `runbooks/` (для людей), но формат — для AI-агента:
> YAML-like frontmatter (`description:`) + markdown body с пошаговыми
> инструкциями.

## Список команд

| Команда | Назначение |
|---------|-----------|
| [livedocs-find.md](livedocs-find.md) | Поиск query по LiveDocs (через `tools/search-livedocs.sh`) |

## Конвенции

- **Имя файла**: kebab-case + `.md` (например, `livedocs-find.md`).
- **Frontmatter**: первая строка — `description: "..."` (без префикса `---`,
  просто `description:`).
- **Body**: markdown с пошаговыми инструкциями для AI-агента.
- **Слой**: под-каталог `commands/` в LiveDocs (не в `.opencode/commands/`,
  поскольку `.opencode/` в `.gitignore` как персональная конфигурация).

## Runtime-конфигурация

Каждый AI-агент имеет своё место для slash-commands:

| Agent | Slash-commands dir | Git-tracked? |
|-------|---------------------|--------------|
| **OpenCode** | `.opencode/commands/` | ❌ (в `.gitignore`) |
| **Claude Code** | `.claude/commands/` (если будет) | ❌ |
| **Cursor** | `.cursor/commands/` (если будет) | ❌ |

Канонический файл живёт в `livedocs/commands/<name>.md`. После изменений
скопировать в `<agent>/commands/<name>.md` (например, `cp
livedocs/commands/livedocs-find.md .opencode/commands/`).

Альтернатива: символическая ссылка (в development-среде).

## Когда добавлять новую команду

✅ **Создавайте**, если команда:
- Повторяется AI-агентом ≥ 3 раз в разных сессиях.
- Документирована в LiveDocs, но нужен **structured workflow** для агента.
- Запускает реальные проверки (CI, git, bash).

❌ **Не** создавайте, если:
- Задача разовая (см. `runbooks/<how-to>.md`).
- Полностью синхронна с shell-скриптом (см. `tools/`).

## Связанные LiveDocs

- [livedocs/runbooks/](../runbooks/) — аналог для людей.
- [livedocs/INDEX.md](../INDEX.md) — карта всех слоёв.
- [tools/README.md](../../tools/README.md) — операционные скрипты.