---
status: Active
slug: stats
type: bounded-context
related:
  - ../domain/publishing.md
  - ../domain/catalog.md
  - ../domain/identity.md
  - ../features/187-site-traffic-anomaly-investigation.md
  - ../architecture/L3-components.md
---

# Bounded Context: stats (Статистика и аналитика)

> Аналитический bounded context — StatBySong (главная страница),
> `tbl_events` (сегментация посетителей), `StatsCacheScheduler`.

## Назначение

**stats** — аналитический bounded context проекта. Отвечает за:
- Сбор событий посещений (`tbl_events`).
- Агрегаты для главной страницы публичного сайта (StatBySong).
- Visitor → Registration → Premium воронка (см. `docs/strategy/growth.md`).
- Аналитика аномалий трафика (см. `features/187-site-traffic-anomaly-investigation.md`).

**Почему выделено**:
- Аналитика — отдельная подсистема (не смешивается с публикацией).
- Visitor/bot-сегментация — специфическая задача.
- Стратегия роста (monetization) — отдельный concern.

**Не путать** с `publishing` (он про доступ к каталогу, эфиры, premium).

## Aggregate Roots

- **StatBySong** (snapshot, без FK): счётчики главной страницы — топ-N
  песен по play_count / last_played / В эфире / Включая премиум / Сборники и т.п.
- **SiteEvent** (`tbl_events`): одна запись на визит/событие.

## Entities

- **StatsCache** (singleton): `AtomicInteger`-кеш для производительности;
  обновляется `StatsCacheScheduler` (см. `cache-invalidation.md`).

## Value Objects

- **BotScore** (0..1): вероятность, что посетитель — бот.
- **VisitorType**: enum — REAL_USER | GOOD_BOT | BAD_BOT.
- **EventType**: enum — VISIT | REGISTRATION | PREMIUM_PURCHASE | PLAY_START | COMPLETE...

## Domain Events

- **SiteVisitRecorded**: новый визит в `tbl_events` (`eventType=VISIT`).
- **BotDetected**: `botScore >= 0.5` — сегментация в бота.
- **PremiumPurchased**: `eventType=PREMIUM_PURCHASE`.
- **StatsCacheRefreshed**: раз в час `StatsCacheScheduler` пересчитывает
  агрегаты.

## Ubiquitous Language

| Термин | Определение | Пример |
|--------|-------------|--------|
| **StatBySong** | Счётчик для конкретной песни (plays, last_played) | `Song.id=12345, play_count=42` |
| **tbl_events** | Append-only таблица событий (visitor/bot/registration/play) | новая строка на каждый визит |
| **Visitor** | Уникальный посетитель (по hash cookies/IP) | `Visitor.id=abc123` |
| **BotScore** | 0..1, вероятность что посетитель — бот | `botScore=0.92` — bad bot |
| **Bot** | Автоматический сканер (Googlebot, YandexBot, ...) | см. `architecture/L1-system-context.md` |
| **REAL_USER** | `botScore < 0.3` | реальный человек |
| **GOOD_BOT** | `0.3 <= botScore < 0.7` (поисковик) | Googlebot, YandexBot |
| **BAD_BOT** | `botScore >= 0.7` | спам-сканеры |
| **Воронка (Funnel)** | visitor → registration → premium | см. `docs/strategy/growth.md` |
| **StatSnapshot** | Атомарный снимок StatBySong (cache) | раз в час пересчитывается |
| **StatsCacheScheduler** | @Scheduled фикс-rate update | каждые 60 мин |

## Связанные фичи

- [features/187-site-traffic-anomaly-investigation.md](../features/187-site-traffic-anomaly-investigation.md) —
  расследование аномалии трафика, baseline-funnel.
- [features/176-authors-new-albums-badge.md](../features/176-authors-new-albums-badge.md) — бейдж
  использует `haveNewAlbum` (stats-like сигнал, не сам stats).
- [features/144-homepage-latest-news.md](../features/144-homepage-latest-news.md) — главная страница.
- [features/180-og-seo-html.md](../features/180-og-seo-html.md) — бот-трафик — большой % (см. 187).
- [architecture/observability.md](../architecture/observability.md) — `tbl_events` метрики.

## Архитектура

- **Модели**: `karaoke-app/.../model/Stat.kt`, `SiteEvent.kt`.
- **Сервисы**: `karaoke-app/.../service/StatsService.kt`, `BotDetectionService.kt`.
- **Scheduler**: `karaoke-app/.../schedulers/StatsCacheScheduler.kt` (60-минутный).
- **БД**: `tbl_settings` (legacy — поле `tbl_event_*`? в TODO Pass N+), `tbl_events`.

## Код

- Frontend: `karaoke-public/src/components/StatsView.vue`, `LatestNewsBlock.vue`.
- Frontend: `webvue3/src/views/StatsView.vue` (admin — 11 параллельных endpoint'ов,
  см. [features/174-fix-stats-connection-leak.md](../features/174-fix-stats-connection-leak.md)).
- Frontend: `webvue3/src/store/modules/stats/store.js` (Vuex module с StatsSnapshot).
- Backend API: `GET /api/public/stats/summary`, `/by-type`, `/by-song`, `/countries`,
  `/referrers`, `/by-year`, `/webevents`.

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14