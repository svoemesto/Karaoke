# Component: pictures

> **Домен**: [catalog](../domain.md)
> **Компонента**: сущность `Pictures` — менеджер картинок альбома/трека.

## Ответственность | Responsibility

`Pictures` — единая сущность для всех картинок проекта, связанных
с альбомом или треком:

- Обложка альбома (`pictureCover*`).
- Баннер альбома (`pictureAlbum*`).
- Превью для соцсетей (`pictureBoosty*`, `pictureDzenKaraoke*`).
- Другие варианты (см. поля).

Хранит URL'ы на MinIO, а **не base64-данные** (по соображениям
размера БД).

**Граница**: контекст НЕ отвечает за:

- Загрузку/ресайз изображений (см. [system/utilities](../../../system/utilities.md) — `UtilsPictures.kt`).
- Конкретные варианты картинок (определяются в `KaraokeProperties` и шаблонах автопубликации).

## Ubiquitous Language | Единый язык

| Термин | Определение |
|---|---|
| **`pictureFull`** | Big-size версия (обычно 1280×1280 для обложки). URL в MinIO. |
| **`pictureAlbumFull`** | Big-size для VK/Boosty/Dzen. URL в MinIO. |
| **`pictureAlbumPreview`** | Preview-size (200×200). URL в MinIO. |
| **`pictureCoverFull`** | Cover big-size (256×256 или 512×512). URL в MinIO. |
| **`pictureCoverPreview`** | Cover preview-size. |
| **`pictureBoostyFull`** | Big-size для Boosty. |
| **`pictureDzenKaraoke`** | Big-size для Dzen Karaoke. |
| **`pictureName`** | Уникальное имя (используется как filename в MinIO). |

## Интерфейсы и Контракты | Interfaces and Contracts

### Контракт класса

`class Pictures(database, storageService, storageApiClient) : Serializable,
Comparable<Pictures>, KaraokeDbTable, KaraokeStorage`.
- `getTableName() = "tbl_pictures"`.
- `compareTo(other)` — по `id`.
- `toDTO(): PicturesDTO`.

### Таблица БД: `tbl_pictures`

| Колонка | Тип | Описание |
|---|---|---|
| `id` | BIGINT PK | Auto-generated |
| `picture_name` | VARCHAR | Уникальное имя (UNIQUE-индекс `uq_tbl_pictures_picture_name`, миграция `11_pictures_unique_name.sql`) |
| `picture_full` | TEXT | `useInList=false` — большой URL/base64 |
| `picture_album_full` | TEXT | |
| `picture_album_preview` | TEXT | |
| `picture_cover_full` | TEXT | |
| `picture_cover_preview` | TEXT | |
| `picture_boosty_full` | TEXT | |
| `picture_dzen_karaoke` | TEXT | |
| `recordhash` | VARCHAR(32) | md5 для sync |

### Kotlin-поля (по `Pictures.kt`)

```kotlin
class Pictures(
    override val database: KaraokeConnection = WORKING_DATABASE,
    override val storageService: KaraokeStorageService = KSS_APP,
    override val storageApiClient: StorageApiClient = SAC_APP,
) : Serializable, Comparable<Pictures>, KaraokeDbTable, KaraokeStorage {
    @KaraokeDbTableField(name = "id", isId = true)
    override var id: Long = 0

    @KaraokeDbTableField(name = "picture_name")
    var name: String = "Picture name"

    @KaraokeDbTableField(name = "picture_full", useInList = false)
    var full: String = ""
        set(value) {
            field = "" // base64 не хранится в БД — только в MinIO
            // decode base64 -> PNG -> storageUploadFile + storageUploadFilePreview
        }

    // picture_album_full, picture_album_preview, picture_cover_full, picture_cover_preview,
    // picture_boosty_full, picture_dzen_karaoke — аналогично.
}
```

**Производные поля (getters, без запроса в БД)**: `author`, `year`, `album`
— разбор `name` по разделителю `" - "` (3+ части); `isAuthorPicture`
(`author` есть, `year`/`album` пусты), `isAlbumPicture` (все три есть);
`fileName` (`LogoAuthor.png` / `LogoAlbum.png`); `pathToFolder`.

### Реализация `KaraokeStorage`

- `storageBucketName = "karaoke"`.
- `storageFileName`: `"$author/$name.author.png"` (автор) /
  `"$author/$year - $album/$name.album.png"` (альбом) / `"$name.png"`.
- `storageFileNamePreview`: те же пути с `".preview.author.png"` /
  `".preview.album.png"` / `"$name.preview.png"`.
- `storageBucketIsPublic` — get/set через `storageService.isBucketPublic` /
  `setBucketPublic` / `setBucketPrivate`.

### Методы (`companion object`)

- `TABLE_NAME = "tbl_pictures"`.
- `loadList(whereArgs, limit, offset, database, storageService, storageApiClient, ignoreUseInList): List<Pictures>` — фильтры `getWhereList`: `id`, `picture_name` (частичный `LOWER(...) LIKE`), `name` (точный).
- `getPictureById(id, ...)` — `KaraokeDbTable.loadById`.
- `getPicturesByIds(ids, ...)` — пакетный `loadByIds` → `Map<Long, Pictures>`.
- `getPictureByName(name, ..., ignoreUseInList = true): Pictures?`.
- `getPicturesByNames(names, ..., ignoreUseInList = true): Map<String, Pictures>` — один `WHERE picture_name IN (...)`, blank/дубликаты отфильтрованы.
- `createNewPicture(newPicture, ...)`, `delete(id, database)`, `listHashes(...)`.

**DTO**: `PicturesDTO(id, name, preview, full, previewUrl, fullUrl, author,
year, album, isAuthorPicture, isAlbumPicture, pathToFolder, fileName)`;
`preview`/`full` в DTO всегда пустые (данные — в MinIO, отдаются по URL).

### CRUD и UI-контракт

- **Отдельного `PicturesController` НЕТ.** Обновление и дайджест —
  `ApiController`: `POST /api/pictures/updatepicture`,
  `POST /api/pictures/picturesdigests`.
- Отдача файла — `GET /api/picture/file?file=<storageFileName>`
  (`ApiController.kt:6730`); загрузка/скачивание объектов — общий
  `StorageController` (`POST /api/storage/upload`,
  `GET /api/storage/download`, ...).
- Vuex store: `webvue3/src/components/Pictures/store.js` +
  `webvue3/src/components/Pictures/filter/store.js`.

## Логика и Алгоритмы | Logic and Algorithms

### Setter `full`: base64 приходит, в БД не остаётся

```kotlin
set(value) {
    field = ""            // base64 не хранится в БД
    if (value.isEmpty()) return
    // Base64.decode -> ImageIO.read -> PNG -> storageUploadFile(полный)
    // preview: resizeBufferedImage(125x50) при width > 400, иначе 50x50
    //          -> storageUploadFilePreview
}
```

Setter **всегда** записывает `""` в поле (в БД уходит пустая строка) и, если
значение непустое, декодирует base64 в PNG, кладёт полный файл и превью в
MinIO. Исключения при декодировании/загрузке глотаются (`println`) — запись
в БД при этом не падает.

### Два пути получения URL: `previewUrl()` против `toDTO()`

- `previewUrl()` — дешёвый, **без DB-запроса**: только строковые производные
  от `name` → `/api/picture/file?file=<storageFileNamePreview>`. Используется
  дайджест-эндпоинтами (`Album.toDigestDTOs`, `Album.toLiteDTOs`).
- `toDTO()` — дополнительно тянет `pathToFolder`, который делает
  `Song.loadListFromDb` на **каждый** вызов (N+1 на тысячах картинок) —
  поэтому дайджесты сознательно `toDTO()` не вызывают.

### Lazy-загрузка больших полей (`useInList`)

Поля с `useInList = false` не включаются в `SELECT` при `loadList`
(иначе OOM на 18k+ записей × base64). Достать их можно только точечной
загрузкой: `getPictureById` (по id) или `getPictureByName` /
`getPicturesByNames` с `ignoreUseInList = true`.
Для preview-картинок Закромов (`Zakroma.buildFromSongs`, Pass 186)
вызывается `getPicturesByNames(..., ignoreUseInList = false)`, чтобы не
отфильтровать preview-записи.

### Идемпотентность `createNewPicture` (гонка)

`createNewPicture` сначала ищет запись по `name` (`getPictureByName`); если
найдена — возвращает её. Иначе INSERT; нарушение UNIQUE-индекса
`uq_tbl_pictures_picture_name` перехватывается `try/catch` и приводит к
повторному `getPictureByName` — проигравшая гонка параллельная вставка
(две песни одного альбома одновременно) возвращает уже вставленную запись,
а не падает 500.

### Sync

`PicturesSyncTarget` (`SyncTarget.kt`): `key = "pictures"`,
`tableName = tbl_pictures`, `oneClickDirection = LOCAL_TO_SERVER`,
`labelFn = { it.name }`, `rowChunkSize = 50` (полный файл грузится через
`ignoreUseInList = true`, часть старых строк может нести base64 — писатель
шлёт по 1, читатель осторожно по 50).
Разрешения — 40 флагов `sync_pictures_<push|pull>_<insert|update|delete|move>_allowed`
в `KaraokeProperties`.

### Прочее

- `saveToDisk()` — скачивает файл из хранилища и пишет PNG в
  `/sm-karaoke/system/pictures/$name.png` (`chmod 666`).
- `VkPreviewWarmupClient` (`services/VkPreviewWarmupClient.kt`) — генерация
  preview перед публикацией в VK (используется `VkAutoPublishService`).
- `syncRemotePicturesInStorage` (`Utils.kt:716`) — синхронизация картинок с
  remote MinIO.

## Hot paths

- **`Pictures.loadList`** — на странице альбомов webvue3. С учётом
  `useInList=false` для больших полей, в SELECT они не включаются
  → нет OOM.
- **`getPictureById` / `getPictureByName(ignoreUseInList = true)`** —
  lazy загрузка одной картинки с полным содержимым.
- **`VkPreviewWarmupClient`** — генерирует preview перед публикацией.
- **`syncRemotePicturesInStorage`** (`Utils.kt:716`) — синхронизация
  картинок с remote MinIO.

## Ловушки и инварианты

1. **`useInList=false` MUST быть у больших полей**: иначе OOM на
   `loadList` (18k+ записей × big binary = crash).
2. **`picture_full` setter `field = ""`**: base64 в БД не сохраняется.
   Данные уходят в MinIO, в поле — пусто; если нужны данные — читайте из
   хранилища по `storageFileName`.
3. **`picture_name` MUST быть уникальным**: используется как filename
   в MinIO. Коллизия = silent overwrite (см. ADR `local-0003`).
4. **Удаление Pictures без удаления файлов в MinIO**: оставит orphan
   файлы. Cleanup job? (TODO Pass 343).

## Связанные ADR | Related ADRs

- [local-0003-shared-minio-image-cache.md](../../../adr/local-0003-shared-minio-image-cache.md) —
  MinIO как shared image cache: обложки/баннеры хранятся объектами в MinIO,
  а в `tbl_pictures` — только имена/URL; отсюда запрет на base64 в БД и
  правила invalidation.

## Известные TODO

- [ ] **`UtilsPictures.kt`** — какие именно форматы поддерживаются
      (JPEG, PNG, WebP?), лимиты размера.
- [ ] **`VkPreviewWarmupClient`** — отдельный компонент.
- [ ] **Cleanup orphan картинок в MinIO** — есть ли job.
- [ ] **Webhook на загрузку новых картинок** — есть ли.

## Код (физическая реализация)

- `karaoke-app/.../model/Pictures.kt`
- `karaoke-app/.../model/PicturesDTO.kt`
- **Отдельного `PicturesController` нет** — обновление/дайджест в
  `ApiController` (`/api/pictures/*`), файлы — через `StorageController`.
- `karaoke-app/.../services/VkPreviewWarmupClient.kt`
- `webvue3/src/components/Pictures/store.js`
- `webvue3/src/components/Pictures/filter/store.js`
- `webvue3/src/components/Pictures/...` (UI)

## Зависимости | Dependencies

- **Storage layer** ([storage domain](../../../domains/storage/domain.md)) —
  `KaraokeStorage` + `KaraokeStorageService` для MinIO-операций.
- **Album** — использует `Pictures.getPictureByName(name=...)` (см. `Album.kt:94, 99`) для превью. **Не FK** в БД, а поиск по `picture_name` (см. KDoc `Album.kt:86`).
- **News** — `News.idPicture` ссылается на `Pictures.id`.
- **Author** — аватарка автора (`name` = имя автора), `Pictures.loadList` из `Author.toDTO()`.

## Changelog

- **Pass 483** (2026-09-27, spec `483-knowledge-domain-catalog`): секции приведены к шаблону компонента. Автор: agent (Karaoke).
- **Pass 341 P3a** (2026-09-09): Initial detailed. Автор: agent (Karaoke).
