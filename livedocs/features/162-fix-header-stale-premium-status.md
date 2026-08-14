---
status: Active
slug: 162-fix-header-stale-premium-status
related:
  - ../domain/identity.md
  - ../domain/publishing.md
  - ../architecture/L3-components.md
  - ../../specs/162-fix-header-stale-premium-status/spec.md
---

# 162 — Устаревший premium-статус в шапке сайта (LiveDoc)

> Drill-down — [specs/162-fix-header-stale-premium-status/spec.md](../../specs/162-fix-header-stale-premium-status/spec.md).

## Что делает

Если у пользователя закончилась подписка, шапка сайта (виджет авторизации —
значок 🪙 «Премиум-подписчик» рядом с именем) **продолжала показывать его как
премиум** до обновления страницы или перелогина. Это вводит в заблуждение
(обещает то, чего уже нет).

**Фикс**:
- Поллинг `/api/public/account/status` каждые 60 сек (или WebSocket push через
  `SSE_PROCESS_USER_EVENTS`).
- На стороне Vuex `auth.currentUser.isPremium` обновляется **live**.
- При `isPremium: false` шапка скрывает значок 🪙 без перезагрузки.
- Симметричный случай: оформление/продление подписки → значок появляется live.

## User Stories (краткий список)

- **US1** (P1): Шапка перестаёт показывать премиум сразу после окончания подписки (без F5).
- **US2** (P2): Симметричный случай — при оформлении подписки значок появляется live.

## Functional Requirements (указатель)

- **FR-001**: Live-обновление `isPremium` через поллинг или SSE (канал `PROCESS_USER_EVENTS`).
- **FR-002**: Vuex mutation `setPremiumStatus(boolean)`.
- **FR-003**: Шапка `karaoke-public` реактивна на `auth.isPremium`.
- **FR-004**: Несколько открытых вкладок — все получают обновление (через `BroadcastChannel` или storage event).
- **FR-005**: Возврат к вкладке после долгой неактивности — статус актуализируется немедленно.

## Acceptance Criteria

- [ ] **AC1**: Подписка истекла → значок премиума в шапке пропадает в ≤ 90 сек без F5.
- [ ] **AC2**: После F5 — статус сразу актуальный.
- [ ] **AC3**: Перелогин — статус сразу актуальный.
- [ ] **AC4**: Активная подписка → значок стабильно премиум, без ложных сбросов (регрессионный тест).
- [ ] **AC5**: Оформление подписки — значок появляется live.

## Связанные LiveDocs

- Domain: [identity.md](../domain/identity.md) (SiteUser + premium-флаг), [publishing.md](../domain/publishing.md) (Subscription)
- Architecture: [L3-components.md](../architecture/L3-components.md) (SSE Hub — `PROCESS_USER_EVENTS`)
- Specs: `specs/171-admin-subscriptions-history` (admin-таблица подписок)

## Код

- Backend: `karaoke-app/.../services/SubscriptionService.kt` — публикация `PROCESS_USER_EVENTS` при изменении подписки
- Backend: `karaoke-app/.../controllers/PublicAccountController.kt` — `/api/public/account/status`
- Frontend: `karaoke-public/src/store/modules/auth.js` — `setPremiumStatus` mutation + action `loadPremiumStatus`
- Frontend: `karaoke-public/src/components/HeaderAuth.vue` — реактивный бейдж 🪙
- Frontend: `karaoke-public/src/composables/usePremiumLiveSync.js` — поллинг / SSE / BroadcastChannel

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14