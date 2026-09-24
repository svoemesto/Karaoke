# Implementation Plan: Полный прогрев кеша хранилища (обход всех песен)

**Branch**: `449-warm-storage-cache` | **Date**: 2026-09-24 | **Spec**: [spec.md](./spec.md)

## Summary

Отдельная кнопка «Прогреть кеш хранилища»: листинг бакета MinIO (`listFilesInfo`,
etag/size из LIST — без `statObject`), обход **всех** песен
(`StorageCacheReset.storageFileNamesForSong`), bulk-upsert в
`tbl_storage_metadata_cache` для LOCAL+REMOTE (включая `exists=false`).

## Technical Context

**Language/Version**: Kotlin (JVM 21), Vue 3 (webvue3)
**Primary Dependencies**: MinIO SDK (LIST), JDBC (LOCAL Postgres)
**Testing**: JUnit 5 (`StorageCacheWarmTest`)
**Constraints**: не менять сигнатуры сервисов (оптимизация impl допустима); фон + SSE
**Scale/Scope**: 26 575 песен × 9 имён × 2 источника ≈ 478k upsert; листинг ~120k объектов

## Constitution Check

- **Principle IX (Knowledge-first)** — pre-flight выполнен.
- **Tier-1 Git CI-gate** — feature-ветка + PR.
- **Tier-1 Knowledge SSoT** — обновлены `knowledge/` + `docs/features/`.

Нарушений нет.

## Project Structure

```text
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
  StorageCacheWarm.kt                        # NEW: warmStorageCache + loadSongsForWarm + listToMap
  services/StorageMetadataCache.kt           # MODIFY: upsertBatch + CacheUpsertRow
  services/KaraokeStorageService.kt          # MODIFY: listFilesInfo из Item.etag()/size()
  services/StorageApiClient.kt               # MODIFY: listFilesInfo из Item.etag()/size()
  controllers/ApiController.kt               # MODIFY: POST /api/utils/warmstoragecache
  controllers/MainController.kt              # MODIFY: зеркало для Thymeleaf
karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/StorageCacheWarmTest.kt  # NEW
webvue3/src/views/HomeView.vue               # MODIFY: кнопка «Прогреть кеш хранилища»
webvue3/src/components/Songs/store.js        # MODIFY: warmStorageCachePromise
docs/features/storage-metadata-cache.md      # MODIFY
knowledge/domains/storage/domain.md          # MODIFY
```

## Phase 1 — Backend

1. `listFilesInfo` (LOCAL/REMOTE): использовать `Item.etag()`/`size()`, фильтр `!isDir`.
2. `StorageMetadataCache.upsertBatch(rows)`: один `INSERT ... ON CONFLICT` на пачку, транзакция.
3. `StorageCacheWarm.kt`: листинги → map; обход песен из `tbl_songs`; батч-upsert; REMOTE circuit-aware; фон; SSE.
4. Endpoint `POST /api/utils/warmstoragecache` + зеркало `GET` в MainController.

**Checkpoint**: `:karaoke-app:compileKotlin` OK.

## Phase 2 — Frontend

5. `HomeView.vue`: кнопка + confirm + handler.
6. `Songs/store.js`: `warmStorageCachePromise`.

**Checkpoint**: lint / prettier / build OK.

## Phase 3 — Tests & Docs

7. `StorageCacheWarmTest`: `listToMap` (существующий/отсутствующий).
8. docs/features + knowledge.
9. report.md + checklist.

## Phase 4 — CI & PR

10. ktlint, pre-commit, PR, CI, merge, tracker.

## Risks

- **Память при листинге**: 59k `StorageFileInfo` — компактно (≤10 МБ). Обход песен
  батчами по 2000.
- **REMOTE circuit OPEN**: REMOTE-источник пропускается целиком; LOCAL прогревается.
- **Расхождение формул имён**: warm и health-report используют `StorageCacheReset`
  (единый источник) — консистентны.
- **Долгий прогон** (удалённый MinIO через nginx): фон + SSE. LOCAL — основной объём.

## Ready for implementation
