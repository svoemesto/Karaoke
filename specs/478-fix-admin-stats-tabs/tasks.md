# Tasks: Починить статистику в админке — все вкладки, кроме KPI, пустые (Issue #184)

**Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Branch**: `478-fix-admin-stats-tabs`
**Status**: все задачи выполнены (2026-09-27).

## Path Conventions

- Frontend: `webvue3/src/…`, `webvue3/scripts/…`
- Backend: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/…`
- Документы: `specs/478-fix-admin-stats-tabs/`, `archive/docs/features/stats.md`, `knowledge/…`

## Phase 1: Setup

- [x] T001 Knowledge-first pre-flight (`tools/spec-knowledge-preflight.sh`, 9 ссылок)
- [x] T002 Claim OpenProject #184 → `In progress`
- [x] T003 Feature-ветка `478-fix-admin-stats-tabs` (`tools/specify-bootstrap.sh`)

## Phase 2: US1 — все вкладки показывают данные (P1)

- [x] T004 `StatsView.vue`: `BTabs v-model:index="activeTab"` + явные `id="stats-tab-N"`
- [x] T005 `StatsView.vue`: `TAB_IDS` + `resolveTabIndex()` (число / id панели / числовая строка)
- [x] T006 `StatsView.vue`: строгая проверка индекса в `loadDataForActiveTab` (никаких ключей `NaN` в кеше)
- [x] T007 `StatsView.vue`: `clearActiveTabData`, `onDaysChange`, `mounted` — через `resolveTabIndex`
- [x] T008 `StatsView.vue`: дедупликация dispatch'ей (`simpleEndpointActions`), «Разбивки» 3 запроса вместо 9

## Phase 3: US2 — смена БД/периода (P2)

- [x] T009 `StatsView.vue`: `onDaysChange` сравнивает приведённый индекс, взводит TTL только при успехе
- [x] T010 `onTargetChange` использует приведённый индекс (через `clearActiveTabData` + `loadDataForActiveTab`)

## Phase 4: US3 — TTL и «Обновить» (P3)

- [x] T011 `store.js`: действия вкладок возвращают `Promise<Boolean>`
- [x] T012 `StatsView.vue`: `loadDataForActiveTab(rawTab, { force })`, TTL взводится только при успехе
- [x] T013 Кнопка «Обновить» → `{ force: true }`

## Phase 5: US4/geo + устойчивость (P2)

- [x] T014 `store.js`: `getJson(url, timeoutMs = 15_000)` + `withTimeout`
- [x] T015 `store.js`: `loadStatsBreakdown` и `loadStatsGeo` — `Promise.allSettled`, независимая обработка
- [x] T016 `GeoIpService.resolveMany`: `timeBudgetMs`
- [x] T017 `StatBySong.kt`: бюджет 3 с для `/api/stats/countries`, 2 с для страницы `/api/webevents`

## Phase 6: Приёмка и документы

- [x] T018 `webvue3/scripts/check-stats-tabs.mjs` — обход 8 вкладок (FR-008)
- [x] T019 Прогон на исправленном коде (dev-сервер) — 0 провалов
- [x] T020 Прогон на старом образе `karaoke-webvue3` — провал по 7 вкладкам (дискриминирующая проверка, SC-005)
- [x] T021 `npm run lint:check` + `format:check` + `build` (webvue3)
- [x] T022 `./gradlew :karaoke-app:bootJar :karaoke-web:bootJar` + `ktlintCheck` (оба модуля)
- [x] T023 `archive/docs/features/stats.md` — per-feature документ (FR-009)
- [x] T024 `knowledge/system/frontend/store-stats.md`, `knowledge/domains/stats/domain.md`
- [x] T025 `report.md` с фактическим выводом проверок (FR-010)

## Handoff (требует владельца)

- [ ] T026 Пересборка образа `karaoke-webvue3` + перезапуск контейнера (`deploy/do.sh build_webvue3` / `start_webvue3`) — по согласию
- [ ] T027 Пересборка/перезапуск `karaoke-app` (бюджет GeoIP вступает в силу) — на nsa-i9 только владелец
- [ ] T028 Прод-деплой — вне scope (по отдельному согласию)
