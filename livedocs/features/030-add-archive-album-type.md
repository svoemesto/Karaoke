---
status: Active
slug: 030-add-archive-album-type
related:
  - ../domain/catalog.md
  - ../features/031-add-tribute-cover-album-type.md
  - ../architecture/L3-components.md
  - ../../specs/030-add-archive-album-type/spec.md
---

# 030 — Добавить тип альбома «Архивные записи» (LiveDoc)

> Drill-down — [specs/030-add-archive-album-type/spec.md](../../specs/030-add-archive-album-type/spec.md).

## Что делает

Новый тип альбома «Архивные записи» (`type: ARCHIVE`) — для старых, неактивных,
раритетных альбомов. Используется для фильтрации, отдельной секции на
`/zakroma` и статистики.

Не путать с «удалёнными альбомами» (физически отсутствующими).

## User Stories (краткий список)

- **US1** (P1): Админ назначает альбому тип «Архивные записи».
- **US2** (P2): В `/zakroma` — фильтр «Только архивы».

## Functional Requirements (указатель)

- **FR-001**: `Album.type` enum расширен значением `ARCHIVE`.
- **FR-002**: UI в карточке альбома — radio «Архивные записи».
- **FR-003**: Фильтр в `AlbumsFilterModal.vue` — тип `ARCHIVE`.

## Acceptance Criteria

- [ ] **AC1**: Альбом с типом `ARCHIVE` помечается соответственно.
- [ ] **AC2**: В `/zakroma` фильтр «Архивы» оставляет только такие альбомы.

## Связанные LiveDocs

- Domain: [catalog.md](../domain/catalog.md) (Album)
- Feature: [031-add-tribute-cover-album-type.md](../features/031-add-tribute-cover-album-type.md)

## Код

- Backend: `karaoke-app/.../model/Album.kt` — `type: AlbumType` enum + `ARCHIVE`
- Backend: SQL: `ALTER TYPE album_type ADD VALUE 'ARCHIVE'`
- Frontend: `webvue3/src/components/Albums/AlbumEditModal.vue` — radio
- Frontend: `karaoke-public/src/views/ZakromaView.vue` — фильтр «Архивы»

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14