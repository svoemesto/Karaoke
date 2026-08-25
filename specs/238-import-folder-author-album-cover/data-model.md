# Data Model: 238 — Импорт из папки: родители только у того же автора + автообложка альбома

**Дата**: 2026-08-25
**Спека**: [spec.md](spec.md)
**Research**: [research.md](research.md)

## Затронутые сущности

Фича **не вводит новых таблиц или колонок** — она работает поверх уже существующей модели данных. Ниже — перечень существующих сущностей, к которым обращается фича, и что именно с ними происходит.

### 1. `tbl_songs` (песня)

**Файл-источник**: `karaoke-db/.../migrations/.../tbl_songs.sql` (читается через `Song.kt`).

| Поле | Тип | Роль в фиче | Изменяется? |
|------|-----|-------------|-------------|
| `id` | `BIGSERIAL` PK | id песни, для которой ищется «родитель» | Нет |
| `song_name` | `TEXT` | нормализуется через `normalizeSongNameForSearch`, сравнивается с кандидатами | Нет |
| `song_author` | `TEXT` | фильтр «того же автора» в `findDuplicateOriginal` | Нет (читается) |
| `source_text` | `TEXT` | фильтр `TRIM(source_text) <> ''` (только песни с непустым текстом — кандидаты в «родители») | Нет (читается) |
| `root_id` | `BIGINT` | записывается id «родителя» (если найден в рамках того же автора) | Да (через `applyDuplicateOriginal`) |

**Связи**:
- `root_id` → `tbl_songs.id` (self-reference: «родитель» — это тоже песня).

**Что делает фича**:
- При импорте новой песни ищет «родителя» **только среди `tbl_songs` с тем же `song_author`** (после удаления fallback'а в `findDuplicateOriginal`). Если найден — копирует `source_text`/`result_text`/`source_markers` и проставляет `root_id`. Если не найден — оставляет `root_id = 0`.

**Validation rules** (уже существуют, не меняются):
- Кандидаты в «родители»: `id <> :newSongId AND TRIM(source_text) <> '' AND LOWER(song_author) = LOWER(:newSongAuthor)` (после фичи).
- Выбор при множестве совпадений: `ORDER BY id ASC LIMIT 1` (стабильно, по наименьшему id).

---

### 2. `tbl_albums` (альбом)

**Файл-источник**: `karaoke-db/.../migrations/.../tbl_albums.sql` (читается через `Album.kt`).

| Поле | Тип | Роль в фиче | Изменяется? |
|------|-----|-------------|-------------|
| `id` | `BIGSERIAL` PK | id альбома, проставляется в `song.album_id` при импорте | Нет |
| `author_id` | `BIGINT` | FK на `tbl_authors` | Нет |
| `year` | `INT` | часть уникального ключа (автор + год + название) | Нет |
| `name` | `TEXT` | часть уникального ключа | Нет |
| `sort_order` | `INT` | сквозная нумерация по автору (см. `AlbumBackfill`) | Нет |

**Связи**:
- `author_id` → `tbl_authors.id`
- `tbl_albums` → `tbl_songs.album_id` (one-to-many: один альбом — много песен).

**Что делает фича**:
- При импорте вызывает `findOrCreateForSongImportWithAutoCover` (новая перегрузка) — логика создания/переиспользования альбома не меняется, но добавляется **после создания** автообложка (если нашёлся ровно один графический файл).

---

### 3. `tbl_pictures` (запись о картинке)

**Файл-источник**: `karaoke-db/.../migrations/.../tbl_pictures.sql` (читается через `Pictures.kt`).

| Поле | Тип | Роль в фиче | Изменяется? |
|------|-----|-------------|-------------|
| `id` | `BIGSERIAL` PK | id записи картинки | Нет |
| `name` | `TEXT` | ключ поиска/создания (`"$author - $year - $album"` для альбома) | Нет (читается/создаётся) |
| `full` | `TEXT` (base64 PNG) | обновляется/создаётся при автообложке | Да (через существующий `Pictures.createNewPicture`) |

**Связи**:
- `name` — это человекочитаемый ключ, **не FK**. По нему идёт `Pictures.getPictureByName(...)`.

**Что делает фича**:
- После успешного сохранения `LogoAlbum.png` на диск вызывает `song.pictureAlbum` (существующий getter) — он либо обновляет существующую запись (`full = base64 нового PNG`), либо создаёт новую. Запись попадает в локальное и удалённое (MinIO) хранилище **тем же путём**, что и ручная обложка через `saveAlbumCover`.

---

### 4. Файловая система (вне БД)

| Сущность | Путь | Роль в фиче |
|----------|------|-------------|
| Папка альбома (`rootFolder`) | `<song.parent>` | содержит файлы песен + потенциально `cover.jpg`/`front.png`/... — кандидат на обложку |
| `LogoAlbum.png` (исходник) | в папке, заданной пользователем | произвольное имя, расширение `jpg`/`jpeg`/`png`/`webp`/`bmp`/`tiff` |
| `LogoAlbum.png` (результат) | `$rootFolder/LogoAlbum.png` | 400×400 PNG (кадрированный по короткой стороне) |
| `LogoAlbum.preview.png` | `$rootFolder/LogoAlbum.preview.png` | превью (логика превью через существующую `Pictures.preview`) |

**Что делает фича**:
- Читает **плоский** список файлов в `rootFolder` (не рекурсивно — иначе для много-дискового альбома попадут файлы из подпапок других дисков).
- Фильтрует по расширению (без учёта регистра), исключает скрытые (`name.startsWith(".")`).
- Если ровно один кандидат — `cropCenterSquareAndResize(bytes, 400)` → `ImageIO.write(..., "png", ...)` в `$rootFolder/LogoAlbum.png` + `chmod 666`.
- Если 0 или ≥2 кандидатов — обложка НЕ создаётся (FR-008, FR-009, см. Clarifications Q3 для случая `LogoAlbum.png + cover.jpg`).

---

## Новые компоненты в коде (не данные, а структура)

### Новый helper: `Album.applyAutoAlbumCoverFromFolder`

**Файл**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Album.kt` (companion object).

**Сигнатура** (черновая):
```kotlin
internal fun applyAutoAlbumCoverFromFolder(
    rootFolder: String,
    author: String,
    year: Int,
    album: String,
    song: Song,                    // для album.albumId и pictures-логики
    database: KaraokeConnection,
    storageService: KaraokeStorageService,
    storageApiClient: StorageApiClient,
): Boolean
```

Возвращает `true` если обложка создана/обновлена, `false` если не создана (нет файлов / несколько / ошибка чтения).

**Алгоритм**:
1. `File(rootFolder).listFiles()` — плоский обход.
2. Фильтр по расширению (`jpg|jpeg|png|webp|bmp|tiff`), не скрытые.
3. Если кандидатов ≠ 1 — return `false`.
4. Прочитать байты кандидата, `cropCenterSquareAndResize(bytes, 400)` → `BufferedImage?`.
5. Если `null` — return `false` (битый файл — FR-010).
6. `ImageIO.write(...)` → `$rootFolder/LogoAlbum.png`, `chmod 666`.
7. `song.pictureAlbum` — обновит/создаст `Pictures`, попадёт в MinIO.

### Новая перегрузка: `Album.findOrCreateForSongImportWithAutoCover`

**Файл**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Album.kt` (companion object).

**Сигнатура** (черновая):
```kotlin
fun findOrCreateForSongImportWithAutoCover(
    authorName: String,
    year: Int,
    albumName: String,
    rootFolder: String,
    song: Song,
    database: KaraokeConnection,
    storageService: KaraokeStorageService,
    storageApiClient: StorageApiClient,
): Album?
```

**Алгоритм**:
1. Вызвать существующую `findOrCreateForSongImport(...)` → получаем `Album?`.
2. Если `Album == null` (пустое имя альбома — сингл) — return `null`.
3. Если альбом **только что создан** (определяется через возвращаемое значение существующей функции — модифицируем её сигнатуру или используем эвристику «свежести», например, проверка `album.id` через сравнение с max(id) до инкремента).
4. Если «свежий» — вызвать `applyAutoAlbumCoverFromFolder(...)`.
5. Return `album`.

**Как определить «свежесть» альбома**: **ввести новую перегрузку** `findOrCreateForSongImportRaw(...)`, возвращающая `Pair<Album?, Boolean>` (`isJustCreated`). Используется внутри `findOrCreateForSongImportWithAutoCover`. Существующая `findOrCreateForSongImport` остаётся без изменений (для `AlbumBackfill` и любых других существующих вызовов).

### Изменение в `Song.createFromPath`

**Файл**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt:8064`.

**До**:
```kotlin
song.albumId = Album.findOrCreateForSongImport(
    authorName = authorStr,
    year = yearStr.toIntOrNull() ?: 0,
    albumName = albumStr,
    database = database,
    storageService = storageService,
    storageApiClient = storageApiClient,
)?.id
song.saveToDb()
```

**После**:
```kotlin
val createdAlbum = Album.findOrCreateForSongImportWithAutoCover(
    authorName = authorStr,
    year = yearStr.toIntOrNull() ?: 0,
    albumName = albumStr,
    rootFolder = rootFolder,
    song = song,
    database = database,
    storageService = storageService,
    storageApiClient = storageApiClient,
)
song.albumId = createdAlbum?.id
song.saveToDb()
```

---

## Что НЕ меняется в данных

- **Структура таблиц** (`tbl_songs`, `tbl_albums`, `tbl_pictures`, `tbl_authors`) — никаких миграций.
- **Триггеры `recordhash`** (Constitution § III) — не затрагиваются, поскольку не меняются колонки и не добавляются новые сущности.
- **Sync-флаги** (8 флагов `sync_<key>_<push|pull>_...`) — не добавляются, не меняются.
- **Существующие записи** в БД — никаких backfill-операций; фича применяется только при создании новых альбомов через импорт из папки.

## Совместимость

- Все существующие вызовы `findOrCreateForSongImport` (например, `AlbumBackfill.kt:114`) продолжают работать без изменений.
- Существующая логика `findDuplicateOriginal` остаётся в коде и доступна для других путей (хотя теперь `doCreateFromFolder` будет вызывать её эффект через урезанный fallback — изменение только внутри функции).
- `AlbumBackfill` (одноразовая миграция из песен в альбомы) НЕ запускает автообложку — намеренно (он для уже существующих песен, а не для новых альбомов; пользователь явно просил «при создании альбома»).