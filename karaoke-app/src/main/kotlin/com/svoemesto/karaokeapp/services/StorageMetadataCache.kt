package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.Connection
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Persistent (eternal) cache для метаданных MinIO — спека #348 (Pass 345), supersede #344.
 *
 * **TTL = ∞**. Кеш НЕ протухает сам по себе.
 *
 * **Single source of truth**: таблица `tbl_storage_metadata_cache` в LOCAL Postgres
 * (миграция `deploy/karaoke-db/48_storage_metadata_cache.sql`). Переживает
 * restart/rebuild `karaoke-app` и `webvue3`.
 *
 * **Invalidation**: 3 источника, write-through:
 *   1. **Hooks**: `StorageApiClient.uploadFile/deleteFile` +
 *      `KaraokeStorageService.uploadFile/deleteFile` вызывают
 *      `recordUpload` / `recordDelete` после успеха.
 *   2. **Manual refresh**: `POST /api/health/cache/refresh` (см. `CacheAdminController`).
 *   3. **No auto-invalidation** (сознательное решение): stale-данные после прямых
 *      `mc rm` мимо Karaoke остаются stale пока кто-то явно не нажмёт refresh.
 *      Это и есть "вечный" кеш — пользователь контролирует.
 *
 * **Контракт**: НЕ МЕНЯЕТ публичные сигнатуры `KaraokeStorageService` /
 * `StorageApiClient` (FR-011 спеки #344). Только добавляет хуки в их implementation.
 *
 * **Что кешируется**: результаты `fileExists` (Boolean), `fileIsActual` (Boolean)
 * и `getFileInfo` (StorageFileInfo). URL-decoded имя файла — caller'а
 * ответственность (`HealthReport.cachedFileExists` в companion object).
 *
 * **Multi-replica** (если несколько `karaoke-app`): PG обеспечивает консистентность
 * через UNIQUE constraint + ON CONFLICT. В проекте сейчас 1 admin-машина →
 * проблема не возникает, но архитектурно поддерживается.
 *
 * @see specs/348-storage-cache-eternal/spec.md
 * @see docs/features/storage-metadata-cache.md
 * @see knowledge/domains/caching/components/web-caches.md
 */
@Component
class StorageMetadataCache {
    companion object {
        const val SOURCE_LOCAL = "LOCAL"
        const val SOURCE_REMOTE = "REMOTE"

        /**
         * String logger `infra.cache.storage` (см. ADR `local-0005`).
         */
        private val log = LoggerFactory.getLogger("infra.cache.storage")
    }

    /**
     * Метрики для `/api/health/cacheStats`. `entries` — COUNT(*) по таблице,
     * `hits`/`misses` — локальные счётчики процесса (НЕ across-restart).
     */
    data class CacheStatsDto(
        val local: StatsBucket,
        val remote: StatsBucket,
    )

    data class StatsBucket(
        val entries: Long,
        val hits: Long,
        val misses: Long,
        val hitRatio: Double,
        val evictions: Long,
    )

    private val localHits =
        java.util.concurrent.atomic
            .LongAdder()
    private val localMisses =
        java.util.concurrent.atomic
            .LongAdder()
    private val localDeletes =
        java.util.concurrent.atomic
            .LongAdder()
    private val remoteHits =
        java.util.concurrent.atomic
            .LongAdder()
    private val remoteMisses =
        java.util.concurrent.atomic
            .LongAdder()
    private val remoteDeletes =
        java.util.concurrent.atomic
            .LongAdder()

    /**
     * Cache key: `"$source:$bucket/$fileName"`.
     */
    private fun buildKey(source: String, bucket: String, fileName: String): String =
        "$source:$bucket/$fileName"

    /**
     * SELECT из кеша, fallback на [loader] при miss.
     *
     * @param source LOCAL или REMOTE.
     * @param bucket bucket name (NOT hardcoded; per Constitution).
     * @param fileName file name (URL-decoded caller'ом).
     * @param loader blocking `() -> Boolean` — вызывается только при cache miss.
     */
    fun getFileExists(source: String, bucket: String, fileName: String, loader: () -> Boolean): Boolean {
        validate(source, bucket, fileName)
        val cached = selectExists(source, bucket, fileName)
        if (cached != null) {
            hit(source)
            return cached
        }
        miss(source)
        val value = loader()
        upsert(source, bucket, fileName, exists = value, etag = null, sizeBytes = null)
        log.info(
            "cache:miss key={} bucket={} fileName={} source={} operation=fileExists value={}",
            buildKey(source, bucket, fileName), bucket, fileName, source, value,
        )
        return value
    }

    /**
     * SELECT из кеша, fallback на [loader] при miss.
     */
    fun getFileIsActual(source: String, bucket: String, fileName: String, loader: () -> Boolean): Boolean {
        validate(source, bucket, fileName)
        val cached = selectExists(source, bucket, fileName)
        if (cached != null) {
            hit(source)
            return cached
        }
        miss(source)
        val value = loader()
        upsert(source, bucket, fileName, exists = value, etag = null, sizeBytes = null)
        log.info(
            "cache:miss key={} bucket={} fileName={} source={} operation=fileIsActual value={}",
            buildKey(source, bucket, fileName), bucket, fileName, source, value,
        )
        return value
    }

    /**
     * SELECT с загрузкой StorageFileInfo. Loader MUST blocking.
     */
    fun getFileInfo(source: String, bucket: String, fileName: String, loader: () -> StorageFileInfo): StorageFileInfo {
        validate(source, bucket, fileName)
        val cached = selectFileInfo(source, bucket, fileName)
        if (cached != null) {
            hit(source)
            return cached
        }
        miss(source)
        val value = loader()
        upsert(source, bucket, fileName, exists = true, etag = value.etag, sizeBytes = value.size)
        log.info(
            "cache:miss key={} bucket={} fileName={} source={} operation=getFileInfo etag={} size={}",
            buildKey(source, bucket, fileName), bucket, fileName, source, value.etag, value.size,
        )
        return value
    }

    /**
     * Write-through hook: вызывается из `StorageApiClient.uploadFile` и
     * `KaraokeStorageService.uploadFile` после успешного MinIO upload.
     */
    fun recordUpload(source: String, bucket: String, fileName: String, etag: String?, sizeBytes: Long?) {
        validate(source, bucket, fileName)
        upsert(source, bucket, fileName, exists = true, etag = etag, sizeBytes = sizeBytes)
        log.info(
            "cache:write-through bucket={} fileName={} source={} operation=upload etag={} size={}",
            bucket, fileName, source, etag, sizeBytes,
        )
    }

    /**
     * Write-through hook: вызывается из `deleteFile` impls после успешного MinIO delete.
     */
    fun recordDelete(source: String, bucket: String, fileName: String) {
        validate(source, bucket, fileName)
        delete(source, bucket, fileName)
        if (source == SOURCE_LOCAL) localDeletes.increment() else remoteDeletes.increment()
        log.info(
            "cache:write-through bucket={} fileName={} source={} operation=delete",
            bucket, fileName, source,
        )
    }

    /**
     * Manual refresh: точечный DELETE строки. Следующий read пойдёт в MinIO.
     */
    fun refresh(source: String, bucket: String, fileName: String): Int {
        validate(source, bucket, fileName)
        val deleted = delete(source, bucket, fileName)
        if (source == SOURCE_LOCAL) localDeletes.increment() else remoteDeletes.increment()
        log.info(
            "cache:refresh bucket={} fileName={} source={} rowsDeleted={}",
            bucket, fileName, source, deleted,
        )
        return deleted
    }

    /**
     * Manual refresh: bulk DELETE всех строк (по источнику).
     */
    fun refreshAll(source: String): Int {
        require(source == SOURCE_LOCAL || source == SOURCE_REMOTE) { "source must be LOCAL or REMOTE" }
        val conn: java.sql.Connection =
            Connection.local().getConnection()
                ?: error("Cannot get local connection")
        return try {
            conn
                .prepareStatement("DELETE FROM tbl_storage_metadata_cache WHERE source = ?")
                .use { ps ->
                    ps.setString(1, source)
                    ps.executeUpdate()
                }.also { count ->
                    if (source == SOURCE_LOCAL) {
                        localDeletes.add(count.toLong())
                    } else {
                        remoteDeletes.add(count.toLong())
                    }
                    log.info("cache:refresh-all source={} rowsDeleted={}", source, count)
                }
        } finally {
            conn.close()
        }
    }

    /**
     * Stats для endpoint `/api/health/cacheStats`. `entries` — COUNT(*),
     * `hits/misses/evictions` — локальные in-process счётчики.
     */
    fun stats(): CacheStatsDto =
        CacheStatsDto(
            local =
                StatsBucket(
                    entries = countEntries(SOURCE_LOCAL),
                    hits = localHits.sum(),
                    misses = localMisses.sum(),
                    hitRatio = hitRatio(localHits.sum(), localMisses.sum()),
                    evictions = localDeletes.sum(),
                ),
            remote =
                StatsBucket(
                    entries = countEntries(SOURCE_REMOTE),
                    hits = remoteHits.sum(),
                    misses = remoteMisses.sum(),
                    hitRatio = hitRatio(remoteHits.sum(), remoteMisses.sum()),
                    evictions = remoteDeletes.sum(),
                ),
        )

    /**
     * Утилита: открыть локальное JDBC-соединение и выполнить блок. Закрывает
     * соединение через `use { conn -> ... }` — гарантия от утечки JDBC.
     */
    private inline fun <T> withConn(block: (java.sql.Connection) -> T): T {
        val conn: java.sql.Connection =
            Connection.local().getConnection()
                ?: error("Cannot get LOCAL JDBC connection")
        return conn.use(block)
    }

    // ---- Internals ------------------------------------------------

    private fun selectExists(source: String, bucket: String, fileName: String): Boolean? =
        withConn { conn ->
            conn
                .prepareStatement(
                    "SELECT exists FROM tbl_storage_metadata_cache WHERE source = ? AND bucket = ? AND file_name = ?",
                ).use { ps ->
                    ps.setString(1, source)
                    ps.setString(2, bucket)
                    ps.setString(3, fileName)
                    ps.executeQuery().use { rs -> if (rs.next()) rs.getBoolean(1) else null }
                }
        }

    private fun selectFileInfo(source: String, bucket: String, fileName: String): StorageFileInfo? =
        withConn { conn ->
            conn
                .prepareStatement(
                    "SELECT exists, etag, size FROM tbl_storage_metadata_cache WHERE source = ? AND bucket = ? AND file_name = ?",
                ).use { ps ->
                    ps.setString(1, source)
                    ps.setString(2, bucket)
                    ps.setString(3, fileName)
                    ps.executeQuery().use { rs ->
                        if (!rs.next()) {
                            null
                        } else {
                            val exists = rs.getBoolean(1)
                            val etag = rs.getString(2) ?: ""
                            val size = rs.getLong(3)
                            if (!exists) null else StorageFileInfo(bucket, fileName, etag, size)
                        }
                    }
                }
        }

    private fun upsert(
        source: String,
        bucket: String,
        fileName: String,
        exists: Boolean,
        etag: String?,
        sizeBytes: Long?,
    ) = withConn { conn ->
        conn
            .prepareStatement(
                """
                INSERT INTO tbl_storage_metadata_cache (source, bucket, file_name, exists, etag, size, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, NOW())
                ON CONFLICT (source, bucket, file_name) DO UPDATE
                SET exists = EXCLUDED.exists, etag = EXCLUDED.etag, size = EXCLUDED.size, updated_at = NOW()
                """.trimIndent(),
            ).use { ps ->
                ps.setString(1, source)
                ps.setString(2, bucket)
                ps.setString(3, fileName)
                ps.setBoolean(4, exists)
                ps.setString(5, etag)
                if (sizeBytes != null) ps.setLong(6, sizeBytes) else ps.setNull(6, java.sql.Types.BIGINT)
                ps.executeUpdate()
            }
    }

    private fun delete(source: String, bucket: String, fileName: String): Int =
        withConn { conn ->
            conn
                .prepareStatement(
                    "DELETE FROM tbl_storage_metadata_cache WHERE source = ? AND bucket = ? AND file_name = ?",
                ).use { ps ->
                    ps.setString(1, source)
                    ps.setString(2, bucket)
                    ps.setString(3, fileName)
                    ps.executeUpdate()
                }
        }

    private fun countEntries(source: String): Long =
        withConn { conn ->
            conn.prepareStatement("SELECT COUNT(*) FROM tbl_storage_metadata_cache WHERE source = ?").use { ps ->
                ps.setString(1, source)
                ps.executeQuery().use { rs -> if (rs.next()) rs.getLong(1) else 0L }
            }
        }

    @Suppress("MagicNumber")
    private fun hitRatio(hits: Long, misses: Long): Double {
        val total = hits + misses
        return if (total == 0L) {
            1.0
        } else {
            Math.round(hits.toDouble() / total * 10_000.0) / 10_000.0
        }
    }

    private fun hit(source: String) {
        (if (source == SOURCE_LOCAL) localHits else remoteHits).increment()
    }

    private fun miss(source: String) {
        (if (source == SOURCE_LOCAL) localMisses else remoteMisses).increment()
    }

    private fun validate(source: String, bucket: String, fileName: String) {
        require(source == SOURCE_LOCAL || source == SOURCE_REMOTE) {
            "source must be LOCAL or REMOTE, got: $source"
        }
        require(bucket.isNotBlank()) { "bucket must not be blank" }
        require(fileName.isNotBlank()) { "fileName must not be blank" }
    }
}
