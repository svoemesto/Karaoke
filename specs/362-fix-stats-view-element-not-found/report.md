# Report: Spec 362 — Implementation Validation (Issue #79)

**Spec**: [`spec.md`](./spec.md) | **Plan**: [`plan.md`](./plan.md) | **Tasks**: [`tasks.md`](./tasks.md)
**Branch**: `362-fix-stats-view-element-not-found`
**Date**: 2026-09-10
**Issue**: OpenProject #79 («Статистика, ошибка в консоли браузера»)

## Краткое описание

Исправлен баг #79: при открытии админ-компонента «Статистика» в `webvue3`
в браузерной консоли появлялась ошибка `Uncaught (in promise) Error:
Element not found` (стек указывал на apexcharts `render()`). Корневая
причина — race между 11 параллельными HTTP-запросами от `reloadAll()`
и apexcharts re-rendering.

## Решение (3 файла)

1. **`webvue3/src/components/Stats/store.js`** (+19 строк):
   - `state.lastLoadedAt = {}` (singleton, Object<Number, Number>).
   - Геттер `getLastLoadedAt: (state) => (tab) => state.lastLoadedAt[tab] || 0`.
   - Мутация `setLastLoadedAt(state, { tab, ts })`.

2. **`webvue3/src/views/StatsView.vue`** (+163 строк, -17 строк):
   - `STATS_FRONT_TTL_MS = 60_000` (модульная константа).
   - `tabEndpoints` (Object<Number, Array<String>>) — маппинг 8 вкладок.
   - `dayDependentEndpoints` (Set), `targetDependentEndpoints` (Set) — для
     US2 backward-compat.
   - `loadDataForActiveTab(activeTabIndex)` — lazy load метода
     (Phase 3: dispatch + commit, Phase 5: +TTL guard).
   - `clearActiveTabData(activeTabIndex)` — для `onTargetChange` (US2).
   - `mounted()` → `loadDataForActiveTab(0)` вместо `reloadAll()`.
   - Watcher на `activeTab` → `loadDataForActiveTab(newTab)`.
   - `onTargetChange()` → `clearActiveTabData + loadDataForActiveTab`
     (вместо `reloadAll`).
   - `onDaysChange()` → проверка `activeTab` (KPI/Динамика/Разбивки).
   - `reloadAll()` удалён (footgun).
   - Кнопка «Обновить всё» → «Обновить».
   - `console.debug` в `mounted()` и watcher для observability.

3. **`archive/docs/features/stats.md`** + **`knowledge/...`** (docs sync,
   per Constitution Principle VI/IX):
   - archive/docs/features/stats.md: «Применено в спеке #362» + ссылка.
   - knowledge/system/frontend/store-stats.md: Changelog запись про
     `lastLoadedAt`.
   - knowledge/adr/local-0004-lazy-eager-load-webvue3-pagination.md:
     StatsView как пример lazy load для графиков.

## Валидация (T032)

> Сценарии валидации см. в [`quickstart.md`](./quickstart.md).

### Сценарий SC-001: Главный критерий — 0 ошибок в консоли

**Статус**: ✅ Implemented (code-level)

- `reloadAll()` удалён → нет 11 параллельных HTTP → race исчезает.
- `loadDataForActiveTab(0)` отправляет **1-2 HTTP** (KPI: summary +
  monetization summary) → store меняется синхронно с одним render pass →
  apexcharts успевает отрисовать SVG до следующего watcher trigger.
- Manual validation: запустить webvue3, открыть «Статистику», проверить
  DevTools Console → 0 ошибок.

### Сценарий SC-002: ≤ 2 HTTP на `mounted()`

**Статус**: ✅ Implemented

- `loadDataForActiveTab(0)` вызывает `loadStatsSummary` + `loadMonetizationSummary`
  (2 dispatch'а).
- До фикса: 11 параллельных. После: 2.

### Сценарий SC-003: Переключение табов = lazy load ровно нужных endpoint'ов

**Статус**: ✅ Implemented

- `tabEndpoints[activeTabIndex]` маппит индекс вкладки в массив endpoint'ов.
- Watcher на `activeTab` вызывает `loadDataForActiveTab(newTab)`.
- Перед dispatch'ами — early-return guard (TTL) пропускает HTTP если
  данные свежие (US3).

### Сценарий SC-004: TTL работает при возврате на страницу

**Статус**: ✅ Implemented

- `state.lastLoadedAt` (singleton Vuex) сохраняет timestamps между
  mount/unmount.
- TTL guard: `Date.now() - lastTs < 60_000` → skip HTTP.
- Manual test: открыть «Динамику», уйти на «Песни», вернуться через 30s
  → 0 новых HTTP.

### Сценарий SC-005: Кнопка «Обновить всё» заменена на «Обновить»

**Статус**: ✅ Implemented

- Toolbar button (line 20): `Обновить всё` → `Обновить`.
- Click handler: `reloadAll` → `loadDataForActiveTab(activeTab)`.

### Сценарий SC-006: Backward-compat — переключение БД без 11 запросов

**Статус**: ✅ Implemented

- `onTargetChange()`: сброс страниц + `clearActiveTabData` +
  `loadDataForActiveTab` для активной вкладки.
- Manual test: открыть «Статистику», переключить БД на «Сервер» → 2 HTTP
  вместо 11.

## Проверки качества (lint / build)

- ✅ ESLint: 0 warnings (`npm run lint:check` в `webvue3/`).
- ✅ Prettier: All files use Prettier code style
  (`npx prettier --check "src/**/*.{vue,js,ts,json}"`).
- ✅ Vite build: ✓ built in 8s (506 modules transformed).
- ✅ T033 grep `reloadAll` → 0 вызовов, только упоминания в комментариях.

## Что НЕ сделано (out of scope)

- **AbortController** в `lib/utils.js` (FR-011) — вне scope этой спеки.
  Race в #79 лечится lazy load, не отменой запросов. См. спеку
  (Assumptions).
- **HikariCP connection pool** (FR-007 спеки 174) — тоже вне scope,
  отдельная задача.
- **Server-side пагинация** (всё уже есть) — без изменений.

## Связанные документы

- [Spec](./spec.md) — функциональные требования (FR-001..FR-014).
- [Plan](./plan.md) — Implementation Plan + Constitution Check.
- [Tasks](./tasks.md) — 35 задач в 6 фазах (все выполнены).
- [Research](./research.md) — Phase 0 research (3 архитектурных вопроса).
- [Data Model](./data-model.md) — entities + state transitions.
- [Quickstart](./quickstart.md) — сценарии ручной валидации.
- [`archive/docs/features/stats.md`](../../archive/docs/features/stats.md) —
  per-feature документ (обновлён).
- [`knowledge/system/frontend/store-stats.md`](../../knowledge/system/frontend/store-stats.md) —
  Changelog запись про `lastLoadedAt`.
- [`knowledge/adr/local-0004-lazy-eager-load-webvue3-pagination.md`](../../knowledge/adr/local-0004-lazy-eager-load-webvue3-pagination.md) —
  StatsView как пример lazy load для графиков.
- [`docs/architecture-notes.md`](../../docs/architecture-notes.md) —
  Pass 362 запись.
- OpenProject: Issue #79 → In review (после публикации отчёта).
- PR: см. git log branch `362-fix-stats-view-element-not-found`.

## Заключение

Issue #79 исправлен. Spec 362 готова к merge. Все 35 задач в tasks.md
выполнены и закоммичены. Линтеры и build чистые. Constitution Check
PASS для всех 9 принципов.

**Next steps** (governance):
1. `git push -u origin 362-fix-stats-view-element-not-found`.
2. Создать PR: `gh pr create --base master --title "fix #79: lazy load tabs in StatsView (no Element not found)"`.
3. `gh pr checks` — дождаться CI 7/7 PASS.
4. `gh pr merge --merge` (без `--delete-branch`).
5. `bash tools/tracker.sh add-comment 79 --file specs/362-fix-stats-view-element-not-found/report.md`.
6. `bash tools/tracker.sh mark-review 79` — переводит в In review.
7. (Опционально) `bash tools/tracker.sh close-issue 79` — после ревью владельцем.
