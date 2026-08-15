---
status: Active
slug: 013-song-status-filter
related:
  - ../domain/catalog.md
  - ../domain/publishing.md
  - ../features/143-song-free-access-window.md
  - ../../specs/013-song-status-filter/spec.md
---

# 013 — Show only songs with status ≥3 on prod (LiveDoc)

> Drill-down — [specs/013-song-status-filter/spec.md](../../specs/013-song-status-filter/spec.md).

## What it does

На проде (в Закромах, в поиске) — отображать только песни со статусом ≥3 (готовые).

**Семантика**: посетитель не видит «сырые» песни (только текст найден, без
проверки/маркеров). Аноним/непремиум — без доступа к неготовым.

**Исключение для редакторов** — см. `017-editor-status-bypass`.

## User Stories (краткий список)

- **US1** (P1): Аноним в Закромах → только песни status ≥ 3.

## Functional Requirements (указаль]

- **FR-001**: `ZakromaService.listForAuthor(authorId)` — `WHERE idStatus >= 3`.
- **FR-002**: `SearchService` — аналогично.

## Acceptance Criteria

- [ ] **AC1**: Аноним в Закромах → только готовые.
- [ ] **AC2**: Поиск — только готовые.
- [ ] **AC3**: Редактор — все (см. `017-editor-status-bypass`).

## Related LiveDocs

- Domain: [catalog.md](../domain/catalog.md) (Song.status), [publishing.md](../domain/publishing.md) (readiness)
- Feature: [143-song-free-access-window.md](../features/143-song-free-access-window.md) (дополнительная семантика free/premium), [017-editor-status-bypass.md](../features/017-editor-status-bypass.md) (исключение для редакторов)

## Code

- Backend: `karaoke-web/.../services/ZakromaService.kt` — filter by `status >= 3`
- Backend: `karaoke-web/.../services/SearchService.kt` — аналогично

## History

- Created: 2026-08-14
- Last updated: 2026-08-14