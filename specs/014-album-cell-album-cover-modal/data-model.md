# Data Model: Альбомы — клик по ячейке открывает модалку обложки альбома

**Date**: 2026-07-27
**Spec**: [./spec.md](./spec.md)
**Research**: [./research.md](./research.md)

## Что меняется в данных

Фича **не вводит новых сущностей** и **не меняет существующие таблицы БД**.
Все нужные данные уже есть: `Album`, `Song`, `Picture` (всё существующее).

Изменения только в:

1. **Helper в `Album.kt`** — добавляется функция `getFirstSongId(albumId, database): Long?` (in-memory helper, не DTO, не БД-таблица).
2. **Новый HTTP endpoint** — `/api/albums/firstsongid` (см. [contracts/api.md](./contracts/api.md)).
3. **Новый Vuex action** в `webvue3/src/components/Albums/store.js` — `getFirstSongIdByAlbumIdPromise(ctx, albumId)`.
4. **Локальный state в `AlbumsTable.vue`** — `isAlbumCoverModalVisible`, `prevCurrentSongId`, `currentAlbumCoverFirstSongId`.

## Сущности, участвующие в фиче (без изменений)

### Album (существующая)
- **Что:** Запись альбома. tbl_albums (PostgreSQL) + DTO `AlbumDTO` для API.
- **Ключевые поля:** `id`, `authorId`, `year`, `name`, `albumType`, `sortOrder`, `description`, `shortDescription`, `warning`.
- **Связи:** `authorId → Author.id`, `id → Song.albumId` (один ко многим).
- **Изменения в фиче:** нет.

### Song (существующая)
- **Что:** Запись песни. tbl_songs (PostgreSQL) + DTO `SongDTO` + `SongDTOdigest`.
- **Ключевые поля для фичи:** `id`, `albumId`, `firstSongInAlbum` (Boolean), `rootFolder`, `album`, `author`, `pictureNameAlbum` (см. `Song.kt:335`).
- **Связи:** `albumId → Album.id` (многие к одному), `pictureNameAlbum → Pictures.name` (один к одному).
- **Изменения в фиче:** нет.

### Picture (существующая, опосредованно)
- **Что:** Запись изображения альбома в БД + MinIO.
- **Ключевые поля:** `id`, `name = song.pictureNameAlbum`, `full` (base64 в MinIO).
- **Связи:** `name ↔ Song.pictureNameAlbum`, `id → AlbumDTO.albumPictureId` (денормализованная ссылка для UI).
- **Изменения в фиче:** нет (модалка использует существующее API `/api/song/savealbumcover`, которое само создаёт/обновляет `Pictures`).

### LogoAlbum.png (файл на диске, не сущность)
- **Что:** PNG-файл обложки альбома, лежит в `settings.rootFolder/LogoAlbum.png` (т.е. в папке конкретной песни, но **имя файла и содержимое одинаковы для всех песен альбома**).
- **Изменения в фиче:** нет (модалка пишет сюда как обычно).

## State машина (только для клика пользователя)

```
                    ┌─────────────────────────────────────────┐
                    │  AlbumsTable.idle                       │
                    │  - isAlbumCoverModalVisible = false     │
                    │  - prevCurrentSongId = null             │
                    └────────────────────┬────────────────────┘
                                         │ user clicks cell(albumPicture) OR cell(name)
                                         │ AND songsCount > 0
                                         ▼
                    ┌─────────────────────────────────────────┐
                    │  AlbumsTable.resolving-context          │
                    │  - dispatch getFirstSongIdByAlbumId     │
                    │  - запоминаем prevCurrentSongId         │
                    │  - isAlbumCoverModalVisible = true      │
                    │  - setCurrentSongIdOnly(firstSongId)    │
                    └────────────────────┬────────────────────┘
                                         │ модалка смонтирована
                                         ▼
                    ┌─────────────────────────────────────────┐
                    │  AlbumCoverModal.open                   │
                    │  (вся логика в AlbumCoverModal.vue)      │
                    └────────────────────┬────────────────────┘
                                         │ @close (без сохранения) ИЛИ @saved
                                         ▼
                    ┌─────────────────────────────────────────┐
                    │  AlbumsTable.closing-modal              │
                    │  - если @saved: loadOneRecord(albumId)  │
                    │  - setCurrentSongIdOnly(prevSongId)     │
                    │  - isAlbumCoverModalVisible = false     │
                    │  - prevCurrentSongId = null             │
                    └────────────────────┬────────────────────┘
                                         │
                                         ▼
                              [back to AlbumsTable.idle]
```

## Валидация / инварианты

- **INV-1:** Модалка **не откроется**, если `songsCount === 0` (UI-блокировка, см. `data().canEditCover` в `AlbumsTable.vue`).
- **INV-2:** После `@close` или `@saved` `currentSongId` восстановлен к `prevCurrentSongId` (если был) или `null` (если до клика не было).
- **INV-3:** `firstSongId`, возвращённый `/api/albums/firstsongid`, **всегда** указывает на песню, у которой `albumId` совпадает с запрошенным (helper `Album.getFirstSongId` гарантирует это SQL-фильтром `WHERE album_id = ?`).
- **INV-4:** Превью в `AlbumsTable` обновится **только при `@saved`** (через `loadOneRecord(albumId)`), при `@close` без сохранения — не обновляется (избегаем лишний сетевой запрос).
- **INV-5:** Файл `LogoAlbum.png` и запись `Pictures` после сохранения **идентичны** независимо от того, открыта ли модалка из `SongEdit.vue` или из `AlbumsTable.vue` (один и тот же backend endpoint `/api/song/savealbumcover`).
