---
status: Active
slug: conversion-funnel
type: topic
related:
  - ../strategy/growth.md
  - ../strategy/growth-audit.md
  - ../strategy/about-page-draft.md
  - ../domain/stats.md
  - ../domain/publishing.md
  - ../architecture/monetization.md
  - ../features/005-free-vs-premium.md
  - ../features/143-song-free-access-window.md
  - ../features/144-homepage-latest-news.md
---

# Conversion Funnel — visitor → registration → premium → retention

> Архитектурный/продуктовый topic для воронки монетизации. Документ
> описывает **технические точки** воронки и **измеряемые метрики**,
> а не продуктовые гипотезы (см. `strategy/growth.md`).

## Воронка (4 стадии)

```
[1] VISITOR (анонимный)         100%  (все, кто зашёл)
  │  - /  (главная, "О проекте", "5 причин", "FREE vs PREMIUM")
  │  - /song/:id (плеер)
  │  - /search
  │  - /zakroma (песни)
  │  - /news (новости)
  │
  ↓ (reg CTA: "Зарегистрироваться", "Войти через VK ID")
  │
[2] REGISTRATION (FREE)         ~0.4%  (текущая конверсия)
  │  - email/пароль или VK ID
  │  - Профиль: имя, email, аватар
  │  - Доступ: история прослушиваний, плейлисты, share-link
  │
  ↓ (premium CTA: "Оформить подписку", "Отключить рекламу")
  │
[3] PREMIUM (paid)              ~0.001% (estimated)
  │  - YOOKASSA payment (RUB)
  │  - Доступ: premium-песни без share-link, обширный каталог
  │  - 30 дней trial — НЕТ (см. growth.md)
  │
  ↓ (retention: контент, новости, push)
  │
[4] RETENTION (churn < 5%/month)  ~95%  (target)
```

Источник данных: `tbl_events` (eventType), `SiteEvent` aggregate root.

## Где находятся точки воронки в коде

### [1] VISITOR → [2] REGISTRATION

**Frontend**: `webvue3/src/.../Auth.vue`, `karaoke-public/src/.../Login.vue` (или аналог)

**Backend**:
- `POST /api/siteusers/register` — email/пароль регистрация
- `POST /api/public/vk-id/auth` — VK ID OAuth (см. спеку 151)
- `PublicVkIdAuthController.kt` (karaoke-web)

**KDoc `@see`**: docs/strategy/growth.md → QW-2, QW-9.

**Точки измерения**:
- `SiteEvent` (eventType=VISIT) — анонимный визит
- `SiteEvent` (eventType=REGISTRATION) — зарегистрированный визит
- **Конверсия**: `count(eventType=REGISTRATION) / count(eventType=VISIT)`

### [2] REGISTRATION → [3] PREMIUM

**Frontend**: `karaoke-public/src/.../Premium.vue`, таблица FREE vs PREMIUM

**Backend**:
- `POST /api/payment/yookassa/create` — создание платежа
- `POST /api/payment/yookassa/callback` — webhook от YOOKASSA
- `PublicPaymentController.kt` (см. спеку 169, 171)

**Точки измерения**:
- `SiteEvent` (eventType=PREMIUM_PURCHASE) — покупка
- **Конверсия**: `count(PREMIUM_PURCHASE) / count(REGISTRATION)`

### [3] PREMIUM → [4] RETENTION

**Retention** — одна из самых сложных метрик.

**Frontend**: регулярный email, push, новый контент

**Backend**:
- `SubscriptionRenewalScheduler` — автопродление
- `StatsCache` — метрики активности

**Точки измерения**:
- `Subscription` (status=active, end_date > NOW) — активные подписки
- `SiteEvent` (eventType=PLAY_START) — engagement
- **Churn**: `count_subscriptions_ended / count_subscriptions_active_month`

## Воронка в коде — где смотреть

### Что есть в `tbl_events`

```sql
CREATE TABLE tbl_events (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,  -- VISIT, REGISTRATION, PREMIUM_PURCHASE, PLAY_START, etc.
    user_id BIGINT,                   -- NULL для анонимных
    bot_score NUMERIC(3, 2),          -- 0..1
    visitor_type VARCHAR(20),         -- REAL_USER, GOOD_BOT, BAD_BOT
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
```

**EventType enum** (см. `karaoke-app/.../model/EventTypes.kt`):
- `VISIT` — любой визит
- `REGISTRATION` — успешная регистрация
- `PREMIUM_PURCHASE` — успешная покупка
- `PLAY_START`, `PLAY_PAUSE`, `PLAY_END` — engagement
- `LOGIN`, `LOGOUT` — auth flow
- `SHARE_LINK_CREATE`, `SHARE_LINK_USED` — viral

### Bot detection

`BotScore` 0..1 вычисляется на основе:
- User-Agent (regex для известных ботов)
- IP (whitelist для Google/Yandex/Bing)
- Поведение (время на странице, частота запросов)

→ См. `features/187-site-traffic-anomaly-investigation.md`

### Где живёт логика

**Visitor** (анонимный):
- `karaoke-web/.../KaraokeWebService.kt` — увеличивает счётчик визитов
- `SiteEvent.recordEvent(VISIT, ...)` — логирует

**Registration**:
- `PublicVkIdAuthController.kt` — VK ID OAuth
- `PublicApiController.kt` — email/пароль
- SiteUser.recordEvent(REGISTRATION) — логирует

**Premium**:
- `PaymentService.kt` (karaoke-web) — YOOKASSA интеграция
- `SubscriptionRenewalScheduler.kt` — автопродление
- `SiteUser.recordEvent(PREMIUM_PURCHASE)` — логирует

**Retention**:
- `StatsCacheScheduler.kt` — обновляет метрики каждый час
- `subscription.isActive()` — проверка подписки

## Метрики воронки

### Базовые (есть)

- `count(tbl_events where event_type='VISIT')` — общее число визитов
- `count(distinct visitor_id)` — уникальные посетители
- `count(tbl_events where event_type='REGISTRATION')` — регистрации
- `count(tbl_users where subscription_status='active')` — активные premium

### Конверсии (нужно считать)

```
VISITOR → REGISTER:  0.4% (current)
  target: 2-5% (×5-13)

REGISTER → PREMIUM:  0.1-1% (estimated)
  target: 5-10%

PREMIUM → RETENTION: 95% (monthly)
  target: 95%+ (churn < 5%)
```

### Engagement (нужно считать)

- **DAU** (Daily Active Users): `count(distinct user_id where event_type in (PLAY_START, ...))`
- **WAU** (Weekly Active Users)
- **MAU** (Monthly Active Users)
- **Stickiness**: DAU/MAU
- **Avg session duration**: time between PLAY_START and PLAY_END

## Anti-patterns (что не делать)

- ❌ **Trial без anti-fraud** — trial-механику отложили (см. growth.md).
- ❌ **Реклама площадок** (Sponsr/Dzen/VK/Max/TG) — сайт-центричная модель.
- ❌ **MP4 download** — оферта запрещает.
- ❌ **«Достигни N песен → premium»** — game-механика, не соответствует нише.
- ❌ **Email-spam** —> лучше push или in-app notification.

## A/B тесты (планируется)

- **QW-2**: «5 причин зарегистрироваться» — на главной
- **QW-9**: «О проекте» — отдельная страница
- **QW-1**: «FREE vs PREMIUM» — таблица

Каждый A/B тест должен иметь:
1. **Hypothesis**: что изменится в метрике
2. **Variant**: A (control), B (treatment)
3. **Sample size**: ≥ 1000 visitors per variant
4. **Duration**: ≥ 2 weeks (captures weekly cycle)
5. **Metric**: primary (conversion), secondary (engagement)

## См. также

- `strategy/growth.md` — продуктовая стратегия
- `strategy/growth-audit.md` — 37+ гипотез
- `strategy/about-page-draft.md` — draft «О проекте»
- `domain/stats.md` — bounded context статистики
- `domain/publishing.md` — публикация (включает premium-доступ)
- `architecture/monetization.md` — модель free-vs-premium + YOOKASSA
- `features/005-free-vs-premium.md` — таблица FREE vs PREMIUM
- `features/143-song-free-access-window.md` — окно бесплатного доступа
- `features/144-homepage-latest-news.md` — главная страница

## История

- Создан: 2026-08-14 (Pass 51+ follow-up спеки 189)
- Автор: opencode (MiniMax-M3)
- Последнее обновление: 2026-08-14