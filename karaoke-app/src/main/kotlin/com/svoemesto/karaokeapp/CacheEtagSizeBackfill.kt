package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.model.Message
import com.svoemesto.karaokeapp.model.SseNotification
import com.svoemesto.karaokeapp.services.KSS_APP
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.SAC_APP
import com.svoemesto.karaokeapp.services.SNS
import com.svoemesto.karaokeapp.services.StorageApiClient
import com.svoemesto.karaokeapp.services.StorageCircuitBreaker
import com.svoemesto.karaokeapp.services.StorageFileInfo
import com.svoemesto.karaokeapp.services.StorageMetadataCache
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Pass 435 (#159): разовый backfill `etag`/`size` в `tbl_storage_metadata_cache`.
 *
 * **Зачем**: строки, созданные через `fileExists`, хранят `exists=true`, но
 * `etag`/`size = NULL`. Из-за этого `fileIsActual` не может отвечать из кеша
 * (Pass 434). Функция проходит по таким строкам и заполняет info через
 * `getFileInfo` соответствующего бэкенда.
 *
 * - LOCAL → `KaraokeStorageService.getFileInfo` (прямой MinIO SDK).
 * - REMOTE → `StorageApiClient.getFileInfo` (circuit-aware: при OPEN строки
 *   пропускаются, чтобы не долбить нестабильный remote; идемпотентно — доберём
 *   при следующем запуске).
 * - Файл не найден (`size = -1`) → `exists = false` (самокоррекция) + WARN.
 *
 * Прогресс — SLF4J `infra.cache.storage`; итог — SSE-уведомление. Тяжёлая
 * операция (~109k строк) идёт в фоновом потоке; функция возвращает управление сразу.
 *
 * @see specs/435-cache-etag-size-backfill/spec.md
 * @see docs/features/storage-metadata-cache.md
 */

/** Что делать со строкой кеша по полученному [StorageFileInfo]. */
enum class BackfillAction { UPDATE, MARK_MISSING, SKIP }

/**
 * Чистое решение (тестируемо): `null` (info неизвестен — circuit/ошибка) → SKIP;
 * `size < 0` (файл не найден) → MARK_MISSING; иначе → UPDATE.
 */
internal fun decideBackfillAction(info: StorageFileInfo?): BackfillAction =
    when {
        info == null -> BackfillAction.SKIP
        info.size < 0 -> BackfillAction.MARK_MISSING
        else -> BackfillAction.UPDATE
    }

private val cacheBackfillLog = LoggerFactory.getLogger("infra.cache.storage")
private val cacheBackfillInProgress = AtomicBoolean(false)

/** Прогресс логируем каждые [PROGRESS_EVERY] строк. */
private const val PROGRESS_EVERY = 500

/**
 * Запускает фоновый backfill. Возвращает `"OK"` (запущено) или `"ALREADY_RUNNING"`.
 */
fun backfillCacheEtagSize(
    storageService: KaraokeStorageService = KSS_APP,
    storageApiClient: StorageApiClient = SAC_APP,
    remoteBreaker: StorageCircuitBreaker? = HealthReport.remoteStorageCircuitBreaker,
): String {
    if (!cacheBackfillInProgress.compareAndSet(false, true)) return "ALREADY_RUNNING"

    thread {
        try {
            val cache =
                HealthReport.storageMetadataCache
                    ?: error("StorageMetadataCache not wired yet")
            val rows = loadRowsNeedingBackfill()
            val total = rows.size
            cacheBackfillLog.info("cache:backfill start total={}", total)
            println("Backfill etag/size кеша хранилища: к обработке строк: $total")

            var updated = 0
            var missing = 0
            var skipped = 0
            rows.forEachIndexed { index, row ->
                try {
                    val info = fetchInfo(row, storageService, storageApiClient, remoteBreaker)
                    when (decideBackfillAction(info)) {
                        BackfillAction.UPDATE -> {
                            cache.updateFileInfo(row.source, row.bucket, row.fileName, info!!.etag, info.size)
                            updated++
                        }
                        BackfillAction.MARK_MISSING -> {
                            cache.markNotExists(row.source, row.bucket, row.fileName)
                            missing++
                            cacheBackfillLog.warn(
                                "cache:backfill missing source={} bucket={} fileName={}",
                                row.source, row.bucket, row.fileName,
                            )
                        }
                        BackfillAction.SKIP -> skipped++
                    }
                } catch (e: Exception) {
                    skipped++
                    cacheBackfillLog.warn(
                        "cache:backfill error source={} bucket={} fileName={} error={}",
                        row.source, row.bucket, row.fileName, "${e::class.simpleName}: ${e.message}",
                    )
                }

                if ((index + 1) % PROGRESS_EVERY == 0 || index + 1 == total) {
                    cacheBackfillLog.info(
                        "cache:backfill processed={}/{} updated={} missing={} skipped={}",
                        index + 1, total, updated, missing, skipped,
                    )
                    println("Backfill etag/size: обработано ${index + 1}/$total (updated=$updated, missing=$missing, skipped=$skipped)")
                }
            }

            val summary =
                "Обработано $total строк: заполнено $updated, помечено отсутствующими $missing, пропущено $skipped" +
                    if (skipped > 0) " (пропуски — remote circuit open/ошибки; запустите повторно)" else ""
            cacheBackfillLog.info("cache:backfill done {}", summary)
            println("Backfill etag/size кеша хранилища: завершено. $summary")
            SNS.send(
                SseNotification.message(
                    Message(
                        type = "info",
                        head = "Backfill etag/size кеша хранилища",
                        body = summary,
                    ),
                ),
            )
        } catch (e: Exception) {
            cacheBackfillLog.error("cache:backfill failed error={}", "${e::class.simpleName}: ${e.message}", e)
            SNS.send(
                SseNotification.error(
                    Message(
                        type = "error",
                        head = "Backfill etag/size кеша хранилища",
                        body = "Ошибка: ${e::class.simpleName}: ${e.message}",
                    ),
                ),
            )
        } finally {
            cacheBackfillInProgress.set(false)
        }
    }
    return "OK"
}

/** Строка кеша, требующая backfill. */
private data class CacheRow(
    val source: String,
    val bucket: String,
    val fileName: String,
)

/**
 * Строки с `exists = true` и пустыми `etag`/`size` (по обоим источникам).
 * Читаем курсором в память (Source/bucket/fileName — компактно; ~109k строк).
 */
private fun loadRowsNeedingBackfill(): List<CacheRow> {
    val result = mutableListOf<CacheRow>()
    try {
        val conn = Connection.local().getConnection() ?: return result
        conn.use { c ->
            val sql =
                """
                SELECT source, bucket, file_name
                FROM tbl_storage_metadata_cache
                WHERE exists = true AND (size IS NULL OR etag IS NULL OR etag = '')
                """.trimIndent()
            c.prepareStatement(sql).use { ps ->
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        result.add(CacheRow(rs.getString(1), rs.getString(2), rs.getString(3)))
                    }
                }
            }
        }
    } catch (e: Exception) {
        cacheBackfillLog.warn("cache:backfill loadRows failed error={}", "${e::class.simpleName}: ${e.message}")
    }
    return result
}

/**
 * Получить [StorageFileInfo] для строки. Для REMOTE — circuit-aware: при OPEN
 * возвращаем `null` (SKIP), без обращения к MinIO.
 */
private fun fetchInfo(
    row: CacheRow,
    storageService: KaraokeStorageService,
    storageApiClient: StorageApiClient,
    remoteBreaker: StorageCircuitBreaker?,
): StorageFileInfo? =
    if (row.source == StorageMetadataCache.SOURCE_LOCAL) {
        storageService.getFileInfo(bucketName = row.bucket, fileName = row.fileName)
    } else {
        if (remoteBreaker != null && remoteBreaker.isFastFail()) {
            null
        } else {
            storageApiClient.getFileInfo(bucketName = row.bucket, fileName = row.fileName).block()
        }
    }
