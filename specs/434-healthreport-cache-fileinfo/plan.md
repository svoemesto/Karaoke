# Implementation Plan: HealthReport кеширует getFileInfo/fileIsActual

**Branch**: `434-healthreport-cache-fileinfo` | **Date**: 2026-09-22 | **Spec**: [spec.md](./spec.md)

## Summary

`fileIsActual`/`getFileInfo` в HealthReport идут в MinIO каждый раз (statObject),
хотя кеш хранит `etag`/`size`. Переводим на `StorageMetadataCache.getFileInfo`
(blocking, hit→без MinIO, miss→loader+upsert); `fileIsActual` вычисляем из
закешированного `size`. Чиним `selectFileInfo` (NULL guard), удаляем мёртвый
`getFileIsActual`, добавляем circuit на `fileIsActual(storageFileInfo)`.

## Technical Context

**Language/Version**: Kotlin (JVM 21), Spring Boot
**Primary Dependencies**: JDBC (LOCAL Postgres cache), MinIO SDK
**Testing**: JUnit 5
**Constraints**: не менять публичные сигнатуры storage-сервисов
**Scale/Scope**: `StorageMetadataCache`, `HealthReport`, `StorageApiClient` + тесты

## Constitution Check

- **Principle IX (Knowledge-first)** — pre-flight выполнен.
- **Tier-1 Hard Gate — Knowledge SSoT** — `health-report.md`, `storage/*`, docs.
- **Tier-1 Hard Gate — Git CI-gate** — ветка + PR + CI.
- **Tier-1 Hard Gate — Machine-Specific** — `nsa-i9`/`nsa`: rebuild ✅, restart ❌.

Нарушений нет.

## Project Structure

```text
karaoke-app/.../services/StorageMetadataCache.kt   # selectFileInfo null-guard; remove getFileIsActual
karaoke-app/.../HealthReport.kt                    # cachedFileInfo helper + 4 места
karaoke-app/.../services/StorageApiClient.kt       # circuit на fileIsActual(storageFileInfo)
karaoke-app/src/test/.../StorageMetadataCacheTest.kt  # NEW
knowledge/domains/health/components/health-report.md
knowledge/domains/storage/components/storage-api-client.md
docs/features/storage-metadata-cache.md
```

## Phase 1 — Design

### `StorageMetadataCache`

```kotlin
// FIX: null-guard — неполная строка (exists есть, size NULL) → miss.
private fun selectFileInfo(source, bucket, fileName): StorageFileInfo? = withConn { conn ->
    ...
    if (!rs.next()) null
    else {
        val exists = rs.getBoolean(1)
        val etag = rs.getString(2)
        val size = rs.getLong(3); val sizeIsNull = rs.wasNull()
        if (!exists || sizeIsNull) null else StorageFileInfo(bucket, fileName, etag ?: "", size)
    }
}
// УДАЛИТЬ getFileIsActual (dead + неверная семантика).
```

### `HealthReport` helper

```kotlin
@JvmStatic
fun cachedFileInfo(
    source: String,
    bucket: String,
    fileName: String,
    loader: () -> StorageFileInfo,
): StorageFileInfo? =
    storageMetadataCache?.getFileInfo(source, bucket, fileName, loader)
```

Замены:
- `actionsLocalStorage:736` `storageService.fileIsActual(path)` →
  `cachedFileIsActualLocal(bucket, fileName, pathToFile, storageService)`:
  `val info = cachedFileInfo("LOCAL", ..., { storageService.getFileInfo(...) }); result = info?.size == File(path).length()`.
- `793` `storageService.getFileInfo` → `cachedFileInfo("LOCAL", ...)`.
- `1081` `storageApiClient.fileIsActual(path)` → через `cachedFileInfo("REMOTE", ...)`.
- `1142` `storageService.getFileInfo` → `cachedFileInfo("LOCAL", ...)`.

`fileIsActual(a, b)` (local vs remote) — оба info из кеша, сравнение size.

### `StorageApiClientImpl.fileIsActual(storageFileInfo)`

Обернуть `getFileInfo(...).block()` в `storageCircuitBreaker.decorateOrEmpty` (как
path-вариант).

## Phase 2 — Tasks

См. [tasks.md](./tasks.md).
