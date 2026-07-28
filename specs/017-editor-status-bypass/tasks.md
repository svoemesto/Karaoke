---

description: "Task list for editor status-bypass feature implementation"

---

# Tasks: Редактор видит все песни в закромах и поиске на karaoke-public независимо от статуса

**Input**: Design documents from `/specs/017-editor-status-bypass/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/public-api-editor-visibility.md, quickstart.md

**Tests**: Not requested in spec.md; per Constitution («Тесты: в CI нет»), verification is manual via `quickstart.md`. No automated test tasks are generated.

**Organization**: Tasks are grouped by user story (US1/US2/US3 from spec.md) to enable independent implementation and testing of each story. All implementation happens in a single existing file (`PublicApiController.kt`), so within-file tasks are sequential (not `[P]`) to avoid edit conflicts, even though the stories are logically independent.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)
- Paths are relative to repository root (`/home/dev/Karaoke`)

## Path Conventions

Existing Gradle multi-module + Vue web app (see plan.md → Project Structure). All backend changes live in:

- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt`
- `docs/features/special-orders.md` (per-feature doc, FR-009)

No changes to `karaoke-app` model layer, `webvue3`, or `karaoke-public` (frontend already sends the Bearer token on every relevant call — see research.md §4).

---

## Phase 1: Setup

**Purpose**: Confirm working branch before any edits (CLAUDE.md: never commit directly to `master`).

- [X] T001 Create/checkout git branch `017-editor-status-bypass` from `master` at repository root `/home/dev/Karaoke` (`git checkout -b 017-editor-status-bypass` if it doesn't exist yet)

**Checkpoint**: On feature branch, ready to edit.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Single shared helper that resolves whether the current request should bypass the `id_status >= 3` filter. All three user stories call this helper, so it must exist first.

**⚠️ CRITICAL**: No user story task can start until T002 is complete.

- [X] T002 Add a private helper in `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt` (near the existing `fetchFromMinIO` private helper) that resolves the current `SiteUser` via the already-injected `siteUserResolver.resolve(request)` and returns whether the `id_status >= 3` filter should still apply — i.e. `onlyPublished = siteUserResolver.resolve(request)?.isEditor != true` — e.g. `private fun onlyPublishedFor(request: HttpServletRequest): Boolean`. Add a one-line KDoc/comment explaining *why* editors bypass the filter (spec FR-002/FR-003, links to specs/017-editor-status-bypass/spec.md), since the `!= true` null-safety idiom (ano­n users, invalid tokens, and explicit `isEditor=false` must all fall back to `true`) is not obvious from the code alone.

**Checkpoint**: Foundation ready — all three endpoint methods can now be updated independently in sequence.

---

## Phase 3: User Story 1 - Редактор просматривает закрома автора со всеми песнями (Priority: P1) 🎯 MVP

**Goal**: `GET /api/public/zakroma` (both single-author and `specialBucket=true` virtual bucket) returns songs of all statuses when the caller is an authenticated editor, and behaves exactly as before otherwise.

**Independent Test**: Per `quickstart.md` Сценарий 1 — call `/api/public/zakroma?author=<AUTHOR>` with and without an editor Bearer token for an author who has both `id_status >= 3` and `id_status < 3` songs; confirm the editor response includes the extra song(s), and that an author with *no* `id_status >= 3` songs at all still returns a non-empty album list for the editor.

### Implementation for User Story 1

- [X] T003 [US1] In `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt`, update the `zakroma()` method to compute `val onlyPublished = onlyPublishedFor(request)` (using T002's helper) and pass it as the `onlyPublished` argument to both `Zakroma.getZakromaBySpecialOrder(...)` and `Zakroma.getZakroma(...)` calls, replacing the hardcoded `onlyPublished = true`. Update the adjacent Russian comment ("Публичная поверхность прода — показываем только готовые песни (specs/013-song-status-filter)") to note the editor exception and reference specs/017-editor-status-bypass.

**Checkpoint**: User Story 1 fully functional and independently testable — editors see full закрома (including the special-order bucket); everyone else unaffected.

---

## Phase 4: User Story 2 - Редактор ищет песни любого статуса (Priority: P1)

**Goal**: `GET /api/public/songs` (search) returns matches of all statuses for an authenticated editor, and behaves exactly as before otherwise.

**Independent Test**: Per `quickstart.md` Сценарий 2 — search by the exact name of a song with `id_status < 3`; confirm it's absent without an editor token and present with one.

### Implementation for User Story 2

- [X] T004 [US2] In `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt`, update the `songs()` method to only set `attr["id_status"] = ">=3"` when `onlyPublishedFor(request)` (T002) is `true` — i.e. skip adding that filter attribute entirely for an authenticated editor, so `Song.loadListFromDb` returns all statuses. Update the adjacent comment ("Публичная поверхность прода — показываем только готовые песни (specs/013-song-status-filter)") accordingly.

**Checkpoint**: User Stories 1 AND 2 both independently functional — editors get full закрома and full search results.

---

## Phase 5: User Story 3 - Подпись количества песен автора соответствует тому, что видит редактор (Priority: P2)

**Goal**: `GET /api/public/authors-tiles` counts and lists authors using the same status scope the caller actually sees, so the caption number for an editor matches the full song list from User Story 1, while the count/author-inclusion for non-editors is unchanged.

**Independent Test**: Per `quickstart.md` Сценарий 3 — compare `authors-tiles?scope=all` `songCount` for a test author with and without an editor token, and cross-check the editor value against the actual song count returned by `/api/public/zakroma` (US1) for the same author/token.

### Implementation for User Story 3

- [X] T005 [US3] In `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt`, add a `request: HttpServletRequest` parameter to the `authorsTiles()` method signature (matching the pattern already used in `zakroma()`/`songs()`), compute `val onlyPublished = onlyPublishedFor(request)` (T002), and pass it into `Song.loadAuthorSongCounts(onlyPublished = onlyPublished, ...)` instead of the hardcoded `onlyPublished = true`. The existing `loadedAuthors.filter { (counts[it] ?: 0L) > 0L }` line needs no change — it already keys off `counts`, which now reflects the resolved scope. Update the adjacent comment block (lines documenting specs/013-song-status-filter behavior) to note the editor exception.

**Checkpoint**: All three user stories independently functional — закрома, поиск, and the author-tile caption all agree for both editors and regular users.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Documentation compliance, lint/coverage gates, and final manual validation across all three stories.

- [X] T006 [P] Update `docs/features/special-orders.md` to describe the new editor-bypass behavior of `Zakroma.getZakroma` / `Zakroma.getZakromaBySpecialOrder` (`onlyPublished` now driven by `SiteUser.isEditor` on the public site, not just hardcoded `true`), per Constitution FR-009 and research.md §5
- [X] T007 Run `./gradlew ktlintCheck` from repository root and fix any violations introduced in `PublicApiController.kt`
- [X] T008 Run `bash tools/check-kdoc-coverage.sh` from repository root and confirm the touched file still meets the coverage gate
- [X] T009 Execute Сценарии 1-4 from `specs/017-editor-status-bypass/quickstart.md` against the local docker stand via curl (editor token, non-editor token, anonymous) and confirm expected results (all matched, including SC-003 count parity and SC-004 regression check). Сценарий 5 (manual browser walkthrough) NOT performed in this session — no browser-automation tool available; backend response payloads were verified directly instead, and `karaoke-public` frontend code is unchanged (confirmed in research.md §4)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS all user stories (T003, T004, T005 all call the T002 helper)
- **User Stories (Phase 3-5)**: All depend on Foundational (T002) completion
  - US1 (T003), US2 (T004), US3 (T005) are logically independent of each other but touch the same file (`PublicApiController.kt`) — complete and commit one before starting the next to avoid diff conflicts
- **Polish (Phase 6)**: Depends on all three user stories being complete (T006-T009 verify the combined result)

### User Story Dependencies

- **User Story 1 (P1)**: Can start after T002 — no dependency on US2/US3
- **User Story 2 (P1)**: Can start after T002 — no dependency on US1/US3
- **User Story 3 (P2)**: Can start after T002 — reads the same `onlyPublishedFor` helper as US1; its independent test cross-checks against US1's output, so implementing US1 first makes T-level validation (not implementation) easier, but is not a hard code dependency

### Within Each User Story

- Each story is a single, self-contained edit to one existing method — no sub-steps beyond the listed task

### Parallel Opportunities

- T001 has no parallel siblings (single setup step)
- T002 has no parallel siblings (single foundational helper)
- T003/T004/T005 are logically independent but share one file — run them **sequentially**, not in parallel, to avoid merge conflicts on the same class
- T006 (docs) can run in parallel with T007-T009 once all three stories are implemented, since it touches a different file

---

## Parallel Example: Polish Phase

```bash
# Once T003, T004, T005 are all done, docs can proceed independently of lint/coverage/quickstart:
Task: "Update docs/features/special-orders.md with editor-bypass behavior"
# ...while separately running:
Task: "./gradlew ktlintCheck"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001)
2. Complete Phase 2: Foundational (T002) — CRITICAL, blocks all stories
3. Complete Phase 3: User Story 1 (T003)
4. **STOP and VALIDATE**: Run quickstart.md Сценарий 1 (and Сценарий 4 for US1's slice) independently
5. Optionally deploy/demo — закрома-only fix is already a coherent, shippable increment

### Incremental Delivery

1. Setup + Foundational → helper ready (T001-T002)
2. Add US1 (T003) → validate закрома → demo
3. Add US2 (T004) → validate поиск → demo
4. Add US3 (T005) → validate подпись count matches закрома → demo
5. Polish (T006-T009) → docs + lint/coverage gates + full quickstart regression pass

---

## Notes

- All backend changes are confined to three existing methods in one file plus one new private helper — no new entities, migrations, or endpoints (see data-model.md, contracts/public-api-editor-visibility.md)
- `karaoke-public` (frontend) requires no changes — confirmed in research.md §4
- Commit after each task or logical group (T002, then each of T003/T004/T005 separately, per CLAUDE.md git workflow — feature branch only, no `git add .`)
- Stop at any checkpoint to validate a story independently before moving to the next
