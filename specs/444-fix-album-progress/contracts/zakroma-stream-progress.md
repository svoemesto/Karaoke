# Contract: `meta.expectedCount` для `/api/public/zakroma/stream`

**Feature**: [spec.md](./spec.md) | **Issue**: OpenProject #179 | **Branch**: `444-fix-album-progress`

## Endpoint

`GET /api/public/zakroma/stream?author={name}[&albumId={id}][&expectedCount={n}]`
`produces = application/x-ndjson`

## Изменение контракта

Меняется **только значение** поля `expectedCount` в первом NDJSON-сообщении `meta`. Wire-формат, набор типов сообщений и правило sequential grouping не меняются.

| Условие | `expectedCount` (было) | `expectedCount` (стало) |
|---|---|---|
| `albumId` задан | доверял `expectedCount` фронта (число песен **автора**) | `ready_song_count` (гость) / `total_song_count` (редактор) **альбома**; `0` если альбом не найден |
| `albumId` не задан, `expectedCount > 0` | `expectedCount` фронта | без изменений |
| `albumId` не задан, иначе | `Song.loadAuthorSongCounts(author)[onlyPublished]` | без изменений |

## Примеры

### Гость, альбом с 10 песнями, готовы 4

```json
{"type":"meta","author":"Машина Времени","expectedCount":4}
```

### Редактор, тот же альбом

```json
{"type":"meta","author":"Машина Времени","expectedCount":10}
```

### Без `albumId` (все песни автора)

```json
{"type":"meta","author":"Машина Времени","expectedCount":2485}
```

### `albumId` не найден

```json
{"type":"meta","author":"Машина Времени","expectedCount":0}
```

## Совместимость

- Старый фронт: не передаёт `albumId` → поведение не меняется.
- Новый фронт: при `?albumId=` не передаёт авторский `expectedCount` (шлёт `undefined`); сервер авторитетно считает по альбому.

## Frontend-контракт

`ZakromaView.tryStartZakromaStream()` (`karaoke-public`):
`expectedCount = selectedAlbumId != null ? undefined : tile.songCount || undefined`.
