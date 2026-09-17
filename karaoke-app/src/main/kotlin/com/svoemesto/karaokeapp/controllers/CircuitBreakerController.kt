package com.svoemesto.karaokeapp.controllers

import com.svoemesto.karaokeapp.services.StorageCircuitBreaker
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * REST-endpoint for manual reset of StorageCircuitBreaker (Pass 372, OpenProject #131, spec #405).
 *
 * `POST /api/health/circuit-breaker/reset` resets [StorageCircuitBreaker] state to CLOSED
 * and zeroes all counters. Returns JSON with previousState and currentState. This is the
 * emergency escape hatch when watchdog fails to recover a stuck HALF_OPEN probe.
 *
 * The GET endpoint `/api/health/circuit-breaker` stays in [CacheStatsController] for
 * backward compatibility (Pass 351, spec #352, FR-008). This controller adds only
 * POST /reset (Pass 372, FR-005).
 *
 * No authorization required — admin-only network, like other endpoints under `/api/health`.
 *
 * @see specs/405-storage-circuit-breaker-watchdog/spec.md (FR-005, FR-007)
 * @see specs/352-storage-graceful-degradation/spec.md (FR-007)
 * @see docs/features/storage-metadata-cache.md
 */
@RestController
@RequestMapping("/api/health/circuit-breaker")
class CircuitBreakerController(
    private val storageCircuitBreaker: StorageCircuitBreaker,
) {
    private val log = LoggerFactory.getLogger("infra.cache.storage")

    /**
     * Response for [reset]. Extends [StorageCircuitBreaker.Metrics] with resetAtMs.
     */
    data class ResetResponse(
        val previousState: StorageCircuitBreaker.State,
        val currentState: StorageCircuitBreaker.State,
        val failureCount: Long,
        val lastFailureAt: Long?,
        val resetAtMs: Long,
        val totalSuccesses: Long,
        val totalNetworkFailures: Long,
    )

    /**
     * Pass 372, FR-005: POST /api/health/circuit-breaker/reset.
     * Resets circuit breaker state to CLOSED. Idempotent — repeated call in CLOSED
     * returns previousState=CLOSED (no-op).
     */
    @PostMapping("/reset")
    fun reset(): ResetResponse {
        val before = storageCircuitBreaker.metrics()
        val after = storageCircuitBreaker.reset()
        val resetAtMs = System.currentTimeMillis()
        log.info(
            "cache:circuit:reset reason=manual_request_endpoint previousState={} currentState={}",
            before.state,
            after.state,
        )
        return ResetResponse(
            previousState = before.state,
            currentState = after.state,
            failureCount = after.failureCount,
            lastFailureAt = after.lastFailureAt,
            resetAtMs = resetAtMs,
            totalSuccesses = after.totalSuccesses,
            totalNetworkFailures = after.totalNetworkFailures,
        )
    }
}
