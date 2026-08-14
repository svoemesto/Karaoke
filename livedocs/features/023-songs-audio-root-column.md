---
status: Active
slug: 023-songs-audio-root-column
related:
  - ../domain/catalog.md
  - ../features/100-audio-similarity-threshold.md
  - ../features/129-copy-family-audio.md
  - ../architecture/L3-components.md
  - ../../specs/023-songs-audio-root-column/spec.md
---

# 023 — Колонка audio_parent_id в таблице песен (LiveDoc)

> Drill-down — [specs/023-songs-audio-root-column/spec.md](../../specs/023-songs-audio-root-column/spec.md).

## Что делает

В админ-таблице «Песни» (`webvue3`) добавлена колонка `audio_parent_id`
(заголовок «A-root»), 3-я по счёту (после `root`).

В строке при **ховере** на ячейку `root` или `A-root` во всплывающей подсказке
отображается информация о песне с этим id: Автор, год, альбом, название.

Поле `audio_parent_id` также добавлено в **фильтр** (`SongsFilterModal.vue`).

## User Stories (краткий список)

- **US1** (P1): Колонка «A-root» видна в таблице «Песни».
- **US2** (P1): Тултип с Автор/Год/Альбом/Название при ховере.

## Functional Requirements (указатель)

- **FR-001**: Колонка в `SongsTable.vue`, header «A-root».
- **FR-002**: Hover → tooltip с DTO.
- **FR-003**: Фильтр в `SongsFilterModal.vue`.

## Acceptance Criteria

- [ ] **AC1**: Колонка «A-root» 3-я по счёту.
- [ ] **AC2**: Hover показывает информацию о родительской песне.
- [ ] **AC3**: Фильтр работает (по A-root).

## Связанные LiveDocs

- Domain: [catalog.md](../domain/catalog.md) (Song.audioParentId)
- Feature: [100-audio-similarity-threshold.md](../features/100-audio-similarity-threshold.md), [129-copy-family-audio.md](../features/129-copy-family-audio.md)

## Код

- Frontend: `webvue3/src/components/Songs/SongsTable.vue` — добавить `<th>` «A-root» + tooltip
- Frontend: `webvue3/src/components/Songs/SongsFilterModal.vue` — поле «A-root»
- Backend: `karaoke-app/.../model/Song.kt` — `audioParentId: Long` (если ещё нет)

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14