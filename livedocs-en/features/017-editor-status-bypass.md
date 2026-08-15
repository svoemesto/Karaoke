---
status: Active
slug: 017-editor-status-bypass
related:
  - ../domain/catalog.md
  - ../domain/editorial.md
  - ../features/182-editor-self-assign-tasks.md
  - ../../specs/017-editor-status-bypass/spec.md
---

# 017 — Editor sees all songs regardless of status (LiveDoc)

> Drill-down — [specs/017-editor-status-bypass/spec.md](../../specs/017-editor-status-bypass/spec.md).

## What it does

На `karaoke-public` в «Закромах» и в поиске отображаются только песни со
статусом ≥3 — корректное поведение для анонима/непремиум. **Исключение**:
если текущий пользователь — **редактор** (`isEditor` в его сессии), правило
«статус ≥3» **не действует** — он видит все песни (любого статуса).

**Эффект**: редактор видит и обрабатывает песни, которые ещё не вышли из
ранних статусов (например, статус 1 = «текст найден, надо проверить»).

## User Stories (краткий список)

- **US1** (P1): Редактор в Закромах видит ВСЕ песни автора (любой статус).

## Functional Requirements (указатель)

- **FR-001**: `ZakromaService.listForAuthor(authorId, currentUser)` — если `currentUser.isEditor` → НЕ фильтровать по `status >= 3`.
- **FR-002**: Аналогично в Search для редакторов.

## Acceptance Criteria

- [ ] **AC1**: Аноним в Закромах → песни только ≥3.
- [ ] **AC2**: Редактор в Закромах → все песни (любой статус).
- [ ] **AC3**: Премиум-пользователь (не редактор) → как аноним.

## Related LiveDocs

- Domain: [catalog.md](../domain/catalog.md) (Song.status), [editorial.md](../domain/editorial.md) (editor role)
- Feature: [182-editor-self-assign-tasks.md](../features/182-editor-self-assign-tasks.md)

## Code

- Backend: `karaoke-web/.../services/ZakromaService.kt` — `listForAuthor()`
- Backend: `karaoke-web/.../services/SearchService.kt` — аналогично
- Frontend: пользователь определяется через `sessionStorage['user']` (editor role)

## History

- Created: 2026-08-14
- Last updated: 2026-08-14