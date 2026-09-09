# Architecture Decision Records (ADR)

> **Статус**: в разработке. Каркас развёрнут в
> `322-knowledge-scaffold`. Наполнение ведётся в следующих
> спецификациях.

ADR фиксируют **почему** мы сделали архитектурный выбор, а не **что**
(«что» — в `knowledge/domains/`, «как» — в `specs/NNN-*/`).

Append-only: нельзя редактировать или удалять ADR после принятия.
Только `superseded_by` / `supersedes`.

## Нумерация (Karaoke-override)

Используется существующая Karaoke-схема:

- `0001-…` … `0008-…` — глобальные решения проекта.
- `local-0001-…` … `local-NNNN-…` — локальные решения внутри отдельного
  контейнера (backend/frontend/MLT). Префикс `local-` сохраняется при
  переносе из `livedocs/architecture/decisions/`.

Шаблон `ADR-XXXX` из generic-bootstrap **не применяется** в Karaoke —
см. `knowledge/README.md` § Karaoke-overrides, п.3.

## Шаблон

См. [`knowledge/templates/adr.md`](../templates/adr.md).

## Миграция из `livedocs/architecture/decisions/`

13 файлов перенесены в спецификации 331 с сохранением ID и slug-имени:

```
0001-raw-jdbc.md
0002-mlt-instead-of-ffmpeg.md
0003-livedocs-markdown-yaml-mermaid.md
0004-karaoke-app-admin-only.md
0005-self-hosted-ml.md
0006-processbuilder-redirect-errorstream.md
0008-tracker-openproject-migration.md
local-0001-karaoke-properties-defaults.md
local-0002-save-exception-handling.md
local-0003-shared-minio-image-cache.md
local-0004-lazy-eager-load-webvue3-pagination.md
local-0005-structured-logging-karaoke-app.md
local-0006-logging-and-error-handling-karaoke-web.md
local-0007-zakroma-album-id-in-stream-dto.md
```

Дополнительный ADR в `knowledge/adr/`:
- `0007-adopt-knowledge-as-ssot.md` — ADR о принятии knowledge/ как
  SSoT (создан в спеке 322). Получил номер 0007, чтобы не конфликтовать
  с существующим `0001-raw-jdbc.md`.

Текущий формат — `* Status:` / `* Date:` / etc. — сохраняется как есть;
полный переход на YAML frontmatter (как в шаблоне) — опционален и
делается отдельным PR.
