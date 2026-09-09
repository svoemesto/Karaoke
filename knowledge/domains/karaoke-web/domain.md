---
id: domain-karaoke-web
title: "Domain: karaoke-web (прод-сервер, public API)"
status: Active
slug: karaoke-web
related:
  - ../storage/domain.md
  - ../persistence/domain.md
  - ../sse/domain.md
  - ../monetization/domain.md
---

# Domain: karaoke-web (прод-сервер)

> Bounded context для прод-сервера `karaoke-web` — публичный API,
> YooKassa webhook, share-ссылки, public composables. Pass 362-365
> Knowledge-аудита.

## Обзор контекста (Bounded Context)

`karaoke-web` — **второй бэкенд проекта** (помимо `karaoke-app`),
запускается на **прод-сервере** (отдельно от admin-машины).

**Чем отличается от `karaoke-app`**:

- `karaoke-app` (admin-машина) — рендер видео, async-очередь,
  HealthReport, **полный read+write к MinIO**.
- `karaoke-web` (прод) — публичный API для сайта, **read-only к MinIO**
  (через nginx-proxy), **только публичные** эндпоинты, **только
  разрешённые операции** для пользователя.

**Граница**: контекст НЕ отвечает за:

- Обработку subprocess'ов — это `karaoke-app` (Pass 341 P1).
- Async-очередь задач — это `karaoke-app`.
- Хранение файлов в MinIO — это [storage domain](../storage/domain.md).

## Архитектурные решения

### Решение 1: WebSocket/SSE через karaoke-app

`karaoke-web` НЕ имеет своего SSE/WebSocket. Real-time события
приходят через `karaoke-app` (который на admin-машине). На проде
`karaoke-web` подключается к SSE на `karaoke-app` через nginx proxy
(обратный прокси).

### Решение 2: Read-only MinIO через nginx

`karaoke-web` не обращается к MinIO напрямую (см.
[storage-flow.md](../storage/components/storage-flow.md)). Публичные
картинки/аудио идут через nginx path-proxy, премиум-файлы
(StemJob) — через `StorageApiClientWeb` (WebClient + reactor).

### Решение 3: Public DTO ≠ Admin DTO

См. [dtos.md](../integration/components/dtos.md). `SongPublicDto` ≠
`SongDTO` — публичный API скрывает internal-флаги.

### Решение 4: Site-traffic-resilience (FR-006/007/010/011/013)

Караоке-проект имеет **продвинутую защиту от DDoS / накруток**:
sampling, dedup, rate limiting. Все настройки — через
`KaraokeProperties` (env). См.
[external-api-clients.md](../integration/components/external-api-clients.md).

## Ubiquitous Language | Единый язык

| Термин | Определение |
|---|---|
| **`WEB_WORK_IN_CONTAINER`** | Запущен в Docker (true) или прямо на хосте (false). |
| **`WEB_WORK_ON_SERVER`** | На проде (true) или admin-машине (false). |
| **`WORKING_DATABASE`** | `Connection.local()` (singleton). |
| **`SamplingConfig`** | Конфигурация sampling rates для WebEvent. |
| **`SamplingFilter`** | Servlet filter — отбрасывает события по rate. |
| **`RateLimitInterceptor`** | HTTP interceptor — per-endpoint limits. |
| **`SiteAuthInterceptor`** | Защита `/api/public/account/*` (см. `config/`). |
| **`PublicCartController`** | Публичная корзина (НЕ в `cart/` домене). |

## Связь с другими компонентами

- **Storage** ([storage domain](../storage/domain.md)) —
  `WebKaraokeStorageServiceImpl` (заглушка), `StorageApiClientWeb`.
- **Persistence** ([persistence domain](../persistence/domain.md)) —
  `KaraokeConnection` через `Connection.local()`.
- **SSE** ([sse domain](../sse/domain.md)) — real-time обновления
  через `karaoke-app`.
- **Monetization** ([monetization domain](../monetization/domain.md)) —
  `PaymentService`, `PriceService`.
- **Caching** ([caching domain](../caching/domain.md)) —
  `DedupCache`, `PollingCache` — реализации.
- **Schedulers** ([schedulers.md](../processing/components/schedulers.md)) —
  фоновые задачи karaoke-web.

## Структура (73 Kotlin файла)

```
karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/
├── KaraokeWebApplication.kt     # @SpringBootApplication
├── Connection.kt                  # Singleton Connection factory
├── WorkingDatabase.kt             # WORKING_DATABASE = Connection.local()
├── TypographUtils.kt              # Типографика (кавычки, тире)
├── StatBySong.kt                  # Read-only счётчики (см. caching/domain.md)
├── config/
│   ├── WebMvcConfig.kt            # Interceptors, CORS
│   ├── WebShareProperties.kt      # share-линки настройки
│   ├── WebClientConfig.kt         # WebClient beans (yookassa, etc.)
│   └── SiteAuthInterceptor.kt     # Auth для /api/public/account/*
├── controllers/                   # 26 контроллеров
│   ├── MainController.kt          # Thymeleaf HTML
│   ├── Public*Controller.kt       # 20 публичных REST endpoints
│   ├── Internal*Controller.kt     # 2 internal (admin→web)
│   ├── DebugDbController.kt       # /api/public/debug/db (защищённый)
│   ├── SiteShareLinksController.kt # Public share-ссылки
│   └── WebSocketConfig.kt         # /ws (SSE)
├── services/                      # 25 сервисов
│   ├── KaraokeWebService.kt        # Главный сервис
│   ├── KaraokeProperties.kt        # Env-binding (~20 настроек)
│   ├── PaymentService.kt          # YooKassa wrapper
│   ├── PriceService.kt            # Расчёт цены с промо
│   ├── SongShareLinkService.kt     # Share-ссылки (CRUD)
│   ├── SiteUserResolver.kt        # Получение SiteUser
│   ├── SiteUserTokenService.kt    # JWT-токены
│   ├── YandexCaptchaValidationService.kt
│   ├── PlayerGestureUnlockService.kt
│   ├── WebKaraokeStorageServiceImpl.kt # ЗАГЛУШКА (см. storage)
│   ├── StorageApiClientWeb.kt      # WebClient reactive
│   ├── DedupCache.kt               # См. caching/web-caches.md
│   ├── PollingCache.kt             # См. caching/web-caches.md
│   ├── SamplingFilter.kt           # DDoS protection
│   ├── SamplingConfig.kt
│   ├── RateLimitInterceptor.kt
│   ├── DebugDbAccessGuard.kt
│   ├── EventsBuffer.kt
│   ├── EventsRetentionScheduler.kt
│   ├── ShareLinkSweeper.kt
│   ├── SongReleaseAnnouncementScheduler.kt
│   ├── StatsCacheScheduler.kt
│   ├── StemJobTempCleanupScheduler.kt
│   └── SubscriptionRenewalScheduler.kt
└── dto/                           # 8 публичных DTO
    ├── AuthorTilePublicDto.kt
    ├── HistoryEntryDto.kt
    ├── PagedSongsDto.kt
    ├── SongPublicDto.kt
    ├── ZakromaAlbumMetaPublicDto.kt
    ├── ZakromaPublicDto.kt
    ├── ZakromaStreamMessageDto.kt
    └── ZakromaStreamMetricDto.kt
```

## Domain Invariants

1. **`WORKING_DATABASE = Connection.local()`** — единственный БД на
   проде. Только LOCAL, НЕ remote (нет двух-БД на проде, она вся
   одна).
2. **Все контроллеры `Public*` доступны БЕЗ авторизации** (кроме
   `/api/public/account/*` через `SiteAuthInterceptor`).
3. **`StorageApiClientWeb` — единственный клиент MinIO** на karaoke-web.
4. **Cron/scheduler таски** — только безопасные (retention, cleanup,
   stats). Опасные (auto-publish, sync) — на admin-машине.

## Hot paths

- **Public Player** (`/api/public/player/{id}/fileminus.mp3` и т.д.) —
  каждый запрос на воспроизведение.
- **`/api/public/news/since`** — polling, PollingCache TTL=60s.
- **`/api/public/account/chat/unreadcount`** — polling, TTL=10s.
- **`/api/public/share/heartbeat`** — heartbeat, TTL=15s.
- **`/api/public/song-picture/{id}`** — rate limit 60/min.
- **`/api/public/song-vk-image/{id}`** — rate limit 60/min.

## Известные TODO

- [ ] **Каждый из 26 controllers** — детальный endpoint-обзор (Pass 343+).
- [ ] **Каждый из 25 services** — детальный state (Pass 343+).
- [ ] **`SiteAuthInterceptor`** — детальная логика.
- [ ] **`WebMvcConfig`** — interceptors, CORS.
- [ ] **YooKassa webhook** — детальный flow (Pass 343+).

## Changelog

- **Pass 362** (2026-09-09): Initial. Автор: agent (Karaoke).