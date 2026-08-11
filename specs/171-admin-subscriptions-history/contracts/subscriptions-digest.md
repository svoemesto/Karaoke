# API Contract: `POST /api/subscriptions/digest`

**Feature**: 171-admin-subscriptions-history
**Date**: 2026-08-11
**Status**: Phase 1 — contracts

> Админ-эндпоинт для глобального списка подписок из `tbl_subscriptions`. Аналог `POST /api/siteusers/digest` и `POST /api/siteplaylists/digest`.

## Endpoint

| | |
|---|---|
| **Method** | `POST` |
| **Path** | `/api/subscriptions/digest` |
| **Controller** | `SubscriptionsController.kt` (новый, в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/`) |
| **Auth** | `permitAll()` (admin SPA, нет авторизации — конституция V) |

## Request

### Параметры (`application/x-www-form-urlencoded` или JSON)

| Имя | Тип | Обязательный | Default | Описание |
|---|---|---|---|---|
| `target` | `String` | YES | `local` | `'local'` или `'remote'` — выбор БД через `Connection.local()` / `Connection.remote()` |
| `page` | `Int` | NO | `1` | Номер страницы (1-based) |
| `pageSize` | `Int` | NO | `25` | Размер страницы (clamp: 1..100) |
| `filterScope` | `String` | NO | `null` (любой) | `'SONG'` / `'SITE'` / null |
| `filterStatus` | `String` | NO | `null` (любой) | `'PAID'` / `'PENDING'` / `'CREATED'` / `'FAILED'` / `'REFUNDED'` / `'CANCELED'` / null |
| `filterUserId` | `Long` | NO | `null` | Фильтр по `tbl_subscriptions.site_user_id` |
| `filterSongId` | `Long` | NO | `null` | Фильтр по `tbl_subscriptions.id_song` |
| `filterCreatedFrom` | `String` | NO | `null` | ISO timestamp `2026-08-01T00:00:00Z` |
| `filterCreatedTo` | `String` | NO | `null` | ISO timestamp |
| `sortBy` | `String` | NO | `created_at` | Колонка сортировки |
| `sortDir` | `String` | NO | `DESC` | `ASC` / `DESC` |

## Response

### `200 OK`

```json
{
  "items": [
    {
      "id": 12345,
      "siteUserId": 678,
      "scope": "SONG",
      "idSong": 98765,
      "tariffId": null,
      "periodDays": 0,
      "basePrice": 150.00,
      "discount": 0.00,
      "finalPrice": 150.00,
      "promoApplied": "",
      "status": "PAID",
      "autoRenew": false,
      "createdAt": "2026-08-10T14:23:45Z",
      "paidAt": "2026-08-10T14:25:01Z",
      "orderId": null,
      "userEmail": "user@example.com",
      "userDisplayName": "Иван Иванов",
      "songName": "Песня о далекой Родине",
      "tariffName": null
    },
    {
      "id": 12346,
      "siteUserId": 678,
      "scope": "SITE",
      "idSong": null,
      "tariffId": 5,
      "periodDays": 30,
      "basePrice": 300.00,
      "discount": 50.00,
      "finalPrice": 250.00,
      "promoApplied": "FIRST_MONTH",
      "status": "PAID",
      "autoRenew": true,
      "createdAt": "2026-08-09T10:00:00Z",
      "paidAt": "2026-08-09T10:05:12Z",
      "orderId": "cart-2026-08-09-678",
      "userEmail": "user@example.com",
      "userDisplayName": "Иван Иванов",
      "songName": null,
      "tariffName": "Месяц премиума"
    }
  ],
  "totalCount": 12345,
  "page": 1,
  "pageSize": 25
}
```

### `400 Bad Request`

```json
{
  "errorCode": "invalidTarget",
  "message": "target must be 'local' or 'remote'"
}
```

или

```json
{
  "errorCode": "invalidFilter",
  "message": "filterScope must be 'SONG', 'SITE' or null"
}
```

### `500 Internal Server Error`

```json
{
  "errorCode": "internal",
  "message": "<details>"
}
```

## SQL (псевдокод для реализации)

```sql
-- 1. WHERE-блок собирается динамически из непустых фильтров
WHERE 1=1
  [AND scope = :filterScope]
  [AND status = :filterStatus]
  [AND site_user_id = :filterUserId]
  [AND id_song = :filterSongId]
  [AND created_at >= :filterCreatedFrom]
  [AND created_at <= :filterCreatedTo]

-- 2. Основной запрос с JOIN-ами
SELECT
  s.id, s.site_user_id, s.scope, s.id_song, s.tariff_id,
  s.period_days, s.base_price, s.discount, s.final_price, s.promo_applied,
  s.status, s.auto_renew, s.created_at, s.paid_at, s.order_id,
  u.email, u.display_name,
  song.song_name,
  t.name AS tariff_name
FROM tbl_subscriptions s
LEFT JOIN tbl_site_users u ON u.id = s.site_user_id
LEFT JOIN tbl_songs song ON s.scope = 'SONG' AND song.id = s.id_song
LEFT JOIN tbl_tariffs t ON s.scope = 'SITE' AND t.id = s.tariff_id
WHERE <dynamic_where>
ORDER BY s.created_at DESC
LIMIT :pageSize OFFSET :(page - 1) * pageSize;

-- 3. Total count для пагинации
SELECT COUNT(*) FROM tbl_subscriptions s WHERE <dynamic_where>;
```

## Реализация на бэкенде (псевдокод Kotlin)

```kotlin
@RestController
@RequestMapping("/api/subscriptions")
class SubscriptionsController {
    @PostMapping("/digest")
    fun digest(
        @RequestParam target: String,
        @RequestParam(required = false, defaultValue = "1") page: Int,
        @RequestParam(required = false, defaultValue = "25") pageSize: Int,
        @RequestParam(required = false) filterScope: String?,
        @RequestParam(required = false) filterStatus: String?,
        @RequestParam(required = false) filterUserId: Long?,
        @RequestParam(required = false) filterSongId: Long?,
        @RequestParam(required = false) filterCreatedFrom: String?,
        @RequestParam(required = false) filterCreatedTo: String?,
    ): Map<String, Any> {
        // 1. Validate target
        val database = when (target) {
            "local" -> Connection.local()
            "remote" -> Connection.remote()
            else -> throw IllegalArgumentException("target must be 'local' or 'remote'")
        }
        // 2. Build dynamic WHERE list
        val whereList = mutableListOf<String>()
        filterScope?.let { whereList.add("scope='$it'") }
        filterStatus?.let { whereList.add("status='$it'") }
        filterUserId?.let { whereList.add("site_user_id=$it") }
        filterSongId?.let { whereList.add("id_song=$it") }
        filterCreatedFrom?.let { whereList.add("created_at >= '$it'") }
        filterCreatedTo?.let { whereList.add("created_at <= '$it'") }
        // 3. Load (via KaraokeDbTable.loadList + сырой SQL для JOIN-ов)
        // 4. Return {items, totalCount, page, pageSize}
    }
}
```

> **Примечание**: конкретный SQL с JOIN-ами — на `Connection.getConnection()` + `prepareStatement` (как `ListeningHistory.getForUser`).

## Vuex-store (frontend, псевдокод)

```javascript
// webvue3/src/components/Subscriptions/store.js
export default {
  state: {
    subscriptionsDigest: [],
    subscriptionsDigestTotalCount: 0,
    subscriptionsDigestIsLoading: false,
    subscriptionsTarget: 'local',
    // Персистентность страницы (FR-006)
    subscriptionsTableCurrentPage: 1,
    subscriptionsFilter: {
      scope: null,
      status: null,
      userId: null,
      songId: null,
      createdFrom: null,
      createdTo: null,
    },
  },
  actions: {
    loadSubscriptionsDigest(ctx, params = {}) {
      const fullParams = Object.assign({}, params, { target: ctx.state.subscriptionsTarget })
      // POST /api/subscriptions/digest
      // commit 'setSubscriptionsDigest', 'setSubscriptionsDigestTotalCount'
    },
  },
}
```

## Error Handling (frontend)

- `400 invalidTarget` → toast «Некорректный target», таблица не обновляется.
- `500 internal` → toast «Не удалось загрузить подписки», таблица показывает предыдущие данные + сообщение «Ошибка загрузки».
- Сетевая ошибка → тост «Нет связи с сервером» + retry-кнопка.

## Связанные документы

- [data-model.md](../data-model.md) — Entity 1: Subscription.
- [quickstart.md](../quickstart.md) — сценарий проверки «Подписки».
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Subscription.kt` — backend-модель.
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/SiteUsersController.kt` — образец admin-controller.
