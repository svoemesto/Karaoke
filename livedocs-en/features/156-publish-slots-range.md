---
status: Active
slug: 156-publish-slots-range
related:
  - ../domain/publishing.md
  - ../architecture/L3-components.md
  - ../../specs/156-publish-slots-range/spec.md
---

# 156 — Расширение диапазона свободных слотов публикации (10 → 22) (LiveDoc)

> Drill-down — [specs/156-publish-slots-range/spec.md](../../specs/156-publish-slots-range/spec.md).

## What it does

В `SongEdit.vue` при пустом поле «Дата публикации» и фокусе отображается
список свободных часовых слотов. Диапазон расширен с **(11:00-17:00)** до
**(10:00-22:00)**.

**Уточнение**: даты в списке — **только в будущем**. Если последняя
публикация в 10:00 была месяц назад, а сейчас 12:00, свободный слот на 10:00 —
**завтрашняя дата**, а не сегодняшняя.

## User Stories (краткий список)

- **US1** (P1): Расширенный диапазон 10:00–22:00 для публикаций.

## Functional Requirements (указатель)

- **FR-001**: Константа `PUBLISH_SLOTS_HOURS = listOf(10..22)`.
- **FR-002**: При фокусе на пустом поле дата → автокомплишен только из будущих слотов.

## Acceptance Criteria

- [ ] **AC1**: Фокус на пустом «Дата публикации» → 13 слотов (10:00..22:00).
- [ ] **AC2**: Все слоты — в будущем (не в прошлом).
- [ ] **AC3**: Если последний слот 10:00 был месяц назад, следующий свободный 10:00 — завтра.

## Related LiveDocs

- Domain: [publishing.md](../domain/publishing.md) (publish date / slots)
- Architecture: [L3-components.md](../architecture/L3-components.md)

## Code

- Frontend: `webvue3/src/components/Songs/SongEdit.vue` — `availablePublishSlots()` + autocomplete
- Backend: `karaoke-app/.../service/PublicationService.kt` (если logic на сервере)

## History

- Created: 2026-08-14
- Last updated: 2026-08-14