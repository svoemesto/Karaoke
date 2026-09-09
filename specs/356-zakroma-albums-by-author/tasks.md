---
description: "Task list for Закрома — Альбомы авторов (spec 356)"
---

# Tasks: Закрома — Альбомы авторов

**Input**: Design documents from `/specs/356-zakroma-albums-by-author/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/albums-tiles-api.md, quickstart.md (all present)
**Tests**: Tests not explicitly requested (AGENTS.md: «тестов нет, проверка пользователем вручную»). Валидация через `quickstart.md`.

**Organization**: Tasks grouped by user story to enable independent implementation and testing.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Backend (Kotlin/Spring Boot)**: `karaoke-app/src/main/kotlin/`, `karaoke-web/src/main/kotlin/`
- **Frontend (Vue 3/Vite)**: `karaoke-public/src/`
- **SQL migrations**: `deploy/karaoke-db/<NNN>_<name>.sql`
- **Knowledge**: `knowledge/`, `docs/features/`

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization, branch reservation, migration file skeleton.

- [ ] T001 Verify branch `356-zakroma-albums-by-author` is active and issue #70 is claimed
- [ ] T002 [P] Create skeleton migration file `deploy/karaoke-db/49_albums_song_counts.sql` with header comment + ALTER TABLE statements (idempotent, `ADD COLUMN IF NOT EXISTS`)
- [ ] T003 [P] Create stub for per-feature document `docs/features/zakroma-albums-by-author.md` (placeholder sections to be filled at end of implementation)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented (DB schema, model fields, DTO, endpoint, frontend skeleton).

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [ ] T004 Complete migration `deploy/karaoke-db/49_albums_song_counts.sql`: add `total_song_count` and `ready_song_count` columns to `tbl_albums`, recreate `update_tbl_albums_recordhash()` to include new columns in md5, create trigger function `trg_tbl_songs_update_album_counts()` and trigger itself (AFTER INSERT/UPDATE/DELETE on `tbl_songs`)
- [ ] T005 Add migration backfill section: `UPDATE tbl_albums a SET total_song_count = ..., ready_song_count = ... FROM (SELECT album_id, COUNT(*), ... FROM tbl_songs GROUP BY album_id) s WHERE a.id = s.album_id` + backfill `recordhash` for existing rows
- [ ] T006 [P] Add fields to `Album.kt` in `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Album.kt`: `@KaraokeDbTableField(name = "total_song_count") var totalSongCount: Long = 0` and `readySongCount: Long = 0` with KDoc explaining trigger maintenance
- [ ] T007 [P] Create DTO `AlbumTilePublicDto.kt` in `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/AlbumTilePublicDto.kt` with fields: id, name, year, pictureUrl, totalSongCount, readySongCount, albumType + KDoc + companion `albumPictureUrl()` function
- [ ] T008 Implement `Album.loadAlbumTilesWithCounts(authorId, scope, onlyPublished, includeSkipped)` in `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Album.kt`: SELECT from `tbl_albums` with JOIN `tbl_authors` (skip filter), `ORDER BY year ASC NULLS LAST, name ASC`, returns `List<Album>` (raw model — **НЕ** `AlbumTilePublicDto`; маппинг в DTO делает `PublicApiController`, паттерн `Author.loadAuthorTilesWithCounts` + `AuthorTilePublicDto.fromAuthorName`)
- [ ] T009 Add endpoint `@GetMapping("/authors/{authorId}/albums")` to `PublicApiController.kt` in `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt`: с `albumsTilesCache` (TTL ≤60s) и `consumeDirty()` invalidation; внутри — маппинг `List<Album>` (из T008) → `List<AlbumTilePublicDto>` через `AlbumTilePublicDto.fromAlbum(album, authorName, storageUrl)` (новый helper — companion object)
- [ ] T010 [P] Add route `/zakroma/:authorId(\d+)/albums` to router in `karaoke-public/src/router/index.js` with `name: zakroma-author-albums` (component to be created in Phase 3)

**Checkpoint**: Foundation ready — DB schema extended, model fields added, DTO exists, endpoint returns mock data, route registered.

---

## Phase 3: User Story 1 — Посетитель переходит от автора к его альбомам (Priority: P1) 🎯 MVP

**Goal**: Гость видит плашки альбомов на `/zakroma/{author_id}/albums` (только с готовыми песнями), клик открывает `/zakroma/{author_id}?album={album_id}`.

**Independent Test**: Зайти гостем на `/zakroma/{author_id}/albums` — видны плашки альбомов с готовыми песнями (без пустых). Клик по плашке → URL содержит `?album=...`. Хлебная крошка в шапке страницы песен ведёт на `/zakroma/{author_id}/albums` (а не на `/zakroma`).

### Implementation for User Story 1

- [ ] T011 [P] [US1] Create `ZakromaAlbumsView.vue` in `karaoke-public/src/views/ZakromaAlbumsView.vue`: загружает `/api/public/authors/{authorId}/albums`, отображает `AppHeader` с `back: '/zakroma'`, рендерит `AuthorTiles` с псевдо-плашкой в `<template #leading>` (ссылка «Все песни автора с группировкой по альбомам» → `/zakroma/{authorId}`)
- [ ] T012 [P] [US1] Add «Альбомы автора» section to `ZakromaView.vue` in `karaoke-public/src/views/ZakromaView.vue` (above songs list): загружает `/api/public/authors/{authorId}/albums`, отображает плашки альбомов только для гостя (skip для редактора — US2)
- [ ] T013 [US1] Modify `ZakromaView.vue` to handle query-параметр `?album=` from route: pre-filter songs by `album_id` (Vue computed property), update `AppHeader` `back` to `/zakroma/{authorId}/albums` instead of `/zakroma` when filter is active
- [ ] T014 [P] [US1] Verify SQL query in `Album.loadAlbumTilesWithCounts` for `onlyPublished=true` produces `WHERE ready_song_count > 0 AND skip = false` (read-only verification — the implementation is already in T008)

**Checkpoint**: US1 fully functional: guest sees filtered album tiles, click navigates to filtered songs page, breadcrumb works.

---

## Phase 4: User Story 2 — Редактор видит все альбомы автора (Priority: P1)

**Goal**: Зарегистрированный редактор видит ВСЕ альбомы автора (включая без готовых песен); подпись плашки показывает «N песен» (не «N готовых»).

**Independent Test**: Залогиниться редактором, открыть `/zakroma/{author_id}/albums` — больше плашек чем у гостя (видны альбомы без готовых); подпись показывает `total_song_count`.

### Implementation for User Story 2

- [ ] T015 [P] [US2] Определить session role detection: проверить `karaoke-public/src/services/auth.js` или `karaoke-web/src/main/kotlin/.../controllers/SecurityConfig.kt`. Если существует — переиспользовать. Если нет — добавить helper `isEditor(request: HttpServletRequest): Boolean` в `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/util/SessionUtil.kt`, проверяющий session cookie role (как у `/api/public/authors-tiles` — см. `PublicApiController.kt` line 264+)
- [ ] T016 [US2] Update endpoint `/authors/{authorId}/albums` in `PublicApiController.kt`: pass `onlyPublished = !isEditor(request)` to `Album.loadAlbumTilesWithCounts`; this changes the `WHERE` clause from `ready_song_count > 0` to `total_song_count > 0`
- [ ] T017 [P] [US2] Update `ZakromaAlbumsView.vue` in `karaoke-public/src/views/ZakromaAlbumsView.vue`: detect editor role (через тот же helper, что и T015 — на стороне клиента проверка `document.cookie` или отдельный GET `/api/me`), show `total_song_count` in tile caption instead of `ready_song_count` («N песен» vs «N готовых»)
- [ ] T018 [P] [US2] Update `ZakromaView.vue` albums section (T012) similarly: for editor, show «N песен» caption; for guest, show «N готовых»

**Checkpoint**: US2 fully functional: editor sees all albums with total counts; guest still sees filtered.

---

## Phase 5: User Story 3 — Счётчики обновляются автоматически (Priority: P1)

**Goal**: Триггер `trg_tbl_songs_update_album_counts` корректно обновляет `total_song_count`/`ready_song_count` в `tbl_albums` при INSERT/UPDATE/DELETE в `tbl_songs`. Sync LOCAL↔SERVER работает.

**Independent Test**: Вставить/обновить/удалить песню в `tbl_songs` — счётчики соответствующего альбома атомарно меняются. После sync LOCAL→SERVER значения совпадают на обеих БД, `recordhash` совпадает.

### Verification for User Story 3

- [ ] T019 [US3] Apply migration `49_albums_song_counts.sql` to LOCAL DB; verify columns and trigger exist via `psql` (run quickstart.md Step 1-2)
- [ ] T020 [US3] Run trigger test (quickstart.md Step 3): INSERT song with id_status=6, UPDATE id_status 6→5, DELETE song, UPDATE album_id (перенос) — verify counter changes
- [ ] T021 [US3] Run sync LOCAL→SERVER (quickstart.md Step 4); verify `recordhash` matches between LOCAL and SERVER; verify counter values are consistent. Также проверить `grep sync_albums_ KaraokeProperties.kt` — флаги `push_update_allowed = true` и `pull_update_allowed = true` остались включёнными (FR-009).
- [ ] T022 [US3] Update migration file with verification notes (comment block at end of `49_albums_song_counts.sql`) listing which test queries passed

**Checkpoint**: US3 verified — DB consistency guaranteed by trigger, sync works through recordhash.

---

## Phase 6: User Story 4 — Слайдер масштаба плашек (Priority: P2)

**Goal**: На `/zakroma` и `/zakroma/{author_id}/albums` есть слайдер размера плашек (200..400px, шаг 50, дефолт 200). Значение в `localStorage["zakroma_tile_size"]`.

**Independent Test**: Открыть `/zakroma`, сдвинуть слайдер — плашки меняют размер. F5 — размер сохранился. Открыть в инкогнито — дефолт 200px.

### Implementation for User Story 4

- [ ] T023 [P] [US4] Create composable `useZakromaSettings.js` in `karaoke-public/src/composables/useZakromaSettings.js`: exports `tileSize` (ref), `setTileSize(value)`, `minTileSize=200`, `maxTileSize=400`, `tileSizeStep=50`, `defaultTileSize=200`; reads/writes `localStorage["zakroma_tile_size"]`
- [ ] T024 [P] [US4] Create component `ZakromaSettings.vue` in `karaoke-public/src/components/ZakromaSettings.vue`: range input (slider) bound to `useZakromaSettings().tileSize`, with min/max/step props, label «Размер плашек», visible at top of pages
- [ ] T025 [US4] Apply tile size to **both** `ZakromaView.vue` (страницы `/zakroma` + `/zakroma/{id}`) и `ZakromaAlbumsView.vue` (`/zakroma/{id}/albums`): import `useZakromaSettings`, установить CSS-переменную `--tile-size` на root element (например, `:style="{ '--tile-size': tileSize + 'px' }"`); стили плашек использовать `var(--tile-size)` для `width`/`height`. Общий slider действует на оба раздела.

**Checkpoint**: US4 functional: slider visible on both pages, size changes tiles, persists across reloads.

---

## Phase 7: User Story 5 — Переключатель «Плашки / Таблица» (Priority: P2)

**Goal**: На `/zakroma` и `/zakroma/{author_id}/albums` есть переключатель «Плашки / Таблица». Значение в `localStorage["zakroma_view_mode"]`.

**Independent Test**: Переключиться на «Таблица» — плашки скрыты, таблица видна. F5 — режим сохранился. Перейти на `/zakroma/{id}/albums` — там тоже таблица (общий режим).

### Implementation for User Story 5

- [ ] T027 [P] [US5] Extend `useZakromaSettings.js`: add `viewMode` (ref), `setViewMode(value)`, constants `VIEW_MODE_TILES="tiles"`, `VIEW_MODE_TABLE="table"`, `defaultViewMode=VIEW_MODE_TILES`; reads/writes `localStorage["zakroma_view_mode"]`
- [ ] T028 [P] [US5] Extend `ZakromaSettings.vue`: add `<button>` или `<select>` toggle bound to `viewMode`, label «Плашки / Таблица» с иконками
- [ ] T029 [US5] Apply view mode to **both** `ZakromaView.vue` (страницы `/zakroma` + `/zakroma/{id}`) и `ZakromaAlbumsView.vue`: `v-if="viewMode === 'tiles'"` для `AuthorTiles`, `v-else` для `<table>` со столбцами: название, год, кол-во песен (для гостя — `ready_song_count`, для редактора — `total_song_count`). Общий переключатель действует на оба раздела.
- [ ] T030 [P] [US5] Style table view: создать CSS в `karaoke-public/src/style.css` (или scoped styles): `.km-zakroma-table { ... }`, `.km-zakroma-table th { ... }` — единый стиль с плашками

**Checkpoint**: US5 functional: toggle works on both pages, persists across reloads, common mode.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Documentation, linting, integration tests, tracker workflow, knowledge updates.

- [ ] T032 [P] Complete `docs/features/zakroma-albums-by-author.md`: заполнить все секции (Что делает, Контракт, Сценарии, Связанные документы) — обязательно для merge (Constitution VI FR-009)
- [ ] T033 [P] Create local ADR `knowledge/adr/local-0007-album-tile-sort-order.md` (Pass 340 SSoT): фиксирует выбор `year ASC NULLS LAST, name ASC` (а не `sort_order`)
- [ ] T034 Run ktlint: `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:ktlintCheck :karaoke-web:ktlintCheck --parallel` — фиксить все warnings (CLAUDE.md § CI 7/7)
- [ ] T035 [P] Run frontend linters: `cd karaoke-public && npm run lint:check && npx prettier --check "src/**/*.{vue,js,ts,json}"` — фиксить все warnings
- [ ] T036 [P] Run KDoc coverage: `bash tools/check-kdoc-coverage.sh --strict` — все новые публичные API с KDoc (FR-006)
- [ ] T037 [P] Run JSDoc coverage: `bash tools/check-jsdoc-coverage.sh karaoke-public --strict` — все новые компоненты/composables с JSDoc
- [ ] T038 [P] Update `knowledge/domains/catalog/components/dictionaries.md` если появились новые магические коды (вряд ли — AlbumType уже зафиксирован, но проверяем)
- [ ] T039 [P] Update `knowledge/domains/catalog/domain.md` если появились новые секции (вряд ли — `tbl_albums.total_song_count`/`ready_song_count` это денормализация, а не новая сущность)
- [ ] T040 Run pre-commit: `pre-commit run --all-files` — все 7 проверок OK
- [ ] T041 Run `quickstart.md` Steps 5-9 end-to-end: build → deploy local → API check → UI smoke test → tracker workflow
- [ ] T042 Build и push: `git add . && git commit -m '...' && git push -u origin 356-zakroma-albums-by-author && gh pr create --base master`
- [ ] T043 [P] Tracker workflow: `bash tools/tracker.sh add-comment 70 --file specs/356-zakroma-albums-by-author/report.md` + `bash tools/tracker.sh mark-review 70`

**Checkpoint**: All phases complete, PR opened, CI 7/7 PASS, tracker workflow done.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion — BLOCKS all user stories
- **User Stories (Phase 3-7)**: All depend on Foundational phase completion
  - US1, US2, US3 (P1) — can proceed sequentially or in parallel
  - US4, US5 (P2) — depend on US1 (UI page exists)
- **Polish (Phase 8)**: Depends on all desired user stories being complete

### User Story Dependencies

- **US1 (P1)**: Depends on Phase 2 only — no dependencies on other stories
- **US2 (P1)**: Depends on Phase 2 + US1 (filters `onlyPublished` for editor in same endpoint; UI captions differ)
- **US3 (P1)**: Depends on Phase 2 only — verifies trigger works (independent of US1/US2)
- **US4 (P2)**: Depends on US1 (applies to `ZakromaView` + `ZakromaAlbumsView`); UI components exist
- **US5 (P2)**: Depends on US1 + US4 (shares `useZakromaSettings` composable)

### Within Each User Story

- **No tests written** (per AGENTS.md, проверка пользователем вручную)
- Models before services (Phase 2 only)
- Services before endpoints (Phase 2 only)
- Backend before frontend integration (per story)
- Story complete before moving to next priority

### Parallel Opportunities

- **Phase 2** [P] tasks: T006, T007 (different files), T010 (router)
- **Phase 3** [P] tasks: T011, T012 (different files — `ZakromaAlbumsView` vs `ZakromaView`)
- **Phase 4** [P] tasks: T015, T017, T018 (different files)
- **Phase 5** — sequential (single DB session)
- **Phase 6** [P] tasks: T023, T024 (composable vs component)
- **Phase 7** [P] tasks: T027, T028, T031 (composable + component + CSS)
- **Phase 8** [P] tasks: T032-T039, T043 (different files)

### Critical Path

```
Phase 1 (T001-T003) → Phase 2 (T004-T010) → Phase 3 US1 (T011-T014) →
Phase 4 US2 (T015-T018) → Phase 5 US3 (T019-T022) →
Phase 6 US4 (T023-T026) → Phase 7 US5 (T027-T031) → Phase 8 Polish (T032-T043)
```

---

## Implementation Strategy

### MVP First (Phase 1 + Phase 2 + Phase 3 = US1)

1. Complete Phase 1: Setup (3 tasks, ~30 min)
2. Complete Phase 2: Foundational (7 tasks, ~2-3 hours — DB migration critical)
3. Complete Phase 3: US1 (4 tasks, ~1-2 hours — UI views)
4. **STOP and VALIDATE**: Run quickstart.md Steps 5-9 for US1 only
5. Deploy/demo if ready — пользователь может проверить MVP

### Incremental Delivery

1. Setup + Foundational → Foundation ready (DB extended, endpoint exists, route registered)
2. Add US1 → Test independently → MVP demo ✅
3. Add US2 → Test independently → Editor view ✅
4. Add US3 → Verify trigger → DB consistency ✅
5. Add US4 → Test independently → Slider ✅
6. Add US5 → Test independently → View mode toggle ✅
7. Polish + tracker workflow → PR ready ✅

### Parallel Team Strategy (если >1 разработчика)

1. Team completes Phase 1 + Phase 2 together
2. Once Phase 2 done:
   - Dev A: Phase 3 (US1 — guest view)
   - Dev B: Phase 4 (US2 — editor view)
   - Dev C: Phase 5 (US3 — trigger verification, sequential single-DB)
3. Stories complete and integrate independently
4. Phase 6 + 7 + 8 — incremental additions

---

## Notes

- **Tasks without [P]** are sequential within their phase (e.g., T009 endpoint depends on T007 DTO)
- **[P] tasks** operate on different files with no shared state
- **Each story is independently testable** — после Phase 3 можно сделать MVP-demo пользователю
- **Commit after each task or logical group** — после T004 (миграция), T010 (endpoint), T014 (US1) и т.п.
- **Stop at any checkpoint to validate** — MVP checkpoint после Phase 3
- **Avoid**: vague tasks, same-file conflicts, cross-story dependencies that break independence

## Сводка

- **Всего задач**: 41 (T001-T041) — после применения фиксов analyze: T026 + T030 слиты с T025 + T029 соответственно
- **По фазам**: Setup=3, Foundational=7, US1=4, US2=4, US3=4, US4=3, US5=4, Polish=12
- **По story (P1)**: US1+US2+US3 = 12 задач
- **По story (P2)**: US4+US5 = 7 задач
- **MVP scope**: Phase 1 + Phase 2 + Phase 3 = 14 задач (≈4-6 часов работы)
- **Full scope**: 41 задача (≈14-18 часов работы)
- **Параллельных возможностей**: ~14 задач с [P] маркером

> **Анализ фиксов применён**: tasks.md обновлён по результатам `speckit-analyze` (2 MEDIUM + 5 LOW).
> Подробнее см. отчёт analyze — раздел «Findings A1-A9».

После завершения всех 43 задач — merge в master, деплой, валидация на проде.