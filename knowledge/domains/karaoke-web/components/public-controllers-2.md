# Public controllers (auth/payment/share/site-account/news)

> **Домен**: [karaoke-web](../domain.md)
> **Компонента**: детальный обзор 5 critical public controllers.


## Ответственность | Responsibility


детальный обзор 5 critical public controllers.

## Назначение

5 самых **security/critical** public-контроллеров. Полный список —
см. [public-controllers.md](public-controllers.md).

## Интерфейсы и Контракты | Interfaces and Contracts

Контракты пяти контроллеров сгруппированы по назначению: сессии/JWT
(`auth`), OAuth (`vk`, `vk-id`), платежи (`payment`) и профиль
(`account`). Пути в таблицах — относительно class-level `@RequestMapping`
контроллера; исходники — в
`karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/`.

### 1. `PublicAuthController` (5 endpoints)

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

Маппинги (сверено с кодом): `@GetMapping("/config")` (стр. 35),
`@PostMapping("/register")` (38), `@PostMapping("/login")` (83),
`@PostMapping("/logout")` (108), `@GetMapping("/me")` (115).

### 2. `PublicVkAuthController` (3 endpoints)

OAuth с **VK** (старый flow):

| URL | Что |
|---|---|
| `/auth/vk/callback` | Callback от VK OAuth |
| `/auth/vk/unlink` | Unlink VK account |
| `/auth/vk/me` | VK profile |

[WARN] Перечисленные `/auth/vk/*` в коде не найдены (grep по
`karaoke-web/.../controllers/` — 0 совпадений). Фактические маппинги
`PublicVkAuthController.kt`: `@GetMapping("/api/public/utils/vkOAuthCodeUrl")`
(стр. 59) и `@GetMapping("/api/public/utils/vkOAuthCallback")` (стр. 74), и
оба помечены DEPRECATED → HTTP 410 Gone; новый flow — в
`PublicVkIdAuthController`.

### 3. `PublicVkIdAuthController` (3 endpoints)

OAuth с **VK ID** (новый flow, 2024+):

| URL | Что |
|---|---|
| `/auth/vk-id/callback` | Callback от VK ID OAuth |
| `/auth/vk-id/unlink` | Unlink VK ID account |
| `/auth/vk-id/me` | VK ID profile |

[WARN] `/auth/vk-id/*` в коде не найдены. Фактические маппинги
`PublicVkIdAuthController.kt`: `@GetMapping("/api/public/utils/vkIdOAuthUrl")`
(стр. 90) и `@GetMapping("/api/public/utils/vkIdOAuthCallback")` (стр. 157).

### 4. `PublicPaymentController` (2 endpoints) — **КРИТИЧНО**

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

[WARN] `/redirect` в `PublicPaymentController.kt` не найден: единственный
маппинг — `@PostMapping("/webhook")` (стр. 40).

### 5. `PublicAccountController` (4 endpoints)

`/api/public/account/*` — **требуют авторизации** через
`SiteAuthInterceptor` (см. [config.md](config.md)).

| URL | Что |
|---|---|
| `/account/profile` | GET/POST — профиль (email, display_name) |
| [WARN] `/account/welcome-message` — НЕ существует (grep 0, Pass 477) | — |
| `/account/change-password` | POST — смена пароля |
| [WARN] `/account/premium-status` — НЕ существует (grep 0, Pass 477); премиум-статус отдаёт `/api/public/stats` | — |

## Логика и Алгоритмы | Logic and Algorithms

Потоки описаны по фактическому коду (сигнатуры и ветки сверены
grep/чтением исходников в
`karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/`).

**`PublicAuthController` (`/api/public/auth`)**:

1. `/register` — последовательный гейт (каждый шаг возвращает
   `400 Bad Request` с `error`): `YandexCaptchaValidationService.validate(captchaToken, remoteAddr)`
   → `captcha`; email содержит `@` и длина `>= 5` → `invalid_email`;
   непустой `displayName` → `display_name_required`; `password.length >= 6`
   → `weak_password`; `password == passwordConfirm` → `password_mismatch`.
   Далее `SiteUser.createNewSiteUser(...)` → `409 email_taken`;
   `SiteUserTokenService.issueToken(...)` → `500 token`; успех —
   `201 {token, user}`.
2. `/login` — `SiteUser.getSiteUserByEmail` + `user.checkPassword(password, passwordEncoder)`
   → `401 invalid_credentials`; `user.isBanned` → `403 {error: banned, reason}`;
   успешный вход обновляет `lastLoginAt = now` и `save()`, затем выдаёт токен.
3. `/logout` — читает `Authorization`, снимает префикс `Bearer ` и вызывает
   `SiteUserTokenService.revokeToken`; всегда возвращает `{ok: true}`.
4. `/me` — пользователя в request уже положил `SiteAuthInterceptor`
   (атрибут `SITE_USER_ATTR`); метод только конвертирует его в DTO.

**`PublicVkIdAuthController`**: `getVkIdOAuthUrl` валидирует конфиг
(`vk.id.client-id > 0`, непустые `redirect-uri` и `client-secret`) и
возвращает `{success, url, scopes, clientId, redirectUri, instructions}`
либо `{success: false, error}`. Flow — Authorization Code + PKCE: на
`/vkIdOAuthUrl` генерируются `code_verifier` и `state`, `code_verifier`
кладётся в `ConcurrentHashMap pendingAuths` по ключу `state`
(`pendingTtlSeconds = 1800L`, `cleanExpiredPending()` чистит протухшие);
`vkIdOAuthCallback(code, state, error)` при `error` отдаёт HTML с ошибкой,
иначе обменивает `code` на токены и формирует HTML с curl-командой для
сохранения на admin-машине (`saveTokensOnAdminMachine`, `adminApiUrl`).

**`PublicPaymentController` (`/webhook`)** — обработка идемпотентна и не
доверяет телу вебхука:

1. `body["object"]["id"]` отсутствует → `400 no_payment_id`.
2. `Subscription.getAllByYookassaPaymentId(paymentId, ...)` пуст →
   `200 {ok: true, note: "unknown_subscription"}` (не `500`: чужой или уже
   удалённый платёж).
3. Все позиции заказа уже `Subscription.STATUS_PAID` → `200 {ok: true}`
   (no-op, идемпотентность при повторной доставке).
4. Статус перезапрашивается у ЮKassa: `PaymentService.verifyAndFetch(paymentId)`;
   `null` → `502 verify_failed`.
5. `succeeded` → для каждой неоплаченной позиции: `STATUS_PAID`,
   `paidAt = now`, `yookassaPaymentMethodId` (если непустой), `save()`,
   затем `applyFulfillment(sub)`.
6. `canceled` → неоплаченные позиции получают `STATUS_FAILED`.
7. Прочие статусы (`pending`, `waiting_for_capture`) — no-op до следующего
   события.
8. `applyFulfillment` действует только на `scope == SCOPE_SITE`: продлевает
   `SiteUser.sitePremiumUntil` от максимума (now либо уже проставленной
   даты), поэтому повторная оплата не теряет оплаченный хвост срока. Для
   `scope=SONG` ничего дополнительно не требуется — владение фиксирует сама
   PAID-запись (`Subscription.isSubscribedToSong`).

**`PublicAccountController`**: весь класс проходит через
`SiteAuthInterceptor` (path-pattern `/api/public/account/**`), поэтому в
любом методе `currentUser(request)` уже авторизован и не забанен.
`POST /profile` требует непустой `displayName` (`400 display_name_required`)
и опционально пишет `sponsrUid`; `POST /change-password` сверяет
`checkPassword(oldPassword, passwordEncoder)` (`400 invalid_old_password`) и
`newPassword.length >= 6` (`400 weak_password`).

## Hot paths

- **`/api/public/payment/webhook`** — критический, не пропускать.
- **`/api/public/auth/login`** — каждый логин.
- **`/api/public/auth/me`** — каждые 5 минут (auto-refresh, см.
  [composable-use-auth.md](../../../system/frontend/composable-use-auth.md)).

## Зависимости | Dependencies

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

- **Pass 481** (2026-09-27, spec `481-knowledge-domain-karaoke-web`): секции
  «1. … 5.» перенесены под «Интерфейсы и Контракты»; добавлена «Логика и
  Алгоритмы» с проверенными по коду потоками `auth`/`payment`/`account` и
  [WARN]-пометками о маппингах VK/VK ID и `/redirect`. Автор: agent (Karaoke).
- **Pass 464-470** (2026-09-09): Initial. Автор: agent (Karaoke).
