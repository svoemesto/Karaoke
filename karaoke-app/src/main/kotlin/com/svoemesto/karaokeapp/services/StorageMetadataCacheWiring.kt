package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.HealthReport
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component

/**
 * Bridge-бин для инъекции [StorageMetadataCache] в статический контекст
 * `HealthReport.Companion` (спека #344, OpenProject #69).
 *
 * **Зачем**: `HealthReport` — массивный companion object (2240 строк), без конструктора.
 * Spring autowire в instance fields невозможен. Решение — хранить ссылку на
 * singleton-кэш в статическом поле companion object, инициализируемом через
 * `@PostConstruct` этого bridge-бина.
 *
 * **Альтернативы (отвергнуты)**:
 *  - Передавать cache как параметр во все 200+ методов companion — invasive.
 *  - Использовать `ApplicationContext.getBean(...)` — медленно, не type-safe.
 *  - AOP/Spring proxy — отвергнуто per Assumption 7 (см. spec.md).
 *
 * @see specs/344-storage-metadata-cache/spec.md (FR-011)
 * @see docs/features/storage-metadata-cache.md
 */
@Component
class StorageMetadataCacheWiring(
    private val cache: StorageMetadataCache,
) {
    @PostConstruct
    fun wire() {
        HealthReport.attachStorageMetadataCache(cache)
        // log via cache string logger; не дублируем здесь.
        cache.let {
            // no-op: just confirms injection; logging happens in HealthReport when first call occurs.
        }
    }
}
