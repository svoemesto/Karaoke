# Component: Album (детальный)

> **Домен**: [catalog](../domain.md)
> **Компонента**: детальное описание `Album` entity.


## Ответственность | Responsibility


детальное описание `Album` entity.

## Файл

`karaoke-app/.../model/Album.kt`

## Назначение

**Сущность «Альбом»** — отдельная таблица `tbl_albums` (миграция
`deploy/karaoke-db/29_albums.sql`). Связи:
- Один автор — много альбомов (через `authorId`).
- Один альбом — много песен (через `Song.albumId`).
- `author_id → tbl_authors(id) ON DELETE RESTRICT`.
- `tbl_songs.album_id → tbl_albums(id) ON DELETE SET NULL`.
- UNIQUE `tbl_albums_author_year_name_key (author_id, year, name)`.

## Интерфейсы и Контракты | Interfaces and Contracts

### Контракт класса

`@JsonIgnoreProperties(value = ["database", "sqlToInsert"])`
`class Album(database, storageService, storageApiClient) : Serializable,
Comparable<Album>, KaraokeDbTable`.
- `getTableName() = TABLE_NAME = "tbl_albums"`.
- `compareTo()` — `compareValuesBy(authorId, sortOrder, year, name)`.
- `toDTO(): AlbumDTO`.

### Поля (Kotlin-тип по `Album.kt`, колонка по `@KaraokeDbTableField`)

| Kotlin-поле | Колонка | Тип (Kotlin) | Описание |
|---|---|---|---|
| `id` | `id` | Long | PK, `isId = true` |
| `authorId` | `author_id` | Long | FK на `tbl_authors.id`, **ON DELETE RESTRICT** |
| `year` | `year` | Int | Год выпуска |
| `name` | `name` | String | Название альбома |
| `albumType` | `album_type` | String | Тип альбома (хранится как `AlbumType.dbValue` — строка) |
| `sortOrder` | `sort_order` | Int | Порядок отображения (сквозной по автору, не привязан к году; меньше = раньше) |
| `description` | `description` | String | Полное описание |
| `shortDescription` | `short_description` | String | Короткое описание |
| `warning` | `warning` | String | Предупреждение (например, "архив") |
| `totalSongCount` | `total_song_count` | Long | Всего песен альбома; поддерживается триггером, вручную не править |
| `readySongCount` | `ready_song_count` | Long | Песен с `id_status >= 6` (APPROVED); поддерживается триггером |

**Типизованный доступ**: `var albumTypeEnum: AlbumType` — getter
`AlbumType.fromDb(albumType)` (fallback `STUDIO`), setter пишет
`albumType = value.dbValue`.

### `albumType` (Enum, файл `AlbumType.kt`)

`enum class AlbumType(dbValue, description, groupLabel, filterLabel) : Serializable`
— 7 значений (порядок объявления ≠ порядок группировки):

| Константа | `dbValue` | `groupLabel` | `filterLabel` |
|---|---|---|---|
| `STUDIO` | `studio` | Студийные альбомы | Студийные |
| `LIVE` | `live` | Концертные альбомы | Концертные |
| `COMPILATION` | `compilation` | Сборники | Сборники |
| `BOOTLEG` | `bootleg` | Бутлеги | Бутлеги |
| `SINGLE` | `single` | Синглы | Синглы |
| `ARCHIVE` | `archive` | Архивные записи | Архивные |
| `TRIBUTE` | `tribute` | Трибьют/Кавер | Трибьют/Кавер |

`AlbumType.fromDb(value: String?): AlbumType` — поиск по `dbValue`,
fallback `STUDIO`. `AlbumType.ZAKROMA_GROUP_ORDER = [STUDIO, SINGLE, LIVE,
COMPILATION, BOOTLEG, ARCHIVE, TRIBUTE]` — порядок группировки/фильтров на
Закромах (FR-024 спеки 012).

**Хранение**: `album_type` хранится как `dbValue` (строка) —
**reflection-слой `KaraokeDbTable` не поддерживает enum-поля напрямую**
(только скалярные типы).

### DTO

`AlbumDTO`: `id`, `authorId`, `year`, `name`, `albumType`, `sortOrder`,
`description`, `shortDescription`, `warning`, `authorName`,
`authorPictureId`, `authorPicturePreviewUrl`, `albumPictureId`,
`albumPicturePreviewUrl`, `songsCount`; `isValid()` /
`validationErrors()` / `fromDto(database): KaraokeDbTable`.

### Методы (`companion object`)

- `TABLE_NAME = "tbl_albums"`.
- `loadList(whereArgs, limit, offset, database, storageService, storageApiClient, ignoreUseInList = true): List<Album>` — фильтры `getWhereList`: `id`, `author_id`, `year`, `name` (точный), `name_search` (частичный `LOWER(name) LIKE`), `album_type`.
- `getAlbumById(...)`, `getAlbumsByIds(...)`, `getAlbumByAuthorYearName(...)`, `getAlbumsByAuthorId(...)`.
- `loadAlbumTilesWithCounts(...)` — плитки альбомов с счётчиками песен.
- `countSongsByAlbumIds(...)` — `SELECT album_id, COUNT(*) ... GROUP BY album_id`.
- `getFirstSongId(...)`.
- `reorderAlbums(orderedIds, ...)` — присваивает `sortOrder` = индекс в списке; совпадающие не пересохраняются; не найденные id пропускаются.
- `normalizeSortOrderAcrossYears(...): Int` — идемпотентная разовая миграция на сквозную нумерацию.
- `createNewAlbum(...)`, `delete(id, database)`, `listHashes(...)`.
- `findOrCreateForSongImport(...)` / `findOrCreateForSongImportRaw(...)` / `findOrCreateForSongImportWithAutoCover(...)`.
- `applyAutoAlbumCoverFromFolder(...)` — подбор обложки из папки (jpg/jpeg/png/webp/bmp/tiff → `LogoAlbum.png`).
- `toLiteDTOs(albums): List<AlbumDTO>` — только сырые поля, без запросов к БД.
- `toDigestDTOs(albums, ...): List<AlbumDTO>` — пакетная версия `toDTO()` без N+1.

### API-контракты (karaoke-app `ApiController`)

| Endpoint | Контракт |
|---|---|
| `POST /api/albums/albumsdigests` | Дайджест альбомов (webvue3 `Albums/store.js`) |
| `POST /api/albums/albumsdigestslite` | Облегчённый дайджест (`toLiteDTOs`) |
| `POST /api/albums/firstsongid` | id первой песни альбома |
| `POST /api/albums/createalbum` | Создание |
| `POST /api/albums/updatealbum` | Обновление |
| `POST /api/albums/deletealbum` | Удаление |
| `POST /api/albums/reorderalbums` | Drag-and-drop порядок (`reorderAlbums`) |
| `POST /api/utils/normalizealbumsortorder` | Разовая нормализация `sort_order` |
| `GET /api/public/authors/{authorId}/albums?scope=main` | Публичный список альбомов автора (Pass 359, spec 356) для `/zakroma/{id}/albums` |

## Логика и Алгоритмы | Logic and Algorithms

### Денормализация: `toDTO()` vs `toLiteDTOs()` vs `toDigestDTOs()`

- `toDTO()` на **каждый** альбом делает отдельные запросы:
  `Author.getAuthorById(authorId)` + до 2 `Pictures.getPictureByName` (имя
  автора и `"$authorName - $year - $name"`). На 2000+ альбомах это тысячи
  запросов.
- `toDigestDTOs(...)` решает ту же задачу пакетно: авторы —
  `Author.getAuthorsByIds`, картинки — `Pictures.getPicturesByNames`, то
  есть `WHERE id IN (...)` / `WHERE picture_name IN (...)`, не в цикле
  (Constitution Principle II). Причина появления — упавший в проде
  дайджест-пикер «Альбом (ссылка)» в `SongEdit.vue` (504 Gateway Timeout).
- `toLiteDTOs(...)` вообще не ходит в БД: только сырые поля альбома
  (id/authorId/year/name/albumType/sortOrder/description/...).

### Счётчики `totalSongCount` / `readySongCount`

Поддерживаются SQL-триггером `trg_tbl_songs_update_album_counts`
(миграция `49_albums_song_counts.sql`) атомарно при
INSERT/UPDATE/DELETE в `tbl_songs`: INSERT — `+1` в оба (ready — если
`id_status >= 6`); DELETE — `-1`; UPDATE `id_status` меняет только
`ready_song_count`. Вручную поля не редактируются
(прецедент — `trg_tbl_songs_update_author_counts` для `tbl_authors`).

### Порядок альбомов

- `reorderAlbums(orderedIds, ...)` — после drag-and-drop: `sortOrder` =
  индекс в присланном списке; альбомы с совпадающим `sortOrder` не
  пересохраняются, неизвестные id молча пропускаются.
- `normalizeSortOrderAcrossYears(...)` — идемпотентная разовая миграция:
  раньше `sortOrder` был номером **внутри** пары (автор, год), теперь —
  сквозным по автору; пересчёт = индекс при сортировке по (year, старый
  sortOrder, name), повторный запуск ничего не меняет.

### Импорт песен: поиск/создание альбома

`findOrCreateForSongImport` / `findOrCreateForSongImportRaw` /
`findOrCreateForSongImportWithAutoCover` ищут альбом по
(автор, год, название) и создают при отсутствии, назначая следующий
`sortOrder`; `applyAutoAlbumCoverFromFolder` подбирает обложку из папки
(drop-in `LogoAlbum.png`).

### Sync

`AlbumsSyncTarget` (`SyncTarget.kt`): `key = "albums"`,
`tableName = tbl_albums`, `oneClickDirection = LOCAL_TO_SERVER` (как у
`tbl_authors` — каталог), `labelFn = { "${it.name} (${it.year})" }`,
`rowChunkSize = 500` (лёгкие строки, фактически один запрос).
Разрешения — 40 флагов `sync_albums_<push|pull>_<insert|update|delete|move>_allowed`
в `KaraokeProperties`.

### Frontend (karaoke-public)

В публичном модуле `albumType` используется для:

1. **Группировки альбомов** в режиме `grouped` (FR-024 спеки #012) —
   `albumRenderItems(zak)` сортирует по `albumTypeCounts` (порядок
   `AlbumType.ZAKROMA_GROUP_ORDER`: studio→single→live→compilation→
   bootleg→archive→tribute).
2. **Быстрого фильтра** — `hiddenAlbumTypes: Set<dbValue>` в
   `localStorage.km-zakroma-hidden-album-types`, пользователь
   вкл/выкл через UI-кнопки в шапке.
3. **Transient auto-reset при `?albumId=`** (Pass 362, spec 363, ADR
   [local-0008](../../../adr/local-0008-effective-hidden-album-types.md)):
   `effectiveHiddenAlbumTypes(zak)` исключает тип открытого альбома
   из фильтра на время просмотра — без побочных эффектов, `localStorage`
   не пишется.

## Связанные ADR | Related ADRs

- [local-0008-effective-hidden-album-types.md](../../../adr/local-0008-effective-hidden-album-types.md) —
  transient auto-reset `effectiveHiddenAlbumTypes` при `?albumId=`:
  определяет поведение публичного списка альбомов (`/zakroma/{id}?albumId=`).

## Зависимости | Dependencies

- [dictionaries.md#albumtype](dictionaries.md) — AlbumType enum.
- [Song entity](song-entity.md) — `Song.albumId` (FK, `ON DELETE SET NULL`).
- [Author entity](author-entity.md) — `authorId` (FK, `ON DELETE RESTRICT`).
- [Pictures](pictures.md) — превью автора/альбома по имени (не FK).
- [store-albums.md](../../../system/frontend/store-albums.md) — UI.
- [local-0008-effective-hidden-album-types.md](../../../adr/local-0008-effective-hidden-album-types.md) — transient auto-reset паттерн.

## Известные TODO

- [ ] **Все поля** — полный список (Pass 343+).
- [ ] **`sort_order` в старых БД** — все ли инсталляции прошли
      `normalizeSortOrderAcrossYears`.

## Changelog

- **Pass 483** (2026-09-27, spec `483-knowledge-domain-catalog`): секции приведены к шаблону компонента. Автор: agent (Karaoke).
- **Pass 426** (2026-09-09): Initial. Автор: agent (Karaoke).
- **Pass 362** (2026-09-10): Frontend-секция + ссылка на ADR local-0008
  про transient auto-reset. Автор: agent (Karaoke).
