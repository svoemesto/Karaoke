# Implementation Plan: Бейдж непрочитанных новостей в шапке

**Branch**: `257-header-news-unread-badge` | **Date**: 2026-08-27 | **Spec**: [spec.md](spec.md)
**Research**: [research.md](research.md) | **Data Model**: [data-model.md](data-model.md) | **Contracts**: [contracts/useNewsUnreadCount-composable.md](contracts/useNewsUnreadCount-composable.md), [contracts/AuthStatusWidget-integration.md](contracts/AuthStatusWidget-integration.md) | **Quickstart**: [quickstart.md](quickstart.md)

**Input**: Feature specification from `/specs/257-header-news-unread-badge/spec.md`

## Summary

Заменить плавающую иконку-колокольчик `NewsBell.vue` (📰 в правом верхнем углу) на компактный inline-бейдж рядом с ссылкой «Новости» в `<AuthStatusWidget>` (правый слот `<AppHeader>` из spec 250). Логика polling-а (`/api/public/news/since`, 45 сек) и работы с `localStorage['km_news_last_seen_id']` переезжает в новый singleton-composable `useNewsUnreadCount`. Текст бейджа — число 1..49 или «50+» при `count = 50` (cap бэкенда). Для новых пользователей (нет ключа `lastSeenId`) — silent reset: один доп. запрос `/api/public/news?page=0&size=1` устанавливает `lastSeenId` до актуального max (FR-013, clarified 2026-08-27).

**Smart reset (US4, добавлено после фидбэка 2026-08-27)**: бейдж автоматически исчезает в двух случаях: (a) **immediate reset** при переходе на `/news` — `count = 0`, `localStorage` обновляется в ту же секунду, до HTTP-запроса `NewsView` (FR-015); (b) **auto-read через 10 секунд** на `/` (где рендерится `<LatestNewsSection>`) — `setTimeout(10_000)` срабатывает, выполняется тот же reset (FR-016). Бейдж имеет `aria-label` для скрин-ридеров без `aria-live` (FR-014).

**Out of scope**:
- Изменения бэкенда `PublicNewsController` (cap=50 и TTL=60s остаются как есть).
- WebSocket/SSE для real-time вместо polling (FR-005 сохраняет 45 сек интервал).
- Multi-tab coordination через BroadcastChannel (Q3 — deferred, существующее поведение NewsBell).
- Активные toast-уведомления (FR-008 — удалено как deprecated-функционал).

## Technical Context

**Language/Version**: Vue 3 (Composition API), JavaScript (ES2022), Node 22 LTS, Vite 5
**Primary Dependencies**: `vue` (3.x), `vue-router` (`useRoute`, `RouterLink`), `services/newsApi.js` (`fetchNews`, `fetchNewsSince`), existing composables `useAuth`/`useCart`/`useDesign`, `localStorage` browser API
**Storage**: N/A (UI-only, `localStorage` keys `km_news_last_seen_id`, `km_auth_token` — existing)
**Testing**: manual smoke-test (10 сценариев в `quickstart.md`); нет автоматизированных тестов (Constitution § «Тесты» — `@Disabled`)
**Target Platform**: `karaoke-public` SPA (Vue 3 + Bootstrap 5), деплой через `do.sh build_start_public`
**Project Type**: Web frontend (`karaoke-public/`)
**Performance Goals**:
- SC-005: 1 polling-запрос каждые 45 сек на вкладку (не больше).
- HTTP-трафик: 1 доп. запрос `/api/public/news?page=0&size=1` при первом монтировании (silent reset) — единоразово.
- Без `aria-live` (FR-014) → 0 лишних screen reader announcements.
**Constraints**:
- **Не менять бэкенд** `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicNewsController.kt` — cap=50 и PollingCache TTL=60s остаются.
- **Не менять `NewsView.vue` логику `markAllSeen()`** (line 102-108) — composable использует тот же `localStorage` ключ, контракт обратной совместимости сохраняется.
- **Не менять `AuthStatusWidget.vue` template-структуру кроме инлайн бейджа** — минимизация изменений для простоты review.
- **Singleton composable** (R-001) — один `setInterval` на приложение.
- **KDoc обязателен** для нового composable (Constitution § VI FR-006, `@see` ссылки на spec.md и LiveDoc).
- **ESLint baseline** (`karaoke-public/.eslint-baseline.json`) — 0 НОВЫХ нарушений.
- **Mobile/responsive**: бейдж внутри `<RouterLink>` → автоматически скрывается на ≤ 700px (FR-011).
**Scale/Scope**:
- 1 новый файл: `karaoke-public/src/composables/useNewsUnreadCount.js` (~80 строк с JSDoc).
- 1 файл модифицируется: `karaoke-public/src/components/AuthStatusWidget.vue` (~10 строк добавлено: import + template span + style).
- 1 файл модифицируется: `karaoke-public/src/App.vue` (3 строки удалено: import NewsBell + components + template).
- 1 файл удаляется: `karaoke-public/src/components/NewsBell.vue` (269 строк).
- 1 LiveDoc создаётся: `livedocs/features/257-header-news-unread-badge.md` (FR-009).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-verified after Phase 1 design.*

- ✅ **Principle I (Self-contained автопайплайн)**: не затрагивается — UI-изменение в `karaoke-public`, не вводит внешних SaaS.
- ✅ **Principle II (Сырой JDBC)**: не затрагивается — frontend-only.
- ✅ **Principle III (SyncRegistry)**: не затрагивается.
- ✅ **Principle IV (Async-очередь)**: не затрагивается.
- ✅ **Principle V (Двух-фронтенд)**: затрагивается **только `karaoke-public`** (публичный SPA). Admin `webvue3` не трогаем. Бэкенд `karaoke-web` не трогаем.
- ✅ **Principle VI (Code Standards)**:
  - KDoc/JSDoc для нового composable (`export function useNewsUnreadCount`) обязателен (FR-006); `@see` ссылки на spec.md и `livedocs/features/257-header-news-unread-badge.md`.
  - Per-feature LiveDoc создаётся в том же PR (FR-009).
  - ESLint baseline (`tools/check-eslint-baseline.sh karaoke-public`) — нет НОВЫХ нарушений.
  - Code style соответствует существующим composables (`useAuth.js`, `useCart.js` — module-level `ref` + singleton).
- ✅ **Principle VII (Cross-Machine)**: не затрагивается — нет cross-machine изменений.
- ✅ **Principle VIII (Секреты)**: не затрагивается — никаких секрет-файлов, никаких hardcoded значений.

**Constitution Check: PASS** до и после Phase 1.

## Project Structure

### Documentation (this feature)

```text
specs/257-header-news-unread-badge/
├── plan.md                                    # Этот файл
├── spec.md                                    # Feature specification (с Clarifications)
├── research.md                                # Phase 0 output (R-001..R-007)
├── data-model.md                              # Phase 1 output (composable state + localStorage keys)
├── quickstart.md                              # Phase 1 output (10 manual smoke-test сценариев)
├── contracts/
│   ├── useNewsUnreadCount-composable.md       # Phase 1: composable API contract
│   └── AuthStatusWidget-integration.md        # Phase 1: Vue template diff + CSS
├── checklists/
│   └── requirements.md                        # 16/16 ✅ (Q1 + Q2 resolved 2026-08-27)
└── tasks.md                                   # Phase 2 (через /speckit.tasks — НЕ создаётся этим планом)
```

### Source Code (changes)

```text
karaoke-public/src/
├── composables/
│   └── useNewsUnreadCount.js                  # NEW: singleton composable (polling, lastSeenId, badge text)
├── components/
│   ├── AppHeader.vue                          # UNCHANGED (уже содержит <AuthStatusWidget> через spec 250)
│   ├── AuthStatusWidget.vue                   # MODIFIED: добавлен inline <span class="km-news-badge"> + setup()
│   └── NewsBell.vue                           # DELETED: 269 строк, вся логика переехала в composable
├── views/
│   ├── NewsView.vue                           # UNCHANGED: markAllSeen() уже корректно пишет lastSeenId
│   └── (прочие views)                         # UNCHANGED
├── App.vue                                    # MODIFIED: удалён <NewsBell /> + import + components
└── services/
    └── newsApi.js                             # UNCHANGED: fetchNews + fetchNewsSince уже существуют

livedocs/
└── features/
    └── 257-header-news-unread-badge.md       # NEW: LiveDoc per Constitution § VI FR-009
```

**Structure Decision**: фича затрагивает **только `karaoke-public/src/`** (frontend). Бэкенд не меняется. Создаётся 1 новый composable, модифицируется 2 существующих файла, удаляется 1 файл. Все артефакты в `specs/257-header-news-unread-badge/` и `livedocs/features/257-...`.

## Phase 0: Research Summary

См. [research.md](research.md). 7 решений (R-001..R-007):

| ID | Решение | Обоснование |
|----|---------|-------------|
| R-001 | Singleton composable (module-level ref) | Существующий паттерн `useAuth`/`useCart`/`useDesign` |
| R-002 | `useRoute()` для route change detection | Стандартный Composition API |
| R-003 | `try/catch` без сброса count | Текущее поведение `NewsBell`, UX-требование |
| R-004 | Локальная функция склонения для aria-label | Без зависимостей |
| R-005 | Удалить `NewsBell.vue`, логика в composable | 100% кода переезжает, мёртвого кода нет |
| R-006 | 0 новых ESLint-нарушений | Стиль соответствует существующим composables |
| R-007 | LiveDoc `livedocs/features/257-...md` | Constitution § VI FR-009 |

**Все NEEDS CLARIFICATION resolved.** Нет открытых вопросов.

## Phase 1: Design Artifacts Summary

См. [data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md).

**Data model**:
- `useNewsUnreadCount` state: `count: Ref<number>`, `pollingPaused: Ref<boolean>`.
- Computed: `badgeText`, `ariaLabel`, `showBadge`.
- `localStorage['km_news_last_seen_id']` — существующий ключ, новый writer — composable при silent reset.
- Бэкенд не меняется (`PublicNewsController.since` + `list`).

**Contracts**:
- `useNewsUnreadCount-composable.md` — полный API: возвращаемые поля, side effects, state machine, guarantees, test surface.
- `AuthStatusWidget-integration.md` — DOM diff (before/after), reactive bindings, CSS additions.

**Quickstart**:
- 10 manual smoke-test сценариев: анонимный, 3 непрочитанных, 50 → «50+», silent reset, открытие `/news`, polling pause на `/player`, отсутствие floating bell, backend error, a11y screen reader, узкий экран.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

Нет нарушений Constitution. Таблица пуста по определению.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| — | — | — |
