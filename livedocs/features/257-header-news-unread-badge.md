---
status: Active
slug: 257-header-news-unread-badge
related:
  - ../../specs/257-header-news-unread-badge/spec.md
  - ../../specs/257-header-news-unread-badge/plan.md
  - ../../specs/257-header-news-unread-badge/contracts/useNewsUnreadCount-composable.md
  - ../../specs/257-header-news-unread-badge/contracts/AuthStatusWidget-integration.md
  - ../../specs/257-header-news-unread-badge/quickstart.md
  - 250-unify-site-header
  - 162-fix-header-stale-premium-status
---

# 257 — Бейдж непрочитанных новостей в шапке (LiveDoc)

> Drill-down — [specs/257-header-news-unread-badge/spec.md](../../specs/257-header-news-unread-badge/spec.md),
> [plan.md](../../specs/257-header-news-unread-badge/plan.md).

## Что делает

Заменяет отдельную плавающую иконку-колокольчик `NewsBell.vue` (📰 в правом верхнем углу поверх контента, см. spec 250, A-002) на компактный inline-бейдж рядом с ссылкой «Новости» в правом слоте `<AppHeader>` → `AuthStatusWidget`. Бейдж показывает число непрочитанных новостей (1..49) или «50+» при cap=50 бэкенда `/api/public/news/since`. Polling-логика (45 сек, suppression на `/news`/`/player`/`/share`, opt-out для анонимов — Pass 52 protection) перенесена в singleton-composable `useNewsUnreadCount` и больше не привязана к плавающей иконке.

Для нового пользователя (нет ключа `km_news_last_seen_id` в `localStorage`) composable делает silent reset: один доп. запрос `/api/public/news?page=0&size=1`, записывает максимальный id в localStorage до начала основного polling-а — бейдж сразу скрыт (`count = 0`) вместо «50+» (FR-013 / Clarification Q1 2026-08-27).

**Smart reset (US4, итерация 2 — 2026-08-27)**: бейдж автоматически исчезает в двух случаях — (a) **immediate** при переходе на `/news` (FR-015) — в ту же секунду, до HTTP-запроса `NewsView`; (b) **auto-read через 10 секунд** на главной `/` (FR-016), где рендерится `<LatestNewsSection>` — пользователь «увидел» последние новости. Семантика: бейдж живёт ровно столько, сколько нужно для «прочтения».

Бейдж имеет фиксированный `aria-label="N непрочитанных {pluralForm}"` для скрин-ридеров, **без** `aria-live` — избегаем шумных объявлений на каждом 45-сек polling-е (FR-014 / Clarification Q2 2026-08-27). Склонение «новость / новости / новостей» через локальную функцию `pluralize()`.

## User Stories (краткий список)

- **US1** (P1) — Бейдж с числом непрочитанных рядом с ссылкой «Новости»: 1..49 / «50+», для анонимов скрыт, на узких экранах автоматически скрыт (наследует `display: none` от родительского `<RouterLink>`).
- **US2** (P1) — Плавающая кнопка `📰` отсутствует на всех страницах сайта; вся информация о новых новостях — через inline-бейдж.
- **US3** (P2) — Polling `/api/public/news/since` сохраняется (45 сек, suppression на скрытых маршрутах и для анонимов), результат обновляет только бейдж.
- **US4** (P1, итерация 2) — Smart reset: immediate на `/news` (FR-015) + auto-read 10 сек на `/` (FR-016). Бейдж живёт ровно столько, сколько пользователю нужно для «прочтения».

## Functional Requirements (указатель)

- **FR-001..FR-016** — спека [spec.md](../../specs/257-header-news-unread-badge/spec.md#requirements).
- **FR-013** (clarified 2026-08-27) — silent reset для нового пользователя.
- **FR-014** (clarified 2026-08-27) — `aria-label` без `aria-live`.
- **FR-015** (US4, 2026-08-27) — immediate reset на `/news`.
- **FR-016** (US4, 2026-08-27) — auto-read через 10 сек на `/`.

## Acceptance Criteria

- [x] **AC1**: Заменить плавающую иконку на inline-бейдж (US1 + US2).
- [x] **AC2**: При `count = 50` бэкенда бейдж показывает «50+».
- [x] **AC3**: При первом визите пользователя (нет `localStorage.km_news_last_seen_id`) бейдж сразу скрыт, ключ создан до основного polling-а.
- [x] **AC4**: ARIA: `aria-label="N непрочитанных {pluralForm}"`, скрин-ридер перечитывает при изменении числа, без шума от повторяющихся объявлений.
- [x] **AC5**: Polling приостановлен на `/news`, `/player`, `/share` и для анонимов (Pass 52).
- [x] **AC6**: На узких экранах (≤ 700px) бейдж автоматически скрыт (наследует `display: none` от `.km-auth-link-news`).
- [x] **AC7** (US4): Клик по ссылке «Новости» → бейдж исчезает в ту же секунду, до HTTP-запроса `/api/public/news`.
- [x] **AC8** (US4): На `/` бейдж автоматически исчезает через 10 сек после появления (auto-read).
- [x] **AC9** (US4): Auto-read timer отменяется при уходе с `/` или при переходе на `/news` (выполняется immediate reset вместо).
- [x] **AC10** (US4): Auto-read timer перезапускается при новых новостях (count > 0 → newCount > oldCount).

## Связанные LiveDocs

- [250-unify-site-header](250-unify-site-header.md) — родительский header-context: `<AppHeader>` + `AuthStatusWidget` (правый слот).
- [162-fix-header-stale-premium-status](162-fix-header-stale-premium-status.md) — live-обновление 🪙-бейджа в `AuthStatusWidget` через `useAuth`/`usePremiumLiveSync`. После миграции логика не затрагивается — `useNewsUnreadCount` независим.
- Architecture: [L3-components.md](../architecture/L3-components.md) — структура Vue-компонентов `karaoke-public`.

## Код

- Frontend: `karaoke-public/src/composables/useNewsUnreadCount.js` (NEW, singleton composable, ~230 строк с US4).
- Frontend: `karaoke-public/src/components/AuthStatusWidget.vue` (MODIFIED — добавлен inline `<span class="km-news-badge">` внутри ссылки «Новости», CSS-стиль `.km-news-badge` в `<style scoped>`).
- Frontend: `karaoke-public/src/App.vue` (MODIFIED — удалён `<NewsBell />` + import + components).
- Frontend: `karaoke-public/src/components/NewsBell.vue` (DELETED, 269 строк — вся логика в composable).
- Frontend: `karaoke-public/src/views/NewsView.vue` (UNCHANGED — `markAllSeen()` остаётся как defense-in-depth, обновлён комментарий).
- Backend: `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicNewsController.kt` (UNCHANGED — cap=50, TTL=60s, сохранены).
- API: `GET /api/public/news/since?id=<long>` (UNCHANGED — `{count, items}`), `GET /api/public/news?page=0&size=1` (NEW use — для silent reset, endpoint существующий).

## Итерация 2 (US4, 2026-08-27)

После первой итерации (PR с базовой функциональностью) добавлены:

- **`items: Ref<News[]>`** в composable — хранит `items` из последнего `/since`-ответа для `markRead()` (R-011).
- **`markRead()`** — sync-функция: `localStorage['km_news_last_seen_id'] = max(items.id)`, `count = 0`, отмена auto-read timer.
- **Immediate reset** в route watcher при `route.name === 'news'` — вызывается до HTTP-запроса `NewsView`, бейдж исчезает в ту же секунду (FR-015).
- **Auto-read timer** через `setTimeout(10_000)` на `/` — стартует при `count 0 → >0` или при входе на `/` с `count > 0` (FR-016). Отменяется при уходе с `/`, при переходе на `/news` или при `count → 0`. Перезапускается при новых новостях.
- **`NEWS_SHOWN_ROUTES = new Set(['/'])`** — пока только главная (R-009, ZakromaView не содержит `<LatestNewsSection>`).

## История

- Создан: 2026-08-27
- Последнее обновление: 2026-08-27
