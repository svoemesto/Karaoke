# Research: HealthReport WAITING status + SSE re-compute

> **Прецедент**: задача #80 — продолжение #75 (Pass 364). Все ключевые
> архитектурные решения уже приняты в Pass 344 (`specs/344-storage-metadata-cache/`),
> Pass 345 (`specs/348-storage-cache-eternal/`), Pass 351 (circuit breaker),
> Pass 364 (`specs/364-healthreport-speedup/`) и зафиксированы в Knowledge.
> Этот research фиксирует анализ текущего кода для подтверждения решения.

## Источники

- `knowledge/domains/health/domain.md` — Bounded Context Health
- `knowledge/domains/health/components/health-report.md` — компонента HealthReport
- `knowledge/domains/sse/domain.md` — SSE инфраструктура
- `knowledge/domains/storage/domain.md` — StorageMetadataCache контракт
- `knowledge/domains/caching/components/web-caches.md` — PollingCache
- `knowledge/domains/caching/components/caching-patterns.md` — паттерны
- `specs/344-storage-metadata-cache/spec.md` — оригинальная спека кеша
- `specs/348-storage-cache-eternal/spec.md` — переход на persistent cache
- `specs/364-healthreport-speedup/spec.md` — async cold-start
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReport.kt` — исходник
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReportStatus.kt` — enum
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/StorageMetadataCache.kt` — кеш

## Текущее поведение (как описано в issue #80)

### Шаг 1: Открытие страницы Songs (cold cache)

```kotlin
// HealthReport.kt:1119 → getHealthReportList
for each song (20):
    for each KaraokeFileType (5-8):
        for each KaraokeFileTypeLocations (3):
            HealthReport.record()
            → actions(location)
              → actionsLocalStorage() / actionsRemoteStorage()
                → cachedFileExists("LOCAL"|"REMOTE", bucket, fileName, loader)
                  → StorageMetadataCache.getFileExists(...)
                    → selectExists(...)  // cache miss (cold start)
                    → loader()  // blocking MinIO HTTP (50-200ms)
                    → upsert(...)  // cache fill
                    → return value
```

**Проблема**: HTTP-тред блокируется на каждый cache miss. На 20 песен × 5 типов × 3 location = до 300 проверок. Если кеш пуст и MinIO отвечает за 50ms каждая = 15 секунд. UI ждёт, ничего не показывает.

### Шаг 2: После Pass 364 (async для REMOTE)

Для **REMOTE** location уже есть `cachedFileExistsAsync` (HealthReport.kt:117) — он:
- Cache hit → возвращает cached value
- Cache miss → `CompletableFuture.completedFuture(null)` + fire-and-forget background fill
- Caller делает `.get(50ms)` — если fill не успел, safe default `true`
- Реальный результат заполняется в фоне через `cacheFillerExecutor.submit { ... }`

**Но**: callback после background fill **не отправляется** — `StorageMetadataCache.getFileExistsAsync` не вызывает никакого recompute. UI не получает SSE-сигнал обновления, видит stale `IN_PROGRESS`/`ERROR`/`OK` до следующего F5.

### Шаг 3: Поведение, описанное в issue #80

> "При открытии страницы с песнями если у песни ещё на закешировано
> обращение к удалённому и локальному хранилищу то эти строки сразу
> считаются "ошибочными" и у песни получается 3 записи в отчёте."

Три записи (по одной на location: LOCAL_FILESYSTEM / LOCAL_STORAGE / REMOTE_STORAGE) — это 3 разных HealthReport с одинаковым `healthReportStatus = ERROR`. После async fill эти три записи должны были бы пересчитаться, но без SSE — не пересчитываются.

## Решение (зафиксированное в спеки)

### Шаг 1: новое значение `WAITING`

```kotlin
enum class HealthReportStatus(val color: String) {
    OK(color = "#99FF99"),
    WARNING(color = "#99CCFF"),
    IN_PROGRESS(color = "#FFFF99"),  // НЕ трогаем (repair-loop)
    WAITING(color = "#FFCCFF"),     // NEW: cache miss, async fill
    ERROR(color = "#FF9999"),
    FATAL_ERROR(color = "#FF0000"),
}
```

Цвет `#FFCCFF` — точно по запросу задачи #80 (владелец явно указал).

### Шаг 2: callback в `cachedFileExists`

```kotlin
fun cachedFileExists(
    source: String,
    bucket: String,
    fileName: String,
    loader: () -> Boolean,
    onFillComplete: (() -> Unit)? = null,  // NEW
): Boolean
```

**Backward compatible**: default параметр `null` — 8 существующих вызовов (HealthReport.kt:466, 484, 664, 736, 800, 1051, 1115) НЕ меняются.

### Шаг 3: callback в `cachedFileExistsAsync`

```kotlin
fun cachedFileExistsAsync(
    source: String,
    bucket: String,
    fileName: String,
    loader: () -> Boolean,
    onFillComplete: (() -> Unit)? = null,  // NEW
): CompletableFuture<Boolean?>
```

`StorageMetadataCache.getFileExistsAsync` уже запускает `cacheFillerExecutor.submit { ... }` — нужно только вставить `onFillComplete` после успешного `upsert`.

### Шаг 4: вызывающая сторона

В местах, где нам нужен recompute после fill (например, **после `cachedFileExistsAsync` для REMOTE** — это единственное место, где cache miss + async fill может изменить HealthReport на ERROR/OK), добавляется callback:

```kotlin
cachedFileExistsAsync(
    "REMOTE", storageBucketName, storageFileName,
    loader = { storageApiClient.fileExists(...) },
    onFillComplete = {
        recomputeAndBroadcast(song.id, database, storageService, storageApiClient)
    }
)
```

**Где именно**: внутри `actionsRemoteStorage` (HealthReport.kt:963) — единственный вызов `cachedFileExistsAsync`. Это покрывает **самый частый** сценарий: REMOTE storage cold-start.

**Где НЕ нужно**: 8 вызовов `cachedFileExists` (sync) — после них fill происходит inline в caller-thread, и `getHealthReportList` сам уже строит отчёт на основе результата. Дополнительный recompute не нужен (и так корректно).

### Шаг 5: SLF4J логирование

Новая категория `infra.cache.storage.waiting` (Pass 368):

```kotlin
INFO  [infra.cache.storage.waiting] source=LOCAL bucket=karaoke fileName=song-12345.mp4
      status=FILLED durationMs=42 songId=12345
WARN  [infra.cache.storage.waiting] source=LOCAL bucket=karaoke fileName=song-12345.mp4
      status=FAILED durationMs=5000 error="Connection refused" songId=12345
```

## Альтернативы рассмотрены

### А. Background sweeper polling каждые N секунд

- **Pro**: простая реализация (cron + scheduler).
- **Con**: overengineering. Pass 348 — кеш eternal, miss происходит 1 раз за life of cache. Polling не нужен — событийная модель (callback) достаточна.
- **Вердикт**: rejected.

### Б. Отдельный SSE-канал `healthReportsUpdate`

- **Pro**: чистое разделение concerns.
- **Con**: `HEALTH_REPORTS` уже существует и принимается webvue3. Дублировать тип — увеличивать attack surface без явной пользы.
- **Вердикт**: rejected (reuse `HEALTH_REPORTS`).

### В. WebSocket вместо SSE

- **Pro**: bidirectional.
- **Con**: уже принято решение SSE (см. Knowledge `sse/domain.md`, раздел «Архитектурные решения»). Решение пересматривать не нужно.
- **Вердикт**: rejected.

### Г. Single-flight guard на `cachedFileExists` через AtomicBoolean per-song

- **Pro**: предотвращает дубли recompute при одновременных HTTP-запросах.
- **Con**: уже реализовано через `StorageMetadataCache` (один cache key → одна строка в БД → `ON CONFLICT DO UPDATE`). Два параллельных вызова на один ключ не создают две записи.
- **Вердикт**: rejected (естественный single-flight уже есть).

### Д. Менять `StorageMetadataCache` для пробрасывания callback

- **Pro**: явный контракт на уровне кеша.
- **Con**: нарушает separation of concerns. `StorageMetadataCache` не знает про Song. Caller (HealthReport) уже знает song — проще пробрасывать callback через `cachedFileExists`, чем через кеш.
- **Вердикт**: rejected (пробрасываем через caller).

## Решение

Применяем Шаги 1-5 выше. Минимальная правка, backward compatible, переиспользует существующие компоненты:
- `HealthReportStatus` +1 значение
- `cachedFileExists` +1 параметр (default null → backward compat)
- `cachedFileExistsAsync` +1 параметр (default null → backward compat)
- `cachedFileExistsAsync` callback → `recomputeAndBroadcast(song.id, ...)`
- Новая SLF4J-категория `infra.cache.storage.waiting`

Никаких изменений схемы БД, никаких изменений DI, никаких изменений UI webvue3 (он уже слушает `HEALTH_REPORTS`).