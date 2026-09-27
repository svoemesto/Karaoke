# Component: public-controllers

> **Домен**: [karaoke-web](../domain.md)
> **Компонента**: детальный обзор 19 Public* контроллеров.


## Ответственность | Responsibility


детальный обзор 19 Public* контроллеров.

## Назначение

`karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/Public*Controller.kt` —
**публичные REST endpoints** для сайта karaoke-public и внешних клиентов.
Всего 19 контроллеров, ~104 endpoint'а (по grep).

## Каталог контроллеров (по количеству endpoints)

| # | Контроллер | Endpoints | URL prefix | Назначение |
|---|---|---|---|---|
| 1 | `PublicPlaylistController` | 15 | `/api/public/playlist/...` | Плейлисты пользователя (CRUD + позиции) |
| 2 | `PublicApiController` | 13 | `/api/public/...` | Общий API (главная, статистика) |
| 3 | `PublicSongEditorController` | 9 | `/api/public/song-editor/...` | Редактирование песни (markers, lyrics) |
| 4 | `PublicPlayerController` | 9 | `/api/public/player/...` | Плеер (playerdata, файлы) |
| 5 | `PublicShareController` | 8 | `/api/public/share/...` | Share-ссылки (heartbeat, sessions) |
| 6 | `PublicSubscriptionController` | 6 | `/api/public/subscription/...` | Подписки пользователя |
| 7 | `PublicCartController` | 6 | `/api/public/cart/...` | Корзина |
| 8 | `PublicAuthController` | 6 | `/api/public/auth/...` | Регистрация/логин/восстановление |
| 9 | `PublicStemJobController` | 5 | `/api/public/account/stemjobs/...` | Премиум StemJob (создать минусовку) |
| 10 | `PublicChatController` | 4 | `/api/public/account/chat/...` | Чат (admin ↔ public) |
| 11 | `PublicAccountController` | 4 | `/api/public/account/...` | Личный кабинет (профиль, настройки) |
| 12 | `PublicVkIdAuthController` | 3 | `/api/public/auth/vk-id/...` | VK ID OAuth (новый flow) |
| 13 | `PublicVkAuthController` | 3 | `/api/public/auth/vk/...` | VK OAuth (старый flow) |
| 14 | `PublicSettingsWebController` | 3 | `/api/properties/...` (`/setproperty`, `/getproperty`, `/digest`) | Публичные настройки сайта |
| 15 | `PublicNewsController` | 3 | `/api/public/news/...` | Новости (since, get, mark) |
| 16 | `PublicPaymentController` | 2 | `/api/public/payment/...` | YooKassa webhook, redirect |
| 17 | `PublicOgSongController` | 2 | `/api/public/og/...` | Open Graph теги для шеринга |
| 18 | `PublicHistoryController` | 2 | `/api/public/history/...` | История прослушиваний |
| 19 | `PublicTypographController` | 1 | `POST /api/replacesymbolsinsong` | Типографика (утилита) |

## Интерфейсы и Контракты | Interfaces and Contracts

### `PublicPlayerController`

**Файл**: `karaoke-web/.../controllers/PublicPlayerController.kt`.

**Endpoints** (по grep `GetMapping/PostMapping`):

- `GET /api/public/player/{id}/access` — проверить доступ (для премиум-песен).
- `GET /api/public/player/{id}/fileminus.mp3` — стрим минусовки.
- `GET /api/public/player/{id}/filevoice.mp3` — стрим вокала.
- `GET /api/public/player/{id}/filebass.mp3` — стрим баса.
- `GET /api/public/player/{id}/filedrums.mp3` — стрим ударных.
- `GET /api/public/player/{id}/playerdata` — JSON playerdata (для UI).
- `GET /api/public/player/{id}/playerfile` — большой playerfile (playerdata + zip).

**NB**: каждый стрим файла идёт через nginx path-proxy
([storage-flow.md](../../storage/components/storage-flow.md)).
Real-time checks `player.readiness` (см.
[health-report.md](../../health/components/health-report.md)).

### `PublicApiController`

**Файл**: `karaoke-web/.../controllers/PublicApiController.kt`.

**Endpoints** (по grep):

- [WARN] `/api/public/home` — эндпоинта НЕ существует (проверено Pass 477: grep 0). Главную отдаёт SPA/Thymeleaf, не API.
- `/api/public/...` — общие endpoint'ы (главная, статистика, счётчики).
- Включает `fetchFromMinIO` — утилита для получения MinIO-файлов
  через nginx-proxy.

### `PublicPaymentController`

**Endpoints**:

- `POST /api/public/payment/webhook` — YooKassa webhook (асинхронное
  уведомление об оплате). **Критический** — пропуск = потеря платежа.
- [WARN] `GET /api/public/payment/redirect` — эндпоинта НЕ существует.
  Реально у `PublicPaymentController` (`@RequestMapping("/api/public/payment")`)
  есть только `POST /webhook` (`:40`), Pass 477.

### `PublicCartController`

`/api/public/cart/...` — CartItem CRUD (см.
[monetization domain](../../monetization/domain.md)).

### `PublicAuthController` / `PublicVkAuthController` / `PublicVkIdAuthController`

3 разных auth-флоу:

- `PublicAuthController` — email/password registration + login.
- `PublicVkAuthController` — VK OAuth (legacy).
- `PublicVkIdAuthController` — VK ID OAuth (new).

### `PublicShareController`

`/api/public/share/...` — share-ссылки (см.
[entities-catalog.md](../../catalog/components/entities-catalog.md#songsharelink)).
Включает heartbeat, sessions management.

### `PublicStemJobController`

`/api/public/account/stemjobs/...` — премиум фича «Создать
минусовку» (см. [stem-job.md](../../catalog/components/remaining-models.md#stemjob)).

### `PublicAccountController` + `SiteAuthInterceptor`

`/api/public/account/*` — ЗАЩИЩЁННЫЕ endpoint'ы (требуют
аутентификации). `SiteAuthInterceptor` проверяет JWT-токен и
устанавливает `request.siteUser`.

## Логика и Алгоритмы | Logic and Algorithms

### Доступ к плееру (`PublicPlayerController`)

- `authorized(id: Long, token: String?, session: String?): Boolean` — двойная
  авторизация: сначала gesture-token
  (`gestureUnlockService.validateToken(token, id)`), затем share-сессия
  (`shareLinkService.validateShareSession(session, id)`); приоритет —
  gesture-token.
- `access(@PathVariable id: Long, @RequestParam session: String?, request)`:
  `ready = song.isContentReady` (персистентные флаги Song, без обращения к
  MinIO), `premium = siteUserResolver.resolve(request)?.isEffectivePremium`,
  `subscribed = !premium && isSubscribedToSong(request, id)`,
  `shareGuest = ready && session != null && validateShareSession(...) != null`.
  Отсюда `canWatch = ready && (song.isFreelyAvailableNow || premium ||
  subscribed || shareGuest)`, `canExport = canWatch && premium && !shareGuest`
  (гость стемы не скачивает), `isDemo = ready && !canWatch`.
- Токен выдаётся только при `canWatch || isDemo`:
  `gestureUnlockService.issueDirectAccessToken(id)` либо
  `issueDemoAccessToken(id, demoFragmentStartSeconds, demoFragmentEndSeconds)`.
- Факт доступа логируется `mainController.doRegisterEvent(...)`:
  `source=list` → `PlayerAction.OPENED`, иначе `PlayerAction.SHOWN`.
- `readiness(@RequestParam ids: String, request)` — batch-проверка без MinIO:
  `ids.split(",")` + дедупликация; `contentReady = song.isContentReady`,
  `watchable = contentReady && (song.isFreelyAvailableNow || premium ||
  id in subscribedIds)`; подписки набираются одним запросом
  `Subscription.subscribedSongIds(userId, songIds, ...)`.

### Платёжный webhook (`PublicPaymentController`)

`webhook(@RequestBody body: Map<String, Any?>)` — событие ЮKassa
(`{"event": ..., "object": {"id": ...}}`):

1. Нет `object.id` → 400 `no_payment_id`.
2. `Subscription.getAllByYookassaPaymentId(paymentId, ...)` — заказ корзины
   (несколько подписок на один платёж); пусто → 200 `unknown_subscription`
   (не 500).
3. Идемпотентность: все позиции уже `STATUS_PAID` → 200, no-op.
4. Телу вебхука не доверяем: `paymentService.verifyAndFetch(paymentId)`
   перезапрашивает статус у ЮKassa; `null` → 502 `verify_failed`.
5. `succeeded` → `STATUS_PAID` + `paidAt` + `yookassaPaymentMethodId` и
   `applyFulfillment(sub)`; `canceled` → `STATUS_FAILED`;
   `pending`/`waiting_for_capture` — ничего (ждём следующего события).
6. `applyFulfillment` работает только для `SCOPE_SITE`: продлевает
   `SiteUser.sitePremiumUntil` от `max(now, текущая дата)`, повторная оплата
   не теряет ранее оплаченный хвост срока.

### Polling-кеши и share-сессии

- `PollingCache<V>` (класс в `karaoke-app`, Pass 456) навешен на:
  `GET /api/public/news/since` (TTL 60s),
  `GET /api/public/account/chat/unreadcount` (10s),
  `POST /api/public/share/heartbeat` (15s, ключ
  `share_heartbeat:<sessionTokenHash>`); cache-hit не дёргает БД.
- `POST /api/public/share/claim(@RequestBody body)` требует `secret` +
  `browserHash`; `SongShareLinkService.tryClaim` возвращает `linkExpiresAt`
  (срок ссылки), `expiresAt` (текущий lease 90s), `sessionTokenHash` и
  `redirectTo`. Ошибки: 400 `share.tokenMissing`, 409 `ConcurrentLimit`,
  429 `RateLimited`, 404 `share.notFound`, 500 `share.internal`.
- `POST /api/public/share/release` принимает и JSON, и form-urlencoded —
  `navigator.sendBeacon` при уходе со страницы не умеет `application/json`.

### Rate limit

`RateLimitInterceptor` (регистрация — `WebMvcConfig`) навешен на
`/api/public/song-picture/**` и `/api/public/song-vk-image/**`; лимит —
`KaraokeProperties.rateLimitSongPicturePerMinute` /
`rateLimitSongVkImagePerMinute` (дефолт 60/мин на IP).

## Архитектурные решения

### Решение 1: Все Public* без авторизации (кроме /account/)

По умолчанию — открытые. Защита только для `/account/*` через
`SiteAuthInterceptor`.

### Решение 2: DTO ≠ Entity

Public DTO (`SongPublicDto`) ≠ admin DTO (`SongDTO`). Скрыты
internal-флаги (`isReady`, `recordHash`, `isDeleted`).

### Решение 3: PollingCache для polling endpoints

`/api/public/news/since` (60s), `/api/public/account/chat/unreadcount`
(10s), `/api/public/share/heartbeat` (15s) — все используют
`PollingCache<V>` (см. [web-caches.md](../../caching/components/web-caches.md)).

### Решение 4: RateLimitInterceptor

`/api/public/song-picture/{id}` и `/api/public/song-vk-image/{id}` —
rate limit 60/min per IP (через `RateLimitInterceptor`,
настраивается через `KaraokeProperties`).

## Зависимости | Dependencies

- **SSE** ([sse domain](../../sse/domain.md)) — нет SSE напрямую в
  karaoke-web; события приходят через `karaoke-app`.
- **Caching** ([caching domain](../../caching/domain.md)) —
  `PollingCache` (**Pass 456: класс в `karaoke-app`**) + `DedupCache`.
- **Monetization** ([monetization domain](../../monetization/domain.md)) —
  `PaymentService`, `PriceService`.
- **Storage** ([storage domain](../../storage/domain.md)) —
  `StorageApiClientWeb`.

## Известные TODO

- [ ] **Каждый endpoint** — полный request/response схема (Pass 343+).
- [ ] **YooKassa webhook** — детальный flow (Pass 343+).
- [ ] **`SiteAuthInterceptor`** — детальная логика (Pass 343+).
- [ ] **CORS** — где настроен, какие origin.
- [ ] **Rate limits** — полный список endpoints с лимитами.

## Changelog

- **Pass 481** (2026-09-27, spec `481-knowledge-domain-karaoke-web`): секции приведены к шаблону компонента. Автор: agent (Karaoke).
- **Pass 363** (2026-09-09): Initial. Автор: agent (Karaoke).