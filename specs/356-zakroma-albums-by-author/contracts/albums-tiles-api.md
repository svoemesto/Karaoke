# API Contract: `GET /api/public/authors/{authorId}/albums`

> **Endpoint**: `/api/public/authors/{authorId}/albums`
> **Версия**: спека 356 (Pass 356+)
> **Backend**: `karaoke-web` (Spring Boot)
> **Frontend**: `karaoke-public` (Vue 3)

## Описание

Возвращает список плашек альбомов конкретного автора для публичного сайта. Используется на странице `/zakroma/{author_id}/albums`. Видимость альбомов зависит от роли пользователя (как `/api/public/authors-tiles`).

## Запрос

```
GET /api/public/authors/{authorId}/albums?scope=main
```

### Параметры

| Параметр | Тип | Обязательность | Описание |
|---|---|---|---|
| `authorId` (path) | `Long` | ✅ | Идентификатор автора (`tbl_authors.id`). |
| `scope` (query) | `String` | ❌ (`"main"` по умолчанию) | Скоуп выборки. Зарезервировано для будущих скоупов (например, `"all"` для админки). |

### Заголовки

| Заголовок | Описание |
|---|---|
| `Cookie` (опционально) | Сессия пользователя (для определения роли — гость / редактор). Если нет — пользователь считается гостем. |

## Ответ

### `200 OK`

```json
[
  {
    "id": 42,
    "name": "Zebra",
    "year": 2020,
    "pictureUrl": "http://localhost:9000/karaoke/Author%20Name%20%2F2020%20-%20Zebra%2FAuthor%20Name%20-2020-Zebra.preview.album.png",
    "totalSongCount": 7,
    "readySongCount": 5,
    "albumType": "studio"
  },
  {
    "id": 17,
    "name": "Alpha",
    "year": 1995,
    "pictureUrl": "...",
    "totalSongCount": 12,
    "readySongCount": 12,
    "albumType": "studio"
  }
]
```

### Поля `AlbumTilePublicDto`

| Поле | Тип | Описание |
|---|---|---|
| `id` | `Long` | `tbl_albums.id`. Используется как query-параметр `?album=` на странице песен автора. |
| `name` | `String` | Название альбома. Может быть обрезано в UI многоточием (`...`); полное название — в tooltip. |
| `year` | `Int` | Год выпуска. `0` если неизвестен (UI скрывает поле). |
| `pictureUrl` | `String` | URL обложки альбома в MinIO (200×200). `""` если обложки нет (UI показывает placeholder). |
| `totalSongCount` | `Long` | Общее кол-во песен альбома. Используется в UI для редакторов («N песен»). |
| `readySongCount` | `Long` | Кол-во песен с `id_status >= 6`. Используется в UI для гостей («N готовых»). |
| `albumType` | `String` | Тип альбома: `"studio"` (по умолчанию) / `"live"` / `"compilation"` / `"bootleg"` (см. `AlbumType.dbValue`). |

## Поведение

### Видимость альбомов

| Роль пользователя | `onlyPublished` | Условие `WHERE` |
|---|---|---|
| **Гость** (не авторизован, не premium-подписчик) | `true` | `ready_song_count > 0 AND tbl_albums.skip = false` |
| **Premium-подписчик** | `true` | `ready_song_count > 0 AND tbl_albums.skip = false` (то же, что гость) |
| **Редактор** | `false` | `total_song_count > 0 AND tbl_albums.skip = false` (видит ВСЕ, включая без готовых) |

**Skip-фильтр**: `tbl_albums.skip = true` скрывает альбом полностью для всех ролей (без утечки данных о существовании — FR-016).

### Сортировка

`ORDER BY year ASC NULLS LAST, name ASC` (см. Clarification Q1, A-009). 

- Альбомы с `year = 0` или `NULL` идут последними.
- Внутри одного года — алфавитный тай-брейкер по `name`.

### Кеш

- Серверный L2-кеш `albumsTilesCache` (TTL ≤60с, аналог `authorsTilesCache` в спека 248 + 286).
- Ключ кеша: `(authorId, scope, onlyPublished, includeSkipped)`.
- Invalidation через `consumeDirty()` при sync (как у `tbl_authors`).

### Производительность

- Запрос НЕ использует `GROUP BY tbl_songs.album_id` (FR-015).
- Прямой `SELECT` из `tbl_albums` с JOIN `tbl_authors` (для `skip`-фильтра).
- Денормализованные `total_song_count`/`ready_song_count` — в индексе не нужны (читаются вместе со строкой).

## Ошибки

| HTTP код | Когда | Тело |
|---|---|---|
| `400 Bad Request` | `authorId` не парсится как Long | `{"error": "Invalid authorId"}` |
| `404 Not Found` | Автор не найден (нет строки в `tbl_authors` с `id = authorId`) | `{"error": "Author not found"}` |
| `500 Internal Server Error` | DB-ошибка | `{"error": "Internal error"}` (без деталей) |

## Примеры вызовов (curl)

```bash
# Гость: только альбомы с готовыми песнями
curl -X GET "http://localhost:8080/api/public/authors/42/albums"

# Редактор (с cookies сессии)
curl -X GET "http://localhost:8080/api/public/authors/42/albums" \
  -b "SESSION=<editor-session-cookie>"

# С явным scope
curl -X GET "http://localhost:8080/api/public/authors/42/albums?scope=main"
```

## Backward Compatibility

- Endpoint НОВЫЙ, не ломает существующих клиентов.
- DTO `AlbumTilePublicDto` — новый, не ломает существующие DTO (`AuthorTilePublicDto`, `SongPublicDto`).
- Sync флаги `tbl_albums` без изменений (`sync_albums_push_update_allowed = true`, `sync_albums_pull_update_allowed = true`).

## Связанные документы

- [spec.md § FR-014](../spec.md)
- [data-model.md § DTO AlbumTilePublicDto](../data-model.md)
- [research.md § R10](../research.md)
- [docs/features/zakroma-albums-by-author.md](../../../docs/features/zakroma-albums-by-author.md) — создаётся в Phase implement.
- [specs/286-author-song-counts-cache](../286-author-song-counts-cache/) — прецедент для денормализации.
- [specs/307-special-authors-zakroma-order](../307-special-authors-zakroma-order/) — прецедент для zakroma-tiles UI.