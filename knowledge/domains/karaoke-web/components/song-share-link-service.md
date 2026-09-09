# Component: song-share-link-service

> **Домен**: [karaoke-web](../domain.md)
> **Компонента**: `SongShareLinkService` — самый большой сервис
> karaoke-web (1130 строк).

## Назначение

CRUD + lifecycle для `SongShareLink` (см.
[entities-catalog.md](../../catalog/components/entities-catalog.md#songsharelink)).
Используется из `PublicShareController` (8 endpoints).

## Файл

`karaoke-web/.../services/SongShareLinkService.kt` — 1130 строк.

## Hot paths

- **Heartbeat** (`/api/public/share/heartbeat`) — PollingCache TTL=15s.
  Каждые ~25s от каждой открытой share-ссылки.
- **First use** — пользователь переходит по ссылке.
- **Active session management** — concurrent session limit.

## State (in-memory, привязан к БД)

Не имеет in-memory state поверх БД. Всё через `SongShareLink.loadById`
+ `save()`. Кеш — на уровне PollingCache (см. [caching
domain](../../caching/domain.md)).

## Логика и Алгоритмы

### Создание share-ссылки

```kotlin
fun createLink(
    songId: Long,
    ownerSiteUserId: Long,
    expiresAt: Timestamp?,
    maxUses: Int?,
): SongShareLink {
    val token = generateSecureToken()  // UUID + random
    val tokenHash = sha256(token)  // НЕ сам токен в БД
    val link = SongShareLink(
        ownerSiteUserId = ownerSiteUserId,
        songId = songId,
        tokenHash = tokenHash,
        active = true,
        expiresAt = expiresAt,
        sessionsTotal = 0,
        rejectedConcurrent = 0,
    )
    link.save()
    return link with rawToken = token  // токен возвращается ОДИН раз
}
```

**Безопасность**: `token_hash` (не `token`) в БД. Сам токен
возвращается **только при создании** — потом только через хеш.

### Heartbeat

```kotlin
fun heartbeat(
    token: String,
    browserHash: String,
): SessionResult {
    val link = SongShareLink.byTokenHash(sha256(token)) ?: return INVALID_TOKEN
    if (!link.active) return INACTIVE
    if (link.expiresAt < now) return EXPIRED

    val activeSession = findActiveSession(link, browserHash)
    if (activeSession != null) {
        // продлить lease
        activeSession.leaseUntil = now + LEASE_DURATION
        activeSession.save()
        return OK_WITH_LEASE
    }

    // первое использование
    if (link.firstUsedAt == null) link.firstUsedAt = now
    link.lastUsedAt = now
    link.sessionsTotal++

    // active session check
    val active = findActiveSessions(link)
    if (active.size >= MAX_CONCURRENT) {
        link.rejectedConcurrent++
        link.save()
        return REJECTED_CONCURRENT
    }

    val session = createSession(link, browserHash)
    link.save()
    return OK_NEW_SESSION
}
```

**`MAX_CONCURRENT`** — лимит одновременных сессий на одну ссылку
(защита от mass sharing).

### Lease

`active_session_lease_until` — на сколько сессия считается живой.
Heartbeat продлевает.

### Cleanup (через ShareLinkSweeper)

Expired (`expires_at < now`) или inactive (`active = false`) — soft
delete через `revoked_at`.

## Domain Invariants

1. **`token_hash` MUST быть sha256 от `token`** — сам `token` в БД не
   хранится.
2. **`MAX_CONCURRENT` сессий на ссылку** — защита от mass sharing.
3. **`active_session_lease_until` < now** → сессия мертва, нужен новый
   `first_used_at` или takeover.
4. **`tokenHash` уникален** — collision бы означало, что кто-то
   угадал чужой токен (impossible для sha256, но defensive).

## Связь с другими компонентами

- **Catalog** ([entities-catalog.md](../../catalog/components/entities-catalog.md#songsharelink)) —
  entity.
- **Schedulers** ([schedulers.md](../../processing/components/schedulers.md)) —
  `ShareLinkSweeper` (cron каждый час).
- **Caching** ([caching domain](../../caching/domain.md)) — `PollingCache`
  для heartbeat.

## Известные TODO

- [ ] **Точные значения** `MAX_CONCURRENT`, `LEASE_DURATION` — Pass 343+.
- [ ] **`generateSecureToken`** — где определён, какой алгоритм.
- [ ] **JWT в `token`** или просто UUID? (Pass 343+).
- [ ] **Browser hash** — что именно хэшируется.
- [ ] **First-use vs returning** — flow.

## Код

`karaoke-web/.../services/SongShareLinkService.kt` (1130 строк).

## Changelog

- **Pass 365** (2026-09-09): Initial. Прецедент: задачи #65, #69 +
  общий Knowledge-аудит. Автор: agent (Karaoke).