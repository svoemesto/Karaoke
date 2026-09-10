---
description: "Task list for Issue #79 — fix Element not found in StatsView"
---

# Tasks: Исправить «Element not found» в компоненте «Статистика» (Issue #79)

**Input**: Design documents from `/specs/362-fix-stats-view-element-not-found/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, quickstart.md

**Tests**: тесты НЕ запрошены в спецификации (валидация — через DevTools Console/Network, см. quickstart.md SC-001..SC-006). Это соответствует практике проекта (см. Constitution § Governance п.6 — «в CI нет, проверка пользователем»).

**Organization**: Tasks сгруппированы по user story для обеспечения независимой реализации и тестирования.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Web app**: `webvue3/src/views/`, `webvue3/src/components/`, `archive/docs/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Подготовка к имплементации — нет новых пакетов, есть проверка текущего состояния.

- [ ] T001 Read current StatsView.vue (lines 1-100, 540-590) and Stats/store.js to verify reloadAll() still exists
- [ ] T002 Verify branch 362-fix-stats-view-element-not-found is active and clean

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Изменения в Vuex store (state + getter + mutation) — MUST быть готово до User Stories.

**⚠️ CRITICAL**: User Story implementation MUST ждать завершения этого этапа.

- [ ] T003 Add `lastLoadedAt: {}` to state in `webvue3/src/components/Stats/store.js` (after `state.topListenedPage = 1` line ~67)
- [ ] T004 [P] Add `getLastLoadedAt: (state) => (tab) => state.lastLoadedAt[tab] || 0` getter in `webvue3/src/components/Stats/store.js` (after `getTopListenedPage` line ~186)
- [ ] T005 [P] Add `setLastLoadedAt(state, { tab, ts }) { state.lastLoadedAt = { ...state.lastLoadedAt, [tab]: ts } }` mutation in `webvue3/src/components/Stats/store.js` (after `setTopListenedPage` mutation)
- [ ] T006 Verify ktlint/ESLint baselines unchanged for store.js (no new lint violations)

**Checkpoint**: Foundation ready — User Story implementation can now begin.

---

## Phase 3: User Story 1 — Открытие «Статистики» без ошибок в консоли (Priority: P1) 🎯 MVP

**Goal**: Главный баг #79. При открытии компонента «Статистика» НЕТ ошибок
`Element not found` в DevTools Console. Реализуется через FR-001..FR-005 + FR-009.

**Independent Test**: Открыть «Статистику» → DevTools Console → 0 ошибок
`Element not found`. В Network → 1-2 запроса на `mounted()`. (SC-001, SC-002)

### Implementation for User Story 1

- [ ] T007 [P] [US1] Add module-level const `STATS_FRONT_TTL_MS = 60_000` at top of `<script>` block in `webvue3/src/views/StatsView.vue`
- [ ] T008 [P] [US1] Add module-level const `tabEndpoints` (Object<Number, Array<String>>) at top of `<script>` block in `webvue3/src/views/StatsView.vue` (mapping from FR-004)
- [ ] T009 [US1] Replace `mounted()` in `webvue3/src/views/StatsView.vue` to call `loadDataForActiveTab(0)` instead of `reloadAll()` (FR-001)
- [ ] T010 [US1] Add `watch: { activeTab(newTab) { this.loadDataForActiveTab(newTab) } }` to `StatsView.vue` (FR-002)
- [ ] T011 [US1] Add `methods.loadDataForActiveTab(activeTabIndex)` method to `StatsView.vue` — iterates `tabEndpoints[activeTabIndex]` and dispatches corresponding `loadXxx` actions, with TTL check via `this.$store.getters.getLastLoadedAt(activeTabIndex)` (FR-004, FR-006)
- [ ] T012 [US1] After each successful dispatch in `loadDataForActiveTab`, commit `setLastLoadedAt({ tab: activeTabIndex, ts: Date.now() })` (FR-006)
- [ ] T013 [US1] Delete `reloadAll()` method (lines 572-583) from `StatsView.vue` (FR-003)
- [ ] T014 [US1] Replace toolbar button text «Обновить всё» with «Обновить» and call `loadDataForActiveTab(this.activeTab)` on click (FR-003, SC-005)
- [ ] T015 [US1] Add `console.debug('[Stats] mounted — lazy loading active tab', { tab: this.activeTab, ts: Date.now() })` in `mounted()` (FR-012)
- [ ] T016 [US1] Add `console.debug('[Stats] tab switched — lazy load', { from, to, ttlRemaining })` in `activeTab` watcher (FR-013)
- [ ] T017 [US1] Verify that no `reloadAll()` references remain in `webvue3/src/` (grep)

**Checkpoint**: User Story 1 should be fully functional and testable independently. Issue #79 closed.

---

## Phase 4: User Story 2 — Backward-compat при смене БД/периода (Priority: P2)

**Goal**: При переключении `local`/`remote` или `days` НЕ происходит 11 параллельных
HTTP. Только активная вкладка + endpoint'ы с фильтром days. Реализуется через FR-007, FR-008.

**Independent Test**: Открыть «Статистику», переключить БД → в Network ≤ 3 запроса
(вместо 11). (SC-006)

### Implementation for User Story 2

- [ ] T018 [P] [US2] Add module-level const `dayDependentEndpoints = new Set(['summary', 'timeseries', 'by-type', 'by-detail'])` in `StatsView.vue`
- [ ] T019 [P] [US2] Add module-level const `targetDependentEndpoints = new Set([...all 11 endpoints...])` in `StatsView.vue` (or use tabEndpoints' union)
- [ ] T020 [US2] Modify `onTargetChange()` in `StatsView.vue` — first clear active tab data via `clearActiveTabData(this.activeTab)`, then call `loadDataForActiveTab(this.activeTab)` (FR-007)
- [ ] T021 [US2] Add `methods.clearActiveTabData(activeTabIndex)` — dispatches `setStatsXxx(null/[]/0)` for endpoints of this tab (FR-007)
- [ ] T022 [US2] Modify `onDaysChange()` in `StatsView.vue` — only dispatch `loadStatsSummary`, `loadStatsTimeSeries`, `loadStatsBreakdown` if `activeTab` is in `{0, 2, 3}` (i.e. endpoint of this tab is in `dayDependentEndpoints`) (FR-008)

**Checkpoint**: User Stories 1 AND 2 should both work independently without regression.

---

## Phase 5: User Story 3 — TTL-кеш не блокирует работу (Priority: P2)

**Goal**: 60-секундный TTL-кеш на фронте работает — при возврате на страницу
в течение 60 сек НЕТ новых HTTP-запросов. Реализуется через FR-006 (TTL check).

**Independent Test**: Открыть «Динамику», уйти на «Песни», вернуться через 30 сек
→ в Network 0 новых запросов. (SC-004, US3)

### Implementation for User Story 3

- [ ] T023 [US3] In `loadDataForActiveTab(activeTabIndex)`, add early-return guard: `const age = Date.now() - this.$store.getters.getLastLoadedAt(activeTabIndex); if (age < STATS_FRONT_TTL_MS) return;` (FR-006, US3)
- [ ] T024 [US3] Verify that `Date.now() - lastLoadedAt[tab] < 60_000` short-circuits HTTP for already-loaded tabs (manual test)
- [ ] T025 [US3] Verify that returning to «Stats» page after 60+ seconds triggers fresh HTTP load (TTL expired)

**Checkpoint**: All 3 user stories should be independently functional.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Улучшения, затрагивающие несколько user stories.

- [ ] T026 [P] Update `archive/docs/features/stats.md` — replace «обещано в спеке 174» на «применено в спеке 362» (FR-009 Constitution VI)
- [ ] T027 [P] Update `knowledge/system/frontend/store-stats.md` — add note about `state.lastLoadedAt` for TTL
- [ ] T028 [P] Update `knowledge/adr/local-0004-lazy-eager-load-webvue3-pagination.md` — add link to spec 362 as example of lazy load pattern applied to charts (not just tables)
- [ ] T029 Run `cd webvue3 && npm run lint:check` — verify no new lint violations
- [ ] T030 Run `cd webvue3 && npm run build` — verify build succeeds
- [ ] T031 Run `cd webvue3 && npx prettier --check "src/**/*.{vue,js,ts,json}"` — verify formatting
- [ ] T032 Run quickstart.md validation scenarios (SC-001..SC-006) in DevTools — confirm 0 console errors, ≤ 2 HTTP on mount
- [ ] T033 Verify that `reloadAll()` is not referenced anywhere in `webvue3/src/` (final grep)
- [ ] T034 Update `docs/architecture-notes.md` — record Pass 362 + Issue #79 + lazy load pattern
- [ ] T035 Write `specs/362-fix-stats-view-element-not-found/report.md` — validation report from quickstart.md scenarios

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: T001, T002 — no dependencies.
- **Phase 2 (Foundational)**: T003..T006 — depends on Phase 1 completion. BLOCKS all User Stories.
- **Phase 3 (US1)**: T007..T017 — depends on Phase 2 completion.
- **Phase 4 (US2)**: T018..T022 — depends on Phase 3 completion (US1 introduces `loadDataForActiveTab`, US2 reuses it).
- **Phase 5 (US3)**: T023..T025 — depends on Phase 3 completion (US3 requires `loadDataForActiveTab` from US1).
- **Phase 6 (Polish)**: T026..T035 — depends on all User Stories completion.

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Phase 2 — no dependencies on other stories. **MVP**.
- **User Story 2 (P2)**: Can start after Phase 3 (US1) — reuses `loadDataForActiveTab` method.
- **User Story 3 (P2)**: Can start after Phase 3 (US1) — extends `loadDataForActiveTab` with TTL guard.

### Within Each User Story

- Module-level constants (T007, T008, T018, T019) before methods that use them.
- `loadDataForActiveTab` (T011) before `onTargetChange`/`onDaysChange` modifications (T020, T022).
- TTL guard (T023) AFTER `loadDataForActiveTab` is implemented (T011).
- All commits after logical groups (T003-T006 → commit, T007-T017 → commit, etc.).

### Parallel Opportunities

- **Phase 1**: T001, T002 — different files, sequential by nature (read-then-verify).
- **Phase 2**: T003, T004, T005 — different parts of same file but at different lines.
  - **T003, T004, T005 [P]** — T003 (state field) is prerequisite for T004, T005 (getter/mutation
    use it). Reclass: T003 first, then T004 + T005 in parallel.
- **Phase 3**: T007, T008 [P] — both at top of `<script>`, different lines.
- **Phase 4**: T018, T019 [P] — both at top of `<script>`, different lines.
- **Phase 6**: T026, T027, T028, T029, T030, T031 — different files, all [P].

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1 (Setup): T001-T002 — read & verify current code.
2. Complete Phase 2 (Foundational): T003-T006 — add `lastLoadedAt` to store.
3. Complete Phase 3 (User Story 1): T007-T017 — lazy load + watcher + delete reloadAll.
4. **STOP and VALIDATE**: Test User Story 1 independently in DevTools
   (SC-001: 0 console errors, SC-002: ≤ 2 HTTP on mount). Issue #79 closed.
5. If MVP works — proceed to US2 and US3 incrementally.

### Incremental Delivery

1. Setup + Foundational → Foundation ready.
2. **MVP (US1)** → Issue #79 closed, smoke test → Deploy/Demo.
3. US2 → Backward-compat for target/days changes → Test independently → Deploy.
4. US3 → TTL-кеш → Test independently → Deploy.
5. Each story adds value without breaking previous.

### Parallel Team Strategy

With 1 developer (current scenario):
- Sequential: Phase 1 → 2 → 3 → 4 → 5 → 6. Total: ~5-8 hours.

With 2 developers:
- Dev A: Phase 2 (T003-T006) → Phase 3 (US1, T007-T017) → Phase 5 (US3, T023-T025).
- Dev B: After Phase 3 → Phase 4 (US2, T018-T022).
- Both: Phase 6 (Polish) — split [P] tasks.

---

## Notes

- [P] tasks = different files or non-overlapping lines, no dependencies.
- [Story] label maps task to specific user story for traceability.
- Each user story is independently completable and testable (see quickstart.md SC-XXX).
- **Verify in DevTools** before commit (no automated tests for this fix).
- **Commit after each logical group**:
  - Commit 1: Phase 2 (T003-T006) — «store: add lastLoadedAt for TTL».
  - Commit 2: Phase 3 (T007-T017) — «stats view: lazy load tabs, delete reloadAll (fix #79)».
  - Commit 3: Phase 4 (T018-T022) — «stats view: backward-compat for target/days changes».
  - Commit 4: Phase 5 (T023-T025) — «stats view: 60s TTL on tab switch».
  - Commit 5: Phase 6 (T026-T035) — «docs: update stats feature doc + knowledge cross-links».
- **Stop at any checkpoint** to validate story independently (MVP at Phase 3).
- Avoid: vague tasks, same file conflicts, cross-story dependencies that break independence.
