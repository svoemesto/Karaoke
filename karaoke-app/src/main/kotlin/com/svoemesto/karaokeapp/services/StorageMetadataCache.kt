package com.svoemesto.karaokeapp.services

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.LongAdder

/**
 * In-memory TTL-кеш для метаданных MinIO (спека #344, OpenProject #69).
 *
 * **Ответственность**: обёртка над двумя `PollingCache` (local MinIO + remote MinIO через
 * nginx path-proxy). Хранит результаты `fileExists` / `fileIsActual` / `getFileInfo`.
 * Используется в `HealthReport.actionsLocalStorage` / `actionsRemoteStorage` через
 * явный Spring-autowire (НЕ через AOP, Assumption 7 спеки).
 *
 * **Конкуренция**: thread-safe через `ConcurrentHashMap` внутри `PollingCache` и `LongAdder`
 * для счётчиков. Без single-flight guard для metadata (race редок, ~50ms MinIO round-trip).
 *
 * **TTL**: 300s default per-instance (local + remote), конфигурируется через
 * `storage.metadata.cache.{local,remote}.ttlSeconds` в `application.yml`.
 *
 * **maxEntries**: 50_000 default (NFR-002). Hard-cap через FIFO eviction в `PollingCache`.
 *
 * **Логирование**: строковый logger `"infra.cache.storage"` (НЕ `LoggerFactory.getLogger(Class)`,
 * см. ADR `local-0005-structured-logging-karaoke-app.md`). Регистрация в
 * `knowledge/domains/monitoring/components/log-categories.md`.
 *
 * @see specs/344-storage-metadata-cache/spec.md (FR-001..FR-008, FR-013)
 * @see specs/344-storage-metadata-cache/data-model.md
 * @see specs/344-storage-metadata-cache/contracts/cache-stats-api.md
 * @see knowledge/domains/caching/components/web-caches.md (готовый PollingCache)
 * @see docs/features/storage-metadata-cache.md (FR-009 per-feature doc)
 */
@Component
class StorageMetadataCache(
    @Value("\${storage.metadata.cache.local.ttlSeconds:300}")
    private val localTtlSeconds: Long,
    @Value("\${storage.metadata.cache.remote.ttlSeconds:300}")
    private val remoteTtlSeconds: Long,
    @Value("\${storage.metadata.cache.maxEntries:50000}")
    private val maxEntries: Int,
) {
    /**
     * Sealed class для двух видов кешируемых значений.
     */
    sealed class CacheResult {
        data class BooleanResult(
            val value: Boolean,
        ) : CacheResult()

        data class FileInfoResult(
            val value: StorageFileInfo,
        ) : CacheResult()
    }

    /**
     * DTO для endpoint `/api/health/cacheStats` (см. contracts/cache-stats-api.md).
     */
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

    companion object {
        const val SOURCE_LOCAL = "LOCAL"
        const val SOURCE_REMOTE = "REMOTE"

        /**
         * String logger `infra.cache.storage` (см. ADR `local-0005`).
         * Регистрируется в `knowledge/domains/monitoring/components/log-categories.md`.
         */
        private val log = LoggerFactory.getLogger("infra.cache.storage")
    }

    private val localCache = PollingCache<CacheResult>(maxEntries)
    private val remoteCache = PollingCache<CacheResult>(maxEntries)

    private val localStoreSize = LongAdder()
    private val remoteStoreSize = LongAdder()

    private fun buildKey(
        source: String,
        operation: String,
        bucket: String,
        fileName: String,
    ): String = "$source:$bucket/$fileName:$operation"

    /**
     * Кешированный `fileExists`. Loader MUST быть blocking `() -> Boolean`.
     * На cache miss — вызывается, сохраняется с TTL, возвращается.
     * Исключения пробрасываются без маскирования (FR-006).
     */
    fun getFileExists(
        source: String,
        bucket: String,
        fileName: String,
        loader: () -> Boolean,
    ): Boolean {
        validate(source, bucket, fileName)
        val key = buildKey(source, "fileExists", bucket, fileName)
        val cache = pickCache(source)
        val ttl = ttlFor(source)
        val cacheLoader = {
            val value = loader()
            log.info(
                "cache:miss key={} bucket={} fileName={} source={} operation=fileExists",
                key, bucket, fileName, source,
            )
            CacheResult.BooleanResult(value)
        }
        val result = cache.getOrCompute(key, ttl, cacheLoader) as CacheResult.BooleanResult
        return result.value
    }

    /**
     * Кешированный `fileIsActual`. Семантика идентична `getFileExists`.
     */
    fun getFileIsActual(
        source: String,
        bucket: String,
        fileName: String,
        loader: () -> Boolean,
    ): Boolean {
        validate(source, bucket, fileName)
        val key = buildKey(source, "fileIsActual", bucket, fileName)
        val cache = pickCache(source)
        val ttl = ttlFor(source)
        val cacheLoader = {
            val value = loader()
            log.info(
                "cache:miss key={} bucket={} fileName={} source={} operation=fileIsActual",
                key, bucket, fileName, source,
            )
            CacheResult.BooleanResult(value)
        }
        val result = cache.getOrCompute(key, ttl, cacheLoader) as CacheResult.BooleanResult
        return result.value
    }

    /**
     * Кешированный `getFileInfo`. Loader MUST быть blocking `() -> StorageFileInfo`.
     */
    fun getFileInfo(
        source: String,
        bucket: String,
        fileName: String,
        loader: () -> StorageFileInfo,
    ): StorageFileInfo {
        validate(source, bucket, fileName)
        val key = buildKey(source, "getFileInfo", bucket, fileName)
        val cache = pickCache(source)
        val ttl = ttlFor(source)
        val cacheLoader = {
            val value = loader()
            log.info(
                "cache:miss key={} bucket={} fileName={} source={} operation=getFileInfo",
                key, bucket, fileName, source,
            )
            CacheResult.FileInfoResult(value)
        }
        val result = cache.getOrCompute(key, ttl, cacheLoader) as CacheResult.FileInfoResult
        return result.value
    }

    /**
     * Snapshot метрик для эндпоинта `/api/health/cacheStats` (см. contracts/cache-stats-api.md).
     */
    fun stats(): CacheStatsDto {
        val localStats = bucket(localCache, localTtlSeconds)
        val remoteStats = bucket(remoteCache, remoteTtlSeconds)
        return CacheStatsDto(local = localStats, remote = remoteStats)
    }

    /**
     * Прямая очистка кеша (для тестов).
     */
    fun clear() {
        localCache.clear()
        remoteCache.clear()
        localStoreSize.reset()
        remoteStoreSize.reset()
    }

    private fun bucket(
        cache: PollingCache<CacheResult>,
        ttlSeconds: Long,
    ): StatsBucket {
        val hits = cache.hits()
        val misses = cache.misses()
        return StatsBucket(
            entries = cache.size(),
            hits = hits,
            misses = misses,
            hitRatio = hitRatio(hits, misses),
            ttlSeconds = ttlSeconds,
            evictions = cache.evictions(),
        )
    }

    @Suppress("MagicNumber")
    private fun hitRatio(hits: Long, misses: Long): Double {
        val total = hits + misses
        return if (total == 0L) {
            1.0
        } else {
            Math.round(hits.toDouble() / total * 10_000.0) / 10_000.0
        }
    }

    private fun pickCache(source: String): PollingCache<CacheResult> =
        when (source) {
            SOURCE_LOCAL -> localCache
            SOURCE_REMOTE -> remoteCache
            else -> throw IllegalArgumentException("Unknown source: $source (expected LOCAL or REMOTE)")
        }

    private fun ttlFor(source: String): Long =
        when (source) {
            SOURCE_LOCAL -> localTtlSeconds
            SOURCE_REMOTE -> remoteTtlSeconds
            else -> throw IllegalArgumentException("Unknown source: $source")
        }

    /**
     * Light validation: bucket и fileName MUST не быть пустыми. URL-decoded имя —
     * ответственность caller'а (FR-012 говорит caller делает decode ДО вызова).
     */
    private fun validate(source: String, bucket: String, fileName: String) {
        require(source == SOURCE_LOCAL || source == SOURCE_REMOTE) {
            "source must be LOCAL or REMOTE, got: $source"
        }
        require(bucket.isNotBlank()) { "bucket must not be blank" }
        require(fileName.isNotBlank()) { "fileName must not be blank" }
    }
}
