---
id: domain-stats
title: "Domain: Stats (Статистика и аналитика)"
status: Active
slug: stats
related:
  - ../publishing/domain.md
  - ../catalog/domain.md
  - ../identity/domain.md
  - ../caching/domain.md
---

# Domain: Stats (Статистика и аналитика)

> Аналитический bounded context — `tbl_events` (сегментация посетителей),
> visitor → registration → premium воронка.
>

## Обзор контекста (Bounded Context)

**stats** — аналитический bounded context проекта. Отвечает за:

- Сбор событий посещений (`tbl_events`).
- Сегментация трафика (bot/real-user).
- Visitor → Registration → Premium воронка (см. `archive/docs/strategy/growth.md`).
- Аналитика аномалий трафика (см. фичу `187-site-traffic-anomaly-investigation`).

**Почему выделено**:

- Аналитика — отдельная подсистема (не смешивается с публикацией).
- Visitor/bot-сегментация — специфическая задача.
- Стратегия роста (monetization) — отдельный concern.

**Не путать** с [publishing](../publishing/domain.md):

- **publishing** — доступ к каталогу (эфиры, premium, подписки).
- **stats** — аналитика трафика и поведения пользователей.

`StatBySong` (счётчики главной страницы) — в [publishing](../publishing/domain.md),
а не здесь, чтобы избежать дублирования.

**Граница**: контекст НЕ отвечает за:

- бизнес-логику публикации (→ [publishing](../publishing/domain.md));
- сами песни (→ [catalog](../catalog/domain.md));
- пользователей (→ [identity](../identity/domain.md));
- хранение кешей (→ [caching](../caching/domain.md), но stats их использует).

## Ubiquitous Language | Единый язык

| Термин | Определение | Пример |
| --- | --- | --- |
| **tbl_events** | Append-only таблица событий (visitor/bot/registration/play) | новая строка на каждый визит |
| **Visitor** | Уникальный посетитель (по hash cookies/IP) | `Visitor.id=abc123` |
> **[WARN] Поправка Pass 474: сегментация трафика НЕ реализована.**
> `BotScore`, `VisitorType` (`REAL_USER`/`GOOD_BOT`/`BAD_BOT`),
> `BotDetectionService`, `StatsService`, `SiteEvent` и колонка
> `bot_score` в коде и миграциях отсутствуют (grep по
> `*.kt`/`*.sql`/`*.vue` — 0 вхождений). Описанная ниже модель
> воронки — ЗАДУМАННАЯ (намерение зафиксировано в спеке 369 и в
> `archive/docs/strategy/growth.md`), но не реализованная.
>
> Что реально есть: события хранит `WebEvent`
> (`model/WebEvent.kt:110`), отдаёт `StatsController.kt` (`:93`,
> `:149`, `:202`); типы событий — `EventTypes.kt` (6 значений,
> см. [dictionaries](components/dictionaries.md)).

| Термин (задумано) | Значение | Пример |
| --- | --- | --- |
| **BotScore** | 0..1, вероятность что посетитель — бот | не реализовано |
| **REAL_USER** | `botScore < 0.5` | не реализовано |
| **GOOD_BOT** | поисковик | не реализовано |
| **BAD_BOT** | `botScore >= 0.7` | не реализовано |
| **Воронка (Funnel)** | visitor → registration → premium | см. `archive/docs/strategy/growth.md` |
| **StatsCacheScheduler** | `@Scheduled` фикс-rate update | каждые 60 мин |
| **BotDetectionService** | Классификация трафика по BotScore | не реализовано — файла нет |

Полный словарь `VisitorType` — см. [publishing dictionaries](../publishing/components/dictionaries.md).
Полный словарь `EventType` — см. [dictionaries](components/dictionaries.md).
Алгоритм воронки — см. [event-funnel](components/event-funnel.md).

## Aggregate Roots

**[WARN] Поправка Pass 474: агрегата `SiteEvent` нет.** Реальная
сущность той же таблицы — `WebEvent` (`model/WebEvent.kt:110`);
полей `visitor_id` и `bot_score` в схеме нет. Прежнее описание:

- ~~**SiteEvent (`tbl_events`)**~~ — одна запись на визит/событие,
  Identity = `id`; содержит `visitor_id`, `event_type`, `created_at`,
  `bot_score`, `country`, `referrer`, `path`.

## Entities

- **StatsCache** (singleton): `AtomicInteger`-кеш для производительности;
  обновляется `StatsCacheScheduler`. Детали — в [caching domain](../caching/domain.md).

## Value Objects

- **BotScore** (0..1): вероятность, что посетитель — бот.
  Детали и пороги — в [publishing dictionaries](../publishing/components/dictionaries.md).
- **VisitorType**: enum — REAL_USER | GOOD_BOT | BAD_BOT.
  В текущей реализации publishing и stats используют **разные пороги**
  (см. ловушки ниже).
- **EventType**: enum — VISIT | REGISTRATION | PREMIUM_PURCHASE |
  PLAY_START | PLAY_COMPLETE | ...

## Domain Events

- **SiteVisitRecorded**: новый визит в `tbl_events` (`eventType=VISIT`).
- **BotDetected**: `botScore >= 0.5` — сегментация в бота.
- **PremiumPurchased**: `eventType=PREMIUM_PURCHASE`.
- **StatsCacheRefreshed**: раз в час `StatsCacheScheduler` пересчитывает
  агрегаты.

## Domain Invariants | Инварианты и правила бизнеса

1. **`tbl_events` append-only**: никогда не удалять и не обновлять
   события, только INSERT. Это аналитическая таблица.
2. **`BotScore ∈ [0.0, 1.0]`**: пороги сегментации зафиксированы,
   см. [publishing dictionaries](../publishing/components/dictionaries.md).
3. **`BAD_BOT` не учитывается** в счётчиках главной страницы
   (`realUsersLast24h`). Подробнее — в [publishing stats-cache](../publishing/components/stats-cache.md).
4. **Воронка считается раз в час**: visitor24h → registration24h →
   premium24h, для отчётов по росту.
5. **Old vs new BotScore thresholds**: статистика legacy использовала
   `< 0.3` / `>= 0.7`, новые пороги (publishing) — `< 0.5` / `>= 0.7`.
   При миграции данных нужно привести к единому стандарту (TODO).

## Публичные контракты (API)

### Public API (через `karaoke-public`)

- `GET /api/public/stats/summary` — общая сводка.
- `GET /api/public/stats/by-song` — статистика по песням.
- `GET /api/public/stats/by-type` — события по типам.

### Admin API (через `webvue3`)

- `GET /api/admin/stats/visitor-segments` — сегментация трафика.
- `GET /api/admin/stats/funnel` — воронка visitor→registration→premium.
- 11 параллельных endpoint'ов (см. фичу `174-fix-stats-connection-leak`).

## Структура компонентов (C4 L3)

- [dictionaries](components/dictionaries.md) — В stats-домене часто возникают ситуации, когда:
- [event-funnel](components/event-funnel.md) — В Karaoke есть **воронка роста** (см. `archive/docs/strategy/growth.md`): **visitor → registration → premium**. Эта…

## Связанные фичи

- `187-site-traffic-anomaly-investigation` — расследование аномалии
  трафика, baseline-funnel.
- `176-authors-new-albums-badge` — бейдж использует `haveNewAlbum`
  (stats-like сигнал, не сам stats).
- `144-homepage-latest-news` — главная страница.
- `180-og-seo-html` — бот-трафик — большой % (см. 187).
- `174-fix-stats-connection-leak` — фикс connection leak в admin
  stats endpoints.

## Архитектура

- **Модели**: `karaoke-app/.../model/Stat.kt`, `SiteEvent.kt`.
- **Сервисы**: `karaoke-app/.../service/StatsService.kt`, `BotDetectionService.kt`.
- **Scheduler**: `karaoke-app/.../schedulers/StatsCacheScheduler.kt` (60-минутный).
- **БД**: `tbl_settings` (legacy), `tbl_events`.

## Код (физическая реализация)

- Frontend: `karaoke-public/src/components/StatsView.vue`, `LatestNewsBlock.vue`.
- Frontend: `webvue3/src/views/StatsView.vue` (admin — ленивая загрузка по
  активной вкладке, `loadDataForActiveTab`; было «11 параллельных endpoint'ов»
  до спеки 362, см. `knowledge/system/frontend/store-stats.md`).
- Приёмка вкладок админ-статистики: `webvue3/scripts/check-stats-tabs.mjs`
  (spec `478-fix-admin-stats-tabs`, OP #184).
- Frontend: `webvue3/src/store/modules/stats/store.js` (Vuex module с StatsSnapshot).
- Backend API: `GET /api/public/stats/{summary,by-type,by-song,countries,referrers,by-year,webevents}`.
