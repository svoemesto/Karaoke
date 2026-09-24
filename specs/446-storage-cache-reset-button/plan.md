# Implementation Plan: Сброс кеша хранилища для песни и страницы

**Branch**: `446-storage-cache-reset-button` | **Date**: 2026-09-24 | **Spec**: [spec.md](./spec.md)

## Summary

UI-кнопки сброса persistent-кеша `tbl_storage_metadata_cache`: «Сбросить кеш» в
`HealthReportTableHeader` (одна песня) и «Сбросить кеш хранилища» в футере
`SongsTable.vue` (страница). Backend `POST /api/song/resetStorageCache`,
переиспользует единый источник формул имён файлов (`StorageCacheReset`).

## Technical Context

**Language/Version**: Kotlin (JVM 21), Vue 3 (webvue3)
**Primary Dependencies**: JDBC (LOCAL Postgres), существующий `StorageMetadataCache`
**Testing**: JUnit 5 (`StorageCacheResetTest` — чистые формулы)
**Constraints**: не менять публичные сигнатуры `KaraokeStorageService`/`StorageApiClient`;
не трогать файлы/БД песен — только кеш
**Scale/Scope**: страница 500 песен × ~9 имён × 2 источника

## Constitution Check

- **Principle IX (Knowledge-first)** — pre-flight выполнен (spec.md).
- **Tier-1 Hard Gate — Git CI-gate** — feature-ветка + PR + CI.
- **Tier-1 Hard Gate — Knowledge SSoT** — обновление `knowledge/` + `docs/features/`.
- **Tier-1 Machine-Specific** — `nsa-i9`: rebuild ✅, restart `karaoke-app` ❌ без согласия.

Нарушений нет.

## Project Structure

```text
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
  StorageCacheReset.kt                        # NEW: чистые формулы + список имён
  services/StorageMetadataCache.kt            # MODIFY: refreshKeys(batch)
  controllers/ApiController.kt                # MODIFY: inject cache + POST /song/resetStorageCache
karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/
  StorageCacheResetTest.kt                    # NEW: формулы/состав
webvue3/src/components/Songs/
  SongsTable.vue                              # MODIFY: кнопка футера + handlers
  store.js                                    # MODIFY: resetStorageCachePromise
webvue3/src/components/Common/HealthReport/
  store.js                                    # (переиспользует action из Songs store)
  components/HealthReportTableHeader.vue       # MODIFY: кнопка «Сбросить кеш»
knowledge/domains/storage/domain.md           # MODIFY: описать сброс кеша
docs/features/storage-metadata-cache.md       # MODIFY: раздел про UI-сброс
```

## Phase 1 — Backend

1. `StorageCacheReset.kt`: `storageFileNameFor(...)` + `storageFileNamesForSong(...)` (примитивы + Song-обёртки).
2. `StorageMetadataCache.refreshKeys(keys)`: batch `DELETE ... WHERE (source,bucket,file_name) IN (...)`, группировка по source.
3. `ApiController`: конструктор + `POST /api/song/resetStorageCache?ids=1;2;3` → `{songs, keys, rowsDeleted}`.

**Checkpoint**: `:karaoke-app:compileKotlin` OK.

## Phase 2 — Frontend

4. `Songs/store.js`: `resetStorageCachePromise(ids)` (JSON.parse ответа).
5. `SongsTable.vue`: кнопка `icon_erase.svg` + `resetStorageCacheForAll`/`doResetStorageCacheForAll`; после — `sendBatchHealthReports(ids)`.
6. `HealthReportTableHeader.vue`: кнопка «Сбросить кеш» → `resetStorageCachePromise([songId])` → `loadHealthReportList(songId)`.

**Checkpoint**: `npm run lint`, `prettier --check`, `npm run build` OK.

## Phase 3 — Tests & Docs

7. `StorageCacheResetTest`: 4 теста (аудио, картинка альбома, картинка автора, состав/уникальность).
8. `knowledge/domains/storage/domain.md`, `docs/features/storage-metadata-cache.md`.
9. `report.md`.

## Phase 4 — CI & PR

10. compileKotlin, ktlint, webvue3 lint/prettier/build, pre-commit guards.
11. PR; CI; merge; tracker workflow.

## Dependencies

Phase 1 → 2 → 3 → 4.

## Risks

- **Формулы имён разойдутся с HealthReport** → покрыто тестом `StorageCacheResetTest`;
  при добавлении нового `KaraokeFileType` со storage нужно править обе стороны.
- **Batch DELETE с очень большим `IN (...)`** → для 500 песен ~9000 кортежей; допустимо
  для PG (лимит параметров 65535). При росте — чанки.
- **Сброс только LOCAL или только REMOTE** → сознательно оба (FR-003).

## Ready for implementation
