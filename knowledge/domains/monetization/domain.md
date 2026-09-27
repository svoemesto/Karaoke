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
> (`archive/docs/strategy/growth.md`).

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
- `priceRub` — стоимость в рублях (`PriceTariff.kt:51`).
- `periodDays` — длительность в днях (`:54`).
- `isActive` (`:57`), `isDefault` (`:60`), `sortOrder` (`:63`).

  (Pass 475: прежняя версия называла поля `price`, `currency` и
  `durationDays`. Поля `currency` в модели нет вообще — цены только
  в рублях; остальные два названы иначе.)

CRUD: `TariffsController` (`/api/tariffs/list|create|update|delete`).
**Target-aware**: реальные тарифы правятся на прод-БД (`target=remote`),
т.к. платёжный конвейер karaoke-web читает их оттуда. Локальная БД —
для теста/разработки.

### `PromoRule`

Поля:

- `name` — название правила (`PromoRule.kt:43`).
- `type` — тип правила (`:46`; константы `TYPE_*` в `:107-121`).
- `paramsJson` — параметры типа (`:49`); процент скидки живёт
  ЗДЕСЬ, а не отдельным полем.
- `appliesTo` (`:52`), `isActive` (`:55`), `priority` (`:64`).
- `validFrom`, `validTo` — период действия (`:58`, `:61`).

  (Pass 475: полей `code`, `discountPercent`, `maxUsages`,
  `currentUsages` в модели НЕТ — промокода как отдельного
  идентификатора не существует, а лимит использований не хранится.)

`paramsJson` — JSON-параметры конкретного типа правила (расширяемо
без миграций схемы).

CRUD: `PromoController` (`/api/promorules/list|create|update|delete`).
**Target-aware** — как тарифы.

### `CartItem`

Поля:

- `id`, `siteUserId` — пользователь (`CartItem.kt:45`).
- `idSong` — песня, которую хотят купить (`:48`).
- `addedAt` — дата добавления (`:51`).

  (Pass 475: ссылок на тариф и промо в модели НЕТ — полей
  `idPriceTariff`/`idPromoRule` не существует, а `created`
  называется `addedAt`. Тариф выбирается на этапе оформления, а
  не хранится в корзине.)

При оформлении заказа → `Subscription` создаётся из `CartItem` +
`PriceTariff`.

**Не участвует в LOCAL↔SERVER SyncRegistry** — только прод.

CRUD: `PublicCartController` в karaoke-web.

### `Subscription`

Поля:

- `siteUserId` — пользователь (`Subscription.kt:52`).
- `tariffId` — ID тарифа (`:62`).
- `scope` (`:55`), `idSong` (`:59`), `periodDays` (`:65`).
- `basePrice`, `discount`, `finalPrice` (`:68-74`).
- `promoApplied` (`:77`), `status` (`:80`), `autoRenew` (`:94`).
- `yookassaPaymentId` (`:83`), `orderId` (`:89`),
  `yookassaPaymentMethodId` (`:97`).

  (Pass 475: полей `idSiteUser`, `idTariff`, `startDate`, `endDate`
  и `isActive` в модели НЕТ. Период задаётся `periodDays`, а
  состояние — `status`; вместо `isActive` — `autoRenew`.)
- `status` — `CREATED` / `PENDING` / `PAID` / `FAILED` /
  `REFUNDED` / `CANCELED` (константы в `Subscription.kt:135-140`):
  - `STATUS_CREATED = "CREATED"` — запись создана в БД.
  - `STATUS_PENDING = "PENDING"` — ожидание webhook'а YooKassa.
  - `STATUS_PAID = "PAID"` — успешная оплата, подписка активна.
  - `STATUS_FAILED = "FAILED"` — ошибка оплаты (YooKassa declined).
  - `STATUS_REFUNDED = "REFUNDED"` — возврат.
  - `STATUS_CANCELED = "CANCELED"` — отмена.
- `yookassaPaymentId` — ID платежа в YooKassa.
- `autoRenew` — флаг автопродления.

`Subscription.fulfillment` (для scope=SITE) — в
`PublicPaymentController.webhook` (karaoke-web): после успешного
webhook'а YooKassa продлевает `SiteUser.sitePremiumUntil`.

**Участвует в LOCAL↔SERVER SyncRegistry** (см.
[two-db-sync](../processing/components/two-db-sync.md)).

## Публичные контракты (API)

### HTTP-контракты: admin (`karaoke-app`), потребитель — `webvue3`

| Контроллер | Эндпоинты |
| --- | --- |
| `TariffsController` (`@RequestMapping("/api/tariffs")`, `:24`) | `POST /api/tariffs/list`, `POST /create`, `POST /update`, `POST /delete` |
| `PromoController` (`@RequestMapping("/api/promorules")`, `:23`) | `POST /api/promorules/list`, `POST /create`, `POST /update`, `POST /delete` |
| `SubscriptionsController` (`@RequestMapping("/api/subscriptions")`, `:39`) | `POST /api/subscriptions/digest` — постраничный список подписок с фильтрами (`scope`, `status`, `userId`, `songId`, даты, `sortBy`/`sortDir`) |

Тарифы и промо-правила — **target-aware**: параметр `target=remote` адресует
прод-БД, иначе LOCAL (`TariffsController.withDb`, `PromoController.withDb`).
Потребители: `webvue3/src/components/Tariffs/store.js`,
`webvue3/src/components/Promotions/store.js`,
`webvue3/src/components/Subscriptions/store.js:66`.

### HTTP-контракты: public (`karaoke-web`), потребитель — `karaoke-public`

| Контроллер | Эндпоинты |
| --- | --- |
| `PublicSubscriptionController` (`/api/public/account/subscription`) | `GET /tariffs?scope=`, `GET /price?scope=&songId=&tariffId=`, `POST /create`, `GET /list`, `POST /cancel` |
| `PublicCartController` (`/api/public/account/cart`) | `GET /list`, `POST /clear`, `POST /toggle?songId=`, `GET /price`, `POST /checkout?disclaimerAccepted=` |
| `PublicPaymentController` (`/api/public/payment`) | `POST /api/public/payment/webhook` — webhook YooKassa: перепроверяет статус через `PaymentService.verifyAndFetch` и идемпотентно переводит `Subscription.status` → `PAID`; потребитель — YooKassa, не фронт |

Потребители: `karaoke-public/src/services/cartApi.js`,
`karaoke-public/src/composables/useSiteSubscription.js`,
`karaoke-public/src/composables/useSongSubscription.js`,
`karaoke-public/src/views/SubscriptionsView.vue`.

### Internal API

- Модели: `PriceTariff` (`loadAll`, `loadActiveByScope`, `getById`, `getDefault`,
  `createNew`, `delete`), `PromoRule` (`loadAll`, `loadActive`, `getById`,
  `createNew`, `delete`, `isCurrentlyActive`, `appliesToScope`), `Subscription`
  (`loadByUser`, `getById`, `getAllByYookassaPaymentId`, `isSubscribedToSong`,
  `subscribedSongIds`, `countPaid`, `createNew`), `CartItem` (`loadByUser`,
  `getByUserAndSong`, `createNew`, `delete`, `deleteByUserAndSongs`).
- Сервисы `karaoke-web`: `PriceService.computePrice` / `computeCartPrice` (промо +
  персональная скидка), `PaymentService` (`hasCredentials`, `createPayment`,
  `createCartPayment`, `chargeRecurring`, `verifyAndFetch`,
  `newIdempotenceKey`), `SubscriptionRenewalScheduler` (автопродление).

HTTP-API у домена есть: admin-часть — только в `karaoke-app`, публичная — только
через `karaoke-web`.

## Структура компонентов (C4 L3)

L3-компонентов у домена пока нет: контекст описан целиком в этом
`domain.md` (см. [домены](../README.md)). Создание компонентных документов —
отдельная задача; сам факт отсутствия L3 зафиксирован здесь, чтобы ссылка
«структура компонентов» не выглядела потерянной.

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
статус подписок в webvue3 (`POST /api/subscriptions/digest`).

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

## Зависимости | Dependencies

- **Identity** ([identity domain](../identity/domain.md)):
  `SiteUser.sitePremiumUntil` — обновляется при успешной оплате
  scope=SITE.
- **Two-DB sync** ([two-db-sync](../processing/components/two-db-sync.md)):
  `Subscription` и `PriceTariff` участвуют (`SubscriptionsSyncTarget`,
  `PriceTariffsSyncTarget` в `SyncRegistry.all`). **`PromoRule` — НЕ
  участвует**: цели синхронизации для промо-правил в
  `sync/SyncTarget.kt` нет (Pass 475).
- **Strategy** (`archive/docs/strategy/growth.md`):
  visitor→registration→premium воронка.

## Известные TODO

- [x] **PaymentService** (`karaoke-web/.../services/PaymentService.kt`)
      — YooKassa wrapper:
  - Использует `WebClient` через nginx-proxy `yookassa.proxy-url`
    (обход MTU black-hole, тот же паттерн что CAPTCHA/STORAGE proxy).
  - `hasCredentials()` — защита от вызов без `YOOKASSA_SHOP_ID` /
    `YOOKASSA_SECRET_KEY` (магазин ещё не зарегистрирован).
  - Credentials из env: `YOOKASSA_SHOP_ID`, `YOOKASSA_SECRET_KEY`.
  - Создаёт платежи для `scope=SONG` (бессрочно) и `scope=SITE`
    (с автопродлением через сохранённый `payment_method`).
  - Данные DTO: `Amount`, `Confirmation`, и др. (см. `PaymentService.kt`).
  - Для самозанятого — авто-чеки в «Мой налог» по 422-ФЗ.
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

- `archive/docs/strategy/growth.md` — visitor→registration→premium.
- `archive/docs/features/telegram-auto-publish.md` — упоминает тарифы.
- `archive/docs/features/dual-db-sync.md` — sync для Subscription.

## Changelog

- **Pass 341 P1** (2026-09-09): Initial. Автор: agent (Karaoke).
- **Pass 486** (2026-09-27, spec `486-knowledge-domains-others`): добавлена секция «Публичные контракты (API)». Автор: agent (Karaoke).