---
status: Active
slug: 031-add-tribute-cover-album-type
related:
  - ../domain/catalog.md
  - ../features/030-add-archive-album-type.md
  - ../architecture/L3-components.md
  - ../../specs/031-add-tribute-cover-album-type/spec.md
---

# 031 — Add Tribute/Cover album type (LiveDoc)

> Drill-down — [specs/031-add-tribute-cover-album-type/spec.md](../../specs/031-add-tribute-cover-album-type/spec.md).

## What it does

Новый тип альбома «Трибьют/Кавер» в дополнение к существующим типам
(обычный, архивный — см. `030-add-archive-album-type`, сборник, etc).

**Эффект**: альбомы-трибьюты (covers) можно помечать специальным типом —
фильтрация, отчёты, иконка в UI, отдельная секция на `/zakroma`.

## User Stories (краткий список)

- **US1** (P1): Админ назначает альбому тип «Трибьют/Кавер».

## Functional Requirements (указатель)

- **FR-001**: `Album.type` enum расширен значением `TRIBUTE_COVER`.
- **FR-002**: UI в карточке альбома — radio «Трибьют/Кавер».
- **FR-003**: Фильтр в `AlbumsFilterModal.vue` — тип `TRIBUTE_COVER`.

## Acceptance Criteria

- [ ] **AC1**: В карточке альбома — radio «Трибьют/Кавер» доступно.
- [ ] **AC2**: Альбом с типом `TRIBUTE_COVER` виден в фильтре типа.
- [ ] **AC3**: Миграция БД — новый enum value (через `ALTER TYPE`).

## Related LiveDocs

- Domain: [catalog.md](../domain/catalog.md) (Album)
- Feature: [030-add-archive-album-type.md](../features/030-add-archive-album-type.md)

## Code

- Backend: `karaoke-app/.../model/Album.kt` — `type: AlbumType` enum + `TRIBUTE_COVER`
- Backend: SQL: `ALTER TYPE album_type ADD VALUE 'TRIBUTE_COVER'`
- Frontend: `webvue3/src/components/Albums/AlbumEditModal.vue` — radio
- Frontend: `webvue3/src/components/Albums/AlbumsFilterModal.vue` — filter

## History

- Created: 2026-08-14
- Last updated: 2026-08-14