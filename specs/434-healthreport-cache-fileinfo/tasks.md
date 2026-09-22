# Tasks: HealthReport кеширует getFileInfo/fileIsActual (Pass 434, #158)

**Input**: `/specs/434-healthreport-cache-fileinfo/{spec,plan}.md`

## Phase 1: Cache correctness

- [ ] T001 Modify `services/StorageMetadataCache.kt`:
  - `selectFileInfo` — null-guard: `if (!exists || sizeIsNull) null` (`rs.wasNull()`).
  - Удалить `fun getFileIsActual(...)` (dead, неверная семантика).
- [ ] T002 Modify `services/StorageApiClient.kt` — `fileIsActual(storageFileInfo)`:
  обернуть `getFileInfo(...).block()` в `storageCircuitBreaker.decorateOrEmpty`.

**Checkpoint**: `:karaoke-app:compileKotlin` — OK.

## Phase 2: HealthReport через кеш

- [ ] T003 Modify `HealthReport.kt` — `@JvmStatic fun cachedFileInfo(source, bucket, fileName, loader): StorageFileInfo?`.
- [ ] T004 Заменить 4 call sites:
  - `actionsLocalStorage` (736): `storageService.fileIsActual(path)` → cached local info
    + сравнение `File(path).length() == info.size`.
  - `actionsLocalStorage` (793): `storageService.getFileInfo` → `cachedFileInfo("LOCAL", ...)`.
  - `actionsRemoteStorage` (1081): `storageApiClient.fileIsActual(path)` → cached remote info
    + сравнение size.
  - `actionsRemoteStorage` (1142): `storageService.getFileInfo` → `cachedFileInfo("LOCAL", ...)`.

**Checkpoint**: compile OK.

## Phase 3: Tests

- [ ] T005 [P] Create `services/StorageMetadataCacheTest.kt`:
  - hit: после upsert повторный `getFileInfo` не вызывает loader.
  - miss: loader вызывается, значение кешируется.
  - `size IS NULL` → `getFileInfo` = miss (loader), не size=0.
  - `exists=false` → null.
- [ ] T006 Run `:karaoke-app:test --tests "*StorageMetadataCacheTest"` — PASS.

## Phase 4: Knowledge & docs

- [ ] T007 [P] `knowledge/domains/health/components/health-report.md` — getFileInfo/fileIsActual из кеша.
- [ ] T008 [P] `knowledge/domains/storage/components/storage-api-client.md` — circuit на fileIsActual.
- [ ] T009 [P] `docs/features/storage-metadata-cache.md` — V2.9.

## Phase 5: Validation & PR

- [ ] T010 Validation (сборка/линтеры/guards).
- [ ] T011 `report.md`; commit/push/PR; CI; merge; tracker.
- [ ] T012 Запросить рестарт `karaoke-app`.

## Dependencies

Phase 1 → 2 → 3 → 4 → 5.

## Ready for implementation
