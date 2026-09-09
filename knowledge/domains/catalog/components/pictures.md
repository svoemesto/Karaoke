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

## Структура данных

### Таблица БД: `tbl_pictures`

| Колонка | Тип | Описание |
|---|---|---|
| `id` | BIGINT PK | Auto-generated |
| `picture_name` | VARCHAR | Уникальное имя |
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
            field = "" // base64 НЕ хранится в БД — только URL в хранилище
        }

    // picture_album_full, picture_album_preview, picture_cover_full, picture_cover_preview,
    // picture_boosty_full, picture_dzen_karaoke — аналогично.
}
```

**NB**: setter `full` устанавливает `field = ""` при любом value —
base64 не хранится в БД, только URL в MinIO.

## Архитектура

### Интерфейсы

- **`KaraokeDbTable`** — для DB I/O (см. [persistence domain](../../../domains/persistence/domain.md)).
- **`KaraokeStorage`** — для работы с хранилищем.
- **`Comparable<Pictures>`** — сортировка по `id` (см. `Pictures.kt`: `override fun compareTo(other: Pictures): Int = id.compareTo(other.id)`).

### Sync

`PicturesSyncTarget` зарегистрирован в `SyncRegistry.all`. Sync:
- `push_insert`, `push_update`, `push_delete`, `pull_*` — через
  флаги `sync_pictures_*` в `KaraokeProperties`.

### CRUD

CRUD идёт через **общий StorageController** (`StorageController.kt`)
как для всех MinIO-объектов: `POST /api/storage/upload`,
`GET /api/storage/download`, etc. Отдельного `PicturesController`
**НЕТ**.

Picture-обёртка (метод `Pictures.getPictureByName`) используется в:

- `Album.kt:94, 99` — поиск превью по имени (`name` → URL).
- `News.kt` (предположительно) — обложка новости.

Vuex store: `webvue3/src/components/Pictures/store.js` +
`webvue3/src/components/Pictures/filter/store.js`.

## Hot paths

- **`Pictures.loadList`** — на странице альбомов webvue3. С учётом
  `useInList=false` для больших полей, в SELECT они не включаются
  → нет OOM.
- **`Pictures.loadFromDbById`** — lazy загрузка одной картинки с
  полным base64 (для preview).
- **VkPreviewWarmupClient** — генерирует preview перед публикацией.
- **`syncRemotePicturesInStorage`** (`Utils.kt:715`) — синхронизация
  картинок с remote MinIO.

## Ловушки и инварианты

1. **`useInList=false` MUST быть у больших полей**: иначе OOM на
   `loadList` (18k+ записей × big binary = crash).
2. **`picture_full` setter `field = ""`**: при попытке сохранить
   base64 в БД — значение игнорируется. Если нужны данные —
   сохраняйте в MinIO и храните URL.
3. **`picture_name` MUST быть уникальным**: используется как filename
   в MinIO. Коллизия = silent overwrite (см. ADR `local-0003`).
4. **Удаление Pictures без удаления файлов в MinIO**: оставит orphan
   файлы. Cleanup job? (TODO Pass 343).

## Известные TODO

- [ ] **`UtilsPictures.kt`** — какие именно форматы поддерживаются
      (JPEG, PNG, WebP?), лимиты размера.
- [ ] **`VkPreviewWarmupClient`** — отдельный компонент.
- [ ] **Cleanup orphan картинок в MinIO** — есть ли job.
- [ ] **CRUD контроллер Pictures** — точные URL'ы, target-aware ли.
- [ ] **Webhook на загрузку новых картинок** — есть ли.

## Код (физическая реализация)

- `karaoke-app/.../model/Pictures.kt`
- `karaoke-app/.../model/PicturesDTO.kt`
- **Отдельного `PicturesController` нет** — CRUD через `StorageController`.
- `karaoke-web/.../services/...` (если есть)
- `webvue3/src/components/Pictures/store.js`
- `webvue3/src/components/Pictures/filter/store.js`
- `webvue3/src/components/Pictures/...` (UI)

## Связь с другими компонентами

- **Storage layer** ([storage domain](../../../domains/storage/domain.md)) —
  `KaraokeStorage` + `KaraokeStorageService` для MinIO-операций.
- **Album** — использует `Pictures.getPictureByName(name=...)` (см. `Album.kt:94, 99`) для превью. **Не FK** в БД, а поиск по `picture_name` (см. KDoc `Album.kt:86`).
- **News** — `News.idPicture` ссылается на `Pictures.id`.

## Changelog

- **Pass 341 P3a** (2026-09-09): Initial detailed. Автор: agent (Karaoke).