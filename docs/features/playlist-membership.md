# Feature: Playlist Membership (избранное + плейлисты) — bulk-fetch API

> **Slug**: playlist-membership
> **Спеки**: [specs/239-zakroma-author-songs-batch-render](../../specs/239-zakroma-author-songs-batch-render/spec.md) (Pass 239, bulk-fetch initial),
>           [specs/361-playlists-membership-uri-length](../../specs/361-playlists-membership-uri-length/spec.md) (Pass 361, GET→POST)
> **Issue**: OpenProject #77
> **Контроллер**: `PublicPlaylistController.kt` (под `/api/public/account`)
> **Фронт**: `karaoke-public/src/composables/usePlaylistMembership.js`

## Назначение

Bulk-fetch membership-информации (избранное + не-избранные плейлисты)
для **списка** песен. Используется для отрисовки иконок «Избранное» (★)
и «Плейлисты» (▶|) в страницах списка (`/zakroma`, `/search`,
`/zakroma?author=...`) без per-row запросов.

## Endpoints

### `POST /api/public/account/playlists/membership` (рекомендуемый, Pass 361)

**Request**:
```http
POST /api/public/account/playlists/membership HTTP/1.1
Authorization: Bearer <JWT>
Content-Type: application/json

{"ids": [22982, 22983, 22984, ...]}
```

**Response 200**:
```json
{
  "items": {
    "22982": {"favorited": true, "playlistIds": [12345]},
    "22983": {"favorited": false, "playlistIds": []},
    "22984": {"favorited": false, "playlistIds": [12346, 12347]}
  }
}
```

**Лимит размера**: nginx `client_max_body_size` (default = 1m). На dev-машинах
установлено ≥ 20M (см. `deploy/web-server-deploy/deploy/nginx.conf`).

### `GET /api/public/account/playlists/membership` (DEPRECATED, backward-compat)

**Request**:
```http
GET /api/public/account/playlists/membership?ids=22982,22983,22984 HTTP/1.1
Authorization: Bearer <JWT>
```

**Response**: идентичен POST.

**Лимит**: nginx `large_client_header_buffers` = 8 КБ → на ~1350+ id возвращает
HTTP 414 Request-URI Too Large (см. OpenProject #77).

## Use cases

- **Залогиненный пользователь на крупном авторе** (≥ 1500 песен):
  фронт делает **POST** с JSON body `{"ids": [...все id песен автора...]}`.
  Сервер возвращает membership-карту одним запросом. Иконки
  отрисовываются финально, без 414.

- **Аноним** (без токена): фронт skip'ает membership-fetch по
  `if (!token.value)` в `usePlaylistMembership.js`. Иконки — «гостевые»
  (серая ★, серая ▶|) с редиректом на `/login` по клику.

- **Маленький автор** (≤ 500 песен): GET работает (URL < 8 КБ).
  Используется legacy клиентами (старые билды в кеше браузера).

## Фронтенд

- `usePlaylistMembership.js` — module-level singleton (Pass 246 fix #1):
  `favoriteIds: Set<number>`, `membership: Map<id, {favorited, playlistIds}>`,
  `playlists: ref<Array>`. Один bulk-fetch на приложение (не per-row).
- `BroadcastChannel('km-favorites')` — синхронизация между вкладками/iframe
  одного origin.
- `fetchMembership(ids)` (в `services/playlistApi.js`) — POST через
  `authPostJson(BASE + '/playlists/membership', { ids }, token())`.

## Curl примеры

### POST (рекомендуемый)

```bash
curl -X POST http://localhost:7907/api/public/account/playlists/membership \
  -H "Authorization: Bearer $JWT" \
  -H "Content-Type: application/json" \
  -d '{"ids":[22982,22983,22984]}'
```

### GET (legacy)

```bash
curl -X GET "http://localhost:7907/api/public/account/playlists/membership?ids=22982,22983" \
  -H "Authorization: Bearer $JWT"
```

## Связанные компоненты

- `PublicPlaylistController.kt` — `@PostMapping("/playlists/membership") membershipPost(...)`,
  `@GetMapping("/playlists/membership") membership(...)` (DEPRECATED).
- `MembershipRequest.kt` (DTO, Pass 361) — `data class MembershipRequest(val ids: List<Long>)`.
- `usePlaylistMembership.js` — композаб с module-level state.
- `authApi.js` — `authPostJson(path, jsonBody, token)` (Pass 361, для JSON-body).
- `SiteAuthInterceptor.kt:25` — JWT в `Authorization: Bearer`, без CSRF.

## Известные TODO

- (Pass 361) После стабилизации POST в течение 1-2 месяцев — пометить
  GET как `@Deprecated` в Kotlin (WARN-лог на каждый вызов).
- После ещё 1-2 месяцев — решить, удалять ли GET по метрике
  `membership_get_calls_total`.

## Changelog

- **Pass 239** (2026-08-25): bulk-fetch через GET (CSV в query-string).
  Решило проблему N×3 фоновых запросов. URL помещался до ~2500 id (~7 КБ).
- **Pass 361** (2026-09-10): GET→POST. На крупных авторах (>2500 песен)
  URL превышал 8 КБ → 414 Request-URI Too Large (OpenProject #77).
  Добавлен POST с JSON body. GET сохранён для backward-compat.