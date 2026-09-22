package com.svoemesto.karaokeapp.controllers

import com.svoemesto.karaokeapp.services.StorageCircuitBreaker
import com.svoemesto.karaokeapp.services.StorageMetadataCache
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * REST-эндпоинт для метрик persistent metadata cache (спека #348, Pass 345) +
 * circuit breaker (Pass 351, #71; разделение local/remote — Pass 429, #153).
 *
 * `GET /api/health/cacheStats` → JSON с hit/miss/evictions/entries/networkFailures
 * для local и remote + `circuitBreaker` (объект с `local` и `remote`).
 * `GET /api/health/circuit-breaker` → `{ local, remote }`.
 *
 * @see specs/429-split-local-remote-circuit-breakers/spec.md
 * @see specs/348-storage-cache-eternal/spec.md
 * @see specs/352-storage-graceful-degradation/spec.md (FR-005, FR-008)
 * @see docs/features/storage-metadata-cache.md
 */
@RestController
@RequestMapping("/api/health")
class CacheStatsController(
    private val storageMetadataCache: StorageMetadataCache,
    @Qualifier("localStorageCircuitBreaker") private val localStorageCircuitBreaker: StorageCircuitBreaker,
    @Qualifier("remoteStorageCircuitBreaker") private val remoteStorageCircuitBreaker: StorageCircuitBreaker,
) {
    /** Pass 429 (#153): оба состояния circuit breaker. */
    data class CircuitBreakers(
        val local: StorageCircuitBreaker.Metrics,
        val remote: StorageCircuitBreaker.Metrics,
    )

    /**
     * Wraps [StorageMetadataCache.StatsBucket] with both circuit breaker states.
     * Backwards compatible: existing fields preserved; `circuitBreaker` теперь
     * объект `{ local, remote }` вместо одного [StorageCircuitBreaker.Metrics].
     */
    data class CacheStatsResponse(
        val local: StorageMetadataCache.StatsBucket,
        val remote: StorageMetadataCache.StatsBucket,
        val circuitBreaker: CircuitBreakers,
    )

    @GetMapping("/cacheStats")
    fun cacheStats(): CacheStatsResponse {
        val base = storageMetadataCache.stats()
        return CacheStatsResponse(
            local = base.local,
            remote = base.remote,
            circuitBreaker = circuitBreakers(),
        )
    }

    /** Pass 351 (P3) + Pass 429 (#153): оба состояния. */
    @GetMapping("/circuit-breaker")
    fun circuitBreakerEndpoint(): CircuitBreakers = circuitBreakers()

    private fun circuitBreakers(): CircuitBreakers =
        CircuitBreakers(
            local = localStorageCircuitBreaker.metrics(),
            remote = remoteStorageCircuitBreaker.metrics(),
        )
}
