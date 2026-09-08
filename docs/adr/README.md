# Architecture Decision Records (ADR)

> **Статус**: в разработке. Каркас развёрнут в
> `320-living-docs-v2-bootstrap`. Наполнение ведётся в следующих
> спецификациях.

ADR фиксируют **почему** мы сделали архитектурный выбор, а не **что**
(«что» — в `docs/domains/`, «как» — в `specs/NNN-*/`).

Append-only: нельзя редактировать или удалять ADR после принятия.
Только `superseded_by` / `supersedes`.

## Нумерация (Karaoke-override)

Используется существующая Karaoke-схема:

- `0001-…` … `0008-…` — глобальные решения проекта.
- `local-0001-…` … `local-NNNN-…` — локальные решения внутри отдельного
  контейнера (backend/frontend/MLT). Префикс `local-` сохраняется при
  переносе из `livedocs/architecture/decisions/`.

Шаблон `ADR-XXXX` из generic-bootstrap **не применяется** в Karaoke —
см. `docs/README.md` § Karaoke-overrides, п.3.

## Шаблон

См. [`docs/templates/adr.md`](../templates/adr.md).

## Миграция из `livedocs/architecture/decisions/`

14 файлов будут перенесены с сохранением ID и slug-имени:

```
0001-raw-jdbc.md
0002-mlt-instead-of-ffmpeg.md
0003-livedocs-markdown-yaml-mermaid.md
0004-karaoke-app-admin-only.md
0005-self-hosted-ml.md
0006-processbuilder-redirecterrorstream.md
0008-tracker-openproject-migration.md
local-0001-karaoke-properties-defaults.md
… (всего 14)
```

Текущий формат — `* Status:` / `* Date:` / etc. — сохраняется как есть;
полный переход на YAML frontmatter (как в шаблоне) — опционален и
делается отдельным PR.
