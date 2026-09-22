package com.svoemesto.karaokeapp.controllers

import com.svoemesto.karaokeapp.services.StorageCircuitBreaker
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * REST-endpoint for manual reset of [StorageCircuitBreaker] (Pass 372, OpenProject #131, spec #405).
 *
 * Pass 429 (#153): теперь **два** независимых breaker'а (local + remote).
 * `POST /api/health/circuit-breaker/reset?storage=local|remote|all` сбрасывает
 * выбранный (без параметра — оба, backward-compatible). `GET /api/health/circuit-breaker`
 * возвращает оба состояния в виде `{ local, remote }`.
 *
 * No authorization required — admin-only network, like other endpoints under `/api/health`.
 *
 * @see specs/429-split-local-remote-circuit-breakers/spec.md
 * @see specs/405-storage-circuit-breaker-watchdog/spec.md (FR-005, FR-007)
 * @see specs/352-storage-graceful-degradation/spec.md (FR-007)
 */
@RestController
@RequestMapping("/api/health/circuit-breaker")
class CircuitBreakerController(
    @Qualifier("localStorageCircuitBreaker") private val localStorageCircuitBreaker: StorageCircuitBreaker,
    @Qualifier("remoteStorageCircuitBreaker") private val remoteStorageCircuitBreaker: StorageCircuitBreaker,
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
     * Pass 372, FR-005 + Pass 429 (#153): POST /api/health/circuit-breaker/reset.
     * `storage=local|remote|all` (default `all`). Идемпотентен.
     */
    @PostMapping("/reset")
    fun reset(
        @RequestParam(name = "storage", required = false, defaultValue = "all") storage: String,
    ): Map<String, ResetResponse> {
        val targets: List<Pair<String, StorageCircuitBreaker>> =
            when (storage.lowercase()) {
                "local" -> listOf("local" to localStorageCircuitBreaker)
                "remote" -> listOf("remote" to remoteStorageCircuitBreaker)
                else -> listOf("local" to localStorageCircuitBreaker, "remote" to remoteStorageCircuitBreaker)
            }
        return targets.associate { (name, breaker) ->
            name to resetOne(name, breaker)
        }
    }

    private fun resetOne(
        name: String,
        breaker: StorageCircuitBreaker,
    ): ResetResponse {
        val before = breaker.metrics()
        val after = breaker.reset()
        val resetAtMs = System.currentTimeMillis()
        log.info(
            "cache:circuit:reset storage={} reason=manual_request_endpoint previousState={} currentState={}",
            name,
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
