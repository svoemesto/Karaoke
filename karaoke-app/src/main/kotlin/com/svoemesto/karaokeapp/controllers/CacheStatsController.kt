package com.svoemesto.karaokeapp.controllers

import com.svoemesto.karaokeapp.services.StorageCircuitBreaker
import com.svoemesto.karaokeapp.services.StorageMetadataCache
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * REST-эндпоинт для метрик persistent metadata cache (спека #348, Pass 345) +
 * circuit breaker (Pass 351, #71).
 *
 * `GET /api/health/cacheStats` → JSON с hit/miss/evictions/entries/networkFailures
 * для local и remote + `circuitBreaker` block. `entries` — COUNT(*) по таблице
 * `tbl_storage_metadata_cache`. Переживает рестарт.
 *
 * @see specs/348-storage-cache-eternal/spec.md
 * @see specs/352-storage-graceful-degradation/spec.md (FR-005, FR-008)
 * @see docs/features/storage-metadata-cache.md (V2 → V2.1)
 */
@RestController
@RequestMapping("/api/health")
class CacheStatsController(
    private val storageMetadataCache: StorageMetadataCache,
    private val storageCircuitBreaker: StorageCircuitBreaker,
) {
    /**
     * Wraps [StorageMetadataCache.CacheStatsDto] with circuit breaker info.
     * Backwards compatible: existing fields (entries, hits, ...) preserved;
     * new fields (networkFailures, circuitBreaker) are additive.
     */
    data class CacheStatsResponse(
        val local: StorageMetadataCache.StatsBucket,
        val remote: StorageMetadataCache.StatsBucket,
        val circuitBreaker: StorageCircuitBreaker.Metrics,
    )

    @GetMapping("/cacheStats")
    fun cacheStats(): CacheStatsResponse {
        val base = storageMetadataCache.stats()
        val cb = storageCircuitBreaker.metrics()
        return CacheStatsResponse(
            local = base.local,
            remote = base.remote,
            circuitBreaker = cb,
        )
    }

    /**
     * Pass 351 (P3): GET /api/health/circuit-breaker.
     * Returns full snapshot of circuit state.
     */
    @GetMapping("/circuit-breaker")
    fun circuitBreaker(): StorageCircuitBreaker.Metrics = storageCircuitBreaker.metrics()
}
