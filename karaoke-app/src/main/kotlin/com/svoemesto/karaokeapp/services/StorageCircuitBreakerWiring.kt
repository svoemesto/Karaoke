package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.HealthReport
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component

/**
 * Bridge-бин для инъекции [StorageCircuitBreaker] в статический контекст
 * `HealthReport.Companion` (спека #364, OpenProject #75).
 *
 * **Зачем**: `HealthReport` — companion object (2500+ строк), без конструктора.
 * Spring autowire в static context невозможен. Решение — аналогично
 * `StorageMetadataCacheWiring` (spec #344): хранить ссылку в static поле,
 * инициализируемом через `@PostConstruct` этого bridge-бина.
 *
 * @see StorageMetadataCacheWiring
 * @see specs/364-healthreport-speedup/spec.md (FR-006)
 */
@Component
class StorageCircuitBreakerWiring(
    private val circuitBreaker: StorageCircuitBreaker,
) {
    @PostConstruct
    fun wire() {
        HealthReport.attachStorageCircuitBreaker(circuitBreaker)
    }
}
