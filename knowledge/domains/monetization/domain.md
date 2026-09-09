---
id: domain-monetization
title: "Domain: Monetization (Монетизация)"
status: Active
slug: monetization
related:
  - ../identity/domain.md
  - ../publishing/domain.md
---

# Domain: Monetization (Монетизация)

> Bounded context для платёжного конвейера: тарифы, промо-правила,
> корзина, подписки. Прецедент создания — P1 Knowledge-аудита
> (Pass 341), а также стратегия visitor→registration→premium
> (`docs/strategy/growth.md`).

## Обзор контекста (Bounded Context)

Монетизация — это **платёжный pipeline** от выбора тарифа до
активации подписки у пользователя. Четыре сущности:

1. **`PriceTariff`** — каталог тарифов (месяц/год/навсегда).
2. **`PromoRule`** — промо-правила (скидки), применяемые поверх тарифа.
3. **`CartItem`** — позиция в корзине пользователя (песня + тариф).
4. **`Subscription`** — оформленная подписка (PAID/REFUNDED/...).

Архитектурное решение — **«покупка сознательно не называется
покупкой нигде»** (см. KDoc `Subscription.kt`): «термин
"покупка" сознательно не используется нигде в проекте».

Два scope подписки:

- **`SONG`** — бессрочная подписка на одну песню. `periodDays`
  игнорируется, доступ = сам факт наличия PAID-записи
  `Subscription`. См. `PublicPlayerController.subscribedToSong`
  в karaoke-web.
- **`SITE`** — периодическая подписка на сайт. `PAID` продлевает
  `SiteUser.sitePremiumUntil` (fulfillment в
  `PublicPaymentController.webhook`), с возможным автопродлением
  (`autoRenew + yookassaPaymentMethodId`).

**Граница**: контекст НЕ отвечает за:

- Платёжный шлюз (YooKassa) — отдельный компонент (см. gaps).
- Регистрация пользователей (→ [identity](../identity/domain.md)).
- Автопродление (см. gaps, Pass 342).

## Ubiquitous Language | Единый язык

| Термин | Определение | Где в коде |
| --- | --- | --- |
| **`PriceTariff`** | Каталог тарифов (`tbl_price_tariffs`) | `model/PriceTariff.kt` |
| **`PromoRule`** | Промо-правила (`tbl_promo_rules`) | `model/PromoRule.kt` |
| **`CartItem`** | Позиция в корзине (`tbl_cart_items`) | `model/CartItem.kt` |
| **`Subscription`** | Оформленная подписка (`tbl_subscriptions`) | `model/Subscription.kt` |
| **`scope=SONG`** | Подписка на одну песню | enum `Subscription.scope` |
| **`scope=SITE`** | Подписка на весь сайт | enum `Subscription.scope` |
| **`applyPromoRule()`** | Применить промо к тарифу, вернуть новую цену | `PromoRule.kt` |
| **`paramsJson`** | JSON-параметры промо (расширяемо без миграций) | `PromoRule.kt` |
| **`TYPE_*`** | Конкретные типы промо (см. KDoc PromoRule) | `PromoRule.kt` |

## Архитектура

### `PriceTariff`

Поля:

- `name` — название («Месяц», «Год», «Навсегда»).
- `price`, `currency` — стоимость.
- `durationDays` — длительность (0 = бессрочно).
- `isActive` — доступен ли для новых покупок.
- `sortOrder` — порядок отображения в UI.

CRUD: `TariffsController` (`/api/tariffs/list|create|update|delete`).
**Target-aware**: реальные тарифы правятся на прод-БД (`target=remote`),
т.к. платёжный конвейер karaoke-web читает их оттуда. Локальная БД —
для теста/разработки.

### `PromoRule`

Поля:

- `id`, `code` — промокод (например, «LAUNCH2024»).
- `discountPercent` — процент скидки (0..100).
- `validFrom`, `validTo` — период действия.
- `maxUsages`, `currentUsages` — лимит использований.
- `isActive` — активно ли правило.
- `priority` — приоритет (больше = раньше проверяется; конфликт
  решает `PriceService`).

`paramsJson` — JSON-параметры конкретного типа правила (расширяемо
без миграций схемы).

CRUD: `PromoController` (`/api/promorules/list|create|update|delete`).
**Target-aware** — как тарифы.

### `CartItem`

Поля:

- `id`, `idSiteUser` — пользователь.
- `idSong` — песня, которую хотят купить.
- `idPriceTariff` — выбранный тариф.
- `idPromoRule` — применённое промо (опционально).
- `created` — дата добавления.

При оформлении заказа → `Subscription` создаётся из `CartItem` +
`PriceTariff`.

**Не участвует в LOCAL↔SERVER SyncRegistry** — только прод.

CRUD: `PublicCartController` в karaoke-web.

### `Subscription`

Поля:

- `id`, `idSiteUser` — пользователь.
- `idTariff` — ID тарифа.
- `startDate`, `endDate` — период действия.
- `isActive` — автопродление/ручное.
- `status` — PAID / REFUNDED / EXPIRED / CANCELLED (предположительно,
  см. gaps).
- `yookassaPaymentId` — ID платежа в YooKassa.
- `autoRenew` — флаг автопродления.

`Subscription.fulfillment` (для scope=SITE) — в
`PublicPaymentController.webhook` (karaoke-web): после успешного
webhook'а YooKassa продлевает `SiteUser.sitePremiumUntil`.

**Участвует в LOCAL↔SERVER SyncRegistry** (см.
[two-db-sync](../processing/components/two-db-sync.md)).

## Domain Invariants

1. **`PriceTariff.isActive=false` MUST скрывать тариф из публичного
   UI**, но существующие `Subscription` остаются валидными.
2. **`PromoRule` НЕ ДОЛЖЕН пересекаться по `code`**: дубликаты
   промокодов → silent bug.
3. **`CartItem` MAY быть удалён без `Subscription`** — пользователь
   может передумать до оплаты.
4. **`Subscription` (PAID) MUST быть идемпотентным**: повторный
   webhook YooKassa не должен создать дубли.
5. **`scope=SONG` подписки** не имеют `endDate` (бессрочные).
   `scope=SITE` — имеют.

## Архитектурные решения

### Решение 1: `PriceTariff` и `PromoRule` правятся на прод-БД

Target-aware контроллеры (`target=remote` по умолчанию для тарифов и
промо). Причина: платёжный pipeline читает из прод-БД, локальная —
только для теста.

### Решение 2: `Subscription` через `SyncRegistry`

`Subscription` участвует в two-DB sync, потому что админу нужно видеть
статус подписок в webvue3 (`/api/subscriptions/list`).

### Решение 3: `CartItem` НЕ через SyncRegistry

Корзина — только прод. Прод-only данные, не нужны админу.

### Решение 4: «Покупка» не используется в коде

Термин сознательно избегается (см. KDoc `Subscription.kt`). Вместо
этого — «подписка» (`Subscription`), «оформление», «оформить».

## Hot paths

- **`/api/tariffs/list`** — админ управляет тарифами. CRUD.
- **`/api/promorules/list`** — админ управляет промо.
- **`/api/public/payment/webhook`** — YooKassa шлёт уведомления об
  оплате. **Критический hot path** — если пропустить, подписка не
  активируется.
- **`PriceService.calculate()`** — расчёт итоговой цены с учётом
  промо. См. gaps (Pass 342).

## Связь с другими компонентами

- **Identity** ([identity domain](../identity/domain.md)):
  `SiteUser.sitePremiumUntil` — обновляется при успешной оплате
  scope=SITE.
- **Two-DB sync** ([two-db-sync](../processing/components/two-db-sync.md)):
  `Subscription`, `PriceTariff`, `PromoRule` участвуют (см.
  SyncRegistry.all).
- **Strategy** (`docs/strategy/growth.md`):
  visitor→registration→premium воронка.

## Известные TODO

- [ ] **PaymentService** (karaoke-web/services/PaymentService.kt) —
      YooKassa wrapper, не описан.
- [ ] **YooKassa integration** — какие API используются, какой
      flow.
- [ ] **YookassaPaymentMethodId, AutoRenew** — детали автопродления.
- [ ] **PriceService.calculate()** — алгоритм применения промо.
- [ ] **`Subscription.status`** enum — какие значения, кто переводит.
- [ ] **Webhook signature verification** — безопасность.
- [ ] **Возврат (REFUNDED)** flow — кто, как, edge cases.
- [ ] **PromoRule приоритеты** — примеры конфликтов и как решаются.
- [ ] **CORS / PaymentWidget** — какие endpoints открыты для
  YooKassa redirect.
- [ ] **YookassaPaymentMethodId** — где хранится, кто обновляет.

## Код (физическая реализация)

### Model

- `karaoke-app/.../model/PriceTariff.kt`
- `karaoke-app/.../model/PriceTariffDto.kt`
- `karaoke-app/.../model/PromoRule.kt`
- `karaoke-app/.../model/PromoRuleDto.kt`
- `karaoke-app/.../model/CartItem.kt`
- `karaoke-app/.../model/CartItemDto.kt`
- `karaoke-app/.../model/Subscription.kt`
- `karaoke-app/.../model/SubscriptionDto.kt`

### Controllers

- `karaoke-app/.../controllers/TariffsController.kt`
- `karaoke-app/.../controllers/PromoController.kt`
- `karaoke-app/.../controllers/SubscriptionsController.kt`
- `karaoke-web/.../controllers/PublicCartController.kt`
- `karaoke-web/.../controllers/PublicPaymentController.kt`
- `karaoke-web/.../controllers/PublicSubscriptionController.kt`

### Services

- `karaoke-web/.../services/PaymentService.kt` (см. gaps)
- `karaoke-web/.../services/PriceService.kt` (см. gaps)
- `karaoke-web/.../services/SubscriptionRenewalScheduler.kt`
  (см. gaps)

## Связанные ADR | Related ADRs

- `docs/strategy/growth.md` — visitor→registration→premium.
- `archive/docs/features/telegram-auto-publish.md` — упоминает тарифы.
- `archive/docs/features/dual-db-sync.md` — sync для Subscription.

## Changelog

- **Pass 341 P1** (2026-09-09): Initial. Автор: agent (Karaoke).