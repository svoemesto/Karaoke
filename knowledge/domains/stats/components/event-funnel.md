# Component: event-funnel

> **Домен**: [stats](../domain.md)
> **Компонент**: воронка visitor → registration → premium, сегментация
> трафика, baseline-funnel для стратегии роста.

## Ответственность | Responsibility

В Karaoke есть **воронка роста** (см. `docs/strategy/growth.md`):
**visitor → registration → premium**. Эта компонента описывает, как
считается каждый шаг воронки, какая сегментация применяется, и где
baseline-метрики для отслеживания роста.

**[WARN] Воронка считается только по REAL_USER** (`BotScore < 0.7`).
Боты не должны попадать в denominator'ы, иначе конверсия будет
искажена вниз.

## Интерфейсы и Контракты | Interfaces and Contracts

### `FunnelStats` DTO

```kotlin
data class FunnelStats(
    val visitors: Int,             // VISIT за период
    val registrations: Int,        // REGISTRATION за период
    val purchases: Int,            // PREMIUM_PURCHASE за период
    val registrationRate: Double,  // registrations / visitors
    val purchaseRate: Double,      // purchases / registrations
    val periodStart: Instant,
    val periodEnd: Instant
)
```

### `GET /api/admin/stats/funnel`

- **Параметры**: `?period=24h` (по умолчанию) или `?period=7d`, `?period=30d`.
- **Возвращает**: `FunnelStats` для указанного периода.

## Логика и Алгоритмы | Logic and Algorithms

### Алгоритм расчёта воронки

```
Вход: period (24h / 7d / 30d)
  ↓
[1] Вычислить since = now - period
  ↓
[2] visitors = SELECT COUNT(*)
               FROM tbl_events
               WHERE event_type = 'VISIT'
                 AND created_at >= ?
                 AND bot_score < 0.7        -- только REAL_USER
  ↓
[3] registrations = SELECT COUNT(*)
                    FROM tbl_events
                    WHERE event_type = 'REGISTRATION'
                      AND created_at >= ?
                      AND bot_score < 0.7
  ↓
[4] purchases = SELECT COUNT(*)
                FROM tbl_events
                WHERE event_type = 'PREMIUM_PURCHASE'
                  AND created_at >= ?
                  AND bot_score < 0.7
  ↓
[5] registrationRate = registrations / visitors
    purchaseRate = purchases / registrations
  ↓
[6] Возврат FunnelStats(...)
```

**[WARN]** Шаг [2]/[3]/[4] используют `bot_score < 0.7` (legacy
порог stats). Согласовать с publishing (`0.5`) — см. [dictionaries](dictionaries.md).

### Алгоритм сегментации трафика

```
Бот или реальный пользователь?
  ↓
[1] botScore = BotDetectionService.detect(visitorId, requestHeaders)
  ↓
[2] visitorType = when {
        botScore < 0.3 -> REAL_USER
        botScore < 0.7 -> GOOD_BOT
        else            -> BAD_BOT
    }
  ↓
[3] INSERT в tbl_events с visitor_type = computed
```

**[WARN]** Пороги legacy `0.3` / `0.7`. TODO: унифицировать с
publishing (`0.5` / `0.7`).

## Зависимости | Dependencies

- → [domain](../domain.md) — AR `SiteEvent`.
- → [dictionaries](dictionaries.md) — `EventType`, `BotScore` пороги.
- → [publishing domain](../../publishing/domain.md) — `Subscription`
  при `PREMIUM_PURCHASE`.
- → [BotDetectionService](../domain.md#entities) — классификация.

## Baseline funnel

Из фичи `187-site-traffic-anomaly-investigation` (2026-09-03):

| Период | Visitors | Registrations | Purchases | Reg rate | Purchase rate |
| --- | --- | --- | --- | --- | --- |
| 24h | ~300 | ~1 | ~0 | 0.33% | 0% |
| 7d | ~2000 | ~7 | ~1 | 0.35% | 14% |
| 30d | ~8500 | ~28 | ~4 | 0.33% | 14% |

**Цель (Pass 2+)**: reg rate ×5 (1.6%), purchase rate ×2 (28%).

Подробнее — `docs/strategy/growth.md`.

## Ловушки и предупреждения

**[WARN] Боты в denominator'ах** искажают конверсию. Всегда фильтровать
`bot_score < 0.7`.

**[WARN] Период не должен включать инциденты** — аномалия трафика
(см. фичу 187) искажает baseline. Использовать `?period=30d` для
robust estimate.

**[WARN] Cross-context вызов на PREMIUM_PURCHASE** — должен быть через
`PremiumPurchased` event, не напрямую `SubscriptionService.create()`
(текущее поведение TODO).

**[WARN] `BotDetectionService` дорогой** — если делать HTTP-парсинг
User-Agent + reverse DNS на каждый запрос, hot path деградирует.
Результат должен кешироваться (TODO).

## Связанные фичи

- `187-site-traffic-anomaly-investigation` — baseline-funnel.
- `144-homepage-latest-news` — отображает `visitors` счётчик.
- `180-og-seo-html` — бот-трафик 30%+ от всех запросов (см. 187).

## Связанные ADR | Related ADRs

- ADR по сегментации трафика (TODO, разрешить конфликт порогов).
