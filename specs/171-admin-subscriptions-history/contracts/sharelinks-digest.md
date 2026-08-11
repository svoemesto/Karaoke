# API Contract: `POST /api/sharelinks/digest`

**Feature**: 171-admin-subscriptions-history
**Date**: 2026-08-11
**Status**: Phase 1 — contracts

> Админ-эндпоинт для глобального списка временных share-ссылок из `tbl_song_share_links` с JOIN к `tbl_songs` и `tbl_site_users`. Действие «Отозвать» — через существующий `POST /api/siteusers/share/links/revoke`.

## Endpoint 1: digest

| | |
|---|---|
| **Method** | `POST` |
| **Path** | `/api/sharelinks/digest` |
| **Controller** | `ShareLinksAdminController.kt` (новый) |
| **Auth** | `permitAll()` |

### Request

| Имя | Тип | Обязательный | Default | Описание |
|---|---|---|---|---|
| `target` | `String` | YES | `local` | `'local'` / `'remote'` |
| `page` | `Int` | NO | `1` | |
| `pageSize` | `Int` | NO | `25` | clamp: 1..100 |
| `filterActiveOnly` | `Boolean` | NO | `false` | Если `true` — только `active=true AND expires_at > now()` |
| `filterOwnerId` | `Long` | NO | `null` | Фильтр по `tbl_song_share_links.owner_site_user_id` |
| `filterSongId` | `Long` | NO | `null` | Фильтр по `tbl_song_share_links.song_id` |
| `filterCreatedFrom` | `String` | NO | `null` | ISO timestamp |
| `filterCreatedTo` | `String` | NO | `null` | ISO timestamp |
| `sortBy` | `String` | NO | `created_at` | |
| `sortDir` | `String` | NO | `DESC` | |

### Response `200 OK`

```json
{
  "items": [
    {
      "id": 777,
      "songId": 98765,
      "ownerSiteUserId": 678,
      "secret": "abc12345-def6-7890-ghij-klmnopqrstuv",
      "createdAt": "2026-08-10T12:00:00Z",
      "expiresAt": "2026-08-17T12:00:00Z",
      "active": true,
      "revokedAt": null,
      "revokeReason": null,
      "hasActiveSession": true,
      "ownerEmail": "user@example.com",
      "ownerDisplayName": "Иван Иванов",
      "songName": "Песня о далекой Родине"
    },
    {
      "id": 776,
      "songId": 98764,
      "ownerSiteUserId": 679,
      "secret": "xyz98765-...",
      "createdAt": "2026-08-09T08:00:00Z",
      "expiresAt": "2026-08-16T08:00:00Z",
      "active": false,
      "revokedAt": "2026-08-10T03:00:00Z",
      "revokeReason": "expired",
      "hasActiveSession": false,
      "ownerEmail": "another@example.com",
      "ownerDisplayName": null,
      "songName": "Другая песня"
    }
  ],
  "totalCount": 4321,
  "page": 1,
  "pageSize": 25
}
```

### SQL (псевдокод)

```sql
WHERE 1=1
  [AND l.owner_site_user_id = :filterOwnerId]
  [AND l.song_id = :filterSongId]
  [AND l.created_at >= :filterCreatedFrom]
  [AND l.created_at <= :filterCreatedTo]
  [AND l.active = true AND l.expires_at > now()  -- если filterActiveOnly]

SELECT
  l.id, l.song_id, l.owner_site_user_id, l.secret,
  l.created_at, l.expires_at, l.active, l.revoked_at, l.revoke_reason,
  (l.active_session_token_hash IS NOT NULL AND l.active_session_lease_until > now()) AS has_active_session,
  u.email, u.display_name,
  s.song_name
FROM tbl_song_share_links l
LEFT JOIN tbl_site_users u ON u.id = l.owner_site_user_id
LEFT JOIN tbl_songs s ON s.id = l.song_id
WHERE <dynamic_where>
ORDER BY l.created_at DESC
LIMIT :pageSize OFFSET :(page - 1) * pageSize;
```

## Endpoint 2: revoke (ПЕРЕИСПОЛЬЗУЕМ существующий!)

> Не создаём новый эндпоинт. Используем существующий `POST /api/siteusers/share/links/revoke` из `SiteShareLinksController.kt`.

### Request

| Имя | Тип | Описание |
|---|---|---|
| `target` | `String` | `'local'` / `'remote'` |
| `shareLinkId` | `Long` | ID ссылки для отзыва |
| `reason` | `String` | Всегда `'admin'` для админ-таблицы (отличает от sweeper'а) |

### Response

- `200 OK` — отозвано. UI обновляет строку in-place (active=false, revoked_at=now, revoke_reason='admin').
- `404` — ссылка не найдена (уже отозвана или не существует).

## Vuex-store

```javascript
// webvue3/src/components/ShareLinks/store.js
import shareLinkStore from '../SiteUsers/shareLinkStore.js' // переиспользуем revoke

export default {
  state: {
    shareLinksDigest: [],
    shareLinksDigestTotalCount: 0,
    shareLinksDigestIsLoading: false,
    shareLinksTarget: 'local',
    shareLinksTableCurrentPage: 1,
    shareLinksFilter: {
      activeOnly: false,
      ownerId: null,
      songId: null,
      createdFrom: null,
      createdTo: null,
    },
  },
  actions: {
    loadShareLinksDigest(ctx, params = {}) { /* POST /api/sharelinks/digest */ },
    // revokeSiteUserShareLink — переиспользуем из shareLinkStore.js:
    //   this.$store.dispatch('revokeSiteUserShareLink', { shareLinkId, reason: 'admin', target })
  },
}
```

> **Важно**: при отзыве UI сначала диспатчит `revokeSiteUserShareLink`, затем обновляет локально строку в `shareLinksDigest` (через mutation `updateShareLinksDigestItem`), без полной перезагрузки таблицы (UX быстрее).

## Связанные документы

- [data-model.md](../data-model.md) — Entity 3: SongShareLink.
- [quickstart.md](../quickstart.md) — сценарий проверки «Временные ссылки».
- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/SongShareLinkService.kt` — существующий сервис (getShareLinksForUser, revoke).
- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/ShareLinkSweeper.kt` — фоновый sweeper (auto-revoke).
- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/SiteShareLinksController.kt` — существующий контроллер, эндпоинт `/api/siteusers/share/links/revoke` переиспользуется.
- `webvue3/src/components/SiteUsers/shareLinkStore.js:64` — `revokeSiteUserShareLink` action.
- `docs/features/guest-share-link.md` — обновляется в этом PR.
