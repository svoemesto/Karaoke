package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.HealthReport
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

/**
 * Bridge-бин для инъекции **двух** [StorageCircuitBreaker] (local + remote) в
 * статический контекст `HealthReport.Companion` (спека #364, OpenProject #75;
 * разделение — Pass 429, #153).
 *
 * **Зачем**: `HealthReport` — companion object (2500+ строк), без конструктора.
 * Spring autowire в static context невозможен. Решение — аналогично
 * `StorageMetadataCacheWiring` (spec #344): хранить ссылки в static-полях,
 * инициализируемых через `@PostConstruct` этого bridge-бина.
 *
 * @see StorageMetadataCacheWiring
 * @see specs/364-healthreport-speedup/spec.md (FR-006)
 * @see specs/429-split-local-remote-circuit-breakers/spec.md
 */
@Component
class StorageCircuitBreakerWiring(
    @Qualifier("localStorageCircuitBreaker") private val localStorageCircuitBreaker: StorageCircuitBreaker,
    @Qualifier("remoteStorageCircuitBreaker") private val remoteStorageCircuitBreaker: StorageCircuitBreaker,
) {
    @PostConstruct
    fun wire() {
        HealthReport.attachStorageCircuitBreakers(
            localStorage = localStorageCircuitBreaker,
            remoteStorage = remoteStorageCircuitBreaker,
        )
    }
}
