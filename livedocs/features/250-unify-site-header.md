---
status: Active
slug: 250-unify-site-header
related:
  - ../architecture/L3-components.md
  - ../../specs/250-unify-site-header/spec.md
  - ../../specs/250-unify-site-header/plan.md
  - 162-fix-header-stale-premium-status
---

# 250 — Унификация шапки сайта (LiveDoc)

> Drill-down — [specs/250-unify-site-header/spec.md](../../specs/250-unify-site-header/spec.md),
> [plan.md](../../specs/250-unify-site-header/plan.md).

## Что делает

Заменяет 20 дублированных `<header class="km-header">`-блоков в `karaoke-public/src/views/*.vue`
на единый Vue-компонент [`<AppHeader>`](../../karaoke-public/src/components/AppHeader.vue).
Логотип `KARAOKE_LOGO.png` **всегда справа**, **всегда кликабельный → `/`**. Слева — back-ссылка
по контексту (`← Главная`, `← Закрома`, `← Мои плейлисты`, `← Мои задания`, `← Личный кабинет`).
В правом слоте — `AuthStatusWidget` + переключатель темы (везде, кроме editor-страниц).

**Исключения**: `PlayerView`, `ShareView`, `SubscriptionReturnView` остаются без шапки
(full-screen / минималистичный layout, A-002). `EditorWorkView` использует `<AppHeader>` через
slots (left = back, center = заголовок песни + автор, right = статус-бейдж).

## User Stories (краткий список)

- **US1** (P1): Единая шапка на 20 страницах сайта.
- **US2** (P2): Единый CSS-стиль шапки (0 дублей в views).
- **US3** (P3): Специализированная шапка `EditorWorkView` через slots.

## Functional Requirements (указатель)

- **FR-001..FR-016** — спека [spec.md](../../specs/250-unify-site-header/spec.md#requirements).

## Acceptance Criteria

- [ ] **AC1**: 20 view-файлов используют `<AppHeader>` (SC-001):
      `grep -l "AppHeader" karaoke-public/src/views/*.vue | wc -l` ≥ 20.
- [ ] **AC2**: 0 CSS-дублей в views (SC-002):
      `grep -rn "\.km-header\|\.km-back\|\.km-logo" karaoke-public/src/views/ --include="*.vue"`
      возвращает пусто.
- [ ] **AC3**: Логотип на любой странице (с `<AppHeader>`) кликабельный → `/` (SC-003).
- [ ] **AC4**: Логотип всегда справа (SC-004).
- [ ] **AC5**: Editor-страницы (`/account/editor`, `/account/editor/<id>`) корректно отделены:
      без `AuthStatusWidget` и theme toggle (SC-005).
- [ ] **AC6**: Live-логика premium не сломана: значок 🪙 в `AuthStatusWidget` обновляется
      через `usePremiumLiveSync` (LiveDoc `162-fix-header-stale-premium-status`, AC1).
- [ ] **AC7**: 0 нарушений ESLint baseline (`tools/check-eslint-baseline.sh karaoke-public`).
- [ ] **AC8**: `npm run build && npm run lint` — PASS.
- [ ] **AC9**: EditorWorkView: back «← Мои задания» слева, заголовок песни + автор в центре,
      статус-бейдж справа.

## Связанные LiveDocs

- [162-fix-header-stale-premium-status](162-fix-header-stale-premium-status.md) — live-обновление
  🪙-бейджа в `AuthStatusWidget` через `usePremiumLiveSync`. После миграции логика не затрагивается —
  `<AppHeader>` лишь рендерит существующий компонент.
- Architecture: [L3-components.md](../architecture/L3-components.md) — структура Vue-компонентов
  `karaoke-public`.

## Код

- `karaoke-public/src/components/AppHeader.vue` — **новый** Vue 3 SFC с props (`back`,
  `profileLink`, `showAuthWidget`, `showThemeToggle`, `sticky`, `logoSrc`, `logoAlt`, `maxWidth`)
  и slots (`left`, `center`, `right`). KDoc описывает API, FR-ссылки, related LiveDocs.
- `karaoke-public/src/views/*.vue` — **20 миграций**: HomeView, SearchView, ZakromaView,
  AboutView, NewsView, PremiumView, LoginView, RegisterView, OfertaView, AccountView,
  EditorTasksView, SongView, PlaylistEditView, AuthorPlaylistView, CartView, StemJobsView,
  SubscriptionsView, PlaylistsView, ChatView, HistoryView + EditorWorkView (slot-based).
- `karaoke-public/src/components/AuthStatusWidget.vue` — без изменений (используется as-is
  через `<AppHeader>`).

## История

- Создан: 2026-08-27
- Последнее обновление: 2026-08-27