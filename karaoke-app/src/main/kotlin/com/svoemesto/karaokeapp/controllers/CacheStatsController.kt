package com.svoemesto.karaokeapp.controllers

import com.svoemesto.karaokeapp.services.StorageMetadataCache
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * REST-эндпоинт для метрик persistent metadata cache (спека #348, Pass 345).
 *
 * `GET /api/health/cacheStats` → JSON с hit/miss/evictions/entries для local и remote.
 * `entries` — COUNT(*) по таблице `tbl_storage_metadata_cache`. Переживает рестарт.
 *
 * @see specs/348-storage-cache-eternal/spec.md
 * @see docs/features/storage-metadata-cache.md (V2 supersede V1)
 */
@RestController
@RequestMapping("/api/health")
class CacheStatsController(
    private val storageMetadataCache: StorageMetadataCache,
) {
    @GetMapping("/cacheStats")
    fun cacheStats(): StorageMetadataCache.CacheStatsDto = storageMetadataCache.stats()
}
