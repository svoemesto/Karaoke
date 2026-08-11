# Contracts — share-link dates

**Feature**: `166-fix-share-link-timezone`
**Date**: 2026-08-11

Все эндпоинты — JSON через `application/json`. Числовые значения дат
передаются как `Long` (epoch ms, реальный момент). Строка-метка
в МСК **не передаётся**.

## 1. Owner: `POST /api/public/share/{songId}/create`

**Реализация**: `PublicShareController.kt:42` → `SongShareLinkService.createLink`.

### Запрос

```
POST /api/public/share/12345/create?ttlSeconds=3600
Cookie: siteToken=...
```

### Ответ 200 (было)

```json
{
  "linkId": 42,
  "secret": "...",
  "url": "https://svoemesto.band/share/42/...",
  "expiresAt": 1786442256000,
  "expiresAtMs": 1786431456000,
  "expiresAtLabel": "06.08.2026 06:57",
  "ttlSeconds": 3600
}
```

### Ответ 200 (стало)

```json
{
  "linkId": 42,
  "secret": "...",
  "url": "https://svoemesto.band/share/42/...",
  "expiresAt": 1786431456000,
  "ttlSeconds": 3600
}
```

**Изменение**: удалены `expiresAtMs` и `expiresAtLabel`. `expiresAt` —
единственное числовое поле = реальный момент (epoch ms).

### Ошибки (без изменений)

| Status | `errorCode` |
|--------|-------------|
| 401 | `share.tokenMissing` |
| 403 | `share.notOwner` |
| 400 | `share.tokenMissing` (любой ttl кроме 3600/86400/604800) |
| 409 | `share.songUnavailable` |
| 429 | `share.linkAlreadyActive` |
| 500 | `share.notFound` |

## 2. Owner: `GET /api/public/share/mine/{songId}`

**Реализация**: `PublicShareController.kt:94` → `SongShareLinkService.getCurrentForOwner`.

### Запрос

```
GET /api/public/share/mine/12345?target=
Cookie: siteToken=...
```

### Ответ 200 (было)

```json
{
  "link": {
    "linkId": 42,
    "songId": 12345,
    "active": true,
    "expiresAt": 1786442256000,
    "expiresAtMs": 1786431456000,
    "expiresAtLabel": "06.08.2026 06:57",
    "createdAt": 1754895456000,
    "createdAtMs": 1754884656000,
    "createdAtLabel": "06.08.2026 06:57",
    "revokedAt": null,
    "revokedAtMs": null,
    "revokedAtLabel": null,
    "revokeReason": "",
    "firstUsedAt": null,
    "firstUsedAtMs": null,
    "firstUsedAtLabel": null,
    "lastUsedAt": null,
    "lastUsedAtMs": null,
    "lastUsedAtLabel": null,
    "sessionsTotal": 0,
    "rejectedConcurrent": 0
  }
}
```

### Ответ 200 (стало)

```json
{
  "link": {
    "linkId": 42,
    "songId": 12345,
    "active": true,
    "expiresAt": 1786431456000,
    "createdAt": 1754884656000,
    "revokedAt": null,
    "revokeReason": "",
    "firstUsedAt": null,
    "lastUsedAt": null,
    "sessionsTotal": 0,
    "rejectedConcurrent": 0
  }
}
```

**Изменение**: удалены `createdAtMs`, `createdAtLabel`, `expiresAtMs`,
`expiresAtLabel`, `revokedAtMs`, `revokedAtLabel`, `firstUsedAtMs`,
`firstUsedAtLabel`, `lastUsedAtMs`, `lastUsedAtLabel`. Все `*At` поля
теперь = реальный момент.

### Пустая ссылка (без изменений)

```json
{ "link": null }
```

### Ошибки (без изменений)

| Status | `errorCode` |
|--------|-------------|
| 401 | `share.tokenMissing` |
| 500 | (внутренняя) |

## 3. Owner: `POST /api/public/share/mine/{songId}/revoke`

**Реализация**: `PublicShareController.kt:133`. Без изменений контракта.

### Ответ 200

```json
{ "revoked": true }
```

## 4. Guest: `POST /api/public/share/claim`

**Реализация**: `PublicShareController.kt:144` → `SongShareLinkService.tryClaim`.

**Изменение (Pass 1 после `/speckit.analyze`)**: до этой правки эндпоинт
**не** возвращал `expiresAt`, из-за чего US4 (гость видит срок) был невыполним.
Контракт расширен: в ответ добавлен `expiresAt: Long` (реальный момент,
epoch ms). Источник — `System.currentTimeMillis() + props.leaseTtlSeconds*1000L`
(для новой сессии) или `leaseUntil.time` (для existing lease).

### Запрос

```json
{
  "secret": "...",
  "browserHash": "..."
}
```

### Ответ 200 (стало)

```json
{
  "linkId": 42,
  "songId": 12345,
  "sessionTokenHash": "...",
  "expiresAt": 1786431456000,
  "redirectTo": "/player/12345?share=1&session=...",
  "songName": "...",
  "author": "...",
  "album": "...",
  "year": 1999,
  "albumImageUrl": "...",
  "artistImageUrl": "..."
}
```

**Изменение**: добавлен `expiresAt: Long` (epoch ms, реальный момент).
`expiresAtMs` и `expiresAtLabel` НЕ возвращаются (FR-013).

### Поведение `ShareView.vue` (гость) — было

```js
expiresAt.value = Number(body.expiresAtMs ?? body.expiresAt ?? 0) || 0
expiresAtLabel.value = body.expiresAtLabel || ''
```

`expiresAtMs` и `expiresAtLabel` **отсутствуют** в ответе `/claim` уже сейчас
(см. `PublicShareController.kt:155-174`). То есть переменные `expiresAt` и
`expiresAtLabel` остаются значениями по умолчанию (`0` и `''`). `isExpired`
= `false` (так как `expiresAt` = 0). Это значит, что сейчас гость видит
«Доступно до» без значения, но фактически срок не показывается.

### Поведение `ShareView.vue` (гость) — стало

```js
expiresAt.value = Number(body.expiresAt) || 0
expiresAtLabel.value = formatDate(expiresAt.value)  // через dateFormat.js
```

`expiresAt` теперь приходит от бэка (T023), `expiresAtLabel` вычисляется
на клиенте через `dateFormat.formatDate` (пояс устройства). Логика
`isExpired` не меняется (`expiresAt <= Date.now()`).

### Ошибки (без изменений)

| Status | `errorCode` |
|--------|-------------|
| 400 | `share.tokenMissing` |
| 404 | `share.notFound` |
| 409 | `share.concurrentLimit` |
| 429 | `share.rateLimited` |
| 500 | `share.notFound` |

## 5. Admin: `POST /api/siteusers/share/links`

**Реализация**: `SiteShareLinksController.kt:38` → `SongShareLinkService.listLinksForUser`.

### Запрос

```json
{ "siteUserId": 42, "activeOnly": false, "limit": 50, "target": "local" }
```

### Ответ 200 (было)

```json
{
  "links": [
    {
      "linkId": 42,
      "songId": 12345,
      "active": true,
      "expiresAt": 1786442256000,
      "expiresAtMs": 1786431456000,
      "expiresAtLabel": "06.08.2026 06:57",
      "createdAt": 1754895456000,
      "createdAtMs": 1754884656000,
      "createdAtLabel": "06.08.2026 06:57",
      "revokedAt": null,
      "revokedAtMs": null,
      "revokedAtLabel": null,
      "revokeReason": "",
      "firstUsedAt": null,
      "firstUsedAtMs": null,
      "firstUsedAtLabel": null,
      "lastUsedAt": null,
      "lastUsedAtMs": null,
      "lastUsedAtLabel": null,
      "sessionsTotal": 0,
      "rejectedConcurrent": 0
    }
  ],
  "callerId": 1
}
```

### Ответ 200 (стало)

```json
{
  "links": [
    {
      "linkId": 42,
      "songId": 12345,
      "active": true,
      "expiresAt": 1786431456000,
      "createdAt": 1754884656000,
      "revokedAt": null,
      "revokeReason": "",
      "firstUsedAt": null,
      "lastUsedAt": null,
      "sessionsTotal": 0,
      "rejectedConcurrent": 0
    }
  ],
  "callerId": 1
}
```

**Изменение**: то же, что и в `/mine/{songId}`.

## 6. Admin: `POST /api/siteusers/share/sessions`

**Реализация**: `SiteShareLinksController.kt:82` → `SongShareLinkService.listSessionsForLink`.

### Запрос

```json
{ "shareLinkId": 42, "target": "local" }
```

### Ответ 200 (было)

```json
{
  "sessions": [
    {
      "sessionId": 100,
      "shareLinkId": 42,
      "songId": 12345,
      "browserHash": "...",
      "ownerSiteUserId": 1,
      "anonId": "",
      "openedAt": 1754895456000,
      "startedAt": null,
      "lastSeenAt": 1786442256000,
      "finishedAt": null,
      "result": ""
    }
  ]
}
```

### Ответ 200 (стало)

```json
{
  "sessions": [
    {
      "sessionId": 100,
      "shareLinkId": 42,
      "songId": 12345,
      "browserHash": "...",
      "ownerSiteUserId": 1,
      "anonId": "",
      "openedAt": 1754884656000,
      "startedAt": null,
      "lastSeenAt": 1786431456000,
      "finishedAt": null,
      "result": ""
    }
  ]
}
```

**Изменение**: числовые поля `openedAt`, `startedAt`, `lastSeenAt`, `finishedAt`
теперь = реальный момент. У этих полей не было `*Ms`/`*Label`-дубликатов.

## 7. Сводка типов

| Имя | Тип | Смысл |
|-----|-----|-------|
| `expiresAt: Long` | epoch ms | реальный момент истечения |
| `createdAt: Long` | epoch ms | реальный момент создания |
| `revokedAt: Long?` | epoch ms | реальный момент отзыва или null |
| `firstUsedAt: Long?` | epoch ms | реальный момент первого использования |
| `lastUsedAt: Long?` | epoch ms | реальный момент последнего использования |
| `openedAt: Long` | epoch ms | реальный момент открытия сессии |
| `startedAt: Long?` | epoch ms | реальный момент первого PLAY |
| `lastSeenAt: Long` | epoch ms | реальный момент heartbeat |
| `finishedAt: Long?` | epoch ms | реальный момент завершения |

**`null` для опциональных дат** (отсутствие даты) — сериализуется как `null`
(JSON). На UI — прочерк (см. FR-009).

## 8. Что НЕ меняется

- `linkId`, `songId`, `active`, `revokeReason`, `sessionsTotal`,
  `rejectedConcurrent` — обычные поля, не даты.
- `secret`, `sessionTokenHash`, `url`, `tokenHash` — auth-поля, не даты.
- `linkId` в `CreateResult` и `TryClaimResult` — это id записи, не дата.
- DDL таблиц и миграции `38_song_share_links.sql`, `39_song_share_recordhash.sql`.
- Формат `recordhash` (зависит от строкового представления в БД; не меняется).
- `ShareErrorCode` enum (`NOT_FOUND`, `EXPIRED`, `REVOKED`, `CONCURRENT_LIMIT` и т.д.).
- Сравнения `expires_at > now()` в `resolveForGuest`, `findLinkIdBySecret`,
  `validateShareSession`, `heartbeat` — это SQL-side, TZ не важна.
