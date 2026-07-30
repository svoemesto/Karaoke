# Tasks: Колонка audio_parent_id в таблице песен админки

**Input**: Design documents from `/specs/023-songs-audio-root-column/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: Интеграционные тесты в проекте не используются для проверки (большинство `@Disabled`). Валидация — ручная по `quickstart.md` + lint/compile.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)
- Include exact file paths in descriptions

---

## Phase 1: Foundational (Backend DTO + endpoint)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented in UI.

**⚠️ CRITICAL**: No frontend work can begin until backend returns `audioParentId` and exposes song short info.

- [ ] T001 [P] [FOUND] Add `audioParentId: Long` to `SongDTOdigest` in `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SongDTOdigest.kt`.
- [ ] T002 [P] [FOUND] Pass `audioParentId = audioParentId` in `SongDTO.toDtoDigest()` in `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SongDTO.kt`.
- [ ] T003 [FOUND] Add `filterAudioParentId` parameter to `ApiController.apisSongsDigests` and map it to `args["filter_audio_parent_id"]` in `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt`.
- [ ] T004 [FOUND] Add mapping `Pair("filter_audio_parent_id", "audio_parent_id")` in `Song.getWhereList()` in `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt`.
- [ ] T005 [P] [FOUND] Create `SongShortInfoDto` in `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SongShortInfoDto.kt`.
- [ ] T006 [FOUND] Add `GET /api/song/{id}/shortinfo` endpoint in `ApiController.kt` returning `SongShortInfoDto` (or 404 for missing/invalid id).

**Checkpoint**: Backend returns `audioParentId` in digests and supports shortinfo endpoint. `./gradlew :karaoke-app:compileKotlin` passes.

---

## Phase 2: User Story 1 — Отображение колонки A-root (Priority: P1) 🎯 MVP

**Goal**: Admin sees an "A-root" column immediately after "root" in the Songs table, showing `audio_parent_id`.

**Independent Test**: Open Songs table, verify column order and values per `quickstart.md`.

### Implementation for User Story 1

- [ ] T007 [US1] Insert `{ key: 'audioParentId', sortable: true, label: 'A-root', style: {...} }` after `rootId` in `songDigestFields()` in `webvue3/src/components/Songs/SongsTable.vue`.
- [ ] T008 [US1] Add `<template #cell(audioParentId)="data">` rendering the value (or "—" if 0/null) with same cell styling as other id cells in `webvue3/src/components/Songs/SongsTable.vue`.
- [ ] T009 [US1] Register `vBTooltip` directive globally in `webvue3/src/main.js` (import from `bootstrap-vue-next`).

**Checkpoint**: Songs table renders A-root column correctly without tooltip yet.

---

## Phase 3: User Story 2 — Tooltip on hover (Priority: P1)

**Goal**: Hovering over `root` or `A-root` cells shows a tooltip with author, year, album, and song name of the referenced song.

**Independent Test**: Hover cells and verify tooltip content per `quickstart.md`.

### Implementation for User Story 2

- [ ] T010 [P] [US2] Create a small composable or method `fetchSongShortInfo(id)` calling `GET /api/song/{id}/shortinfo` in `webvue3/src/components/Songs/SongsTable.vue` (or separate `useSongTooltip.js` helper).
- [ ] T011 [P] [US2] Cache fetched shortinfo by id to avoid duplicate requests within the component session.
- [ ] T012 [US2] Wrap `rootId` cell content in `webvue3/src/components/Songs/SongsTable.vue` with `v-b-tooltip` and a dynamic title function that resolves parent song info.
- [ ] T013 [US2] Wrap `audioParentId` cell content in `webvue3/src/components/Songs/SongsTable.vue` with the same tooltip logic.
- [ ] T014 [US2] Handle missing/invalid ids: tooltip shows "Не найдено" or no tooltip for id <= 0.

**Checkpoint**: Tooltips appear on both root and A-root cells with correct data.

---

## Phase 4: User Story 3 — Filter by audio_parent_id (Priority: P2)

**Goal**: Admin can filter the Songs table by `audio_parent_id`.

**Independent Test**: Use filter modal, enter an A-root id, apply, verify filtered results per `quickstart.md`.

### Implementation for User Story 3

- [ ] T015 [US3] Add `songsFilterAudioParentId` to state in `webvue3/src/components/Songs/filter/store.js`.
- [ ] T016 [P] [US3] Add getter, mutation, action for `songsFilterAudioParentId` in `webvue3/src/components/Songs/filter/store.js`.
- [ ] T017 [US3] Add computed property `songsFilterAudioParentId` (get/set) in `webvue3/src/components/Songs/filter/SongsFilterModal.vue`.
- [ ] T018 [US3] Add input row labeled "A-root ID:" in the "Поля для поиска" tab of `SongsFilterModal.vue` between "root ID:" and "Композиция:".
- [ ] T019 [US3] Restore filter value from `getWebvueProp('songsFilterAudioParentId', '')` in `beforeMount` of `SongsFilterModal.vue`.
- [ ] T020 [US3] Save filter value via `setSongsFilterAudioParentId` in `ok()` of `SongsFilterModal.vue`.
- [ ] T021 [US3] Include `filterAudioParentId` in the `params` object dispatched to `loadSongsDigests` in `SongsFilterModal.vue`.

**Checkpoint**: Filter by A-root works; clearing it restores full list.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Quality gates, documentation, and per-feature doc per FR-009.

- [ ] T022 [P] [POLISH] Add KDoc to new Kotlin DTO and endpoint.
- [ ] T023 [P] [POLISH] Add JSDoc comments to new Vue component logic/composables.
- [ ] T024 [POLISH] Update or create per-feature document `docs/features/songs-table.md` describing the Songs table, columns, and filters (FR-009).
- [ ] T025 [P] [POLISH] Run `./gradlew ktlintCheck :karaoke-app:compileKotlin :karaoke-web:compileKotlin` and fix issues.
- [ ] T026 [P] [POLISH] Run `cd webvue3 && npm run lint:check` and fix issues.
- [ ] T027 [POLISH] Run manual validation steps from `quickstart.md` (column order, tooltip, filter).
- [ ] T028 [POLISH] Verify no secrets or local config files were committed; run `git status` and review diff.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Foundational (Phase 1)**: No dependencies — can start immediately.
- **User Story 1 (Phase 2)**: Depends on Phase 1 (backend returns `audioParentId`).
- **User Story 2 (Phase 3)**: Depends on Phase 1 (shortinfo endpoint) and Phase 2 (cells exist).
- **User Story 3 (Phase 4)**: Depends on Phase 1 (backend filter parameter).
- **Polish (Phase 5)**: Depends on all user stories.

### Parallel Opportunities

- T001 + T002 + T005 can run in parallel.
- T003 + T004 can run in parallel after T001/T002.
- T007 + T008 + T009 can run in parallel after Phase 1.
- T010 + T011 can run in parallel.
- T015 + T016 + T017 + T018 can run in parallel after Phase 1.
- T022 + T023 + T024 + T025 + T026 can run in parallel after implementation.

### Execution Strategy

Recommended order for a single developer:

1. Phase 1: backend DTO + endpoint + filter mapping (compile must pass).
2. Phase 2: add A-root column.
3. Phase 3: add tooltips.
4. Phase 4: add filter.
5. Phase 5: lint, docs, manual validation.
