---
status: Active
slug: 164-complete-guest-share-link
related:
  - ../domain/publishing.md
  - ../domain/identity.md
  - ../architecture/L3-components.md
  - ../../specs/164-complete-guest-share-link/spec.md
  - ../../archive/docs/features/guest-share-link.md
---

# 164 — Завершение share-link: плеер гостя + heartbeat + sweep (LiveDoc)

> Drill-down — [specs/164-complete-guest-share-link/spec.md](../../specs/164-complete-guest-share-link/spec.md).

## What it does

Доделывает фичу гостевого доступа к песням по share-ссылке. Раньше гость
после `claim` физически не мог открыть плеер (PlayerView ждал
`sessionStorage['kp_token_${id}']`, которого у гостя нет). Также не было
heartbeat (lease истекал через 60 сек), и не было `release()` (лимит 2
устройств не освобождался честно).

**Фиксы**:
1. PlayerView пускает гостя через `validateShareSession()` (пробрасываем
   `sessionStorage['kp_share_session_<songId>']`).
2. `KaraokePlayer` шлёт `heartbeat()` каждые ~30 сек независимо от паузы.
3. `KaraokePlayer` шлёт `release()` на `_onEnded` и `beforeunload`.
4. webvue3 endpoints `/api/siteusers/share/{links,sessions,links/revoke}` —
   реализованы, не только KDoc.
5. `ShareLinkSweeper` — авто-отзыв при: потере премиума владельцем, теге
   `SKIP`, истечении `active_session_lease_until`, ручном `ban` админом.
6. 410 `leaseExpired` / 404 `revoked` → overlay + кнопка «Закрыть» без авто-recovery.
7. UX: кнопка «Скопировать ссылку» в `ShareLinkModal`, автообновление статуса.

**TTL опции** для владельца: **1ч / 24ч / 7д**.

## User Stories (краткий список)

- **US1** (P1): Гость после claim попадает в плеер, видит песню в полном качестве.
- **US2** (P1): Lease поддерживается (`heartbeat`); при честном закрытии — `release()`.
- **US3** (P2): webvue3 показывает таблицу share-ссылок (сейчас «Загрузка…» и ничего).
- **US4** (P2): Sweeper автоматически отзывает ссылки при потере премиума / SKIP.

## Functional Requirements (указатель)

- **FR-001**: `PlayerView` пускает гостя через `validateShareSession()` + `sessionStorage['kp_share_session_<songId>']`.
- **FR-002**: `KaraokePlayer` → `heartbeat()` каждые 30 сек независимо от паузы.
- **FR-003**: `KaraokePlayer` → `release()` на `_onEnded` + `beforeunload` (через `sendBeacon`).
- **FR-004**: `webvue3` endpoints: `/api/siteusers/share/links`, `/sessions`, `/links/revoke`.
- **FR-005**: `ShareLinkSweeper` (`@Scheduled`) — 4 триггера авто-отзыва.
- **FR-006**: 410/404 → overlay + «Закрыть», без auto-recovery.
- **FR-007**: TTL: 1ч / 24ч / 7д (radio в ShareLinkModal).

## Acceptance Criteria

- [ ] **AC1**: Гость `/share/{id}/{secret}` → claim → редирект → плеер играет.
- [ ] **AC2**: Через 60 сек проигрывания вкладка жива → lease не истёк (heartbeat работает).
- [ ] **AC3**: Закрытие вкладки честно → лимит 2 устройств освобождается через ≤ 60 сек (sweeper fallback).
- [ ] **AC4**: webvue3 → «UserShareLinksModal» показывает таблицу ссылок + сессий.
- [ ] **AC5**: Владелец теряет премиум → все его ссылки авто-отзываются (sweeper).
- [ ] **AC6**: 410 во время воспроизведения → overlay с «Закрыть», плеер на паузе.
- [ ] **AC7**: TTL 1ч / 24ч / 7д работают (radio + create с этим TTL).

## Related LiveDocs

- Domain: [publishing.md](../domain/publishing.md) (ShareLink = гость), [identity.md](../domain/identity.md) (SiteUser — владелец)
- Architecture: [L3-components.md](../architecture/L3-components.md) (controllers + scheduler)
- Specs: `166-fix-share-link-timezone`, `167-fix-share-claim-500`, `169-share-link-in-premium-compare`, `172-db-sync-temporary-links` (смежные)

## Code

- Backend: `karaoke-web/.../services/SongShareLinkService.kt` — добавить `validateShareSession()` вызов из PlayerView
- Backend: `karaoke-web/.../controllers/PublicShareController.kt` — endpoints
- Backend: `karaoke-web/.../controllers/PublicPlayerController.kt:93-96,431` — гость-токен authorize
- Backend: `karaoke-web/.../schedulers/ShareLinkSweeper.kt` (новый)
- Backend: `karaoke-web/.../controllers/AdminShareLinksController.kt` (новый) — для webvue3
- Frontend: `karaoke-public/src/views/PlayerView.vue:165-167` — гость-токен authorize
- Frontend: `karaoke-public/src/components/KaraokePlayer.vue` — heartbeat + release
- Frontend: `webvue3/src/components/Users/UserShareLinksModal.vue` — отобразить таблицу
- Frontend: `webvue3/src/store/modules/shareLinkStore.js:49-86` — реализовать actions

## History

- Created: 2026-08-14
- Last updated: 2026-08-14