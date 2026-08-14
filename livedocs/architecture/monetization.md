---
status: Active
slug: monetization
type: topic
related:
  - ../features/005-free-vs-premium.md
  - ../features/143-song-free-access-window.md
  - ../features/162-fix-header-stale-premium-status.md
  - ../features/169-share-link-in-premium-compare.md
  - ../features/171-admin-subscriptions-history.md
  - ../features/122-premium-auto-publish.md
  - ../domain/publishing.md
  - ../domain/identity.md
---

# Monetization — модель free-vs-premium + подписки + платежи (topic)

> Drill-down для всей модели монетизации проекта. Конкретные фичи —
> в [`../features/`](../features/README.md).

## Назначение

**Monetization** — паттерн монетизации Karaoke: free vs premium песни,
подписки, оплата через YOOKASSA, авто-публикация premium-песен.

## Модель free-vs-premium

### Бесплатный доступ (free)

- Любой пользователь видит **все** песни, **доступные в эфире**.
- Free-песня (без premium-флага) — доступна всем без ограничений.

### Premium-песня

- Доступна только **подписчикам premium** или по **share-link** от владельца.
- Песня становится premium после `publishDate` + окно бесплатного доступа
  (по умолчанию **1 календарный месяц**, см. спеку 143).

### Сравнение FREE vs PREMIUM

См. [`features/005-free-vs-premium.md`](../features/005-free-vs-premium.md) —
таблица `/premium` (FREE vs PREMIUM) с возможностями каждого уровня.

**Ключевые различия**:
- **FREE**: доступ к free-песням, история прослушиваний, share-link,
  специальные заказы.
- **PREMIUM**: всё из FREE + доступ ко всем premium-песням без share-link,
  расширенный каталог (премиум-сборники).

## Подписки

### Модель подписки

- **`tbl_subscriptions`** (модель `Subscription.kt`) — записи о подписках.
  Поля: `userId`, `startDate`, `endDate`, `status`, `paymentId`,
  `yookassaPaymentId`, `yookassaPaymentMethodId`, `autoRenew`.
- **Типы**: `MONTHLY`, `YEARLY` (см. `KaraokeProperties.kt`).
- **Автопродление**: через `autoRenew` + `yookassaPaymentMethodId`.

### Жизненный цикл

1. **Регистрация пользователя** (`/register`) — `SiteUser` создаётся.
2. **Оформление подписки** (`/premium/checkout`) — POST на
   `YOOKASSA` API → redirect на payment page → callback → создание
   `Subscription` записи.
3. **Активная подписка** — пользователь видит premium-песни.
4. **Истечение** (`SubscriptionExpired` event) — премиум-доступ закрыт,
   пользователь переходит на FREE.

### Управление

- **Админ-таблица** (`SubscriptionHistoryView.vue`) — список всех
  подписок с фильтрами (см. спеку 171).
- **Header badge** — индикатор «PREMIUM» в шапке сайта, авто-обновление
  при изменении статуса (см. спеку 162 — фикс stale premium status).

## Платежи (YOOKASSA)

### Архитектура

- **YOOKASSA** — внешняя платёжная система (Россия).
- **`Subscription.kt:paymentId`** — ID платежа (внешняя система).
- **`yookassa_payment_id`** — ID в системе YOOKASSA. Один платёж может
  покрывать несколько подписок (для корзины — `CartItem.kt`).
- **`yookassa_payment_method_id`** — ID метода оплаты для автопродления.

### Flow

```
User → /premium/checkout
  → POST /api/payment/yookassa/create
  → Backend → YOOKASSA API (create payment)
  → YOOKASSA → redirect user на payment page
  → User pays
  → YOOKASSA → webhook callback /api/payment/yookassa/callback
  → Backend → verify signature → activate subscription
  → SSE event "subscription-activated" → header обновляется
```

### Безопасность

- **HMAC signature verification** для webhook (header `Content-SHA256`).
- **Idempotency** через `paymentId` — повторный callback не создаёт дубль.
- **Test mode** через `KaraokeProperties.yookassaTestMode` (sandbox URL).

## Авто-публикация premium-песен

### Telegram

- Спека 113: после `publishDate` (или сразу после approve) — публикация
  в Telegram-канале с MP3+текстом (demo MP4) или с превью + ссылкой.
- **Канал**: `@svoemesto_karaoke`.
- **Шаблон**: `KaraokeProperties.telegramTemplate` с placeholder'ами.

### VK

- Спека 121: после `publishDate` — публикация в VK-группе.
- **Группа**: `vk.com/svoemesto_karaoke`.
- **Шаблон**: `KaraokeProperties.vkTemplate`.

### Premium auto-publish scheduler

- **`PremiumAutoPublishScheduler`** — периодический scheduler, который
  находит песни с `premiumAutoPublishState in (NEW, FAILED)` и запускает
  публикацию.
- См. спеку 122.

### News (auto)

- **`SongReleaseAnnouncementService`** — детектирует переход
  `newsAvailableAnnounced: false → true` и создаёт новость на сайте.
- Kill-switch `karaokeNewsAutoPublishKillSwitch` в `KaraokeProperties`
  (см. спеку 124 + FR-010).

## Share-link для premium

Premium-песни можно дать послушать **бесплатно** через share-link (см.
[`share-link.md`](share-link.md)):
- Владелец премиум-подписки генерирует ссылку.
- Гость (без подписки) переходит по ссылке → claim → временный доступ.
- TTL: 1ч / 24ч / 7д.
- Лимит 2 устройств на ссылку.

В таблице FREE vs PREMIUM (спека 169) share-link упомянут как
«Временная ссылка для гостя».

## Воронка монетизации

```
Visitor (анонимный)
  → Регистрация (FREE)
  → Прослушивание free-песен
  → Решение оформить premium
  → Подписка через YOOKASSA
  → Premium-доступ ко всем песням
  → Share-link гостям (виральный рост)
```

Детали воронки — `docs/strategy/growth.md`.

## Метрики монетизации

- **`/api/public/stats/monetization`** — общая статистика (конверсия,
  churn, ARR).
- **`/api/public/stats/monetization-top-songs`** — топ premium-песен по
  выручке / прослушиваниям.

## См. также

- [`../features/005-free-vs-premium.md`](../features/005-free-vs-premium.md) —
  основная фича.
- [`../features/143-song-free-access-window.md`](../features/143-song-free-access-window.md) —
  окно бесплатного доступа.
- [`../features/122-premium-auto-publish.md`](../features/122-premium-auto-publish.md) —
  авто-публикация premium-песен.
- [`../features/169-share-link-in-premium-compare.md`](../features/169-share-link-in-premium-compare.md) —
  share-link в таблице FREE vs PREMIUM.
- [`../features/171-admin-subscriptions-history.md`](../features/171-admin-subscriptions-history.md) —
  админ-таблица подписок.
- [`../domain/publishing.md`](../domain/publishing.md) — контекст публикации.
- [`../domain/identity.md`](../domain/identity.md) — пользователи и роли.
- [`../architecture/share-link.md`](../architecture/share-link.md) —
  гостевой доступ.
- [`../architecture/censoring.md`](../architecture/censoring.md) —
  цензурирование в публикациях.

## История

- Создан: 2026-08-14 (Pass 46 follow-up спеки 189-live-documentation)
- Последнее обновление: 2026-08-14