---
status: Active
slug: publishing
type: bounded-context
related:
  - ../features/187-site-traffic-anomaly-investigation.md
  - ../domain/identity.md
  - ../architecture/L3-components.md
---

# Bounded Context: publishing (Публикация)

> Доступ пользователей к каталогу: эфир, подписка, premium.

## Назначение

Publishing — контекст, отвечающий за **доставку каталога пользователям**:
когда песня становится эфирной, кто видит exclusive, как работает подписка.
Это монетизационная зона проекта и активная зона роста (см.
`docs/strategy/growth.md`).

Контекст **read-heavy** (публичный сайт делает много запросов для проверки
доступа) + **write-light** (смена publishDate / подписки — редкие операции).

## Aggregate Roots

- **PublishWindow (Окно публикации)**: окно, в которое песня доступна публично.
  Identity = `songId`. Содержит `publishDate`, `publishTime`, флаг `isExclusive`.
  Инвариант: `publishDate + publishTime` либо в прошлом (эфир), либо в будущем
  (premium-only).

- **Subscription (Подписка)**: подписка пользователя на premium-доступ.
  Identity = `id`. Содержит `userId`, `startDate`, `endDate`, `status`.

- **SiteStats (Статистика сайта)**: счётчики для главной страницы
  (StatBySong). Identity = singleton. `AtomicInteger`-кеш, обновляется
  `StatsCacheScheduler` каждый час.

## Entities

- **SiteUser (Пользователь сайта)**: см. identity context (cross-reference).
- **PublishEvent (Событие публикации)**: лог публикаций (для аудита).
- **SiteEvent (Событие трафика)**: см. tbl_events (visitor, bot_score, ...).

## Value Objects

- **AccessMode (open | premium-only)**: режим доступа к песне.
- **BotScore (0.0..1.0)**: вероятность что посетитель — бот.
- **VisitorType (real_user | good_bot | bad_bot)**: сегмент трафика.

## Domain Events

- **SongPublished**: `publishDate` истёк, песня стала эфирной.
- **SubscriptionStarted**: пользователь оформил подписку.
- **SubscriptionExpired**: подписка истекла.
- **SongMadeExclusive**: песня переведена в premium-only.

## Ubiquitous Language (глоссарий)

| Термин | Определение | Пример в коде |
|--------|-------------|----------------|
| **Эфир (On-Air)** | Песня в открытом доступе (`publishDate` истёк) | `Stat.kt` (формула "В открытом доступе") |
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

## Связанные фичи

- [187-site-traffic-anomaly-investigation.md](../features/187-site-traffic-anomaly-investigation.md) — сегментация трафика

## Связанные LiveDocs

- Architecture: [L3-components.md](../architecture/L3-components.md)
- Domain: [identity.md](identity.md) (SiteUser для подписок)

## Код

- Модели: `karaoke-app/src/main/kotlin/.../model/Song.kt` (publishDate/isExclusive), `Subscription.kt`
- Сервисы: `StatsService.kt`, `StatsCacheScheduler.kt`
- Контроллеры: `PublicApiController.kt` (`/api/stats`), `MainController.kt` (Thymeleaf)
- SQL: `deploy/karaoke-db/<NNN>_tbl_settings_publish.sql`, `<NNN>_tbl_subscriptions.sql`
- Frontend: `karaoke-public/src/store/modules/stats.js`, `HomeView.vue`

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14