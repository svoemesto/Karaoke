# Contracts: Временный полный доступ к песне

**Spec**: [./spec.md](./spec.md)
**Branch**: `164-complete-guest-share-link`

Все endpoint'ы — JSON поверх HTTP. Контракты фиксируют **наблюдаемое поведение** для владельца, гостя, плеера и админа. Формат ошибок унифицирован: `{"errorCode": "share.<code>", ...доп.поля}`.

## Группа 1: Owner API (требует `Authorization: Bearer <km_auth_token>`, владелец = премиум)

### 1.1. POST `/api/public/share/{songId}/create`

Создаёт или перевыпускает ссылку.

**Request**:
| Param | Type | Default | Notes |
|---|---|---|---|
| `songId` | path | — | Long |
| `ttlSeconds` | query | 3600 | 3600 \| 86400 \| 604800 (см. Clarifications Q5) |

**Response 200**:
```json
{
  "linkId": 12345,
  "secret": "base64url-32-bytes",
  "url": "https://sm-karaoke.ru/share/789/secret-string",
  "expiresAt": 1723300800000,
  "expiresAtMs": 1723300800000,
  "expiresAtLabel": "10.08.2026 22:00",
  "ttlSeconds": 3600
}
```

**Errors**:
- `403 {"errorCode":"share.notOwner"}` — не премиум.
- `400 {"errorCode":"share.tokenMissing"}` — ttlSeconds не из whitelist.
- `409 {"errorCode":"share.songUnavailable"}` — песня не готова / SKIP.
- `429 {"errorCode":"share.linkAlreadyActive", "reason":"max_active_per_user", "limit":5, "actual":6}` — лимиты.

### 1.2. GET `/api/public/share/mine/{songId}`

Текущая активная ссылка пользователя на эту песню.

**Response 200**:
```json
{
  "link": {
    "linkId": 12345,
    "songId": 789,
    "active": true,
    "expiresAt": 1723300800000,
    "expiresAtMs": 1723300800000,
    "expiresAtLabel": "10.08.2026 22:00",
    "createdAt": 1723297200000,
    "createdAtMs": 1723297200000,
    "createdAtLabel": "10.08.2026 21:00",
    "revokedAt": null,
    "revokedAtMs": null,
    "revokedAtLabel": null,
    "revokeReason": "",
    "firstUsedAt": 1723297300000,
    "firstUsedAtMs": 1723297300000,
    "firstUsedAtLabel": "10.08.2026 21:01",
    "lastUsedAt": 1723297500000,
    "lastUsedAtMs": 1723297500000,
    "lastUsedAtLabel": "10.08.2026 21:05",
    "sessionsTotal": 3,
    "rejectedConcurrent": 0
  }
}
```
или `{"link": null}` если активной нет.

### 1.3. POST `/api/public/share/mine/{songId}/revoke`

**Request**: `?reason=manual` (default).

**Response 200**: `{"revoked": true}`.

## Группа 2: Guest API (анонимный, без авторизации)

### 2.1. POST `/api/public/share/claim`

**Request body**:
```json
{"secret": "base64url-string", "browserHash": "sha256-hex64"}
```

**Response 200**:
```json
{
  "linkId": 12345,
  "songId": 789,
  "sessionTokenHash": "sha256-hex64",
  "redirectTo": "/player/789?share=1&session=<sessionTokenHash>",
  "songName": "Название песни",
  "author": "Автор",
  "album": "Альбом",
  "year": 2020,
  "albumImageUrl": "/api/public/picture?file=...",
  "artistImageUrl": "/api/public/picture?file=..."
}
```

**Errors**:
- `400 {"errorCode":"share.tokenMissing"}` — нет secret или browserHash.
- `404 {"errorCode":"share.notFound"}` — ссылка не найдена / истекла / отозвана / перевыпущена.
- `409 {"errorCode":"share.songUnavailable"}` — песня помечена SKIP.
- `409 {"errorCode":"share.concurrentLimit"}` — превышен лимит устройств (2).
- `429 {"errorCode":"share.rateLimited"}` — rate limit по IP (10/мин).

### 2.2. POST `/api/public/share/heartbeat`

**Request body**: `{"sessionTokenHash": "sha256-hex64"}`.

**Response 200**: `{"ok": true}`.

**Errors**:
- `400 {"errorCode":"share.tokenMissing"}` — нет sessionTokenHash.
- `410 {"errorCode":"share.leaseExpired"}` — lease истёк (следующий тик sweeper закроет сессию).

### 2.3. POST `/api/public/share/release`

**Request body**: `{"sessionTokenHash": "sha256-hex64", "result": "ended"|"closed"|"timeout"|"revoked"|"replaced"}`.

**Response 200**: `{"ok": true}`.

### 2.4. POST `/api/public/share/debug` (diagnostic, НЕ для прода)

Пошагово показывает, на каком этапе ломается `/claim`.

**Response 200**:
```json
{
  "step1_resolve": "OK linkId=12345",
  "linkId": 12345,
  "step2_ownerId": "OK ownerId=42",
  "ownerId": 42,
  "step3_songId": "OK songId=789",
  "songId": 789,
  "step4_songIsShareable": "OK shareable=true",
  "shareable": true,
  "step5_loadSongInfo": "OK songName='Название'",
  "songName": "Название",
  "author": "Автор"
}
```

## Группа 3: Player API — расширения (гость + премиум)

### 3.1. GET `/api/public/player/{id}/access?session=<sessionTokenHash>`

(Новое: принимает `session` query-param.)

**Поведение**:
1. Если gesture `token` есть и валиден → использовать (как раньше).
2. Иначе, если `session` есть и валиден (validateShareSession):
   - `canWatch = true` (если ready)
   - `canExport = false` (гость, см. Clarifications Q1)
   - `isDemo = false`
3. Иначе — старая логика (для анонимов и не-премиум).

**Response 200** (как раньше):
```json
{
  "ready": true,
  "isPremiumUser": false,
  "canWatch": true,
  "canExport": false,
  "isDemo": false,
  "demoFadeInSeconds": null,
  "token": "<gesture-token-or-demo-token>"
}
```

### 3.2. GET `/api/public/player/{id}/playerdata?session=<sessionTokenHash>`

(Новое: принимает `session` query-param.)

**Поведение** `authorized()`:
1. Если gesture `token` есть и валиден → пускаем.
2. Иначе, если `session` есть и `validateShareSession(session, id)` не null → пускаем.
3. Иначе → 404.

Response — как раньше, но `canExport=false` для гостя.

### 3.3. GET `/api/public/player/{id}/file{minus,voice,bass,drums}.mp3?session=<sessionTokenHash>`

(Новое: принимает `session` query-param.)

Та же `authorized()` логика, что и 3.2.

## Группа 4: Admin API (требует залогиненного editor'а — `isEditor=true`)

Защита: расширенный `SiteAuthInterceptor` + ручная проверка `user.isEditor` в контроллере.

### 4.1. POST `/api/siteusers/share/links`

**Request body**:
```json
{
  "siteUserId": 42,
  "activeOnly": false,
  "limit": 50,
  "target": "local"
}
```

`target`: `local` → `WORKING_DATABASE` (karaoke-web), `remote` → `Connection.remote()` (прод-БД).

**Response 200**:
```json
{
  "links": [
    {
      "id": 12345,
      "ownerSiteUserId": 42,
      "songId": 789,
      "active": true,
      "expiresAtMs": 1723300800000,
      "expiresAtLabel": "10.08.2026 22:00",
      "createdAtMs": 1723297200000,
      "createdAtLabel": "10.08.2026 21:00",
      "revokedAtMs": null,
      "revokedAtLabel": null,
      "revokeReason": "",
      "sessionsTotal": 3,
      "rejectedConcurrent": 0
    }
  ]
}
```

**Errors**:
- `401` — не залогинен.
- `403 {"errorCode":"share.notEditor"}` — залогинен, но не editor.
- `503 {"errorCode":"site.remote_unavailable"}` — `target=remote` и remote-БД недоступна.

### 4.2. POST `/api/siteusers/share/links/revoke`

**Request body**:
```json
{
  "shareLinkId": 12345,
  "reason": "admin:compromised",
  "target": "local"
}
```

**Response 200**: `{"revoked": true}`.

### 4.3. POST `/api/siteusers/share/sessions`

**Request body**:
```json
{
  "shareLinkId": 12345,
  "target": "local"
}
```

**Response 200**:
```json
{
  "sessions": [
    {
      "sessionId": 9001,
      "shareLinkId": 12345,
      "songId": 789,
      "browserHash": "sha256-prefix-12",
      "ownerSiteUserId": 42,
      "anonId": "uuid",
      "openedAtMs": 1723297300000,
      "startedAtMs": 1723297310000,
      "lastSeenAtMs": 1723297500000,
      "finishedAtMs": 1723297600000,
      "result": "closed"
    }
  ]
}
```

## Унифицированные error-коды

| Code | HTTP | Когда | Где |
|---|---|---|---|
| `share.notOwner` | 403 | Не премиум при создании | 1.1 |
| `share.tokenMissing` | 400 | Не передан токен или невалидный TTL | 1.1, 2.1, 2.2, 2.3, 4.1 |
| `share.songUnavailable` | 409 | Песня не готова / SKIP / future publishDate | 1.1, 2.1 |
| `share.concurrentLimit` | 409 | Превышен лимит 2 устройств | 2.1 |
| `share.rateLimited` | 429 | >10 claim/мин с IP | 2.1 |
| `share.notFound` | 404 | Ссылка не найдена / истекла / отозвана | 2.1 |
| `share.leaseExpired` | 410 | Lease истёк (heartbeat) | 2.2 |
| `share.linkAlreadyActive` | 429 | Превышены лимиты генерации | 1.1 |
| `share.notEditor` | 403 | Admin endpoint без `isEditor=true` | 4.1, 4.2, 4.3 |
| `site.remote_unavailable` | 503 | `target=remote` и нет подключения | 4.1, 4.2, 4.3 |

## Frontend Contract: ShareLinkModal error mapping

| `errorCode` | Текст в модалке |
|---|---|
| `share.notOwner` | «Создание временной ссылки доступно только для премиум-подписки» |
| `share.songUnavailable` | «Песня ещё не готова к отправке» |
| `share.concurrentLimit` | «Уже открыто максимум устройств (2). Закройте одно, чтобы открыть новое» |
| `share.rateLimited` | «Слишком много попыток. Подождите минуту» |
| `share.notFound` | «Ссылка не найдена или отозвана» |
| `share.tokenMissing` | «Некорректные параметры запроса» |
| `share.linkAlreadyActive` | «Превышен лимит: <reason>. Подождите или отзовите старые ссылки» |

## Frontend Contract: Player overlay при 410/404

| Ситуация | Текст overlay |
|---|---|
| `410 share.leaseExpired` (heartbeat) | «Время сеанса истекло. Попросите владельца прислать новую ссылку» + «Закрыть» |
| `404 share.notFound` (playerdata) | «Ссылка отозвана. Попросите владельца прислать новую» + «Закрыть» |

Без auto-recovery (см. Clarifications Q4).
