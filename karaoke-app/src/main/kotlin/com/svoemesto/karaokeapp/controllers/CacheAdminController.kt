package com.svoemesto.karaokeapp.controllers

import com.svoemesto.karaokeapp.services.StorageMetadataCache
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Admin endpoints для управления persistent metadata cache (Pass 345, спека #348).
 *
 * Кеш живёт в Postgres (`tbl_storage_metadata_cache`) и обновляется write-through
 * хуками в upload/delete. Этот контроллер даёт admin-возможность принудительной
 * инвалидации (для случаев, когда файл изменён мимо Karaoke — через `mc`, прямой
 * SQL, и т.п.).
 *
 * @see specs/348-storage-cache-eternal/spec.md
 * @see docs/features/storage-metadata-cache.md
 */
@RestController
@RequestMapping("/api/health/cache")
class CacheAdminController(
    private val storageMetadataCache: StorageMetadataCache,
) {
    /**
     * DELETE `/api/health/cache/refresh?source=LOCAL&bucket=...&fileName=...`
     *
     * Точечный refresh: удаляет одну строку из кеша. Следующий read идёт в MinIO.
     *
     * - `source` — обязательно `LOCAL` или `REMOTE`.
     * - `bucket` — обязательно.
     * - `fileName` — обязательно.
     *
     * Returns: количество удалённых строк (0 или 1).
     */
    @DeleteMapping("/refresh")
    fun refreshKey(
        @RequestParam source: String,
        @RequestParam bucket: String,
        @RequestParam("fileName") fileName: String,
    ): Map<String, Any> {
        val deleted = storageMetadataCache.refresh(source, bucket, fileName)
        return mapOf(
            "source" to source,
            "bucket" to bucket,
            "fileName" to fileName,
            "rowsDeleted" to deleted,
        )
    }

    /**
     * DELETE `/api/health/cache/refresh-all?source=LOCAL`
     *
     * Bulk refresh: удаляет ВСЕ строки кеша для указанного источника. Используйте
     * после массовых изменений в MinIO мимо Karaoke. Следующий read каждого ключа
     * пойдёт в MinIO (Cold start — но перманентный).
     *
     * Returns: количество удалённых строк.
     */
    @DeleteMapping("/refresh-all")
    fun refreshAll(
        @RequestParam source: String,
    ): Map<String, Any> {
        val deleted = storageMetadataCache.refreshAll(source)
        return mapOf(
            "source" to source,
            "rowsDeleted" to deleted,
        )
    }
}
