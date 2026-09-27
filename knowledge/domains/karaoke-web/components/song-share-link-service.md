# Component: song-share-link-service

> **Домен**: [karaoke-web](../domain.md)
> **Компонента**: `SongShareLinkService` — самый большой сервис
> karaoke-web (1130 строк).


## Ответственность | Responsibility


`SongShareLinkService` — самый большой сервис karaoke-web (1130 строк).

## Назначение

CRUD + lifecycle для `SongShareLink` (см.
[entities-catalog.md](../../catalog/components/entities-catalog.md#songsharelink)).
Используется из `PublicShareController` (7 `@*Mapping`-методов:
`create`, `mine`, `revoke`, `claim`, `heartbeat`, `release`, `debug`),
а также из `PublicPlayerController` и `SiteShareLinksController`.

**Модель — двухуровневая** (из KDoc класса): долгоживущий *грант* в
`tbl_song_share_links` (владелец-премиум, хранит SHA-256 секрета) и
короткоживущая *playback-сессия* в `tbl_song_share_sessions`
(гость, lease + heartbeat).

## Файл

`karaoke-web/.../services/SongShareLinkService.kt` — 1130 строк.
Настройки — `WebShareProperties` (`karaoke.share.*`, см. [config.md](config.md)).

## Интерфейсы и Контракты | Interfaces and Contracts

### Публичные методы сервиса

| Метод | Вход → Выход |
|---|---|
| `createLink(siteUserId, songId, ttlSeconds, baseUrl, database)` | → `CreateResult(linkId, secret, expiresAt, url)`; бросает `SongSkipped`, `SongUnavailable`, `LinkAlreadyActive` |
| `revokeLink(siteUserId, songId, reason = "manual", database)` | `Unit`; `UPDATE ... active=false, revoke_reason=?` |
| `revokeLinkById(linkId, reason = "admin", database)` | `Unit`; в одной транзакции: завершает активные lease-сессии (`result='revoked'`) и обнуляет `active_session_*` |
| `getCurrentForOwner(siteUserId, songId, database)` | → `OwnerLinkView?` |
| `findLinkIdBySecret(secret, database)` | → `Long?` (активная неотозванная, `expires_at > now()`); используется `PublicPlayerController.access` |
| `tryClaim(secret, browserHash, request, database)` | → `TryClaimResult`; бросает `RateLimited`, `NotFound`, `ConcurrentLimit`, `InternalError` |
| `heartbeat(sessionTokenHash, database)` | `Unit`; бросает `LeaseExpired` |
| `release(sessionTokenHash, result, database)` | `Unit`; `result` нормализуется к `ended \| closed \| timeout \| revoked \| replaced` |
| `validateShareSession(sessionTokenHash?, songId, database)` | → `Long?` (`linkId`) — живой ли lease для stem-эндпоинтов |
| `listLinksForUser(siteUserId, activeOnly, limit, database)` | → `List<OwnerLinkView>` (admin API) |
| `listSessionsForLink(linkId, database)` | → `List<SessionView>` (admin API) |
| `debugTryClaim(secret, database)` | → `Map<String, Any?>` — пошаговая диагностика без создания сессии |

**DTO**: `CreateResult`, `OwnerLinkView`, `TryClaimResult`
(включая карточку песни: `songName`, `author`, `album`, `year`,
`albumImageUrl`, `artistImageUrl`), `SessionView`.
Утилиты: `sha256Hex(input)` (companion), `songHasSkipTag(tags)`,
`songIsShareablePublic(songId, database)`, файловая
`toMskLocalDateTime(epochMs)`.

### Ошибки (`sealed class ShareException`: `code`, `httpStatus`)

| Исключение | `errorCode` | HTTP |
|---|---|---|
| `NotFound` | `share.notFound` | 404 |
| `Expired` | `share.expired` | 404 |
| `Revoked` | `share.revoked` | 404 |
| `SongUnavailable` | `share.songUnavailable` | 409 |
| `SongSkipped` | `share.songSkipped` | 409 |
| `ConcurrentLimit` | `share.concurrentLimit` | 409 |
| `LeaseExpired` | `share.leaseExpired` | 410 |
| `RateLimited` | `share.rateLimited` | 429 |
| `NotOwner` | `share.notOwner` | 403 |
| `LinkAlreadyActive(reason, limit, actual)` | `share.linkAlreadyActive` | 429 |
| `TokenMissing` | `share.tokenMissing` | 400 |
| `InternalError(cause)` | `share.internal` | 500 |

Коды — enum `ShareErrorCode` в `karaoke-web/.../util/ShareErrorCode.kt`;
секрет в логах/`referer` маскируется `ShareSecretMask.mask`
(первые 4 + `***` + последние 4 символа).

### Endpoints-обёртка (`PublicShareController`, `/api/public/share`)

| Метод | Тело/параметры → ответ |
|---|---|
| `POST /{songId}/create` | `ttlSeconds` (query, default 3600; допустимы 3600/86400/604800) → `linkId`, `secret`, `url`, `expiresAt`, `ttlSeconds`; только `isEffectivePremium` |
| `GET /mine/{songId}` | → `{ "link": {...} \| null }` |
| `POST /mine/{songId}/revoke` | `reason` (query, default `manual`) → `{ "revoked": true }` |
| `POST /claim` | `{ secret, browserHash }` → `linkId`, `songId`, `sessionTokenHash`, `linkExpiresAt`, `expiresAt`, `redirectTo`, карточка песни |
| `POST /heartbeat` | `{ sessionTokenHash }` → `{ "ok": true }` (или 410/500); ответ кешируется `PollingCache` TTL 15 с по ключу `share_heartbeat:<hash>` |
| `POST /release` | `sessionTokenHash` + `result` (JSON или form-urlencoded — `sendBeacon`) → `{ "ok": true }` |
| `POST /debug` | `{ secret }` → диагностический `Map` |

## Логика и Алгоритмы | Logic and Algorithms

### Создание ссылки (`createLink`)

1. `songIsSkipped(songId)` — отдельная быстрая проверка тега `SKIP`
   ДО общей проверки, чтобы бросить специфичный `SongSkipped`.
2. `songIsShareable(songId)`: `tags` без `SKIP`, `id_status >= 6`,
   непустой `source_markers` и все четыре флага в
   `player_readiness_flags` (`stemAccompanimentReady`,
   `stemVocalReady`, `pictureAlbumReady`, `pictureAuthorReady`) —
   иначе `SongUnavailable`.
3. Лимиты из `WebShareProperties`: `maxActivePerUser` (дефолт 5),
   `maxGenerationsPerDay` (30), `maxReissuesPerSongPerHour` (3) —
   при превышении `LinkAlreadyActive(reason, limit, actual)`.
4. Секрет: `generateSecret()` = 32 байта `SecureRandom`, base64url
   без padding; в БД пишется `sha256Hex(secret)`.
5. Прежняя активная ссылка владельца на эту же песню переводится в
   `active=false, revoked_at=now(), revoke_reason='replaced'`.
6. `expires_at = now + ttlSeconds*1000` пишется как naive-МСК через
   `setObject(toMskLocalDateTime(expiresAt), Types.TIMESTAMP)` —
   `setTimestamp` читал бы TZ JVM и давал сдвиг.
7. URL собирается как `"$baseUrl/share/$newId/$secret"`;
   `baseUrl` = `${app.public-site-url}` (см. [config.md](config.md)).

### Claim playback-сессии (`tryClaim`)

1. `checkRateLimit(request)` — bucket `claim:<ip>:<минута>` в
   `ConcurrentHashMap`, `> claimRateLimitPerIpPerMin` (дефолт 10/мин)
   → `RateLimited`.
2. `resolveForGuest(secret)` — SHA-256 секрета ищется в
   `tbl_song_share_links` (`active`, не отозван, не истёк) → `linkId`.
3. Если по ссылке уже есть живой lease
   (`active_session_token_hash != null && lease_until > now`) —
   возвращается **тот же** `sessionTokenHash` (вкладки одного
   устройства делят слот; TODO в коде — проверка совпадения
   `browserHash`).
4. Иначе считается число активных сессий; при
   `>= maxConcurrentSessions` (дефолт 2) → `rejected_concurrent++` и
   `ConcurrentLimit` (409).
5. Создаётся строка `tbl_song_share_sessions`
   (`browser_hash`, `client_ip_hash`, `user_agent_hash` — SHA-256 с
   солью `share-salt`); lease ссылки =
   `now() + leaseTtlSeconds` (дефолт 90 с), обновляются
   `first_used_at` (COALESCE), `last_used_at`, `sessions_total`.
6. Ответ содержит карточку песни из `loadSongInfo` — сырой SQL по
   `tbl_songs` (в karaoke-web `Song.loadFromDbById` тянет
   storage-заглушки, поэтому не используется).

### Heartbeat и release

- `heartbeat`: `active_session_lease_until = now() + leaseTtlSeconds`,
  `last_used_at = now()` при `active AND expires_at > now()`;
  `executeUpdate() == 0` → `LeaseExpired`. Вторым UPDATE
  проставляется `last_seen_at` незавершённой сессии.
- `release`: `finished_at = now()`, `result` нормализуется
  (`ended/closed/timeout/revoked/replaced`, иначе `closed`), затем
  lease-поля ссылки обнуляются. Вызывается из `_onEnded`,
  `beforeunload` (sendBeacon) и при отзыве.
- `revokeLinkById`: три операции в одной транзакции
  (`autoCommit = false`).

### Sweeper и даты

- `ShareLinkSweeper.sweep` — `@Scheduled(fixedDelayString = "${karaoke.share.sweep-interval-seconds:60}000")`:
  lease-таймауты, истёкшие ссылки, потеря премиума владельцем,
  SKIP/будущая публикация песни; ошибки тика логируются.
- Все даты в DTO/JSON — реальный момент в epoch ms
  (`EXTRACT(EPOCH FROM ts AT TIME ZONE 'Europe/Moscow')*1000`);
  DDL остаётся `timestamp without time zone` (naive, источник правды — МСК).

## State (in-memory, привязан к БД)

Сами ссылки/сессии — только в PostgreSQL: сервис переживает рестарт
karaoke-web (в отличие от `PlayerGestureUnlockService`).
Единственное in-memory состояние — bucket'ы rate-limit claim
(`ConcurrentHashMap<String, AtomicInteger>`, ключ `claim:<ip>:<минута>`),
то есть лимит сбрасывается при рестарте.

## Hot paths

- **Heartbeat** (`/api/public/share/heartbeat`) — каждые ~25 с от
  каждой открытой share-ссылки; ответ контроллера кешируется
  `PollingCache` TTL=15 с (см. [web-caches.md](../../caching/components/web-caches.md)).
- **Claim** (`/api/public/share/claim`) — вход гостя по ссылке.
- **First use** — `first_used_at` проставляется через `COALESCE`.

## Domain Invariants

1. **`token_hash` = SHA-256 от секрета** — сам секрет в БД не
   хранится и отдаётся ровно один раз (при создании/перевыпуске).
2. **≤ `maxConcurrentSessions` (дефолт 2) активных lease на ссылку.**
3. **Lease-поля ссылки — единственный активный слот**:
   `active_session_token_hash` / `active_session_lease_until`;
   heartbeat продлевает lease, `release` обнуляет.
4. **Все временные метки — naive-МСК**; в JSON — epoch ms,
   посчитанный через `AT TIME ZONE 'Europe/Moscow'`.
5. **`result` завершённой сессии** ∈ `ended | closed | timeout | revoked | replaced`.
6. **SKIP-контент не распространяется** через share-ссылки
   независимо от `canWorkWithSkipped` (compliance, specs/293-skip-author-toggle).

## Зависимости | Dependencies

- **Catalog** ([entities-catalog.md](../../catalog/components/entities-catalog.md#songsharelink)) —
  entity.
- **Schedulers** ([schedulers.md](../../processing/components/schedulers.md)) —
  `ShareLinkSweeper` (cron каждую минуту).
- **Caching** ([caching domain](../../caching/domain.md)) — `PollingCache`
  для heartbeat.
- **Config** ([config.md](config.md)) — `WebShareProperties`
  (`karaoke.share.*`) — все лимиты и TTL.

## Известные TODO

- [ ] **Browser hash** — `tryClaim` возвращает существующий lease по
      ссылке без сверки `browserHash` (TODO в коде).
- [ ] **`NotOwner`/`Expired`/`Revoked`** — сейчас в сервисе объявлены,
      но `resolveForGuest` сводит негативные кейсы к `NotFound`;
      где именно различаются коды — Pass 343+.
- [ ] **Retention сессий** `tbl_song_share_sessions` — где чистится.
- [ ] **Admin API** (`SiteShareLinksController`) — детальный контракт.

## Код

`karaoke-web/.../services/SongShareLinkService.kt` (1130 строк).

## Changelog

- **Pass 481** (2026-09-27, spec `481-knowledge-domain-karaoke-web`): секции приведены к шаблону компонента. Автор: agent (Karaoke).
- **Pass 365** (2026-09-09): Initial. Прецедент: задачи #65, #69 +
  общий Knowledge-аудит. Автор: agent (Karaoke).
