# API Contract: `/api/public/authors-tiles`

**Feature**: 286-author-song-counts-cache
**Date**: 2026-08-31
**Status**: Phase 1 — контракт стабилен (без breaking changes)

## Endpoint

```
GET /api/public/authors-tiles?scope={scope}
```

### Query parameters

| Name | Type | Required | Default | Description |
|------|------|----------|---------|-------------|
| `scope` | `string` | no | `"main"` | Один из: `"main"` (не спецзаказ), `"special"` (только спецзаказ), `"all"` (все). |
| `anonId` | `string` | no | `""` | Анонимный ID посетителя (см. спеку 187). |
| `referrer` | `string` | no | `""` | Referrer первого захода (см. спеку 187). |

### Headers

| Header | Required | Description |
|--------|----------|-------------|
| `Authorization` | no | Bearer-токен авторизованного пользователя (опционально). |

### Authentication

- Не требуется для анонимов.
- Если токен валиден и `isEditor = true` → фильтр по `id_status` снимается (см. спеку 017 `editor-status-bypass`), `songCount` = `total_songs_count`.
- Иначе → `songCount` = `ready_songs_count`.

## Response

`200 OK` — JSON-массив объектов:

```json
[
  {
    "id": 42,
    "author": "Имя автора",
    "authorPictureUrl": "/minio/karaoke/%D0%98%D0%BC%D1%8F%20%D0%B0%D0%B2%D1%82%D0%BE%D1%80%D0%B0/%D0%98%D0%BC%D1%8F%20%D0%B0%D0%B2%D1%82%D0%BE%D1%80%D0%B0.preview.author.png",
    "songCount": 42,
    "isSpecialOrder": false
  },
  ...
]
```

### Поля

| Поле | Тип | Описание |
|------|-----|----------|
| `id` | `number` (long) | ID автора в `tbl_authors`. Используется для URL `/zakroma/:id(\\d+)`. |
| `author` | `string` | Имя автора. |
| `authorPictureUrl` | `string` | URL превью-картинки через nginx/MinIO (см. спеку 187). |
| `songCount` | `number` (long) | Количество песен: `ready_songs_count` для анонимов, `total_songs_count` для редактора. |
| `isSpecialOrder` | `boolean` | `true` для спецзаказных авторов (1-2 песни). Поле сериализуется через `@JsonProperty("isSpecialOrder")` (Constitution о Jackson). |

### Фильтрация на стороне бэка

- Авторы с `songCount = 0` (для своего scope) НЕ возвращаются.
- Skip-авторы (`tbl_authors.skip = true`) НЕ возвращаются (UI-фильтр).

## Errors

| Code | When |
|------|------|
| `200` | Всегда при валидном запросе (пустой массив — допустимый ответ, если в БД нет подходящих авторов). |
| `5xx` | Ошибка БД или внутренняя ошибка сервера. |

## Caching

- L2-кеш `authorsTilesCache` (см. спеку 248, TTL=30 мин, key=`scope:onlyPublished`).
- Инвалидация через `StatBySong.consumeDirty()` (см. `research.md#r3`).
- Триггер `notifyStatsDirty` вызывается при:
  - изменении `id_status` песни (готово/не готово) — **новое в этой фиче**;
  - изменении `free` флага песни (существующее);
  - sync oneclick с пушем песен (существующее).

## Breaking changes

**Нет.** Формат ответа не меняется. `songCount` численно совпадает с предыдущей реализацией для всех scope'ов.

## Examples

### Запрос (аноним, scope=main)

```http
GET /api/public/authors-tiles?scope=main&anonId=abc123
Authorization: (нет)
```

### Ответ

```json
[
  {
    "id": 42,
    "author": "Beatles",
    "authorPictureUrl": "/minio/karaoke/Beatles/Beatles.preview.author.png",
    "songCount": 38,
    "isSpecialOrder": false
  },
  ...
]
```

### Запрос (редактор, scope=main)

```http
GET /api/public/authors-tiles?scope=main&anonId=def456
Authorization: Bearer eyJ...
```

### Ответ (тот же набор, но `songCount` = `total_songs_count`)

```json
[
  {
    "id": 42,
    "author": "Beatles",
    "authorPictureUrl": "/minio/karaoke/Beatles/Beatles.preview.author.png",
    "songCount": 42,
    "isSpecialOrder": false
  },
  ...
]
```

## Тестовая проверка

После применения фичи:

1. `curl -s 'http://localhost:8080/api/public/authors-tiles?scope=main'` → массив с числами, **идентичными** предыдущей реализации.
2. Изменить статус одной песни (`id_status: 5 → 6`) → следующий запрос показывает `+1` в `ready_songs_count` для автора этой песни (для анонимов).
3. `karaoke-web` лог НЕ содержит SQL `group by song_author` (см. SC-001 спеки).

## Связанные контракты

- **DTO**: `AuthorTilePublicDto` — `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/AuthorTilePublicDto.kt`.
- **Внутренний API**: `POST /api/internal/stats/mark-dirty` — `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/InternalStatsController.kt:44` (для инвалидации кэша с karaoke-app).
- **Sync**: `AuthorsSyncTarget` — `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/sync/SyncTarget.kt:281`.