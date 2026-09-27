# Feature: Playlist Membership (избранное + плейлисты) — bulk-fetch API

> **Status**: active
> **Feature Key**: playlist-membership
> **Last Updated**: 2026-09-27
> **Спеки**: [specs/239-zakroma-author-songs-batch-render](../../specs/239-zakroma-author-songs-batch-render/spec.md) (Pass 239, bulk-fetch initial),
>           [specs/361-playlists-membership-uri-length](../../specs/361-playlists-membership-uri-length/spec.md) (Pass 361, GET→POST)
> **Issue**: OpenProject #77
> **Контроллер**: `PublicPlaylistController.kt` (под `/api/public/account`)
> **Фронт**: `karaoke-public/src/composables/usePlaylistMembership.js`

## Что делает

Bulk-fetch membership-информации (избранное + не-избранные плейлисты)
для **списка** песен. Используется для отрисовки иконок «Избранное» (★)
и «Плейлисты» (▶|) в страницах списка (`/zakroma`, `/search`,
`/zakroma?author=...`) без per-row запросов.

## Зачем

До bulk-fetch фронт на каждую песню списка делал отдельные фоновые запросы
(избранное + плейлисты) — на крупных авторах это N×3 запросов и заметная задержка
отрисовки иконок. Один bulk-запрос снимает эту нагрузку. Изначально он делался через
GET с CSV в query-string, но на крупных авторах (>2500 песен) URL превышал 8 КБ и
сервер отвечал HTTP 414 Request-URI Too Large (OpenProject #77) — отсюда переход на
POST с JSON body.

## Как работает (кратко)

Фронт одним запросом передаёт список id песен и получает карту
`id → {favorited, playlistIds}`; иконки рисуются финально, без per-row запросов.
Состояние живёт в module-level singleton композабла `usePlaylistMembership.js` и
синхронизируется между вкладками/iframe одного origin через
`BroadcastChannel('km-favorites')`.

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

### Use cases

- **Залогиненный пользователь на крупном авторе** (≥ 1500 песен):
  фронт делает **POST** с JSON body `{"ids": [...все id песен автора...]}`.
  Сервер возвращает membership-карту одним запросом. Иконки
  отрисовываются финально, без 414.

- **Аноним** (без токена): фронт skip'ает membership-fetch по
  `if (!token.value)` в `usePlaylistMembership.js`. Иконки — «гостевые»
  (серая ★, серая ▶|) с редиректом на `/login` по клику.

- **Маленький автор** (≤ 500 песен): GET работает (URL < 8 КБ).
  Используется legacy клиентами (старые билды в кеше браузера).

### Фронтенд

- `usePlaylistMembership.js` — module-level singleton (Pass 246 fix #1):
  `favoriteIds: Set<number>`, `membership` (reactive-карта id →
  `{favorited, playlistIds}`), `playlists: ref<Array>`. Один bulk-fetch на приложение
  (не per-row).
- `BroadcastChannel('km-favorites')` — синхронизация между вкладками/iframe
  одного origin.
- `fetchMembership(ids)` (в `services/playlistApi.js`) — POST через
  `authPostJson(BASE + '/playlists/membership', { ids }, token())`.

### Curl примеры

**POST (рекомендуемый)**:

```bash
curl -X POST http://localhost:7907/api/public/account/playlists/membership \
  -H "Authorization: Bearer $JWT" \
  -H "Content-Type: application/json" \
  -d '{"ids":[22982,22983,22984]}'
```

**GET (legacy)**:

```bash
curl -X GET "http://localhost:7907/api/public/account/playlists/membership?ids=22982,22983" \
  -H "Authorization: Bearer $JWT"
```

## Инварианты

- **MUST**: для крупных выборок использовать **POST** с JSON body; GET —
  только deprecated backward-compat (Pass 361, OpenProject #77).
- **MUST**: один bulk-fetch на приложение, не per-row — состояние держится в
  module-level singleton `usePlaylistMembership.js`.
- **MUST**: без токена membership-fetch пропускается (`if (!token.value)`);
  иконки остаются «гостевыми» со ссылкой на `/login`.
- **MUST**: состояние общее на всё приложение; кросс-вкладочная синхронизация — через
  `BroadcastChannel('km-favorites')` (только один origin).
- **MUST**: JWT передаётся в `Authorization: Bearer` без CSRF
  (`SiteAuthInterceptor.kt`).
- **MUST**: размер тела POST ограничен nginx `client_max_body_size` (на dev — 20M,
  см. `deploy/web-server-deploy/deploy/nginx.conf`).
- **SHOULD**: после стабилизации POST пометить GET как `@Deprecated` в Kotlin
  (WARN-лог), затем решать об удалении по метрике `membership_get_calls_total`.
  См. [AGENTS.md](../../AGENTS.md) и
  [constitution.md](../../.specify/memory/constitution.md).

## Известные ловушки

- **HTTP 414 на GET с ~1350+ id** — nginx `large_client_header_buffers` = 8 КБ;
  именно поэтому введён POST (OpenProject #77).
- **`client_max_body_size` default = 1m** — на проде нужно проверять конфиг nginx:
  при значении по умолчанию большой POST-список id может быть отвергнут.
- **GET сохранён для backward-compat** — старые закэшированные билды браузера всё
  ещё ходят через GET; удалять его нельзя без метрики использования.
- **Module-level singleton** — состояние (`favoriteIds`, `membership`, `playlists`)
  общее для всех компонентов и страниц; точечный сброс делается через
  `favoriteIds.clear()`, а не пересозданием композабла.
- **`BroadcastChannel` работает только в пределах одного origin** — вкладки/iframe
  других origin состояние не получат.
- **Аноним и гость** — при отсутствии токена запросы молча пропускаются; иконки
  остаются серыми, это ожидаемое поведение, а не сбой загрузки.

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

## Ссылки

- [`specs/239-zakroma-author-songs-batch-render/spec.md`](../../specs/239-zakroma-author-songs-batch-render/spec.md)
  — первоначальный bulk-fetch (Pass 239).
- [`specs/361-playlists-membership-uri-length/spec.md`](../../specs/361-playlists-membership-uri-length/spec.md)
  — переход GET→POST из-за длины URI (Pass 361).
- [`karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicPlaylistController.kt`](../../karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicPlaylistController.kt)
  — `@PostMapping("/playlists/membership")` и deprecated `@GetMapping`.
- [`karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/MembershipRequest.kt`](../../karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/MembershipRequest.kt)
  — DTO `MembershipRequest(val ids: List<Long>)` (Pass 361).
- [`karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/config/SiteAuthInterceptor.kt`](../../karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/config/SiteAuthInterceptor.kt)
  — JWT в `Authorization: Bearer`, без CSRF.
- [`karaoke-public/src/composables/usePlaylistMembership.js`](../../karaoke-public/src/composables/usePlaylistMembership.js)
  — композабл с module-level состоянием и `BroadcastChannel`.
- [`karaoke-public/src/services/playlistApi.js`](../../karaoke-public/src/services/playlistApi.js)
  — `fetchMembership`, `fetchFavoritesIds`, `fetchPlaylists`.
- [`karaoke-public/src/services/authApi.js`](../../karaoke-public/src/services/authApi.js)
  — `authPostJson(path, jsonBody, token)` для JSON-body.
- [`deploy/web-server-deploy/deploy/nginx.conf`](../../deploy/web-server-deploy/deploy/nginx.conf)
  — `client_max_body_size 20M`.
