---
status: Active
slug: 017-fix-markers-at-position-zero
related:
  - ../domain/catalog.md
  - ../domain/editorial.md
  - ../features/010-lyrics-spec-tags.md
  - ../features/018-fix-spec-tag-markers-at-zero.md
  - ../features/019-fix-setcontent-resets-position.md
  - ../../specs/017-fix-markers-at-position-zero/spec.md
---

# 017 — Fix markers at position zero (LiveDoc)

> Drill-down — [specs/017-fix-markers-at-position-zero/spec.md](../../specs/017-fix-markers-at-position-zero/spec.md).

## What it does

Pass 28 (`016-fix-spec-tags-marker-loss-on-reopen`) перенёс `loadedMarkers` +
создание регионов из `ws.on('decode')` в `mounted()` **синхронно ДО**
`this.sourceText = await ...`. Это устранило race `sourceText` watcher ↔
`ws.on('decode')` для спецтегов, но породило **новую регрессию**: на
**первом открытии** любой песни **все маркеры «залипают» в позиции 0**
(толстая красная линия на старте таймлайна).

**Корневая причина**: `loadedMarkers` срабатывает ДО того, как
`wavesurfer-instance` фактически готов принимать позиции. Маркеры создаются
с `time = 0` (по умолчанию), и после привязки к wavesurfer они «застывают»
на нуле до следующего `redrawMarkers()`.

**Фикс**:
- `loadedMarkers` — асинхронно после `wavesurfer-instance` ready event.
- `setTimeout(50)` или `await wavesurfer.instance(...)` перед `markerRender()`.

**Эта спека — ПЕРВЫЙ слой** бага (на первом открытии). Второй слой —
красная линия в нуле из-за спецтегов — исправлен в
`018-fix-spec-tag-markers-at-zero`. Третий слой (тики re-layout после
`setContent`) — в `019-fix-setcontent-resets-position`.

## User Stories (краткий список)

- **US1** (P1): На первом открытии SubsEdit маркеры на правильных позициях.

## Functional Requirements (указатель)

- **FR-001**: `mounted()` сначала `await wavesurfer.on('ready')`, затем создание регионов.
- **FR-002**: Snapshot позиций до/после `setContent()`.

## Acceptance Criteria

- [ ] **AC1**: При открытии `/songs/{id}/edit` маркеры сразу на правильных позициях.
- [ ] **AC2**: Спецтеги не «залипают» в нуле.
- [ ] **AC3**: Нет тиков/миганий.

## Related LiveDocs

- Domain: [catalog.md](../domain/catalog.md) (lyrics), [editorial.md](../domain/editorial.md) (SubsEdit)
- Feature: [010-lyrics-spec-tags.md](010-lyrics-spec-tags.md),
  [018-fix-spec-tag-markers-at-zero.md](018-fix-spec-tag-markers-at-zero.md),
  [019-fix-setcontent-resets-position.md](019-fix-setcontent-resets-position.md)

## Code

- Frontend: `webvue3/src/components/Songs/SubsEdit.vue` — `mounted()`, `loadedMarkers`
- Frontend: `webvue3/src/services/wavesurfer-marker-service.js` — async-ready

## History

- Created: 2026-08-14
- Last updated: 2026-08-14