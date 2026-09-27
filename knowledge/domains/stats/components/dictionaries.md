# Component: dictionaries

> **Домен**: [stats](../domain.md)
> **Компонент**: централизованное хранение магических кодов аналитики:
> `EventType`, типы событий для воронки.

## Ответственность | Responsibility

В stats-домене часто возникают ситуации, когда:

- новый тип события нужно добавить в `tbl_events` (например,
  `PLAY_COMPLETE` для новой фичи);
- аналитик хочет посчитать конверсию `VISIT → REGISTRATION → PREMIUM_PURCHASE`;
- сегмент трафика (`REAL_USER` / `GOOD_BOT` / `BAD_BOT`) нужно
  интерпретировать одинаково в разных отчётах.

Эта компонента — единственное место, где эти константы определены.
Использование литералов в L3-спецификациях и коде без ссылки на эту
страницу — **критический дефект** (см. `audit-living-docs` «Magic Codes»).

## Интерфейсы и Контракты | Interfaces and Contracts

### `EventType` — тип события в `tbl_events`

**[WARN] Поправка Pass 474: значения ниже НЕ совпадают с кодом.**
Реальный `model/EventTypes.kt` (`:12-22`) содержит шесть значений:
`CALL_REST`, `CLICK_TO_LINK`, `PLAY`, `PLAYER`, `ENGAGEMENT`, `UI`
(плюс `fromDb(dbValue)`). Пересечений с таблицей ниже нет.
Таблица оставлена как описание ЗАДУМАННОЙ (не реализованной)
модели воронки — см. [domain](../domain.md).

| Значение (задумано, в коде отсутствует) | Описание |
| --- | --- |
| `VISIT` | Любой визит на публичный сайт. |
| `REGISTRATION` | Регистрация нового пользователя. |
| `PREMIUM_PURCHASE` | Покупка подписки. |
| `PLAY_START` | Начало проигрывания песни. |
| `PLAY_COMPLETE` | Полное прослушивание (≥ 90% длины). |
| `SHARE_LINK_OPEN` | Открытие share-link. |
| `SHARE_LINK_CLAIM` | Claim guest access. |

**Место определения**: `karaoke-app/.../model/EventTypes.kt` (**множественное число**; файла `EventType.kt` нет — Pass 474).

**Использование**:

- В БД: колонка `tbl_events.event_type` (varchar).
- В API: поле `SiteEventDTO.eventType`.
- В воронке: фильтр `WHERE event_type = ?`.

**Запрещено**: строковые литералы `"VISIT"`/`"REGISTRATION"`/...

### `BotScore` — пороги

**[WARN] Дублирование с [publishing dictionaries](../../publishing/components/dictionaries.md)**.

| Диапазон | VisitorType (stats legacy) | VisitorType (publishing new) |
| --- | --- | --- |
| `0.0..0.3` | `REAL_USER` | — |
| `0.3..0.5` | `GOOD_BOT` | `REAL_USER` |
| `0.5..0.7` | `GOOD_BOT` | пограничная |
| `0.7..1.0` | `BAD_BOT` | `BAD_BOT` |

**Конфликт**: текущая реализация stats использует пороги `0.3/0.7`,
publishing — `0.5/0.7`. **TODO**: привести к единому стандарту
(рекомендую publishing-схему `0.5/0.7`, более либеральная).

**Запрещено**: использовать литералы `0.3`, `0.5`, `0.7` в коде —
только через константы `StatsService.BOT_SCORE_*` или
`PublishingService.BOT_SCORE_*`.

## Логика и Алгоритмы | Logic and Algorithms

### Алгоритм INSERT в `tbl_events`

```kotlin
fun recordEvent(eventType: EventType, visitorId: String, botScore: Double) {
    // [1] Вычислить visitorType по botScore
    val visitorType = when {
        botScore < 0.3 -> VisitorType.REAL_USER   // legacy, см. [WARN]
        botScore < 0.7 -> VisitorType.GOOD_BOT
        else -> VisitorType.BAD_BOT
    }

    // [2] INSERT в tbl_events (append-only)
    INSERT INTO tbl_events (
        visitor_id, event_type, bot_score, visitor_type,
        created_at, country, referrer, path
    ) VALUES (?, ?, ?, ?, now(), ?, ?, ?)

    // [3] Если это PREMIUM_PURCHASE — дёрнуть publishing domain:
    //     SubscriptionService.create(...)
}
```

**[WARN]** Шаг [3] — **межконтекстный вызов**. В строгом DDD — через
доменное событие (`PremiumPurchased`), не напрямую. Текущая реализация
вызывает напрямую (TODO: рефакторинг).

### Алгоритм воронки (visitor → registration → premium)

```kotlin
fun computeFunnel(): FunnelStats {
    val now = Instant.now()
    val since = now.minus(24, ChronoUnit.HOURS)

    val visits = SELECT COUNT(*) FROM tbl_events
                  WHERE event_type = 'VISIT' AND created_at >= ?
    val registrations = SELECT COUNT(*) FROM tbl_events
                        WHERE event_type = 'REGISTRATION' AND created_at >= ?
    val purchases = SELECT COUNT(*) FROM tbl_events
                    WHERE event_type = 'PREMIUM_PURCHASE' AND created_at >= ?

    return FunnelStats(
        visitors = visits,
        registrations = registrations,
        purchases = purchases,
        registrationRate = registrations.toDouble() / visits,
        purchaseRate = purchases.toDouble() / registrations
    )
}
```

**Использование**: `docs/strategy/growth.md` (baseline-funnel для
отслеживания роста).

> **[WARN] Поправка 2026-09-27: эндпоинта `/api/admin/stats/funnel` (и типа
> `FunnelStats`) в коде нет** — grep по `*.kt`/`*.js`/`*.vue` даёт 0
> вхождений. Алгоритм воронки выше — ЗАДУМАННЫЙ (см. [WARN] в
> [domain.md](../domain.md) и [event-funnel.md](event-funnel.md)). Реальная
> статистика отдаётся `GET /api/stats/*` (`StatsController`:
> `/api/stats/summary`, `/by-type`, `/by-song`, `/top-users`,
> `/top-listened`, `/monetization`).

## Зависимости | Dependencies

- → [domain](../domain.md) — AR `SiteEvent`.
- → [publishing dictionaries](../../publishing/components/dictionaries.md) —
  `BotScore` пороги (см. [WARN] выше).
- → [publishing stats-cache](../../publishing/components/stats-cache.md) —
  `realUsersLast24h` использует `BotScore`.
- → [caching domain](../../caching/domain.md) — `StatsCache` singleton.

## Ловушки и предупреждения

**[WARN] Дублирование `BotScore` порогов** между stats и publishing —
технический долг (см. таблицу выше). Не добавлять новые пороги в одном
домене без синхронизации с другим.

**[WARN] `tbl_events` растёт неограниченно** — за год может быть
сотни миллионов строк. Партиционирование по `created_at` (TODO).

**[WARN] Append-only** — никогда не делать `UPDATE tbl_events SET ...`
или `DELETE FROM tbl_events ...`. Только INSERT.

**[WARN] Cross-context вызовы** (шаг [3] алгоритма INSERT) — нарушение
DDD. Использовать доменные события (`PremiumPurchased`) для eventual
consistency.

## Связанные ADR | Related ADRs

- ADR по event sourcing (TODO).
- ADR по таблице `BotScore` порогов (нужно для разрешения конфликта).
