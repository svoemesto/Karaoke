# Data Model: Корректный прогресс загрузки песен при открытии альбома автора

## Затронутая сущность

### `Album` (`tbl_albums`) — только чтение

| Колонка | Тип | Роль в фиче |
|---|---|---|
| `id` | BIGINT PK | ключ `albumId` из query `?albumId=` |
| `total_song_count` | BIGINT | знаменатель прогресса для редактора |
| `ready_song_count` | BIGINT | знаменатель прогресса для гостя (`id_status >= 6`) |

Источник: триггер `trg_tbl_songs_update_album_counts` (миграция `49_albums_song_counts.sql`), тот же счётчик, что на плашке альбома (`AlbumTilePublicDto.totalSongCount` / `readySongCount`). Совпадение с плашкой — целевой инвариант FR-002.

## Новый тип (karaoke-web)

### `ZakromaStreamProgress.AlbumCounters`

```kotlin
data class AlbumCounters(
    val readySongCount: Long,   // = Album.readySongCount
    val totalSongCount: Long,   // = Album.totalSongCount
)
```

Промежуточное представление, чтобы чистая функция `resolveExpectedCount` не зависела от `Album` (модель karaoke-app с БД/storage-зависимостями) и была offline-тестируемой.

## Поток данных

```
?albumId=N
   │
   ▼
PublicApiController.zakromaStream
   │  Album.getAlbumById(N) ──► AlbumCounters(ready, total)
   │
   ▼
ZakromaStreamProgress.resolveExpectedCount(albumId, album, onlyPublished, provided, fallback)
   │
   ▼
ZakromaStreamMessageDto.meta(author, expectedCount)  ── NDJSON ──►  frontend progress bar
```

## Инварианты

1. `albumId != null` → знаменатель строго из альбома (гость: ready, редактор: total); `album == null` → `0`.
2. `albumId == null` → без изменений спеки 181 (provided > 0 → provided; иначе author-fallback).
3. `done.actualCount` не меняется — фактическое число отправленных песен.
4. Ключ dedup стора остаётся `author:albumId` (Pass 359) — знаменатели альбомов не смешиваются.
