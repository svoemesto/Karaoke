# Contract: Cache stats API endpoint

**Path**: `specs/344-storage-metadata-cache/contracts/cache-stats-api.md`
**Phase**: 1 — Design & Contracts
**Date**: 2026-09-09
**Spec**: [spec.md](../spec.md)

## Endpoint: `GET /api/health/cacheStats`

**Auth**: admin (permitAll, как все `/api/health/*` в `karaoke-web` SecurityConfig, см. Constitution V и `webvue3`).
**HTTP method**: GET
**Content-Type**: application/json
**Side effects**: none (read-only).

### Request

```http
GET /api/health/cacheStats
Accept: application/json
```

### Response 200 OK

```json
{
  "local": {
    "entries": 1234,
    "hits": 5678,
    "misses": 42,
    "hitRatio": 0.9926,
    "ttlSeconds": 300,
    "evictions": 10
  },
  "remote": {
    "entries": 2345,
    "hits": 9876,
    "misses": 123,
    "hitRatio": 0.9877,
    "ttlSeconds": 300,
    "evictions": 15
  }
}
```

### Schema

```typescript
type CacheStatsResponse = {
  local: StatsBucket;
  remote: StatsBucket;
};

type StatsBucket = {
  /** Current number of entries in the cache */
  entries: number;        // integer, ≥ 0
  /** Total number of cache hits since start */
  hits: number;           // integer, ≥ 0
  /** Total number of cache misses since start */
  misses: number;         // integer, ≥ 0
  /** hits / (hits + misses), rounded to 4 decimals. 1.0 if hits + misses = 0. */
  hitRatio: number;       // float in [0.0, 1.0]
  /** Configured TTL in seconds */
  ttlSeconds: number;     // integer, > 0
  /** Total number of entries evicted by lazy cleanup */
  evictions: number;      // integer, ≥ 0
};
```

### Field semantics

| Поле | Описание | Когда обновляется |
|---|---|---|
| `entries` | Текущее количество ключей | На каждой cache hit/miss (размер `store.size`) |
| `hits` | Сколько раз `getOrCompute` вернул кешированное значение | На каждом cache hit (атомарный `LongAdder.increment`) |
| `misses` | Сколько раз `getOrCompute` вызвал loader | На каждом cache miss |
| `hitRatio` | `hits / (hits + misses)`, округлено до 4 знаков | Snapshot при запросе endpoint |
| `ttlSeconds` | TTL, с которым кеш сконфигурирован | Читается из бина при запросе (не меняется в runtime) |
| `evictions` | Сколько entries было удалено lazy cleanup'ом | На каждом `maybeCleanup` (atomic counter) |

### Edge cases

| Ситуация | Поведение |
|---|---|
| Холодный старт (entries=0, hits=0, misses=0) | `hitRatio: 1.0` (по convention, не делить на 0) |
| `hits=10, misses=0` | `hitRatio: 1.0` |
| `hits=0, misses=10` | `hitRatio: 0.0` |
| `entries=0` после cleanup | OK — пустой кеш валиден |

### Error response

| Code | When |
|---|---|
| 200 OK | Always (auto-recovery endpoint, деградации нет) |
| 401/403 | Только если вдруг появится auth на `/api/health/*` (сейчас permitAll) |

`CacheStatsController` НЕ имеет `@ExceptionHandler` — если что-то упало внутри (что почти невозможно: `LongAdder.sum` не падает), Spring вернёт default 500 + JSON в формате `local-0006` (см. ADR).

## Implementation sketch

```kotlin
package com.svoemesto.karaokeapp.controllers

import com.svoemesto.karaokeapp.services.StorageMetadataCache
import com.svoemesto.karaokeapp.services.StatsBucket
import com.svoemesto.karaokeapp.services.CacheStatsDto
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Метрики in-memory кеша метаданных MinIO (спека #344, OpenProject #69).
 *
 * GET /api/health/cacheStats → JSON с hit/miss/entries для local и remote.
 *
 * @see specs/344-storage-metadata-cache/spec.md (FR-008)
 * @see archive/docs/features/<op69-slug>.md (после создания в tasks)
 */
@RestController
@RequestMapping("/api/health")
class CacheStatsController(
    private val storageMetadataCache: StorageMetadataCache,
) {
    @GetMapping("/cacheStats")
    fun cacheStats(): CacheStatsDto = storageMetadataCache.stats()
}
```

`storageMetadataCache.stats()` (в `StorageMetadataCache.kt`):

```kotlin
fun stats(): CacheStatsDto = CacheStatsDto(
    local = StatsBucket(
        entries = localCache.size(),
        hits = localHits.sum(),
        misses = localMisses.sum(),
        hitRatio = hitRatio(localHits.sum(), localMisses.sum()),
        ttlSeconds = localTtlSeconds,
        evictions = localEvictions.sum(),
    ),
    remote = StatsBucket(
        entries = remoteCache.size(),
        hits = remoteHits.sum(),
        misses = remoteMisses.sum(),
        hitRatio = hitRatio(remoteHits.sum(), remoteMisses.sum()),
        ttlSeconds = remoteTtlSeconds,
        evictions = remoteEvictions.sum(),
    ),
)

private fun hitRatio(hits: Long, misses: Long): Double {
    val total = hits + misses
    return if (total == 0L) 1.0
    else Math.round(hits.toDouble() / total * 10_000.0) / 10_000.0
}
```

## Тестирование (unit)

`CacheStatsControllerTest.kt` — минимальный: один тест на happy path, один на cold-start (zero state). Полное тестирование через integration scenarios в `quickstart.md`.

## Связь с другими контрактами

- НЕ часть публичного API (`karaoke-web`) — это admin-only endpoint.
- Соседние endpoints: `GET /api/health/getHealthReportList`, `GET /api/monitor/alerts` (см. `MonitoringController.kt`).
- Не путать с `StorageApiClient.checkIfExists` (HTTP-endpoint в `karaoke-app/StorageController.kt:213` для admin-проверки одного файла — другая семантика).
