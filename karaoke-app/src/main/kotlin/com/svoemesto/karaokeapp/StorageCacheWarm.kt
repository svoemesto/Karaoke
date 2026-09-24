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
 * Спека #449 (#182): полный прогрев persistent-кеша хранилища.
 *
 * Отличие от backfill (#435/#448): backfill до(за)полняет **существующие** строки;
 * warm строит кеш **с нуля по всем песням** — включая `exists=false` для файлов,
 * которых нет. Это нужно после сброса кеша (`refresh`/`refreshKeys` делают DELETE).
 *
 * Алгоритм (гибрид — быстро и полно):
 *   1. Листинг бакета `karaoke` (LOCAL и REMOTE) через `listFilesInfo` — один
 *      LIST-проход, MinIO отдаёт `etag`/`size` без `statObject` (спека #449).
 *   2. Обход **всех** песен (id/author/year/album/fileName) и всех имён из
 *      [StorageCacheReset.storageFileNamesForSong].
 *   3. Для каждого имени × источник — upsert: есть в map → `exists=true` + etag/size;
 *      нет → `exists=false`.
 *
 * Батчами, фоном, single-flight guard, прогресс в `infra.cache.storage`, итог — SSE.
 * REMOTE circuit-aware: при OPEN весь REMOTE-источник пропускается (WARN).
 *
 * @see specs/449-warm-storage-cache/spec.md
 * @see docs/features/storage-metadata-cache.md
 */

private val cacheWarmLog = LoggerFactory.getLogger("infra.cache.storage")
private val cacheWarmInProgress = AtomicBoolean(false)

/** Прогресс логируем каждые [WARM_PROGRESS_EVERY] песен. */
private const val WARM_PROGRESS_EVERY = 500

/** Размер пачки upsert (строк кеша за один INSERT ... ON CONFLICT). */
private const val WARM_BATCH_SIZE = 2000

/**
 * Запускает фоновый полный прогрев. Возвращает `"OK"` (запущено) или `"ALREADY_RUNNING"`.
 */
fun warmStorageCache(
    database: KaraokeConnection = WORKING_DATABASE,
    storageService: KaraokeStorageService = KSS_APP,
    storageApiClient: StorageApiClient = SAC_APP,
    remoteBreaker: StorageCircuitBreaker? = HealthReport.remoteStorageCircuitBreaker,
): String {
    if (!cacheWarmInProgress.compareAndSet(false, true)) return "ALREADY_RUNNING"

    thread {
        try {
            val cache =
                HealthReport.storageMetadataCache
                    ?: error("StorageMetadataCache not wired yet")

            val startedAt = System.currentTimeMillis()
            cacheWarmLog.info("cache:warm start")
            println("Прогрев кеша хранилища: старт")

            // [1] Листинги бакета (LOCAL/REMOTE) → map fileName → StorageFileInfo.
            val localMap = listToMap(storageService.listFilesInfo("karaoke"))
            cacheWarmLog.info("cache:warm local-listing objects={}", localMap.size)

            val remoteMap =
                if (remoteBreaker != null && remoteBreaker.isFastFail()) {
                    cacheWarmLog.warn("cache:warm remote-skip reason=circuit-open")
                    null
                } else {
                    try {
                        val m = listToMap(storageApiClient.listFilesInfo("karaoke").block() ?: emptyList())
                        cacheWarmLog.info("cache:warm remote-listing objects={}", m.size)
                        m
                    } catch (e: Exception) {
                        cacheWarmLog.warn("cache:warm remote-listing-failed error={}", "${e::class.simpleName}: ${e.message}")
                        null
                    }
                }

            // [2] Все песни (лёгкие поля — без полного Song для экономии памяти).
            val songs = loadSongsForWarm(database)
            val total = songs.size
            cacheWarmLog.info("cache:warm songs={}", total)
            println("Прогрев кеша хранилища: песен к обработке $total")

            // [3] Обход песен, батч-upsert.
            var localFound = 0
            var localMissing = 0
            var remoteFound = 0
            var remoteMissing = 0
            var batch = mutableListOf<StorageMetadataCache.CacheUpsertRow>()

            fun flush() {
                if (batch.isEmpty()) return
                cache.upsertBatch(batch)
                batch = mutableListOf()
            }

            songs.forEachIndexed { index, s ->
                val names = StorageCacheReset.storageFileNamesForSong(s.author, s.year, s.album, s.fileName)
                names.forEach { name ->
                    val localInfo = localMap[name]
                    batch.add(
                        StorageMetadataCache.CacheUpsertRow(
                            source = StorageMetadataCache.SOURCE_LOCAL,
                            bucket = "karaoke",
                            fileName = name,
                            exists = localInfo != null,
                            etag = localInfo?.etag,
                            size = localInfo?.size,
                        ),
                    )
                    if (localInfo != null) localFound++ else localMissing++

                    if (remoteMap != null) {
                        val remoteInfo = remoteMap[name]
                        batch.add(
                            StorageMetadataCache.CacheUpsertRow(
                                source = StorageMetadataCache.SOURCE_REMOTE,
                                bucket = "karaoke",
                                fileName = name,
                                exists = remoteInfo != null,
                                etag = remoteInfo?.etag,
                                size = remoteInfo?.size,
                            ),
                        )
                        if (remoteInfo != null) remoteFound++ else remoteMissing++
                    }
                }
                if (batch.size >= WARM_BATCH_SIZE) flush()

                val done = index + 1
                if (done % WARM_PROGRESS_EVERY == 0 || done == total) {
                    cacheWarmLog.info(
                        "cache:warm processed={}/{} localFound={} localMissing={} remoteFound={} remoteMissing={}",
                        done, total, localFound, localMissing, remoteFound, remoteMissing,
                    )
                    println(
                        "Прогрев кеша: $done/$total (local: найдено $localFound, нет $localMissing; " +
                            "remote: найдено $remoteFound, нет $remoteMissing)",
                    )
                }
            }
            flush()

            val durationMs = System.currentTimeMillis() - startedAt
            val remoteNote = if (remoteMap == null) " REMOTE пропущен (circuit open) — повторите позже." else ""
            val summary =
                "Песен: $total. LOCAL: найдено $localFound, нет $localMissing. " +
                    "REMOTE: найдено $remoteFound, нет $remoteMissing.$remoteNote" +
                    " За ${durationMs / 1000} с."
            cacheWarmLog.info("cache:warm done {}", summary)
            println("Прогрев кеша хранилища: завершено. $summary")
            SNS.send(
                SseNotification.message(
                    Message(
                        type = "info",
                        head = "Прогрев кеша хранилища",
                        body = summary,
                    ),
                ),
            )
        } catch (e: Exception) {
            cacheWarmLog.error("cache:warm failed error={}", "${e::class.simpleName}: ${e.message}", e)
            SNS.send(
                SseNotification.error(
                    Message(
                        type = "error",
                        head = "Прогрев кеша хранилища",
                        body = "Ошибка: ${e::class.simpleName}: ${e.message}",
                    ),
                ),
            )
        } finally {
            cacheWarmInProgress.set(false)
        }
    }
    return "OK"
}

internal fun listToMap(list: List<StorageFileInfo>): Map<String, StorageFileInfo> = list.associateBy { it.fileName }

/** Лёгкая проекция песни для прогрева (без полного `Song`). */
data class WarmSong(
    val id: Long,
    val author: String,
    val year: String,
    val album: String,
    val fileName: String,
)

/** Спека #449: все песни — только поля, нужные для формул имён. */
internal fun loadSongsForWarm(database: KaraokeConnection): List<WarmSong> {
    val result = mutableListOf<WarmSong>()
    val conn = database.getConnection() ?: return result
    conn.use { c ->
        c
            .prepareStatement(
                "SELECT id, song_author, song_year, song_album, file_name FROM tbl_songs ORDER BY id",
            ).use { ps ->
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        result.add(
                            WarmSong(
                                id = rs.getLong(1),
                                author = rs.getString(2) ?: "",
                                year = rs.getString(3) ?: "",
                                album = rs.getString(4) ?: "",
                                fileName = rs.getString(5) ?: "",
                            ),
                        )
                    }
                }
            }
    }
    return result
}
