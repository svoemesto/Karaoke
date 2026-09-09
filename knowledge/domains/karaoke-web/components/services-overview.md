# Component: karaoke-web services

> **Домен**: [karaoke-web](../domain.md)
> **Компонента**: детальный каталог 25 services.

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
| 20 | `YandexCaptchaValidationService` | 57 | Yandex SmartCaptcha validation (HTTP POST) |
| 21 | `DebugDbAccessGuard` | 41 | IP allowlist для DebugDbController |
| 22 | `StemJobTempCleanupScheduler` | 35 | Cron cleanup temp-файлов (см. [schedulers.md](../../processing/components/schedulers.md)) |
| 23 | `SiteUserResolver` | 29 | Получение текущего SiteUser (через `SiteAuthInterceptor`) |
| 24 | `SamplingConfig` | 27 | Конфигурация sampling rates |

## Детальные контракты

### `SongShareLinkService` (1130 строк, hot path)

**Файл**: `karaoke-web/.../services/SongShareLinkService.kt`.

**Логика** (по grep `@Service` + KDoc):

- CRUD для `SongShareLink` (см.
  [entities-catalog.md](../../catalog/components/entities-catalog.md#songsharelink)).
- Создание токена + хэш.
- Проверка активной сессии (lease + browser hash).
- Heartbeat endpoint.
- Rejected concurrent.
- Active session management.

**NB**: `token_hash` (НЕ `token`) — сам токен не хранится в БД,
безопасность (см. [security issue](internal-controllers.md)).

**Hot path**: `/api/public/share/heartbeat` — каждый ~25s от
открытых share-линок.

### `EventsBuffer` (297 строк, FR-109)

**Файл**: `karaoke-web/.../services/EventsBuffer.kt`.

**Логика**: batch INSERT в `tbl_events` вместо sync INSERT.

**Эффект** (по KDoc): снижает RPS INSERT на ≥80% (50 INSERT/5 сек
→ 1 batch).

**Kill-switch**: `karaoke.web.events.batch-enabled` (default `false` —
sync INSERT как раньше).

### `PaymentService` (294 строки, см. monetization)

См. [monetization domain](../../monetization/domain.md#paymentservice).
YooKassa WebClient + nginx proxy + env credentials.

### `PriceService` (207 строк, см. monetization)

См. [monetization domain](../../monetization/domain.md).
Расчёт цены с учётом `PromoRule` (priority, percent, valid period).

### `PlayerGestureUnlockService` (175 строк)

**Файл**: `karaoke-web/.../services/PlayerGestureUnlockService.kt`.

**Логика**: gesture unlock для мобильного плеера (touch/swipe).

**Hot path**: используется в `PlayerView.vue` на мобильных.

### `SamplingFilter` (146 строк, DDoS protection)

**Файл**: `karaoke-web/.../services/SamplingFilter.kt`.

**Логика**: 1/N sampling для WebEvent (см.
[entities-catalog.md](../../catalog/components/entities-catalog.md#webevent)).

**Rates**:

- Анонимы: 1/20.
- Залогиненные: 1/5.
- Admin: 1/1 (всё).

**SamplingConfig** (27 строк) — конфигурация rates.

### `RateLimitInterceptor` (118 строк)

**Файл**: `karaoke-web/.../services/RateLimitInterceptor.kt`.

**Логика**: per-endpoint rate limit (HTTP interceptor).

**Default**: 60/min per IP для `song-picture`, `song-vk-image`.

**Настройка**: `KaraokeProperties` env.

### `SiteUserTokenService` (92 строки)

**Файл**: `karaoke-web/.../services/SiteUserTokenService.kt`.

**Логика**: JWT-токены для `/api/public/account/*`.

### `KaraokeWebService` (88 строк, DI setup)

**Файл**: `karaoke-web/.../services/KaraokeWebService.kt`.

**Содержит**:
- `WEBSOCKET: SimpMessagingTemplate` (lateinit).
- `WEB_WORK_IN_CONTAINER`, `WEB_WORK_ON_SERVER` (через @Value).
- DB credentials (`DB_LOCAL_POSTGRES_USER` и т.д.).

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

## Связь с другими компонентами

- **Storage** ([storage domain](../../storage/domain.md)) —
  `StorageApiClientWeb`, `WebKaraokeStorageServiceImpl`.
- **Caching** ([caching domain](../../caching/domain.md)) — `DedupCache`,
  `PollingCache`.
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

- **Pass 365** (2026-09-09): Initial. Автор: agent (Karaoke).