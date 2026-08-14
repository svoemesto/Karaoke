description: "Search through LiveDocs (livedocs/) by query, type, and path. Shows file:line:context matches with optional filter by domain/feature/architecture/adr/runbook."
---

# /livedocs-find — поиск по LiveDocs

> Это **slash-command для AI-агентов** (OpenCode, Claude Code, Cursor, и др.).
> Файл лежит в LiveDocs (`livedocs/commands/livedocs-find.md`), а в
> `.opencode/commands/livedocs-find.md` — копия для runtime-агента
> (поскольку `.opencode/` в `.gitignore`).

## Использование

```
/livedocs-find "query" [--type TYPE] [--path SUBPATH]
```

- **query** — строка поиска (multiple words или regex).
- `--type` — фильтр: `feature | domain | architecture | adr | runbook | template | all`.
- `--path` — RELATIVE путь от корня репозитория (например, `livedocs/architecture/decisions`).

## Examples

```
/livedocs-find "KaraokeConnection" --type architecture
/livedocs-find "render MP4"
/livedocs-find "self-assign" --type feature
/livedocs-find "redirectErrorStream" --path livedocs/architecture
```

## Steps

1. **Парсинг аргументов**: `bash tools/search-livedocs.sh "<query>" [<flags>]`.
2. **Запуск + парсинг результата**: формат `file:line: context`.
3. **Прочитать referenced files целиком** (не только matched line):
   - Frontmatter (`status:`, `slug:`, `related:`).
   - Секцию «Related LiveDocs» внизу файла.
   - Для ADR файлов — также `livedocs-en/decisions/<NNN>.md` (English mirror).
4. **Follow-up**:
   - Если ссылка на фичу → прочитать `specs/<NNN>-<slug>/spec.md` для полной спеки.
   - Если расхождение код↔LiveDocs — упомянуть пользователю.
   - Если 0 матчей → попробовать альтернативные формулировки или `livedocs/CHANGELOG.md`.

## Дополнительно

- `bash tools/search-livedocs.sh --help` — все флаги.
- LiveDocs структура — `livedocs/README.md`, `livedocs/INDEX.md`.
- Список скриптов — `tools/README.md`.

## Related LiveDocs

- [livedocs/README.md](../README.md) — root manifesto (прочитать первым).
- [livedocs/INDEX.md](../INDEX.md) — карта слоёв + decision tree.
- [livedocs/CHANGELOG.md](../CHANGELOG.md) — история LiveDocs (Pass 62+).
- [livedocs-en/README.md](../../livedocs-en/README.md) — English mirror.

---

**Implementation note**: AI-agent runtime (OpenCode) читает `.opencode/commands/livedocs-find.md`
при старте сессии. Этот файл (`livedocs/commands/livedocs-find.md`) — канонический, через
CI валидируется. После изменений скопируй обновлённую версию в `.opencode/commands/`
(или используй символическую ссылку в development-среде).