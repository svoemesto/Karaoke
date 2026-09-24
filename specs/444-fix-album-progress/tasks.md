# Tasks: Корректный прогресс загрузки песен при открытии альбома автора

**Spec**: [spec.md](./spec.md) | **Branch**: `444-fix-album-progress` | **Issue**: #179

## Phase 1 — Fix (backend)

- [X] T001 `karaoke-web/.../services/ZakromaStreamProgress.kt` (NEW): чистая `resolveExpectedCount` + `AlbumCounters`. (FR-001..FR-005)
- [X] T002 `PublicApiController.zakromaStream`: при `albumId` грузить `Album.getAlbumById`, строить `AlbumCounters`, вызывать helper; не доверять фронтовому count. (FR-001, FR-003, FR-005)
- [X] T003 KDoc + `@see specs/444-...` на helper и в точке вызова. (Principle VI)

## Phase 2 — Fix (frontend)

- [X] T004 `karaoke-public/src/views/ZakromaView.vue`: при `selectedAlbumId != null` не слать авторский `expectedCount`. (FR-006)

## Phase 3 — Tests

- [X] T005 `karaoke-web/src/test/.../ZakromaStreamProgressTest.kt` (NEW, 6 тестов): гость/редактор/не найден/no-albumId trust/no-albumId fallback/no fallback при albumId. (FR-001..FR-005, SC-004)
- [X] T006 `:karaoke-web:test --tests ZakromaStreamProgressTest` PASS (6/6). (SC-004)

## Phase 4 — Verification

- [X] T007 `:karaoke-web:compileKotlin` / `:karaoke-app:compileKotlin` OK. (SC-004)
- [X] T008 `:karaoke-web:ktlintCheck` OK. (SC-005)
- [X] T009 `cd karaoke-public && npm run lint:check` + `npx prettier --check src/views/ZakromaView.vue` OK. (SC-005)

## Phase 5 — Knowledge / docs (SSoT)

- [X] T010 `knowledge/system/frontend/composable-zakroma-stream.md`: album-scoped `expectedCount` + changelog.
- [X] T011 `knowledge/adr/local-0007-zakroma-album-id-in-stream-dto.md`: Consequences — знаменатель альбома (follow-up) + история.
- [X] T012 `docs/features/zakroma-albums-by-author.md`: раздел про прогресс альбома (issue #179).
- [X] T013 `archive/docs/features/zakroma-stream-progress.md`: инвариант album-scoped expectedCount.

## Phase 6 — Delivery

- [ ] T014 `git commit` + `git push` + `gh pr create --base master`.
- [ ] T015 `gh pr checks` all PASS.
- [ ] T016 `add-comment 179 --file report.md` + `mark-review 179`.
