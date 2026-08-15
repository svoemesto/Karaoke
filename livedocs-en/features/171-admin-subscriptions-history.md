---
status: Active
slug: 171-admin-subscriptions-history
related:
  - ../domain/catalog.md
  - ../domain/publishing.md
  - ../domain/identity.md
  - ../architecture/L3-components.md
  - ../../specs/171-admin-subscriptions-history/spec.md
---

# 171 — Админ-таблицы «Подписки», «История прослушиваний», «Временные ссылки» (LiveDoc)

> Drill-down — [specs/171-admin-subscriptions-history/spec.md](../../specs/171-admin-subscriptions-history/spec.md).

## What it does

В админ-SPA (`webvue3`) добавлены **три новых пункта меню** с read-only
таблицами (для саппорта и аудита):

1. **«Подписки»** (`/subscriptions`) — все записи `tbl_subscriptions` с фильтрами
   по `userId / scope / status / дате` и target-aware (local/remote).
2. **«История прослушиваний»** (`/listeninghistory`) — все записи `tbl_listening_history`
   с JOIN к `tbl_songs` (название/исполнитель/альбом). Skip-фильтр.
3. **«Временные ссылки»** (`/sharelinks`) — все записи `tbl_song_share_links`
   с JOIN к `tbl_songs` + `tbl_site_users`. Возможность **отозвать** активную
   ссылку прямо из таблицы (переиспользует `/api/siteusers/share/links/revoke`).

Все три таблицы используют **стандартный паттерн проекта**: персистентность
страницы пагинации через Vuex (см. `AGENTS.md` Q&A и
[`../architecture/webvue3-patterns.md`](../architecture/webvue3-patterns.md)) — админ
теряет позицию при переключении пунктов меню.

## User Stories (краткий список)

- **US1** (P1): Глобальный список подписок с фильтрами (scope=SITE/SONG, status=PAID, по дате, по пользователю).
- **US2** (P1): Глобальная история прослушиваний с JOIN к `tbl_songs`, Skip-фильтр.
- **US3** (P1): Глобальный список временных ссылок + возможность «Отозвать» активную.
- **US4** (P2): Сохранение позиции (страница + фильтры) при переключении пунктов меню (стандартный паттерн).

## Functional Requirements (указатель)

- **FR-001**: Три новых пункта меню: «Подписки», «История прослушиваний», «Временные ссылки».
- **FR-002**: Read-only таблица с фильтрами (по пользователю/песне/дате/статусу) + target-aware (local/remote).
- **FR-003**: Подписки: `userId / scope (SITE|SONG) / status (PAID|...|) / amount / auto-renew / paid_at / order_id`.
- **FR-004**: История: JOIN к `tbl_songs`, Skip-фильтр на чтении (тот же getter, что в `getForUser`).
- **FR-005**: Временные ссылки: JOIN к `tbl_songs` и `tbl_site_users`; `revokeSiteUserShareLink()` action.
- **FR-006**: Drill-down: клик по пользователю/песне открывает карточку в SPA (без новой вкладки).
- **FR-007**: Стандартный паттерн персистентности пагинации (по `AGENTS.md` Q&A): `state.currentPage` в Vuex + `setWebvueProp`/`getWebvueProp` (для webvue3).

## Acceptance Criteria

- [ ] **AC1**: Открыть `/subscriptions` — таблица отрисовалась, по 5+ подписок в день (production data).
- [ ] **AC2**: Открыть `/listeninghistory` — JOIN работает, фильтр по userId применяется.
- [ ] **AC3**: Открыть `/sharelinks` — активные ссылки с зелёным бейджем, отозванные с красным + причиной.
- [ ] **AC4**: Кнопка «Отозвать» в `/sharelinks` — переводит ссылку в `active=false`, причина `'admin'`, обновление без F5.
- [ ] **AC5**: Skip-фильтр в истории: песни с тегом `SKIP` НЕ появляются.
- [ ] **AC6**: Drill-down: клик по пользователю → карточка `/siteusers` (фокус на нём, не новая вкладка).
- [ ] **AC7**: Сохранение позиции: пагинация + фильтры помнятся при переключении пунктов меню.

## Related LiveDocs

- Domain: [catalog.md](../domain/catalog.md) (Song), [publishing.md](../domain/publishing.md) (Subscription, On-Air), [identity.md](../domain/identity.md) (SiteUser)
- Architecture: [L3-components.md](../architecture/L3-components.md) (controllers + Vuex)
- Architecture: [webvue3-patterns.md](../architecture/webvue3-patterns.md) (паттерн пагинации)

## Code

- Backend контроллер: `karaoke-web/src/main/kotlin/.../controllers/AdminSubscriptionsController.kt` (новый), `AdminListeningHistoryController.kt` (новый), `AdminShareLinksController.kt` (новый)
- Backend service: `SubscriptionService.listAll()`, `ListeningHistoryService.listAll()`, `ShareLinkService.listAll()`
- Frontend: `webvue3/src/components/Subscriptions/SubscriptionsTable.vue` (новый)
- Frontend: `webvue3/src/components/ListeningHistory/ListeningHistoryTable.vue` (новый)
- Frontend: `webvue3/src/components/ShareLinks/ShareLinksTable.vue` (новый)
- Frontend: `webvue3/src/router/index.js` — три новых маршрута
- DTO: `SubscriptionAdminDto.kt`, `ListeningHistoryAdminDto.kt`, `ShareLinkAdminDto.kt`

## History

- Created: 2026-08-14
- Last updated: 2026-08-14