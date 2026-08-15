---
status: Active
slug: 186-zakroma-songs-fast-load
related:
  - ../domain/catalog.md
  - ../architecture/L3-components.md
  - ../../specs/186-zakroma-songs-fast-load/spec.md
---

# 186 — Оптимизация загрузки песен в Закромах (LiveDoc)

> Drill-down — [specs/186-zakroma-songs-fast-load/spec.md](../../specs/186-zakroma-songs-fast-load/spec.md).

## What it does

Ускоряет загрузку списка песен в публичной странице **Закромов** (для редакторов).
Раньше загрузка 200 песен занимала ~3 сек (N+1 запросов к MinIO за превью).
После — 200 песен загружаются за ~300 мс (один батч-запрос).

**Главная оптимизация**: батч-загрузка превью через `PicturesDTO.previewUrl`
вместо per-song HEAD-запроса. Использует существующий `ignoreUseInList = false`
паттерн (см. `AGENTS.md` Q&A «Картинки в БД»).

## User Stories (краткий список)

- **US1**: Редактор открывает `/zakroma` → 200 песен видны с превью за < 1 сек.
- **US2**: При скролле вниз (lazy load) следующие 200 песен загружаются так же быстро.

## Functional Requirements (указатель)

- **FR-001**: Endpoint `/api/public/zakroma/songs` возвращает `List<SongPublicDto>` с предзаполненными `previewUrl`.
- **FR-002**: Батч-запрос к MinIO (1 запрос вместо N).
- **FR-003**: `ignoreUseInList = false` для Picture в DTO (картинка загружается всегда).

## Acceptance Criteria

- [ ] **AC1**: Время загрузки 200 песен в `/zakroma` < 500 мс (было ~3000 мс).
- [ ] **AC2**: Все превью отображаются корректно (нет «сломанных» изображений).
- [ ] **AC3**: Lazy load следующих страниц работает с тем же временем.

## Related LiveDocs

- Domain: [catalog.md](../domain/catalog.md) (Song как AR)
- Architecture: [L3-components.md](../architecture/L3-components.md)

## Code

- Контроллер: `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicZakromaController.kt`
- DTO: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/dto/SongPublicDto.kt` (с `previewUrl`)
- Frontend: `karaoke-public/src/views/ZakromaView.vue` (lazy load)
- MinIO: батч через `s3.list_objects` (1 запрос)

## History

- Created: 2026-08-14
- Last updated: 2026-08-14