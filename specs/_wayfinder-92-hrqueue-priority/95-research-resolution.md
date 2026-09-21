# #95 Resolution — Backend queue size endpoint

> **Resolution**: 2026-09-14, после research субагента.
> **Источник**: `research/92-backend-queue-size/REPORT.md` (SHA256 `65f0a3436eaa44b0c270a403d242939301cf0f021c384137eeb732d296e68343`).

## Verdict (в 1 строку)

**Endpoint существует** (`GET /api/health/cacheStats`, Pass 348). Создавать новый НЕ нужно — нужно **расширить** существующий response.

## Ключевые факты

1. **Endpoint**: `GET /api/health/cacheStats` — `karaoke-app/.../controllers/CacheStatsController.kt:38`.
   Security уже `permitAll()` (можно вызывать без auth).
2. **Существующий response**:
   ```json
   {
     "local":  {entries, hits, misses, hitRatio, evictions},
     "remote": {entries, hits, misses, hitRatio, evictions},
     "circuitBreaker": {...}
   }
   ```
3. **Что показывать**: `pendingTotal = activeCount + queue.size()` (по аналогии с
   `ProcessWorker.vue` бейджем `countWaiting`).
4. **`cacheFillerExecutor` уже есть** в `StorageMetadataCache.Companion` —
   `ThreadPoolExecutor(0, 16, 60s, LinkedBlockingQueue())` (Pass 372).
5. **CB Pass 351 НЕ мешает**: `StorageCircuitBreaker.acquire()` действует на MinIO-вызов,
   не на submit. Очередь растёт независимо от CB state — корректное поведение.
6. **SSE vs polling**: владелец выбрал **SSE** (как `countWaiting` в ProcessWorker).

## Минимальное вмешательство (~30 строк, additive)

### Backend

**1. `StorageMetadataCache.kt`** (companion object):
```kotlin
data class CacheFillerMetrics(
    val corePoolSize: Int,
    val maximumPoolSize: Int,
    val activeCount: Int,
    val poolSize: Int,
    val queueSize: Int,
    val completedTaskCount: Long,
    val pendingTotal: Int,  // activeCount + queueSize — UI-ready
)
```
Расширить `stats()` чтобы возвращал `cacheFiller: CacheFillerMetrics` (читает
`cacheFillerExecutor` напрямую).

**2. `CacheStatsController.kt`**:
- Добавить поле `cacheFiller: CacheFillerMetrics` в `CacheStatsResponse`.

### Frontend

**3. `webvue3/`** — расширить SSE-событие `cacheStats` (или новое `cacheFillerMetrics`).
Обновлять Vuex-store, отображать в бейдже.

## Что осталось неизвестным (для следующих тикетов)

1. **Семантика приоритезации** — решено через #97 (grilling, см. ниже).
2. **Цвет бейджа** — тикет #96 (prototype, HITL).
3. **Расположение** — тикет #96.
4. **SSE-событие vs polling** — владелец выбрал SSE.

## Связанные

- `knowledge/domains/storage/components/karaoke-storage-service.md` — для архитектурного контекста.
- `research/92-hrqueue-current-behavior/REPORT.md` (#94 — в работе) — текущее поведение очереди.
- OP #96 (prototype badge) — следующий шаг после #94.

— resolution для #95