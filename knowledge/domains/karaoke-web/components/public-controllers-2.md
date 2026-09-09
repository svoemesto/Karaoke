# Public controllers (auth/payment/share/site-account/news)

> **Домен**: [karaoke-web](../domain.md)
> **Компонента**: детальный обзор 5 critical public controllers.

## Назначение

5 самых **security/critical** public-контроллеров. Полный список —
см. [public-controllers.md](public-controllers.md).

## 1. `PublicAuthController` (6 endpoints)

`/api/public/auth/*`:

| URL | Метод | Что |
|---|---|---|
| `/config` | GET | Конфигурация auth (для UI) |
| `/register` | POST | Регистрация (email/password) |
| `/login` | POST | Логин (JWT) |
| `/logout` | POST | Logout (clear token) |
| `/me` | GET | Текущий user (нужен `km_auth_token`) |

**Безопасность**: bcrypt для password. JWT в `localStorage`
(см. [composable-use-auth.md](../../../system/frontend/composable-use-auth.md)).
**Капча** через `YandexCaptchaValidationService` (Pass 345).

## 2. `PublicVkAuthController` (3 endpoints)

OAuth с **VK** (старый flow):

| URL | Что |
|---|---|
| `/auth/vk/callback` | Callback от VK OAuth |
| `/auth/vk/unlink` | Unlink VK account |
| `/auth/vk/me` | VK profile |

## 3. `PublicVkIdAuthController` (3 endpoints)

OAuth с **VK ID** (новый flow, 2024+):

| URL | Что |
|---|---|
| `/auth/vk-id/callback` | Callback от VK ID OAuth |
| `/auth/vk-id/unlink` | Unlink VK ID account |
| `/auth/vk-id/me` | VK ID profile |

## 4. `PublicPaymentController` (2 endpoints) — **КРИТИЧНО**

`/api/public/payment/*`:

| URL | Метод | Что |
|---|---|---|
| `/webhook` | POST | **YooKassa webhook** — критический! |
| `/redirect` | GET | Redirect после оплаты |

**`/webhook`** (KDoc): асинхронное уведомление об оплате от
YooKassa. При успешном платеже:
- `Subscription.status = PAID`.
- Продлевает `SiteUser.sitePremiumUntil` (для scope=SITE).
- Создаёт записи в `tbl_subscriptions`.

**НЕ пропускать** webhook — иначе подписка не активируется,
пользователь заплатит, но не получит premium. См.
[monitor-checks-detailed.md](../../monitoring/components/monitor-checks-detailed.md) — `UnreadChatMessagesCheck` подобный алерт.

**Защита webhook**: проверка signature от YooKassa (если включено).
**НЕ** rate-limited (YooKassa шлёт в любое время).

## 5. `PublicAccountController` (4 endpoints)

`/api/public/account/*` — **требуют авторизации** через
`SiteAuthInterceptor` (см. [config.md](config.md)).

| URL | Что |
|---|---|
| `/account/profile` | GET/POST — профиль (email, display_name) |
| `/account/welcome-message` | GET — приветственное сообщение |
| `/account/change-password` | POST — смена пароля |
| `/account/premium-status` | GET | premium-статус |

## Hot paths

- **`/api/public/payment/webhook`** — критический, не пропускать.
- **`/api/public/auth/login`** — каждый логин.
- **`/api/public/auth/me`** — каждые 5 минут (auto-refresh, см.
  [composable-use-auth.md](../../../system/frontend/composable-use-auth.md)).

## Связь

- [public-controllers.md](public-controllers.md) — обзор всех 19.
- [monetization domain](../../monetization/domain.md) —
  PaymentService.
- [identity domain](../../identity/domain.md) — SiteUser.
- [config.md](config.md) — SiteAuthInterceptor.

## Известные TODO

- [ ] **YooKassa signature** — детальная валидация (Pass 343+).
- [ ] **Rate limiting** на `/webhook` — почему нет (YooKassa сама
      retry при 5xx).
- [ ] **VK ID vs VK OAuth** — migration path (Pass 343+).

## Changelog

- **Pass 464-470** (2026-09-09): Initial. Автор: agent (Karaoke).