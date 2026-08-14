---
status: Active
slug: 018-fix-album-name-year-grouping
related:
  - ../domain/catalog.md
  - ../features/181-zakroma-author-load-progress.md
  - ../../specs/018-fix-album-name-year-grouping/spec.md
---

# 018 — Закрома: альбомы с одинаковым именем, но разными годами — не сливаются (LiveDoc)

> Drill-down — [specs/018-fix-album-name-year-grouping/spec.md](../../specs/018-fix-album-name-year-grouping/spec.md).

## Что делает

Баг: если у автора два альбома с **одинаковым названием** (но разными
годами), в Закромах все песни этих двух альбомов показывались в **первом**
по счёту альбоме, второго как будто не было.

**Корневая причина**: группировка в `Grouping.js` шла по `album.name` (а не по
`album.id`). Два альбома с одинаковым именем → один ключ → один блок.

**Фикс**: группировка по `album.id` (уникальный). Опционально подзаголовок в
секции альбома: `${name} (${year})`.

## User Stories (краткий список)

- **US1** (P1): Закрома автора с двумя одноимёнными альбомами — оба видны.

## Functional Requirements (указаль)

- **FR-001**: `GroupAlbumsBy(groupBy='albumId')` (вместо `albumName`).
- **FR-002**: Подзаголовок `${name} (${year})` если `groupBy == albumId`.

## Acceptance Criteria

- [ ] **AC1**: Два альбома с одним именем → обе секции видны.
- [ ] **AC2**: Подзаголовок `${name} (${year})` присутствует для неоднозначности.

## Связанные LiveDocs

- Domain: [catalog.md](../domain/catalog.md) (Album)
- Feature: [181-zakroma-author-load-progress.md](../features/181-zakroma-author-load-progress.md) (Закромы)

## Код

- Frontend: `karaoke-public/src/views/ZakromaView.vue` — `groupAlbumsByAlbumId()`
- Backend: `karaoke-app/.../service/ZakromaService.kt` — передать `albumId` в DTO

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14