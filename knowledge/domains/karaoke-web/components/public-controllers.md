# Component: public-controllers

> **Домен**: [karaoke-web](../domain.md)
> **Компонента**: детальный обзор 19 Public* контроллеров.

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
| 14 | `PublicSettingsWebController` | 3 | `/api/public/settings/...` | Публичные настройки сайта |
| 15 | `PublicNewsController` | 3 | `/api/public/news/...` | Новости (since, get, mark) |
| 16 | `PublicPaymentController` | 2 | `/api/public/payment/...` | YooKassa webhook, redirect |
| 17 | `PublicOgSongController` | 2 | `/api/public/og/...` | Open Graph теги для шеринга |
| 18 | `PublicHistoryController` | 2 | `/api/public/history/...` | История прослушиваний |
| 19 | `PublicTypographController` | 1 | `/api/public/typograph/...` | Типографика (утилита) |

## Детальные контракты

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

- `/api/public/home` — главная страница.
- `/api/public/...` — общие endpoint'ы (главная, статистика, счётчики).
- Включает `fetchFromMinIO` — утилита для получения MinIO-файлов
  через nginx-proxy.

### `PublicPaymentController`

**Endpoints**:

- `POST /api/public/payment/webhook` — YooKassa webhook (асинхронное
  уведомление об оплате). **Критический** — пропуск = потеря платежа.
- `GET /api/public/payment/redirect` — redirect после оплаты.

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

## Связь с другими компонентами

- **SSE** ([sse domain](../../sse/domain.md)) — нет SSE напрямую в
  karaoke-web; события приходят через `karaoke-app`.
- **Caching** ([caching domain](../../caching/domain.md)) —
  `PollingCache` + `DedupCache`.
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

- **Pass 363** (2026-09-09): Initial. Автор: agent (Karaoke).