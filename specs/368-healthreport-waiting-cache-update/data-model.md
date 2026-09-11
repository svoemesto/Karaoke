# Data Model: HealthReport WAITING status + SSE re-compute

> **Прецедент**: задача #80 — продолжение #75 (Pass 364).
> Минимальное изменение: +1 значение enum + 2 параметра в существующих
> функциях. Никаких изменений схемы БД.

## Изменения в `HealthReportStatus` enum

**Файл**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReportStatus.kt`

### До

```kotlin
enum class HealthReportStatus(val color: String) {
    OK(color = "#99FF99"),          // Всё хорошо
    WARNING(color = "#99CCFF"),     // Всё хорошо, но есть нюансы
    IN_PROGRESS(color = "#FFFF99"), // Уже чиним (есть KaraokeProcess)
    ERROR(color = "#FF9999"),       // Всё плохо, но можно сделать хорошо
    FATAL_ERROR(color = "#FF0000"), // Всё совсем плохо
}
```

### После

```kotlin
enum class HealthReportStatus(val color: String) {
    OK(color = "#99FF99"),          // Всё хорошо
    WARNING(color = "#99CCFF"),     // Всё хорошо, но есть нюансы
    IN_PROGRESS(color = "#FFFF99"), // Уже чиним (есть KaraokeProcess) — repair-loop
    WAITING(color = "#FFCCFF"),     // NEW (спека #368): кеш MinIO ещё не заполнен, async fill
    ERROR(color = "#FF9999"),       // Всё плохо, но можно сделать хорошо
    FATAL_ERROR(color = "#FF0000"), // Всё совсем плохо
}
```

### Семантика

| Значение | Цвет | Когда | Что показывает UI |
|----------|------|-------|-------------------|
| `OK` | `#99FF99` | Все файлы на месте | Зелёный |
| `WARNING` | `#99CCFF` | Частичная проблема | Голубой |
| `IN_PROGRESS` | `#FFFF99` | Уже чиним (есть `KaraokeProcess`) | Жёлтый |
| `WAITING` (NEW) | `#FFCCFF` | Cache miss → async fill | Розовый |
| `ERROR` | `#FF9999` | Реальная ошибка | Красный |
| `FATAL_ERROR` | `#FF0000` | Неисправимая ошибка | Ярко-красный |

**Различие `WAITING` vs `IN_PROGRESS`** (явный запрос владельца):
- `IN_PROGRESS`: KaraokeProcess уже создан и работает (repair-loop активен).
- `WAITING`: процесс ещё не запущен; идёт первый cache fill в background.

## Изменения в `HealthReport.cachedFileExists` сигнатуре

**Файл**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReport.kt:110`

### До

```kotlin
@JvmStatic
fun cachedFileExists(
    source: String,
    bucket: String,
    fileName: String,
    loader: () -> Boolean,
): Boolean =
    storageMetadataCache?.getFileExists(source, bucket, fileName, loader) ?: loader()
```

### После

```kotlin
/**
 * @param onFillComplete callback, вызываемый ПОСЛЕ успешного fill кеша
 *   (т.е. на cache miss). Если кеш уже был заполнен (hit) — callback НЕ
 *   вызывается. Используется для recompute+SSE после cold-start (спека #368).
 */
@JvmStatic
fun cachedFileExists(
    source: String,
    bucket: String,
    fileName: String,
    loader: () -> Boolean,
    onFillComplete: (() -> Unit)? = null,
): Boolean =
    storageMetadataCache?.getFileExists(
        source, bucket, fileName, loader,
        onFillComplete = onFillComplete,
    ) ?: loader()
```

**Backward compat**: default `null` для нового параметра → 8 существующих вызовов в HealthReport.kt НЕ требуют изменений.

## Изменения в `HealthReport.cachedFileExistsAsync` сигнатуре

**Файл**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReport.kt:117`

### До

```kotlin
@JvmStatic
fun cachedFileExistsAsync(
    source: String,
    bucket: String,
    fileName: String,
    loader: () -> Boolean,
): CompletableFuture<Boolean?> =
    storageMetadataCache?.getFileExistsAsync(source, bucket, fileName, loader)
        ?: CompletableFuture.completedFuture(loader())
```

### После

```kotlin
/**
 * @param onFillComplete callback, вызываемый ПОСЛЕ background fill
 *   (cache miss case). Hit case — callback НЕ вызывается.
 */
@JvmStatic
fun cachedFileExistsAsync(
    source: String,
    bucket: String,
    fileName: String,
    loader: () -> Boolean,
    onFillComplete: (() -> Unit)? = null,
): CompletableFuture<Boolean?> =
    storageMetadataCache?.getFileExistsAsync(
        source, bucket, fileName, loader,
        onFillComplete = onFillComplete,
    ) ?: CompletableFuture.completedFuture(loader())
```

## Изменения в `StorageMetadataCache.getFileExists` сигнатуре

**Файл**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/StorageMetadataCache.kt:114`

### До

```kotlin
fun getFileExists(
    source: String,
    bucket: String,
    fileName: String,
    loader: () -> Boolean,
): Boolean {
    validate(source, bucket, fileName)
    val cached = selectExists(source, bucket, fileName)
    if (cached != null) {
        hit(source)
        return cached
    }
    miss(source)
    val value = loader()
    upsert(source, bucket, fileName, exists = value, etag = null, sizeBytes = null)
    log.info("cache:miss ...")
    return value
}
```

### После

```kotlin
fun getFileExists(
    source: String,
    bucket: String,
    fileName: String,
    loader: () -> Boolean,
    onFillComplete: (() -> Unit)? = null,  // NEW (спека #368)
): Boolean {
    validate(source, bucket, fileName)
    val cached = selectExists(source, bucket, fileName)
    if (cached != null) {
        hit(source)
        return cached
    }
    miss(source)
    val value = loader()
    upsert(source, bucket, fileName, exists = value, etag = null, sizeBytes = null)
    log.info("cache:miss ...")
    onFillComplete?.invoke()  // NEW
    return value
}
```

## Изменения в `StorageMetadataCache.getFileExistsAsync`

**Файл**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/StorageMetadataCache.kt:397`

### До

```kotlin
fun getFileExistsAsync(...): CompletableFuture<Boolean?> {
    validate(source, bucket, fileName)
    val cached = selectExists(source, bucket, fileName)
    if (cached != null) {
        hit(source)
        return CompletableFuture.completedFuture(cached)
    }
    miss(source)
    cacheFillerExecutor.submit {
        try {
            val value = loader()
            upsert(source, bucket, fileName, exists = value, etag = null, sizeBytes = null)
            log.info("cache:miss:async ...")
        } catch (e: Exception) {
            log.warn("cache:miss:async:error ...")
        }
    }
    return CompletableFuture.completedFuture(null)
}
```

### После

```kotlin
fun getFileExistsAsync(
    source: String,
    bucket: String,
    fileName: String,
    loader: () -> Boolean,
    onFillComplete: (() -> Unit)? = null,  // NEW (спека #368)
): CompletableFuture<Boolean?> {
    validate(source, bucket, fileName)
    val cached = selectExists(source, bucket, fileName)
    if (cached != null) {
        hit(source)
        return CompletableFuture.completedFuture(cached)
    }
    miss(source)
    cacheFillerExecutor.submit {
        try {
            val value = loader()
            upsert(source, bucket, fileName, exists = value, etag = null, sizeBytes = null)
            log.info("cache:miss:async ...")
            onFillComplete?.invoke()  // NEW
        } catch (e: Exception) {
            log.warn("cache:miss:async:error ...")
            // onFillComplete НЕ вызывается (US2/AC3 спеки: остаёмся в WAITING)
        }
    }
    return CompletableFuture.completedFuture(null)
}
```

## Существующие сущности (НЕ изменяются, но перечислены для полноты)

### `HealthReport.recomputeAndBroadcast`

HealthReport.kt:2303. Принимает `songId`, пересчитывает HealthReport, шлёт SSE `HEALTH_REPORTS`. Используется без изменений из нового callback в `cachedFileExistsAsync`.

```kotlin
@JvmStatic
fun recomputeAndBroadcast(
    songId: Long,
    database: KaraokeConnection,
    storageService: KaraokeStorageService,
    storageApiClient: StorageApiClient,
): List<HealthReport>
```

### `SseNotification.HEALTH_REPORTS`

`model/SseNotification.kt` — единственный тип SSE для обновления UI. UI webvue3 уже слушает (Pass 341).

### `StorageMetadataCache.upsert`

Внутренний метод (StorageMetadataCache.kt:310). Используется без изменений.

## Схема БД

**Никаких изменений**. Используется существующая таблица `tbl_storage_metadata_cache`
(миграция `deploy/karaoke-db/48_storage_metadata_cache.sql`).