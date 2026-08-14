---
status: Active
slug: 011-album-song-rename
related:
  - ../domain/catalog.md
  - ../architecture/L3-components.md
  - ../../specs/011-album-song-rename/spec.md
---

# 011 — Альбом как сущность + переименование Settings→Song (LiveDoc)

> Drill-down — [specs/011-album-song-rename/spec.md](../../specs/011-album-song-rename/spec.md).

## Что делает

Большое структурное изменение:
1. **Альбом — отдельная сущность** (отдельная синхронизирующаяся таблица).
   - Поля: автор, год, название, **признак типа** (студийный / концертный /
     сборник / бутлег), **порядок сортировки** внутри автора (для альбомов
     одного года — не по названию).
   - **Песня ссылается на альбом** (FK).
2. **Переименование Settings → Song** (исторически сложилось, что сущность
   «песня» называлась «settings»). После рефакторинга — везде `Song`.

## User Stories (краткий список)

- **US1** (P1): Сущность Album — отдельная.
- **US2** (P1): Песня ссылается на альбом.
- **US3** (P1): Переименование `settings` → `Song` в коде/БД/DTO.

## Functional Requirements (указатель)

- **FR-001**: SQL — `CREATE TABLE tbl_albums (...)`.
- **FR-002**: SQL — `ALTER TABLE tbl_settings ADD COLUMN album_id BIGINT`.
- **FR-003**: Backend — `Album.kt` как AR.
- **FR-004**: Рефакторинг имён: `Settings → Song` (см. также `102-rename-song-settings-vars`).

## Acceptance Criteria

- [ ] **AC1**: `tbl_albums` создана, FK `tbl_settings.album_id`.
- [ ] **AC2**: Album — AR с типом и порядком.
- [ ] **AC3**: Весь код использует `Song` (не `Settings`).

## Связанные LiveDocs

- Domain: [catalog.md](../catalog.md) (Album, Song как AR)
- Architecture: [L3-components.md](../architecture/L3-components.md)
- Feature: [102-rename-song-settings-vars.md](../features/102-rename-song-settings-vars.md) (полный рефакторинг названий)

## Код

- SQL: `deploy/karaoke-db/28_rename_settings_to_songs.sql` (уже)
- SQL: `deploy/karaoke-db/<NNN>_tbl_albums.sql` (новый)
- Backend: `karaoke-app/.../model/Album.kt` (новый)
- Backend: рефакторинг `Settings → Song` (см. `102-rename-song-settings-vars.md`)

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14