# Implementation Plan: Backfill etag/size в tbl_storage_metadata_cache

**Branch**: `435-cache-etag-size-backfill` | **Date**: 2026-09-22 | **Spec**: [spec.md](./spec.md)

## Summary

Фоновая функция `backfillCacheEtagSize`: курсором по `tbl_storage_metadata_cache`
(строки `exists=true AND size IS NULL`) получает `getFileInfo` (LOCAL →
`KaraokeStorageService`, REMOTE → `StorageApiClient`, circuit-aware), заполняет
`etag`/`size`; при `size=-1` → `exists=false`. Прогресс в `infra.cache.storage`,
итог — SSE. Кнопка на `HomeView.vue`.

## Technical Context

**Language/Version**: Kotlin (JVM 21), Vue 3 (webvue3)
**Primary Dependencies**: JDBC (LOCAL), MinIO SDK, circuit breaker
**Testing**: JUnit 5 (чистая логика решения)
**Constraints**: не блокировать HTTP; не менять публичные сигнатуры
**Scale/Scope**: ~109k строк; фон, пачками

## Constitution Check

- **Principle IX (Knowledge-first)** — pre-flight выполнен.
- **Tier-1 Hard Gate — Knowledge SSoT** — `storage/*`, docs/features.
- **Tier-1 Hard Gate — Git CI-gate** — ветка + PR + CI.
- **Tier-1 Hard Gate — Machine-Specific** — `nsa-i9`/`nsa`: rebuild ✅, restart ❌.

Нарушений нет.

## Project Structure

```text
karaoke-app/.../CacheEtagSizeBackfill.kt        # NEW: backfillCacheEtagSize
karaoke-app/.../services/StorageMetadataCache.kt # MODIFY: updateFileInfo / markNotExists
karaoke-app/.../controllers/ApiController.kt      # endpoint POST /api/utils/backfillcacheetagsize
karaoke-app/.../controllers/MainController.kt     # зеркало (Thymeleaf)
webvue3/src/components/Songs/store.js             # backfillCacheEtagSizePromise
webvue3/src/views/HomeView.vue                    # кнопка
karaoke-app/src/test/.../CacheEtagSizeBackfillTest.kt
knowledge/domains/storage/components/storage-api-client.md
docs/features/storage-metadata-cache.md
```

## Phase 1 — Design

### Чистое решение (тестируемо)

```kotlin
enum class BackfillAction { UPDATE, MARK_MISSING, SKIP }

/** size >= 0 → обновить etag/size; size < 0 → exists=false; null → skip (circuit/ошибка). */
internal fun decideBackfillAction(info: StorageFileInfo?): BackfillAction =
    when {
        info == null -> BackfillAction.SKIP
        info.size < 0 -> BackfillAction.MARK_MISSING
        else -> BackfillAction.UPDATE
    }
```

### Функция

```kotlin
fun backfillCacheEtagSize(
    storageService: KaraokeStorageService,
    storageApiClient: StorageApiClient,
    remoteBreaker: StorageCircuitBreaker?,
): String {
    if (!cacheBackfillInProgress.compareAndSet(false, true)) return "ALREADY_RUNNING"
    thread {
        try {
            val rows = loadRows()  // (source, bucket, file_name) where exists=true and size is null
            var updated=0; var missing=0; var skipped=0
            rows.forEachIndexed { i, row ->
                try {
                    val info = when(row.source) {
                        LOCAL -> storageService.getFileInfo(row.bucket, row.fileName).takeIf { it.size>=0 }
                        REMOTE -> if (remoteBreaker?.isFastFail()==true) null
                                  else storageApiClient.getFileInfo(row.bucket, row.fileName).block()
                    }
                    when (decideBackfillAction(info)) { ... }
                } catch (e) { skipped++ }
                if ((i+1) % N == 0) log.info("cache:backfill processed={}/{} ...")
            }
            SNS.send(message(... summary))
        } finally { cacheBackfillInProgress.set(false) }
    }
    return "OK"
}
```

### `StorageMetadataCache`

- `updateFileInfo(source, bucket, fileName, etag, size)` — переиспользует `upsert`.
- `markNotExists(source, bucket, fileName)` — `upsert(exists=false, etag=null, size=null)`.

## Phase 2 — Tasks

См. [tasks.md](./tasks.md).
