# Data Model: 190-playlist-play-button-and-stems-cancel

**Branch**: `190-playlist-play-button-and-stems-cancel` | **Date**: 2026-08-14
**Spec**: [spec.md](spec.md) | **Research**: [research.md](research.md)

## Существующие сущности (без изменений схемы БД)

### `tbl_site_playlists` (уже есть)
- `id`, `owner_id`, `name`, `is_favorites`, `sort_order`, `continuous`, `repeat_mode`, `shuffle`, … (см. `SitePlaylist.kt`).
- Миграция **НЕ требуется**.

### `tbl_site_playlist_items` (уже есть)
- `id`, `playlist_id`, `song_id`, `position`, `muted`.
- Миграция **НЕ требуется**.

## Изменения (DTO + entity-зеркало, без миграции)

### `SitePlaylistItemDto` (karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SitePlaylistItemDto.kt)

Добавляются **два опциональных** поля с дефолтом `""`:

```kotlin
data class SitePlaylistItemDto(
    val id: Long = 0,
    val playlistId: Long = 0,
    val songId: Long = 0,
    val position: Long = 0,
    val muted: Boolean = false,
    val songName: String = "",
    val author: String = "",
    val album: String = "",
    val year: Long = 0,
    val albumPictureUrl: String = "",   // NEW: пусто, если обложки нет
    val authorPictureUrl: String = "",  // NEW: пусто, если фото нет
) : Serializable, KaraokeDbTableDto {
    override fun fromDto(database: KaraokeConnection): SitePlaylistItem { ... }
}
```

### `SitePlaylistItem` entity (karaoke-app/.../SitePlaylistItem.kt)

Зеркальные поля без `@KaraokeDbTableField` аннотаций — они не пишутся в БД, а формируются контроллером на лету. Цель: сохранить совместимость с reflection-сериализацией KaraokeDbTable (иначе поля не пройдут через `recordhash`-дифф).

```kotlin
class SitePlaylistItem(...) : KaraokeDbTable(...) {
    @KaraokeDbTableField(name = "id") var id: Long = 0
    @KaraokeDbTableField(name = "playlist_id") var playlistId: Long = 0
    @KaraokeDbTableField(name = "song_id") var songId: Long = 0
    @KaraokeDbTableField(name = "position") var position: Long = 0
    @KaraokeDbTableField(name = "muted") var muted: Boolean = false
    // NEW — НЕ аннотированы, формируются контроллером
    var albumPictureUrl: String = ""
    var authorPictureUrl: String = ""
    // существующие songName/author/album/year — уже НЕ аннотированы (контроллером)
}
```

> **Важно (Constitution II)**: новые поля **не** участвуют в sync (не аннотированы `@KaraokeDbTableField` → не пишутся в БД → не попадают в `recordhash`). Sync-логика не затрагивается.

## Сущность «запись в плейлисте» для клиента (UI state)

В `PlaylistEditView.vue` каждая песня в плейлисте представлена реактивным объектом:

```typescript
interface PlaylistItemView {
  id: number                       // tbl_site_playlist_items.id
  songId: number                   // tbl_songs.id
  position: number
  muted: boolean
  songName: string
  author: string
  album: string
  year: number
  albumPictureUrl: string          // NEW: '' если нет обложки
  authorPictureUrl: string         // NEW: '' если нет фото
  // NEW: локальный state для fallback-плейсхолдера (НЕ от сервера)
  albumPictureFailed?: boolean     // true после `<img @error>` на cover
  authorPictureFailed?: boolean    // true после `<img @error>` на author
}
```

`albumPictureFailed` / `authorPictureFailed` — **только на клиенте**, не персистятся. Сбрасываются при перерендере списка (после `load()` или `reorder`).

## Логика формирования URL на бэкенде

Используется в `PublicPlaylistController.playlistDetail()` при формировании каждого `itemsDto`:

```kotlin
// Формула совпадает с AuthorTilePublicDto.fromAuthorName (Pass 50)
private fun authorPreviewUrl(author: String): String {
    val key = "$author/$author.preview.author.png"
    val encoded = URLEncoder.encode(key, StandardCharsets.UTF_8).replace("+", "%20")
    return "/minio/karaoke/$encoded"   // nginx-прокси, минует Spring (Pass 50)
}

private fun albumPreviewUrl(song: Song): String {
    val key = "${song.author}/${song.year} - ${song.album}/" +
              "${song.author} - ${song.year} - ${song.album}.preview.album.png"
    val encoded = URLEncoder.encode(key, StandardCharsets.UTF_8).replace("+", "%20")
    return "/minio/karaoke/$encoded"
}
```

Оба метода **возвращают строку, даже если файла в MinIO нет** — MinIO/вернёт 404 при запросе, а фронт спрячет `<img>` по `@error`.

> **Не делать** проверку `existsInMinIO` (HEAD через nginx-прокси, как в `PublicPlayerController.pictureAlbumStorageKey`) — это удваивает HTTP-запросы на каждый рендер плейлиста (премиум-плейлист до 200 песен = 400 лишних HEAD). Полагаемся на `@error` fallback.

## Validation rules

| Поле | Правило |
|---|---|
| `albumPictureUrl` | Non-null, default `""`. Если строка пустая или MinIO 404 — UI показывает плейсхолдер. |
| `authorPictureUrl` | Non-null, default `""`. Аналогично. |
| `muted` | Без префикса `is` (Jackson conventions проекта; см. Constitution §Jackson conventions). Уже соблюдено. |

## State transitions (frontend)

`PlaylistItemView` не имеет сложных стейт-машин; только два derived-state флага:
- `albumPictureFailed: false → true` (один раз, на `<img @error>`)
- `authorPictureFailed: false → true` (один раз, на `<img @error>`)

Сброс:
- при `load()` (новые items с сервера);
- при `reorder` (vuedraggable может перерендерить).

Никакой бизнес-логики, никаких race-condition между ними.

## Backwards compatibility

- `SitePlaylistItemDto.albumPictureUrl` / `authorPictureUrl` с дефолтом `""` → старые клиенты, не знающие о полях, работают как раньше (поля просто игнорируются).
- `PublicPlaylistController.playlistDetail()` всегда возвращает эти поля → новые клиенты получают сразу.
- `karaoke-public` (Vue 3) — единственный публичный клиент, тронемый изменениями (см. plan.md Project Structure).

## Sync implications (Constitution III)

- Новые поля **НЕ** аннотированы `@KaraokeDbTableField` → не пишутся в БД → не входят в `recordhash`.
- `SyncTarget.kt` (`site_playlist_items`) — **не меняется**: поля не участвуют в push/pull (нет смысла их синхронизировать, они пересчитываются из `song_id` → URL формируется из формулы).
- Запись `site_playlist_items` в `SyncRegistry.all` уже есть (проверено: `SyncTarget.kt`), ничего не добавляем.

## Migration plan

**Нет миграции БД.** Изменения чисто runtime:
1. Изменить `SitePlaylistItem` (добавить 2 transient-поля).
2. Изменить `SitePlaylistItemDto` (добавить 2 поля с дефолтом).
3. Изменить `PublicPlaylistController.playlistDetail()` (заполнить поля).
4. Изменить `karaoke-public` (UI + плеер).

Все шаги **обратно совместимы** (дефолт `""`).
