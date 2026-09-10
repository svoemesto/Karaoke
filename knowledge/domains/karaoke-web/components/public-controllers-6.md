# Public controllers: SongEditor/Subscription/Share/Cart/Playlist/Stats (средние)

> **Домен**: [karaoke-web](../domain.md)
> **Компонента**: обзор 7 critical Public-контроллеров (средние).

## Файлы

| Store | Файл | Строк | Endpoints |
|---|---|---|---|
| `PublicSongEditorController` | `webvue3/.../PublicSongEditorController.kt` | 343 | 8 |
| `PublicSubscriptionController` | `webvue3/.../PublicSubscriptionController.kt` | 304 | 5 |
| `PublicShareController` | `webvue3/.../PublicShareController.kt` | 274 | 7 |
| `PublicCartController` | `webvue3/.../PublicCartController.kt` | 225 | 5 |
| `PublicPlaylistController` | `webvue3/.../PublicPlaylistController.kt` | 522 | 14 |
| `PublicSettingsWebController` | `webvue3/.../PublicSettingsWebController.kt` | 328 | 3 |
| `PublicStemJobController` | `webvue3/.../PublicStemJobController.kt` | 5 (или подобно) | 5 |
| `SiteShareLinksController` | `webvue3/.../SiteShareLinksController.kt` | 136 | 3 |
| `InternalStatsController` | `webvue3/.../InternalStatsController.kt` | 56 | 1 |

## 1. `PublicSongEditorController` (8 endpoints, 343 строки)

`/api/public/song-editor/*` — **онлайн-редактор караоке-разметки**
(см. [editorial domain](../../editorial/domain.md) +
[composable-karaoke-editor.md](../../../system/frontend/composable-karaoke-editor.md)).

**CRITICAL** — этот контроллер + `SongEditorController`
(внутренний) реализуют главный user-facing flow:
- assignment → work → submit → approve.
- `defaultTarget='remote'` (см. [store-song-editor.md](../../../system/frontend/store-song-editor.md)) — реальная работа на проде.

## 2. `PublicSubscriptionController` (5 endpoints, 304 строки)

`/api/public/subscription/*` — оформление подписки (см.
[monetization domain](../../monetization/domain.md)).
- `POST /site` — подписка на сайт (scope=SITE).
- `POST /song` — подписка на песню (scope=SONG).
- `GET /my` — мои подписки.
- `POST /cancel` — отмена.

## 3. `PublicShareController` (7 endpoints, 274 строки)

`/api/public/share/*` — share-ссылки (см.
[SongShareLinkService](./song-share-link-service.md)):
- `POST /{songId}/create` — создать.
- `GET /mine/{songId}` — текущая.
- `POST /mine/{songId}/revoke` — отозвать.
- `POST /{token}/heartbeat` — heartbeat.
- `GET /{token}/session` — session info.

## 4. `PublicCartController` (5 endpoints, 225 строк)

`/api/public/cart/*` — корзина (см.
[composable-use-cart.md](../../../system/frontend/composable-use-cart.md)):
- `GET /list` — список.
- `POST /{songId}/toggle` — toggle.
- `POST /clear` — clear.

## 5. `PublicPlaylistController` (14 endpoints, **самый большой**)

`/api/public/playlist/*` — плейлисты пользователя
(см. [usePlaylistMembership.md](../../../system/frontend/composable-playlist-membership.md)):
- `GET /list` — список плейлистов.
- `POST /create` — создать.
- `GET /{id}` — детали.
- `POST /{id}/add/{songId}` — добавить песню.
- `POST /{id}/remove/{songId}` — убрать.
- `POST /{id}/reorder` — переставить.
- ... ещё 8 endpoints.
- `GET /playlists/membership` — DEPRECATED (Pass 361, см. [ADR-0009](../../adr/0009-get-vs-post-large-payload.md)).
- `POST /playlists/membership` — рекомендуемый (Pass 361, обход
  HTTP 414 на крупных авторах, см. [specs/361](../../../../specs/361-playlists-membership-uri-length/spec.md)).
  Оба делегируют в общий `buildMembershipResponse(user, songIds)`.

## 6. `PublicSettingsWebController` (3 endpoints, 328 строк)

`/api/public/settings/*` — публичные настройки (live в `tbl_public_settings`):
- `GET /all` — все настройки (для UI).
- `GET /{key}` — одна.
- `GET /kdc` — капча (конфиг).

## 7. `PublicStemJobController` (5 endpoints)

`/api/public/account/stemjobs/*` — премиум-фича «Создать минусовку»:
- `POST /create` — загрузить файл + запустить.
- `GET /list` — мои задания.
- `GET /{id}/download` — скачать результат.
- `POST /{id}/delete` — удалить.
- `POST /{id}/cancel` — отменить.

## 8. `SiteShareLinksController` (3 endpoints, 136 строк)

`/api/share-links/*` — admin управление share-ссылками.

## 9. `InternalStatsController` (1 endpoint, 56 строк)

`/api/internal/stats/*` — server-to-server (внутренний).

## Hot paths

- **`PublicPlaylistController`** — самый длинный (14 endpoints).
- **`PublicShareController`** — каждое воспроизведение по share-ссылке.
- **`PublicCartController`** — каждое добавление в корзину.

## Связь

- [public-controllers.md](public-controllers.md) — обзор.
- [karaoke-web/services-overview.md](services-overview.md) — backend services.
- [composable-use-cart.md](../../../system/frontend/composable-use-cart.md) — useCart.

## Changelog

- **Pass 481-485** (2026-09-09): Initial. Автор: agent (Karaoke).