package com.svoemesto.karaokeapp.controllers

import com.svoemesto.karaokeapp.services.StorageMetadataCache
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * REST-эндпоинт для метрик in-memory кеша метаданных MinIO (спека #344, OpenProject #69).
 *
 * `GET /api/health/cacheStats` → JSON с hit/miss/evictions/entries для local и remote кешей.
 *
 * @see specs/344-storage-metadata-cache/spec.md (FR-008)
 * @see specs/344-storage-metadata-cache/contracts/cache-stats-api.md
 * @see docs/features/storage-metadata-cache.md
 */
@RestController
@RequestMapping("/api/health")
class CacheStatsController(
    private val storageMetadataCache: StorageMetadataCache,
) {
    @GetMapping("/cacheStats")
    fun cacheStats(): StorageMetadataCache.CacheStatsDto = storageMetadataCache.stats()
}
