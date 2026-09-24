# Tasks: Сброс кеша хранилища для песни и страницы (Pass 446, #180)

**Input**: `specs/446-storage-cache-reset-button/{spec,plan}.md`

## Phase 1: Backend

- [x] T001 `StorageCacheReset.kt` — чистые `storageFileNameFor`/`storageFileNamesForSong` (примитивы) + `Song`-обёртки.
- [x] T002 `StorageMetadataCache.refreshKeys(keys)` — batch DELETE с группировкой по source.
- [x] T003 `ApiController` — inject `storageMetadataCache` + `POST /api/song/resetStorageCache`.

**Checkpoint**: `:karaoke-app:compileKotlin` — OK.

## Phase 2: Frontend

- [x] T004 `Songs/store.js` — `resetStorageCachePromise(ids)`.
- [x] T005 `SongsTable.vue` — кнопка футера + `resetStorageCacheForAll`/`doResetStorageCacheForAll` + `sendBatchHealthReports`.
- [x] T006 `HealthReportTableHeader.vue` — кнопка «Сбросить кеш» + перезапрос.

**Checkpoint**: lint / prettier / build — OK.

## Phase 3: Tests & Docs

- [x] T007 `StorageCacheResetTest` — 4 теста.
- [x] T008 `knowledge/domains/storage/domain.md` — описание сброса кеша.
- [x] T009 `docs/features/storage-metadata-cache.md` — раздел UI-сброса.
- [x] T010 `report.md`.

## Phase 4: CI & PR

- [ ] T011 compileKotlin + ktlint + webvue3 lint/prettier/build + pre-commit.
- [ ] T012 commit/push/PR; CI; merge.
- [ ] T013 tracker: add-comment + mark-review.

## Dependencies

Phase 1 → 2 → 3 → 4.
