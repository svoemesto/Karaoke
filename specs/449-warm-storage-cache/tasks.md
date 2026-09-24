# Tasks: Полный прогрев кеша хранилища (Pass 449, #182)

**Input**: `specs/449-warm-storage-cache/{spec,plan}.md`

## Phase 1: Backend

- [x] T001 `KaraokeStorageService.listFilesInfo` — etag/size из `Item` (без statObject).
- [x] T002 `StorageApiClientImpl.listFilesInfo` — то же.
- [x] T003 `StorageMetadataCache.upsertBatch` + `CacheUpsertRow`.
- [x] T004 `StorageCacheWarm.kt` — warmStorageCache + loadSongsForWarm + listToMap.
- [x] T005 `ApiController` POST `/api/utils/warmstoragecache`.
- [x] T006 `MainController` зеркало GET.

**Checkpoint**: `:karaoke-app:compileKotlin` — OK.

## Phase 2: Frontend

- [x] T007 `HomeView.vue` — кнопка «Прогреть кеш хранилища» + confirm + handler.
- [x] T008 `Songs/store.js` — `warmStorageCachePromise`.

**Checkpoint**: lint / prettier / build — OK.

## Phase 3: Tests & Docs

- [x] T009 `StorageCacheWarmTest` — `listToMap`.
- [x] T010 `docs/features/storage-metadata-cache.md`.
- [x] T011 `knowledge/domains/storage/domain.md`.
- [x] T012 `report.md` + `checklists/requirements.md`.

## Phase 4: CI & PR

- [ ] T013 ktlint + pre-commit + PR; CI; merge.
- [ ] T014 tracker: add-comment + mark-review.

## Dependencies

Phase 1 → 2 → 3 → 4.
