# Contract: `Album.findOrCreateForSongImportWithAutoCover` (новый helper)

**Дата**: 2026-08-25
**Спека**: [../spec.md](../spec.md)
**Data Model**: [../data-model.md](../data-model.md)

## Назначение

Внутренний Kotlin-helper в `Album.kt` (companion object). Вызывается из `Song.createFromPath` сразу после парсинга имени файла и до `song.saveToDb()`. Заменяет прямой вызов `Album.findOrCreateForSongImport(...)` для пути импорта из папки, добавляя автоматическое создание обложки альбома из графического файла в `rootFolder` (если альбом создан впервые).

## Сигнатура

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

**Параметры**:
- `authorName` — строка автора, распарсенная из имени файла.
- `year` — год альбома (Int, 0 если не распарсился).
- `albumName` — название альбома, распарсенное из имени папки.
- `rootFolder` — путь к папке альбома (parent-каталог файла песни; для много-дискового альбома — это `Album/CD1/`, `Album/CD2/` и т. п.).
- `song` — создаваемая песня (нужна для `song.pictureAlbum` логики — recordhash и `pictureNameAlbum`).
- `database`, `storageService`, `storageApiClient` — стандартные зависимости.

**Возврат**: `Album?` (новый или переиспользованный альбом), `null` если `albumName.isBlank()` (сингл без альбома).

## Алгоритм

1. Вызвать существующую `findOrCreateForSongImportRaw(authorName, year, albumName, database, storageService, storageApiClient): Pair<Album?, Boolean>` — возвращает пару `(album, isJustCreated)`. Существующая `findOrCreateForSongImport` остаётся без изменений для обратной совместимости (`AlbumBackfill`).
2. Если `album == null` — вернуть `null` (без автообложки).
3. Если `!isJustCreated` (альбом уже был в БД) — вернуть `album` **без** вызова автообложки (FR-009: существующие обложки не перезатираются).
4. Если `isJustCreated` — вызвать `applyAutoAlbumCoverFromFolder(rootFolder, authorName, year, albumName, song, database, storageService, storageApiClient): Boolean`. Возвращаемое значение игнорируется (не блокирует создание альбома).
5. Вернуть `album`.

## Поведенческие гарантии

- **Идемпотентность**: повторный вызов для **уже существующего** альбома НЕ создаёт обложку повторно (см. п. 3).
- **Безопасность при ошибке**: если `applyAutoAlbumCoverFromFolder` бросит исключение или вернёт `false` (битый файл, нет файлов, много файлов) — альбом всё равно создаётся (обложка — опциональная фича, см. FR-010).
- **Только новый альбом**: для существующих альбомов функция эквивалентна `findOrCreateForSongImport` (никакой побочной логики).

## Что НЕ делает

- НЕ изменяет API/HTTP-контракты.
- НЕ трогает `tbl_pictures` через прямой SQL — только через `song.pictureAlbum` (т.е. через ту же логику, что и `saveAlbumCover`).
- НЕ пересоздаёт обложки существующих альбомов.
- НЕ лезет в MinIO напрямую — только через `song.pictureAlbum` → `Pictures.createNewPicture`.

## Изменения в существующем коде

| Файл | Что меняется |
|------|--------------|
| `karaoke-app/.../model/Album.kt` | Добавляются три новых companion-метода: `findOrCreateForSongImportRaw` (внутренний helper, возвращает `Pair<Album?, Boolean>` с `isJustCreated`), `applyAutoAlbumCoverFromFolder` (логика автообложки) и публичный `findOrCreateForSongImportWithAutoCover` (оркестратор: вызывает `Raw`, при `isJustCreated=true` вызывает `applyAutoAlbumCoverFromFolder`). **Существующая `findOrCreateForSongImport` остаётся без изменений** — её использует `AlbumBackfill.kt:114` и любые другие существующие вызовы; никаких модификаций сигнатуры. Выбор: новая перегрузка Raw, а не модификация существующей — минимальный blast radius (см. research.md R5). |
| `karaoke-app/.../model/Song.kt` | `createFromPath:8064` — заменить вызов `Album.findOrCreateForSongImport(...)` на `Album.findOrCreateForSongImportWithAutoCover(...)`, передав `rootFolder` и `song`. |

## Совместимость

- `AlbumBackfill` (`AlbumBackfill.kt:114`) продолжает использовать старую `findOrCreateForSongImport` — автообложка НЕ запускается при backfill (намеренно, см. A-007).
- `ApiController.saveAlbumCover` (ручной путь через модалку) — без изменений.
- `MainController.doCreateFromFolder` — без изменений в сигнатуре, переиспользует обновлённую `Song.createFromPath` (см. research.md R1, R5).