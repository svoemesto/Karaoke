# Contract: GET /api/public/account/playlists/{id}

**Branch**: `190-playlist-play-button-and-stems-cancel` | **Date**: 2026-08-14
**Spec**: [spec.md](spec.md) | **Data model**: [data-model.md](data-model.md)

## Endpoint

```
GET /api/public/account/playlists/{id}
```

**Auth**: SiteAuthInterceptor (как и весь `/api/public/account/*`). Требуется зарегистрированный пользователь (`km_auth_token` в `Authorization: Bearer` или в cookie сессии).

**Параметры**:
- `id` (path): `Long` — идентификатор плейлиста.

**Ответ**:
- `200 OK` — JSON (см. ниже).
- `404 Not Found` — плейлист не существует ИЛИ принадлежит другому пользователю ИЛИ скрыт (не-премиум пытается открыть не-«Избранное» плейлист).
- `403 Forbidden` — тело `{ "error": "premium_required", "benefits": [...] }` (если плейлист недоступен не-премиуму).

## Response JSON schema

```jsonc
{
  "playlist": {
    "id": 42,
    "ownerId": 7,
    "name": "Избранное",           // или имя пользовательского плейлиста
    "favorites": true,             // без is-префикса (Jackson conventions проекта)
    "continuous": true,
    "repeatMode": "none",          // "none" | "all" | "one"
    "shuffle": false,
    "itemsCount": 17
  },
  "items": [
    {
      "id": 101,
      "playlistId": 42,
      "songId": 533,
      "position": 1,
      "muted": false,
      "songName": "Камнем по голове",
      "author": "Король и Шут",
      "album": "Камнем по голове",
      "year": 1996,

      // NEW — превью-картинки (прямой URL на MinIO через nginx-прокси)
      "albumPictureUrl": "/minio/karaoke/...encoded...preview.album.png",
      "authorPictureUrl": "/minio/karaoke/...encoded...preview.author.png"
      // Оба поля: пустая строка "" если файла в MinIO нет (UI покажет плейсхолдер).
    }
    // ...
  ]
}
```

## Изменения относительно текущего контракта

| Поле | Статус | Тип | Семантика |
|---|---|---|---|
| `playlist.itemsCount` | без изменений | int | уже возвращается |
| `items[].albumPictureUrl` | **NEW** | string | прямой URL на MinIO через `/minio/karaoke/<encoded>`; `""` если нет |
| `items[].authorPictureUrl` | **NEW** | string | аналогично |
| `items[].songName/author/album/year` | без изменений | string/int | уже возвращаются |
| `items[].muted` | без изменений | bool | уже возвращается |

## Формат URL

`albumPictureUrl` и `authorPictureUrl` — **прямой URL на MinIO через nginx**, не через Spring-контроллер `/api/public/picture?file=...`. Это:

1. **Минует Spring** — экономит CPU и убирает 302-redirect (Pass 50).
2. **Кэшируется nginx** — `Cache-Control: public, max-age=86400`.
3. **404 → `@error`** — фронт прячет `<img>` по нативному событию, показывает плейсхолдер (никаких запросов на бэк для проверки существования).

Примеры формирования (Kotlin, см. `data-model.md` §Логика формирования URL):

```kotlin
private fun authorPreviewUrl(author: String): String {
    val key = "$author/$author.preview.author.png"
    val encoded = URLEncoder.encode(key, Charsets.UTF_8).replace("+", "%20")
    return "/minio/karaoke/$encoded"
}

private fun albumPreviewUrl(song: Song): String {
    val key = "${song.author}/${song.year} - ${song.album}/" +
              "${song.author} - ${song.year} - ${song.album}.preview.album.png"
    val encoded = URLEncoder.encode(key, Charsets.UTF_8).replace("+", "%20")
    return "/minio/karaoke/$encoded"
}
```

## Backward compatibility

- **Старые клиенты** (без чтения `albumPictureUrl`/`authorPictureUrl`): новые поля просто игнорируются — регрессий нет.
- **Новые клиенты** (karaoke-public после фикса): всегда получают поля с дефолтом `""`.
- **Дефолт** для пустого значения — пустая строка `""`, а не `null` (Constitution §Jackson conventions: `JsonInclude(NON_NULL)` не нужен, дефолт уже непустой).

## Зависимости / контракты-соседи

- **`SitePlaylistDto`** — без изменений (только playlist-метаданные).
- **`SitePlaylistItemDto`** — добавлены 2 поля; остальные без изменений.
- **MinIO bucket `karaoke/`** — файлы превью уже существуют для всех песен/авторов с момента публикации (см. Pass 50 `AuthorTilePublicDto`).

## Test cases (для ручной проверки пользователем — автотестов в CI нет)

| Сценарий | Ожидание |
|---|---|
| GET на «Избранное» обычного пользователя | 200, в каждом item есть `albumPictureUrl` и `authorPictureUrl` (или `""` если нет файла). |
| GET на обычный плейлист не-премиума | 403 `{ "error": "premium_required", ... }`. |
| GET на чужой плейлист | 404 (не 403, чтобы не утекал факт существования). |
| GET с устаревшим/невалидным токеном | 401 (обрабатывается SiteAuthInterceptor). |
| Прямой GET `albumPictureUrl` через nginx | 200 + `Content-Type: image/png` + `Cache-Control: public, max-age=86400`. |
| Прямой GET несуществующего `albumPictureUrl` | 404 (фронт по `@error` показывает плейсхолдер). |
