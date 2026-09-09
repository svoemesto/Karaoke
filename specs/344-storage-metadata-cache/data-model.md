# Data Model: Storage metadata cache (OpenProject #69)

**Phase**: 1 — Design & Contracts
**Date**: 2026-09-09
**Spec**: [spec.md](./spec.md)
**Research**: [research.md](./research.md)

## Сущности

### 1. `PollingCache<V>` (copy of `karaoke-web/.../services/PollingCache.kt`)

**Где**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/PollingCache.kt` (80 строк, КОПИЯ из `karaoke-web`).

**Решает**: «нужен ли Shared Gradle module?» → **НЕТ**, копия с KDoc-ссылкой (Assumption 6 в spec).

```kotlin
package com.svoemesto.karaokeapp.services

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Локальная копия PollingCache из karaoke-web, для использования в StorageMetadataCache.
 * Контракт идентичен. См. [оригинальный файл в karaoke-web] для деталей.
 *
 * @see <relative-path-to>/karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/PollingCache.kt
 * @see archive/docs/features/<op69-slug>.md (будет создан в Plan-tasks)
 */
class PollingCache<V> {
    private data class CacheEntry<V>(
        val value: V,
        val expiresAtMs: Long,
    )

    private val store = ConcurrentHashMap<String, CacheEntry<V>>()
    private val callsSinceCleanup = AtomicLong(0L)
    private val cleanupEvery = 500L

    fun getOrCompute(key: String, ttlSeconds: Long, loader: () -> V): V { /* ... */ }
    fun size(): Int = store.size
    fun clear() { /* ... */ }
}
```

**Идентично по контракту с оригиналом**, пакет и KDoc меняются.

---

### 2. `StorageMetadataCache` (новый бин)

**Где**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/StorageMetadataCache.kt` (~150 строк).

**Ответственность**: обёртка над двумя `PollingCache` (`local` + `remote`) + метрики (`LongAdder`) + логирование (`infra.cache.storage`).

**Поля**:

```kotlin
@Component
class StorageMetadataCache(
    @Value("\${storage.metadata.cache.local.ttlSeconds:300}") private val localTtlSeconds: Long,
    @Value("\${storage.metadata.cache.remote.ttlSeconds:300}") private val remoteTtlSeconds: Long,
) {
    companion object {
        const val SOURCE_LOCAL = "LOCAL"
        const val SOURCE_REMOTE = "REMOTE"

        private val log = LoggerFactory.getLogger("infra.cache.storage")
    }

    private val localCache = PollingCache<CacheResult>()
    private val remoteCache = PollingCache<CacheResult>()

    // LongAdder для thread-safe счётчиков (лучше AtomicLong под hot contention)
    private val localHits = LongAdder()
    private val localMisses = LongAdder()
    private val localEvictions = LongAdder()
    private val remoteHits = LongAdder()
    private val remoteMisses = LongAdder()
    private val remoteEvictions = LongAdder()

    /**
     * Кешированный fileExists с loader fallback.
     * @param source "LOCAL" или "REMOTE"
     * @param bucket bucket name
     * @param fileName имя файла (URL-decoded ДО вызова; см. FR-012)
     * @param loader блок, вызываемый при cache miss
     */
    fun getFileExists(
        source: String,
        bucket: String,
        fileName: String,
        loader: () -> Boolean,
    ): Boolean {
        val key = buildKey(source, "fileExists", bucket, fileName)
        return compute(source, key) { loader() } as CacheResult.BooleanResult
            .also { /* unbox */ }
    }

    // Аналогично getFileIsActual(...): Boolean, getFileInfo(...): StorageFileInfo

    private fun buildKey(source: String, operation: String, bucket: String, fileName: String): String =
        "$source:$bucket/$fileName:$operation"

    /** Snapshot метрик для эндпоинта /api/health/cacheStats. */
    fun stats(): CacheStatsDto { /* см. contracts/cache-stats-api.md */ }
}
```

**Граница типов результата**:

```kotlin
sealed class CacheResult {
    data class BooleanResult(val value: Boolean) : CacheResult()
    data class FileInfoResult(val value: StorageFileInfo) : CacheResult()
}
```

**Решение формы**: `sealed class CacheResult` (покрывает `Boolean` + `StorageFileInfo` для FR-001/002/003). Альтернатива (Operation enum + nullable-поля) — отвергнута как overengineering.

---

### 3. `CacheStatsDto` (DTO для эндпоинта)

**Где**: `karaoke-app/.../controllers/CacheStatsController.kt` (внутренний DTO в файле).

```kotlin
data class CacheStatsDto(
    val local: StatsBucket,
    val remote: StatsBucket,
)

data class StatsBucket(
    val entries: Int,
    val hits: Long,
    val misses: Long,
    val hitRatio: Double,
    val ttlSeconds: Long,
    val evictions: Long,
)
```

Полный контракт — `contracts/cache-stats-api.md`.

---

### 4. `PollingCacheTest` (юнит-тесты)

**Где**: `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/PollingCacheTest.kt`.

Копия существующего `DedupCacheTest` (если есть) или новый набор. Минимум 5 unit-тестов:

1. `getOrCompute returns loader on cache miss` — первая вставка.
2. `getOrCompute returns cached on cache hit (within TTL)` — повторный вызов.
3. `getOrCompute re-invokes loader after TTL expiry` — старый entry заменён.
4. `concurrent getOrCompute is thread-safe (no exceptions)` — 10 потоков × 100 calls.
5. `size and clear work as expected` — базовые операции.

(Аналогичный набор для `DedupCache` уже в `karaoke-web` — port pattern.)

---

## Связи между сущностями

```text
                    ┌─────────────────────────────┐
                    │   HealthReport.kt           │
                    │   (karaoke-app)             │
                    └─────────────┬───────────────┘
                                  │ autowire (Spring)
                                  ▼
                    ┌─────────────────────────────┐
                    │   StorageMetadataCache      │
                    │   @Component, singleton     │
                    │                             │
                    │   • PollingCache (local)    │
                    │   • PollingCache (remote)   │
                    │   • LongAdder (hits/misses) │
                    │   • SLF4J "infra.cache.     │
                    │       storage"              │
                    └─────┬───────────┬───────────┘
                          │           │
                          ▼           ▼
                    ┌─────────┐ ┌──────────────┐
                    │Polling- │ │PollingCache  │
                    │Cache    │ │(remote)      │
                    │(local)  │ │              │
                    └─────────┘ └──────────────┘
                          ▲           ▲
                          │           │
                          │ getOrCompute(key, ttl, loader)
                          │
                    ┌─────┴─────────────────┐
                    │  Loaders (passed by   │
                    │  HealthReport):       │
                    │  • KaraokeStorage-    │
                    │    Service.fileExists │
                    │  • StorageApiClient.  │
                    │    fileExists         │
                    │  • getFileInfo / file-│
                    │    IsActual           │
                    └───────────────────────┘
```

**Важно**: `StorageMetadataCache` НЕ меняет контракты `KaraokeStorageService` / `StorageApiClient` (FR-011). Это explicit cache, autowired в HealthReport. Loader передаётся как Kotlin `() -> Boolean` / `() -> StorageFileInfo`.

## Конфигурация

### Spring `@Value` (минимум зависимостей от KaraokeProperties)

```yaml
# application.yml (или override через env)
storage:
  metadata:
    cache:
      local:
        ttlSeconds: 300       # default
      remote:
        ttlSeconds: 300       # default
```

Env override: `STORAGE_METADATA_CACHE_LOCAL_TTL_SECONDS=600`.

### SLF4J уровни

```yaml
# application.yml
logging:
  level:
    infra.cache.storage: INFO     # default, можно DEBUG для диагностики
```

## Валидационные правила

| Constraint | Где проверяется |
|---|---|
| `source ∈ {LOCAL, REMOTE}` | `StorageMetadataCache.buildKey` (внутренний enum) |
| `bucket != null, fileName != null` | Kotlin compile-time (non-null типы) |
| `ttlSeconds > 0` | `PollingCache.getOrCompute` (если `ttlSeconds <= 0`, miss → miss → бесконечно) — **НЕ валидируется** в первой версии, caller несёт ответственность. |
| Loader MUST NOT throw | FR-006: если loader бросает — exception пробрасывается, кеш не обновляется (next call → retry). |

## State transitions

`PollingCache` entry:
```text
[absent] ───getOrCompute(miss)────> [fresh] (TTL=300s)
[fresh] ───getOrCompute(hit)─────> [fresh] (TTL reset НЕ на каждом hit*;
                                              TTL фиксируется на момент создания,
                                              см. web-caches.md#known-gaps)
[fresh] ───TTL истёк────────────> [stale]
[stale] ───getOrCompute──────────> [fresh] (новый entry создаётся)
[fresh] ───cleanupEvery=500─────> [absent] (lazy eviction)
```

\* TTL фиксируется при `store[key] = CacheEntry(fresh, expiresAt)`. На каждом hit expiresAt НЕ продлевается. Это документированное поведение существующего `PollingCache` (см. `web-caches.md#known-gaps`), изменение не входит в scope #344.

## Готовность к contracts/quickstart

3 сущности (`PollingCache` copy, `StorageMetadataCache`, `CacheStatsDto`) + контракт endpoint + тесты. Можно переходить к `contracts/cache-stats-api.md`.
