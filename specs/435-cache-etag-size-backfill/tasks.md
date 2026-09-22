# Tasks: Backfill etag/size в tbl_storage_metadata_cache (Pass 435, #159)

**Input**: `/specs/435-cache-etag-size-backfill/{spec,plan}.md`

## Phase 1: Cache write helpers

- [ ] T001 Modify `services/StorageMetadataCache.kt`:
  - `updateFileInfo(source, bucket, fileName, etag, size)` → `upsert(exists=true, ...)`.
  - `markNotExists(source, bucket, fileName)` → `upsert(exists=false, etag=null, size=null)`.
  - (либо сделать `upsert` доступным иначе).

**Checkpoint**: `:karaoke-app:compileKotlin` — OK.

## Phase 2: Backfill function

- [ ] T002 Create `CacheEtagSizeBackfill.kt`:
  - `internal fun decideBackfillAction(info: StorageFileInfo?): BackfillAction` (чистая).
  - `fun backfillCacheEtagSize(storageService, storageApiClient, remoteBreaker): String`.
  - курсор по строкам `exists=true AND size IS NULL` (LOCAL/REMOTE).
  - circuit-aware для REMOTE; `size=-1` → markNotExists; прогресс `infra.cache.storage`; SSE-итог; single-flight.

**Checkpoint**: compile OK.

## Phase 3: Endpoints

- [ ] T003 Modify `controllers/ApiController.kt` — `POST /api/utils/backfillcacheetagsize`.
- [ ] T004 Modify `controllers/MainController.kt` — зеркало (Thymeleaf).

## Phase 4: Frontend

- [ ] T005 Modify `webvue3/src/components/Songs/store.js` — `backfillCacheEtagSizePromise`.
- [ ] T006 Modify `webvue3/src/views/HomeView.vue` — кнопка + confirm + handler.

## Phase 5: Tests

- [ ] T007 [P] Create `CacheEtagSizeBackfillTest.kt` — `decideBackfillAction` (null→SKIP,
  size<0→MARK_MISSING, size>=0→UPDATE).
- [ ] T008 Run `:karaoke-app:test --tests "*CacheEtagSizeBackfillTest"` — PASS.

## Phase 6: Knowledge & docs

- [ ] T009 [P] `knowledge/domains/storage/components/storage-api-client.md` — Pass 435.
- [ ] T010 [P] `docs/features/storage-metadata-cache.md` — V2.10.

## Phase 7: Validation & PR

- [ ] T011 Validation (сборка/линтеры/guards + webvue3).
- [ ] T012 `report.md`; commit/push/PR; CI; merge; tracker.
- [ ] T013 Запросить рестарт `karaoke-app` + прогон кнопки.

## Dependencies

Phase 1 → 2 → 3/4 → 5 → 6 → 7.

## Ready for implementation
