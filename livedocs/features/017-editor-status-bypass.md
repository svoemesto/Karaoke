---
status: Active
slug: 017-editor-status-bypass
related:
  - ../domain/catalog.md
  - ../domain/editorial.md
  - ../features/182-editor-self-assign-tasks.md
  - ../../specs/017-editor-status-bypass/spec.md
---

# 017 — Редактор видит все песни независимо от статуса (LiveDoc)

> Drill-down — [specs/017-editor-status-bypass/spec.md](../../specs/017-editor-status-bypass/spec.md).

## Что делает

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

## Связанные LiveDocs

- Domain: [catalog.md](../catalog.md) (Song.status), [editorial.md](../editorial.md) (editor role)
- Feature: [182-editor-self-assign-tasks.md](../features/182-editor-self-assign-tasks.md)

## Код

- Backend: `karaoke-web/.../services/ZakromaService.kt` — `listForAuthor()`
- Backend: `karaoke-web/.../services/SearchService.kt` — аналогично
- Frontend: пользователь определяется через `sessionStorage['user']` (editor role)

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14