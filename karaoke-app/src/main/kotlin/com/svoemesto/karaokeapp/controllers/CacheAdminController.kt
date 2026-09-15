package com.svoemesto.karaokeapp.controllers

import com.svoemesto.karaokeapp.services.StorageMetadataCache
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
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

    /**
     * specs/118 #397: POST `/api/health/active-song-ids`
     *
     * Устанавливает множество активных songId (песен текущей страницы админки) —
     * backend приоритезирует cache-fill задачи для этих songId (вставка в начало
     * очереди, всплытие при смене страницы).
     *
     * Body: `{ "activeSongIds": [123, 456, ...] }`
     *
     * Returns: `Map { "activeSongIds" -> установленное множество (size) }`.
     */
    @PostMapping("/active-song-ids")
    fun setActiveSongIds(
        @RequestBody body: Map<String, Any>,
    ): Map<String, Any> {
        val rawIds = body["activeSongIds"]
        val activeSongIds: Set<Long> = parseActiveSongIds(rawIds)
        StorageMetadataCache.setActiveSongIds(activeSongIds)
        return mapOf(
            "activeSongIds" to activeSongIds.size,
            "queueSize" to StorageMetadataCache.cacheQueueSize(),
        )
    }

    private fun parseActiveSongIds(rawIds: Any?): Set<Long> =
        when (rawIds) {
            is List<*> -> rawIds.mapNotNull { (it as? Number)?.toLong() }.toSet()
            is Array<*> -> rawIds.mapNotNull { (it as? Number)?.toLong() }.toSet()
            else -> emptySet()
        }

    /**
     * specs/118 #397: GET `/api/health/cache-queue-size`
     *
     * Текущий размер очереди cache-fill (`StorageMetadataCache.cacheFillerExecutor`).
     * Используется для бейджа в UI (правый верхний угол кнопки Старт/Стоп в SongsTable).
     */
    @org.springframework.web.bind.annotation.GetMapping("/cache-queue-size")
    fun cacheQueueSize(): Map<String, Any> =
        mapOf("queueSize" to StorageMetadataCache.cacheQueueSize())
}
