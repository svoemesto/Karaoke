# API Contract: `POST /api/listeninghistory/digest`

**Feature**: 171-admin-subscriptions-history
**Date**: 2026-08-11
**Status**: Phase 1 — contracts

> Админ-эндпоинт для глобального списка истории прослушиваний из `tbl_listening_history` с JOIN к `tbl_songs`.

## Endpoint

| | |
|---|---|
| **Method** | `POST` |
| **Path** | `/api/listeninghistory/digest` |
| **Controller** | `ListeningHistoryController.kt` (новый) |
| **Auth** | `permitAll()` |

## Request

### Параметры

| Имя | Тип | Обязательный | Default | Описание |
|---|---|---|---|---|
| `target` | `String` | YES | `local` | `'local'` / `'remote'` |
| `page` | `Int` | NO | `1` | Номер страницы |
| `pageSize` | `Int` | NO | `500` | Размер страницы (clamp: 1..1000) |
| `filterUserId` | `Long` | NO | `null` | Фильтр по `tbl_listening_history.site_user_id` |
| `filterSongId` | `Long` | NO | `null` | Фильтр по `tbl_listening_history.song_id` |
| `filterLastPlayedFrom` | `String` | NO | `null` | ISO timestamp |
| `filterLastPlayedTo` | `String` | NO | `null` | ISO timestamp |
| `sortBy` | `String` | NO | `last_played_at` | |
| `sortDir` | `String` | NO | `DESC` | |

## Response

### `200 OK`

```json
{
  "items": [
    {
      "id": 54321,
      "siteUserId": 678,
      "songId": 98765,
      "playCount": 7,
      "lastPlayedAt": "2026-08-11T08:14:22Z",
      "userEmail": "user@example.com",
      "userDisplayName": "Иван Иванов",
      "songName": "Песня о далекой Родине",
      "songAuthor": "Исполнитель",
      "songAlbum": "Альбом 1975"
    }
  ],
  "totalCount": 50123,
  "page": 1,
  "pageSize": 500
}
```

### `400 / 500` — те же форматы, что в [subscriptions-digest.md](./subscriptions-digest.md#400-bad-request)

## SQL (псевдокод)

```sql
-- 1. WHERE + SKIP-фильтр (из публичного getForUser)
WHERE 1=1
  AND (s.tags IS NULL OR NOT ('SKIP' = ANY(string_to_array(upper(coalesce(s.tags,'')), ' '))))
  [AND h.site_user_id = :filterUserId]
  [AND h.song_id = :filterSongId]
  [AND h.last_played_at >= :filterLastPlayedFrom]
  [AND h.last_played_at <= :filterLastPlayedTo]

-- 2. Main query
SELECT
  h.id, h.site_user_id, h.song_id, h.play_count, h.last_played_at,
  u.email, u.display_name,
  s.song_name, s.song_author, s.song_album
FROM tbl_listening_history h
JOIN tbl_songs s ON s.id = h.song_id
LEFT JOIN tbl_site_users u ON u.id = h.site_user_id
WHERE <dynamic_where>
ORDER BY h.last_played_at DESC
LIMIT :pageSize OFFSET :(page - 1) * pageSize;

-- 3. Count
SELECT COUNT(*) FROM tbl_listening_history h
JOIN tbl_songs s ON s.id = h.song_id
WHERE <dynamic_where>;
```

## Реализация (паттерн из `ListeningHistory.getForUser`)

```kotlin
fun getForAdminDigest(
    filterUserId: Long?,
    filterSongId: Long?,
    filterLastPlayedFrom: String?,
    filterLastPlayedTo: String?,
    page: Int,
    pageSize: Int,
    database: KaraokeConnection,
): Pair<List<ListeningHistoryEntry>, Int> {
    val connection = database.getConnection() ?: return emptyList<ListeningHistoryEntry>() to 0
    val whereList = mutableListOf<String>()
    // SKIP-фильтр (ОБЯЗАТЕЛЬНО)
    whereList.add("(s.tags IS NULL OR NOT ('SKIP' = ANY(string_to_array(upper(coalesce(s.tags,'')), ' '))))")
    filterUserId?.let { whereList.add("h.site_user_id = $it") }
    filterSongId?.let { whereList.add("h.song_id = $it") }
    filterLastPlayedFrom?.let { whereList.add("h.last_played_at >= '$it'") }
    filterLastPlayedTo?.let { whereList.add("h.last_played_at <= '$it'") }
    val where = whereList.joinToString(" AND ")
    // ... executeQuery, prepareStatement ...
    // return (items, totalCount)
}
```

## Vuex-store (frontend, псевдокод)

```javascript
// webvue3/src/components/ListeningHistory/store.js
export default {
  state: {
    listeningHistoryDigest: [],
    listeningHistoryDigestTotalCount: 0,
    listeningHistoryDigestIsLoading: false,
    listeningHistoryTarget: 'local',
    listeningHistoryTableCurrentPage: 1,
    listeningHistoryFilter: {
      userId: null,
      songId: null,
      lastPlayedFrom: null,
      lastPlayedTo: null,
    },
  },
  // ... actions: loadListeningHistoryDigest
}
```

## Footer UI

> В админ-таблице под пагинацией — `«Показано X из Y»` если `Y > X`. Здесь `X = page * pageSize`, `Y = totalCount`.

Пример: «Показано 500 из 50 123» (если первая страница из 500 на 50k записей).

## Связанные документы

- [data-model.md](../data-model.md) — Entity 2: ListeningHistory.
- [quickstart.md](../quickstart.md) — сценарий проверки «История».
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/ListeningHistory.kt:113-161` — существующий `getForUser` (образец для JOIN-а и SKIP-фильтра).
