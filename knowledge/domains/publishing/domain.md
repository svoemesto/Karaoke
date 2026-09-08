---
id: domain-publishing
title: "Domain: Publishing (Публикация)"
status: Active
slug: publishing
related:
  - ../catalog/domain.md
  - ../identity/domain.md
  - ../rendering/domain.md
  - ../processing/domain.md
  - ../caching/domain.md
  - ../../adr/0001-raw-jdbc.md
---

# Domain: Publishing (Публикация)

> Доступ пользователей к каталогу: эфир, подписка, premium.
>
> Drill-down (legacy): [livedocs/domain/publishing.md](../../../livedocs/domain/publishing.md).

## Обзор контекста (Bounded Context)

Publishing — контекст, отвечающий за **доставку каталога пользователям**:
когда песня становится эфирной, кто видит exclusive, как работает
подписка. Это монетизационная зона проекта и активная зона роста
(см. `docs/strategy/growth.md`).

Контекст **read-heavy** (публичный сайт делает много запросов для
проверки доступа) + **write-light** (смена `publishDate` / подписки —
редкие операции).

**Граница**: контекст НЕ отвечает за:

- сами песни и метаданные (→ [catalog](../catalog/domain.md));
- аутентификацию пользователей (→ [identity](../identity/domain.md));
- монетизационный флоу оплаты (TODO: payment context, не выделен).

## Ubiquitous Language | Единый язык

| Термин | Определение | Пример в коде |
| --- | --- | --- |
| **Эфир (On-Air)** | Песня в открытом доступе (`publishDate` истёк) | `Stat.kt` (формула «В открытом доступе») |
| **Эфирная песня** | То же, что On-Air | `Song.kt` |
| **Exclusive** | Доступна только по подписке | `tbl_settings.is_exclusive` |
| **premium-only** | Доступна только подписчикам | `AccessMode.premium-only` |
| **Подписка (Subscription)** | Premium-доступ на N дней | `Subscription.kt` |
| **Visitor (посетитель)** | Один визит на сайт | `tbl_events` |
| **BotScore** | 0.0..1.0, вероятность что это бот | `tbl_events.bot_score` |
| **Grandfathered** | Старая песня, ставшая эфирной до введения premium | обсуждается (Pass 2+) |
| **Воронка** | visitor→registration→premium | `docs/strategy/growth.md` |
| **publish_date / publish_time** | Когда песня станет эфирной | `tbl_settings.publish_date/time` |
| **StatBySong** | Счётчики главной страницы | `Stat.kt`, `StatsCacheScheduler` |

Полный словарь магических кодов (`AccessMode`, `VisitorType`,
`BotScore`) — см. [dictionaries](components/dictionaries.md). Детали
по AtomicInteger-кешу статистики — см. [stats-cache](components/stats-cache.md).

## Aggregate Roots

- **PublishWindow (Окно публикации)**: окно, в которое песня доступна
  публично. Identity = `songId`. Содержит `publishDate`, `publishTime`,
  флаг `isExclusive`.
  Инварианты:
  - `publishDate + publishTime` либо в прошлом (эфир), либо в будущем
    (premium-only);
  - `isExclusive=true` означает premium-only независимо от `publishDate`.

- **Subscription (Подписка)**: подписка пользователя на premium-доступ.
  Identity = `id`. Содержит `userId`, `startDate`, `endDate`, `status`.
  Инварианты:
  - `endDate > startDate`;
  - `status ∈ {active, expired, cancelled}`.

- **SiteStats (Статистика сайта)**: счётчики для главной страницы
  (`StatBySong`). Identity = singleton. `AtomicInteger`-кеш, обновляется
  `StatsCacheScheduler` каждый час.

## Entities

- **SiteUser (Пользователь сайта)**: см. [identity context](../identity/domain.md)
  (cross-reference).
- **PublishEvent (Событие публикации)**: лог публикаций (для аудита).
- **SiteEvent (Событие трафика)**: см. `tbl_events` (visitor, bot_score, ...).

## Value Objects

- **AccessMode (open | premium-only)**: режим доступа к песне.
- **BotScore (0.0..1.0)**: вероятность что посетитель — бот.
- **VisitorType (real_user | good_bot | bad_bot)**: сегмент трафика.

## Domain Events

- **SongPublished**: `publishDate` истёк, песня стала эфирной.
- **SubscriptionStarted**: пользователь оформил подписку.
- **SubscriptionExpired**: подписка истекла.
- **SongMadeExclusive**: песня переведена в premium-only.

## Domain Invariants | Инварианты и правила бизнеса

1. **Гранды (grandfathered)**: песни, ставшие эфирными до введения
   premium, остаются в открытом доступе, даже если их `publishDate`
   перевести в будущее. Это историческое решение, см. `docs/strategy/growth.md`.
2. **`isExclusive=true` блокирует открытый доступ** независимо от
   `publishDate`. Чтобы сделать песню снова публичной — нужен отдельный
   эпик.
3. **Подписка активна** если `now() ∈ [startDate, endDate]` И `status=active`.
   Просроченные подписки (`endDate < now()`) автоматически переходят в
   `status=expired` через scheduler (TODO: точное расписание).
4. **`StatBySong` кеш обновляется раз в час**: `StatsCacheScheduler`
   читает из БД и обновляет AtomicInteger. Не чаще, чтобы не нагружать БД.
5. **`BotScore > 0.7` = bad_bot**: такие визиты не учитываются в
   счётчиках главной страницы (см. `Stat.kt`).

## Публичные контракты (API)

### Internal API

- `StatsService` — счётчики для главной страницы.
- `StatsCacheScheduler` — обновление кеша каждый час.

### Public API (через `karaoke-public`)

- `GET /api/stats` — публичные счётчики.
- `GET /api/songs/{id}/access` — проверка доступа (`AccessMode`,
  `SubscriptionStatus`).

## Структура компонентов (C4 L3)

- [dictionaries](components/dictionaries.md) — `AccessMode` enum,
  `VisitorType`, `BotScore` thresholds. Магические коды публикации.
- [stats-cache](components/stats-cache.md) — `StatBySong`
  AtomicInteger-кеш, обновление через `StatsCacheScheduler` каждый
  час. Hot-path cache invalidation.

## Связанные фичи

- `187-site-traffic-anomaly-investigation` — сегментация трафика.

## Связанные ADR

- [0001-raw-jdbc](../../adr/0001-raw-jdbc.md) — сырой JDBC.

## Код (физическая реализация)

- Модели: `karaoke-app/src/main/kotlin/.../model/Song.kt` (`publishDate`/
  `isExclusive`), `Subscription.kt`
- Сервисы: `StatsService.kt`, `StatsCacheScheduler.kt`
- Контроллеры: `PublicApiController.kt` (`/api/stats`), `MainController.kt` (Thymeleaf)
- SQL: `deploy/karaoke-db/<NNN>_tbl_settings_publish.sql`,
  `<NNN>_tbl_subscriptions.sql`
- Frontend: `karaoke-public/src/store/modules/stats.js`, `HomeView.vue`
