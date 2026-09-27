# Component: karaoke-web services

> **Домен**: [karaoke-web](../domain.md)
> **Компонента**: детальный каталог 25 services.


## Ответственность | Responsibility


детальный каталог 25 services.

## Назначение

`karaoke-web/.../services/` — 25 сервисов разного размера (от 27 до
1130 строк). Здесь — каталог с кратким описанием. Детальные
документы — по необходимости (Pass 343+).

## Каталог (по размеру)

| # | Сервис | Строк | Назначение |
|---|---|---|---|
| 1 | `SongShareLinkService` | 1130 | **Самый большой** — share-линки (CRUD, сессии, активные сессии, лизы) |
| 2 | `StorageApiClientWeb` | 404 | WebClient reactive к nginx-proxy (см. [storage domain](../../storage/domain.md)) |
| 3 | `EventsBuffer` | 297 | **Batch INSERT в tbl_events** (FR-109) — снижает RPS INSERT на ≥80% |
| 4 | `PaymentService` | 294 | YooKassa wrapper (см. [monetization domain](../../monetization/domain.md)) |
| 5 | `PriceService` | 207 | Расчёт цены с промо (см. [monetization domain](../../monetization/domain.md)) |
| 6 | `PlayerGestureUnlockService` | 175 | Gesture unlock для плеера (мобильный) |
| 7 | `ShareLinkSweeper` | 170 | Cron-sweep expired share-линков (см. [schedulers.md](../../processing/components/schedulers.md)) |
| 8 | `SamplingFilter` | 146 | DDoS protection (sampling 1/N) |
| 9 | `WebKaraokeStorageServiceImpl` | 124 | **ЗАГЛУШКА** MinIO-клиента (см. [storage-flow.md](../../storage/components/storage-flow.md)) |
| 10 | `RateLimitInterceptor` | 118 | Per-endpoint rate limit (60/min для song-picture) |
| 11 | `SubscriptionRenewalScheduler` | 112 | Cron автопродления подписок (см. [schedulers.md](../../processing/components/schedulers.md)) |
| 12 | `KaraokeProperties` | 105 | Env-binding (~20 настроек site-traffic-resilience) |
| 13 | `SiteUserTokenService` | 92 | JWT-токены для /api/public/account/* |
| 14 | `KaraokeWebService` | 88 | Главный сервис (DI-параметры, инициализация) |
| 15 | `DedupCache` | 86 | См. [web-caches.md](../../caching/components/web-caches.md) |
| 16 | `PollingCache` | 80 | См. [web-caches.md](../../caching/components/web-caches.md) |
| 17 | `StatsCacheScheduler` | 74 | См. [schedulers.md](../../processing/components/schedulers.md) |
| 18 | `EventsRetentionScheduler` | 69 | Cron retention для tbl_events (см. [schedulers.md](../../processing/components/schedulers.md)) |
| 19 | `CaptchaConfigService` | 65 | Yandex SmartCaptcha config |
| 20 | `ZakromaStreamProgress` | 64 | Чистая логика `meta.expectedCount` для NDJSON-стрима `/api/public/zakroma/stream` (specs/444-fix-album-progress) |
| 21 | `YandexCaptchaValidationService` | 57 | Yandex SmartCaptcha validation (HTTP POST) |
| 22 | `DebugDbAccessGuard` | 41 | IP allowlist для DebugDbController |
| 23 | `SongReleaseAnnouncementScheduler` | 41 | Cron (~5 мин) проверки «песня вышла в эфир» (specs/101-song-news-flag) |
| 24 | `StemJobTempCleanupScheduler` | 35 | Cron cleanup temp-файлов (см. [schedulers.md](../../processing/components/schedulers.md)) |
| 25 | `SiteUserResolver` | 29 | Получение текущего SiteUser (через `SiteAuthInterceptor`) |
| 26 | `SamplingConfig` | 27 | Конфигурация sampling rates |

**Итого**: в пакете `karaoke-web/.../services/` — 25 `.kt`-файлов.
`PollingCache` (строка 16) — исключение: с Pass 456 его реализация
живёт в `karaoke-app`, отсюда он только используется.

## Интерфейсы и Контракты | Interfaces and Contracts

Пакет — не один компонент с единым API: контракт каждого сервиса —
его Kotlin-класс (Spring-бин) и публичные методы. Ниже — фактическая
поверхность по исходникам `karaoke-web/.../services/*.kt`.

| Сервис | Публичный контракт (методы) |
|---|---|
| `SongShareLinkService` | `createLink`, `revokeLink`, `revokeLinkById`, `getCurrentForOwner`, `findLinkIdBySecret`, `tryClaim`, `heartbeat`, `release`, `validateShareSession`, `listLinksForUser`, `listSessionsForLink`; DTO — `CreateResult`, `OwnerLinkView`, `TryClaimResult`, `SessionView` |
| `StorageApiClientWeb` | Реализация `StorageApiClient`: `uploadFile`, `getFileUrl`, `downloadFile`, `deleteFile`, `listFiles`, `checkIfExists`/`fileExists` (WebClient reactive) |
| `EventsBuffer` | `add(EventRecord)`, `flush()`, `bufferSize()`, `isFlushing()`, `clear()`; SQL — `buildInsertSql` (internal) |
| `PaymentService` | `hasCredentials()`, `createPayment`, `createCartPayment`, `chargeRecurring`, `verifyAndFetch`, `newIdempotenceKey()` |
| `PriceService` | `computePrice`, `computeCartPrice` |
| `PlayerGestureUnlockService` | `registerClick`, `validateToken`, `issueDirectAccessToken`, `issueDirectAccessTokenForAssignment`, `issueDemoAccessToken`, `assignmentIdForToken`, `demoRangeForToken` |
| `SamplingFilter` | `shouldSkip(restName, parameters, siteUserId, anonId)`, `dedupCacheSize()` |
| `RateLimitInterceptor` | `preHandle` (`HandlerInterceptor`); настраиваемые поля `endpointName`, `limitPerMinute`; `bucketSize()`, `clear()` |
| `SiteUserTokenService` | `issueToken`, `resolveToken`, `revokeToken` |
| `SiteUserResolver` | `resolve(request): SiteUser?` |
| `DebugDbAccessGuard` | `isAllowed(properties, request)` |
| `DedupCache` | `isDuplicate(key)`, `size()`, `clear()` |
| `CaptchaConfigService` | `getClientKey()`, `getServerKey()` |
| `YandexCaptchaValidationService` | `validate(...)` |
| `KaraokeProperties` | `samplingConfig`, `eventsDedupTtlMs()`, `eventsRetentionDays`, `debugDbEnabled`, `debugDbAllowedIps`, `rateLimitSongPicturePerMinute`, `rateLimitSongVkImagePerMinute` |
| `SamplingConfig` | Data-holder rates (методов нет) |
| `ShareLinkSweeper` | `sweep()` — `@Scheduled(fixedDelayString = "${karaoke.share.sweep-interval-seconds:60}000")` |
| `StatsCacheScheduler` | `warmUp()`, `refreshHourly()`, `refreshIfDirty()` |
| `EventsRetentionScheduler` | `cleanup()` |
| `SubscriptionRenewalScheduler` | `renewExpiringSiteSubscriptions()` |
| `StemJobTempCleanupScheduler` | `cleanup()` |
| `SongReleaseAnnouncementScheduler` | `checkOnAir()` — `@Scheduled(fixedDelay = 5 мин, initialDelay = 60 с)` |
| `KaraokeWebService` | Конструктор-DI; публичных методов нет — инициализирует глобалы `WEBSOCKET`, `WEB_WORK_IN_CONTAINER`, `WEB_WORK_ON_SERVER`, `DB_*` |
| `WebKaraokeStorageServiceImpl` | Реализация-заглушка: все методы бросают `UnsupportedOperationException` |
| `ZakromaStreamProgress` | `resolveExpectedCount(albumId, album, onlyPublished, providedExpectedCount, authorCountFallback)` — object, чистая логика |

**`SongShareLinkService`** — единственный сервис с несколькими
контроллерами-потребителями: `PublicShareController`,
`PublicPlayerController`, `SiteShareLinksController` и `ShareLinkSweeper`
(проверено grep по `SongShareLinkService`).

**NB**: `token_hash` (НЕ `token`) — сам токен не хранится в БД,
безопасность (см. [security issue](internal-controllers.md)).
Исходный секрет — 32 байта `SecureRandom` (base64url) — отдаётся ровно
один раз при создании; `sha256Hex` — SHA-256.

**Hot path**: `/api/public/share/heartbeat` — каждый ~25s от
открытых share-линок (см. [song-share-link-service.md](song-share-link-service.md)).

## Логика и Алгоритмы | Logic and Algorithms

### Поток веб-аналитики (SamplingFilter → DedupCache → EventsBuffer)

1. Событие приходит в `POST /registerevent`; без `eventType` в теле —
   сразу `false` (см. [main-controller.md](main-controller.md)).
2. Для `EventType.CALL_REST` вызывается `SamplingFilter.shouldSkip(...)`:
   сначала dedup-ключ `(restName, canonical(parameters), anonId-or-userId)`
   с TTL 30 с (`karaoke-web.events.dedup-ttl-seconds`), затем sampling
   `random.nextInt(rate) == 0` (rate: анонимы 20, залогиненные 5,
   admin 1; все — `coerceAtLeast(1)`).
3. `EventsBuffer.add(record)`:
   - kill-switch `karaoke.web.events.batch-enabled` (default `false`) —
     sync INSERT, как раньше;
   - иначе запись в `ConcurrentLinkedQueue`; flush по `@Scheduled`
     каждые 5000 мс или досрочно при переполнении буфера
     (`karaoke.web.events.batch-max-buffer-size`, default 500) —
     backpressure в том же потоке;
   - flush — JDBC `addBatch()` + `executeBatch()`, двойной flush
     предотвращает `AtomicBoolean flushing`;
   - fail-open: при ошибке batch буфер очищается (событие логируется
     через SLF4J, потеря допустима — это метрики вовлечённости).
4. `SamplingConfig` (27 строк) — конфигурация rates, читается
   `KaraokeProperties.samplingConfig` на каждый вызов.

### Rate limit

`RateLimitInterceptor.preHandle` строит bucket по ключу
`"$ip|$endpointName"` и сравнивает `count > limitPerMinute`
(поле, default 60). `WebMvcConfig` (см. [config.md](config.md)) создаёт
два экземпляра с `endpointName = "song-picture"` /
`"song-vk-image"` и лимитами из `KaraokeProperties`
(`rateLimitSongPicturePerMinute`, `rateLimitSongVkImagePerMinute`).

### Share-линки (жизненный цикл)

1. **Создание** (`createLink`): SKIP-тег → `SongSkipped`;
   не готовый к публикации контент → `SongUnavailable`; далее лимиты
   `maxActivePerUser` (5), `maxGenerationsPerDay` (30),
   `maxReissuesPerSongPerHour` (3) → `LinkAlreadyActive`. Прежняя
   активная ссылка на ту же песню переводится в
   `active=false, revoke_reason='replaced'`. `expires_at` пишется как
   naive-МСК через `toMskLocalDateTime` (`setObject(..., Types.TIMESTAMP)`),
   чтобы не зависеть от TZ JVM.
2. **Claim** (`tryClaim`): rate-limit по IP
   (`claimRateLimitPerIpPerMin` = 10/мин); если по ссылке уже есть живой
   lease — возвращается существующий `sessionTokenHash` (то же
   устройство/вкладки); иначе считается число активных сессий, при
   `>= maxConcurrentSessions` (2) → `ConcurrentLimit` и
   `rejected_concurrent++`; создаётся строка в `tbl_song_share_sessions`
   и lease `now() + leaseTtlSeconds` (90 с).
3. **Heartbeat** (`heartbeat`): `active_session_lease_until = now() + leaseTtlSeconds`,
   `last_used_at = now()`; `executeUpdate() == 0` → `LeaseExpired`;
   отдельным UPDATE обновляется `last_seen_at` сессии.
4. **Release** (`release`): `finished_at = now()`, `result`
   нормализуется к одному из `ended | closed | timeout | revoked | replaced`
   (иначе `closed`), lease-поля ссылки обнуляются.
5. **Sweeper** (`ShareLinkSweeper.sweep`, каждые
   `karaoke.share.sweep-interval-seconds` = 60 с): lease-таймауты,
   истёкшие `expires_at`, потеря премиума владельцем, SKIP/будущая
   публикация песни; ошибки тика логируются и не роняют процесс.

### Инициализация `KaraokeWebService`

Конструктор биндит `@Value`-параметры (`work-in-container`,
`work-on-server`, `db-*-postgres-*`) в Kotlin-глобалы, инициализирует
`WEBSOCKET` (`SimpMessagingTemplate`) и подставляет заглушки
`KSS_APP`/`SAC_APP`. `SNS = SseNotificationService(objectMapper)` —
у karaoke-web нет `/subscribe`, emitters всегда пуст. Уборка бакетов
(`deleteAllEmptyBuckets`) намеренно не вызывается — karaoke-web не
ходит в MinIO напрямую.

### Заглушка `WebKaraokeStorageServiceImpl`

Все методы бросают `UnsupportedOperationException` (см. [storage
domain](../../storage/domain.md)). Реальный доступ — через nginx
или `StorageApiClientWeb`.

## Архитектурные решения

### Решение 1: Site-traffic-resilience (FR-006/007/010/011/013)

Караоке имеет **продвинутую защиту** от DDoS/накруток:
sampling + dedup + rate limit + PollingCache. Все настройки через
`KaraokeProperties` env. См. [KaraokeProperties.kt source].

### Решение 2: Read-only к MinIO

`karaoke-web` **не обращается к MinIO напрямую** (см.
[storage-flow.md](../../storage/components/storage-flow.md)). Только
через `StorageApiClientWeb` (для premium) или nginx path-proxy (для
public).

### Решение 3: `WebKaraokeStorageServiceImpl` — заглушка

Все методы бросают `UnsupportedOperationException` (см. [storage
domain](../../storage/domain.md)). Реальный доступ — через nginx
или `StorageApiClientWeb`.

## Зависимости | Dependencies

- **Storage** ([storage domain](../../storage/domain.md)) —
  `StorageApiClientWeb`, `WebKaraokeStorageServiceImpl`.
- **Caching** ([caching domain](../../caching/domain.md)) — `DedupCache`
  (реализация здесь), `PollingCache` (**Pass 456 — реализация в `karaoke-app`**,
  отсюда только используется).
- **Schedulers** ([schedulers.md](../../processing/components/schedulers.md)) —
  `ShareLinkSweeper`, `StatsCacheScheduler`, etc.
- **Monetization** ([monetization domain](../../monetization/domain.md)) —
  `PaymentService`, `PriceService`.

## Известные TODO

- [ ] **Каждый из 25 services** — детальный state/contracts (Pass 343+).
- [ ] **`SongShareLinkService`** (1130 строк!) — отдельный документ.
- [ ] **`EventsBuffer`** — детальный batch flow.
- [ ] **JWT** — алгоритм подписи, expiration.
- [ ] **YandexCaptcha** — детали.

## Changelog

- **Pass 481** (2026-09-27, spec `481-knowledge-domain-karaoke-web`): секции приведены к шаблону компонента. Автор: agent (Karaoke).
- **Pass 365** (2026-09-09): Initial. Автор: agent (Karaoke).
