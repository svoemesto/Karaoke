---
status: Active
slug: 019-fix-setcontent-resets-position
related:
  - ../domain/catalog.md
  - ../domain/editorial.md
  - ../features/017-editor-status-bypass.md
  - ../features/163-fix-song-editor-regressions.md
  - ../../specs/019-fix-setcontent-resets-position/spec.md
---

# 019 — setContent/setOptions reset marker positions (LiveDoc)

> Drill-down — [specs/019-fix-setcontent-resets-position/spec.md](../../specs/019-fix-setcontent-resets-position/spec.md).

## What it does

После фиксов `017-editor-status-bypass` и `018-fix-spec-tag-markers-at-zero` на
**первом открытии** маркеров в SubsEdit наблюдалась остаточная регрессия —
позиции маркеров «прыгали».

**Корневая причина**: `setContent()/setOptions()` в `updateMarkersBySyllables`
вызывают тики re-layout, которые сбрасывают позиции маркеров в DOM
(canvas waveform).

**Фикс**:
- Запоминать позиции маркеров **ДО** `setContent()`.
- Вызывать `setContent()` с `silentOption=true`.
- Восстанавливать позиции ПОСЛЕ.

## User Stories (краткий список)

- **US1** (P1): На первом открытии SubsEdit позиции маркеров корректные.

## Functional Requirements (указатель)

- **FR-001**: `MarkerRenderer.render()` — snapshot positions before, restore after.
- **FR-002**: `setContent(silent = true)`.
- **FR-003**: Тест: открыть редактор → маркеры НЕ «прыгают».

## Acceptance Criteria

- [ ] **AC1**: На первом открытии маркеры на правильных позициях.
- [ ] **AC2**: Нет задержек/миганий.

## Related LiveDocs

- Domain: [catalog.md](../domain/catalog.md) (lyrics), [editorial.md](../domain/editorial.md) (editor)
- Feature: [017-editor-status-bypass.md](../features/017-editor-status-bypass.md), [163-fix-song-editor-regressions.md](../features/163-fix-song-editor-regressions.md)

## Code

- Frontend: `webvue3/src/components/Songs/SubsEdit.vue` — `MarkerRenderer.render()`

## History

- Created: 2026-08-14
- Last updated: 2026-08-14