package com.svoemesto.karaokeapp.services.songdeletion

import com.svoemesto.karaokeapp.AppLog
import com.svoemesto.karaokeapp.Connection
import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.KaraokeProcess
import com.svoemesto.karaokeapp.KaraokeProcessTypes
import com.svoemesto.karaokeapp.PROJECT_ROOT_FOLDERS
import com.svoemesto.karaokeapp.WORKING_DATABASE
import com.svoemesto.karaokeapp.model.Song
import com.svoemesto.karaokeapp.services.KSS_APP
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.SAC_APP
import com.svoemesto.karaokeapp.services.StorageApiClient
import org.slf4j.LoggerFactory
import java.sql.Connection as SqlConnection

/**
 * Удаление песни с каскадной уборкой (KARAOKE-429, спека 739, US1).
 *
 * **Три фазы, как задумано планом:**
 * 1. **Фаза A — LOCAL-БД, одна транзакция:** строка песни + каскад связанных записей
 *    ([SongDeletionPlanner]). Любой сбой → `rollback`, эндпоинт отвечает `500 db_error`,
 *    файлы и объекты НЕ трогаются (fail-closed).
 * 2. **Фаза B — SERVER-БД, отдельная транзакция:** строка песни и строка
 *    `tbl_songs_sync`. Её сбой НЕ отменяет фазу A, но результат честно отражает
 *    неудачу: `partially_deleted` (FR-045, решение лида В-01).
 * 3. **Фоновая уборка** — задание очереди `KaraokeProcess` типа `SONG_DELETE_CLEANUP`
 *    ([SongCleanupExecutor]). Файлы и объекты удаляются в фоне, а не в HTTP-запросе
 *    (FR-029; инвариант storage № 4: MinIO-операции не в HTTP-треде).
 *
 * **Честный результат.** Метод возвращает явный исход — успех, частичный успех,
 * блокировку, отсутствие песни или отказ БД. «Ложного успеха» (песня удалена в
 * интерфейсе, но осталась в базе) не бывает (SC-003).
 *
 * @see specs/739-song-delete-cascade/spec.md FR-001…FR-009, FR-045
 * @see specs/739-song-delete-cascade/contracts/song-delete-api.md
 */
class SongDeletionService(
    private val database: KaraokeConnection = WORKING_DATABASE,
    /**
     * Провайдеры зависимостей — лямбды, а не готовые объекты.
     *
     * Глобали приложения (`KSS_APP`, `SAC_APP`, `Connection.remote()`) инициализирует
     * Spring в `KaraokeAppService`; в юнит-тесте их нет, и обращение к ним в
     * конструкторе роняло бы тест. Через лямбды зависимости запрашиваются только
     * в момент реального использования.
     */
    private val remoteDatabaseProvider: () -> KaraokeConnection = { Connection.remote() },
    private val storageServiceProvider: () -> KaraokeStorageService = { KSS_APP },
    private val storageApiClientProvider: () -> StorageApiClient = { SAC_APP },
) {
    companion object {
        /** Категория структурированных логов фичи (ADR local-0005). */
        const val CATEGORY = "infra.song.delete"

        private val log = LoggerFactory.getLogger(CATEGORY)
    }

    /** Исход удаления песни. */
    sealed interface Outcome {
        /** id песни. */
        val songId: Long

        /** Удаление состоялось (возможно, частично — см. [Deleted.deletedServer]). */
        data class Deleted(
            override val songId: Long,
            val deletedLocal: Boolean,
            val deletedServer: Boolean,
            val runId: Long?,
            val serverError: String?,
        ) : Outcome

        /** Песни уже нет — повторное удаление (FR-002, идемпотентность). */
        data class AlreadyDeleted(
            override val songId: Long,
        ) : Outcome

        /** Песни не существует и удаление ранее не производилось. */
        data class NotFound(
            override val songId: Long,
        ) : Outcome

        /** Удаление запрещено: `not_owned` или `process_running`. */
        data class Blocked(
            override val songId: Long,
            val reason: String,
            val message: String,
        ) : Outcome

        /** Отказ базы данных — ничего не удалено. */
        data class DbError(
            override val songId: Long,
            val message: String,
        ) : Outcome
    }

    /**
     * Удалить песню.
     *
     * @param songId идентификатор песни.
     * @param initiator кто инициировал удаление (администратор).
     * @return исход удаления — честный признак фактического результата.
     */
    fun delete(
        songId: Long,
        initiator: String,
    ): Outcome {
        val song =
            Song.loadFromDbById(
                id = songId,
                database = database,
                storageService = storageServiceProvider(),
                storageApiClient = storageApiClientProvider(),
            )
        if (song == null) {
            // FR-002: отсутствие песни — не ошибка, если удаление уже состоялось.
            return if (SongCleanupJournal.hasDeleteRun(songId, database)) {
                Outcome.AlreadyDeleted(songId)
            } else {
                Outcome.NotFound(songId)
            }
        }

        val ownershipProblem = ownershipProblem(song)
        if (ownershipProblem != null) {
            AppLog.warn(
                category = CATEGORY,
                code = "song.delete.blocked.ownership",
                event = "song.delete.blocked.ownership",
                fields = mapOf("songId" to songId, "reason" to ownershipProblem.first),
            )
            return Outcome.Blocked(songId, ownershipProblem.first, ownershipProblem.second)
        }

        if (KaraokeProcess.hasActiveProcess(songId, database)) {
            AppLog.warn(
                category = CATEGORY,
                code = "song.delete.blocked.process",
                event = "song.delete.blocked.process",
                fields = mapOf("songId" to songId),
            )
            return Outcome.Blocked(
                songId,
                "process_running",
                "Над песней идёт фоновая обработка — удаление запрещено.",
            )
        }

        log.info(
            "event=song.delete.requested code=SONG-DELETE-001 songId={} initiator={}",
            songId,
            initiator,
        )

        // Журнал уборки создаётся ДО фазы A: он держит снимок контекста песни
        // (имя файла, год, альбом, папка), который нужен фоновой уборке — строки
        // песни к тому моменту уже не будет.
        val runId =
            SongCleanupJournal.createRun(
                songId = songId,
                songName = song.songName,
                author = song.author,
                album = song.album,
                rootFolder = song.rootFolder,
                fileName = song.fileName,
                year = song.year.toInt(),
                albumId = song.albumId ?: 0L,
                runKind = CleanupRunKind.SONG_DELETE,
                initiator = initiator,
                database = database,
            )
        if (runId <= 0L) {
            return Outcome.DbError(songId, "не удалось создать прогон уборки (журнал недоступен)")
        }

        // Фаза A — LOCAL-БД, одна транзакция.
        val phaseA = deleteLocal(songId)
        if (!phaseA) {
            SongCleanupJournal.deleteRun(runId, database)
            AppLog.error(
                category = CATEGORY,
                code = "song.delete.db.local.failed",
                event = "song.delete.db.local.failed",
                fields = mapOf("songId" to songId),
            )
            return Outcome.DbError(songId, "отказ базы данных при удалении строки песни — ничего не удалено")
        }
        log.info(
            "event=song.delete.db.local.ok code=SONG-DELETE-002 songId={}",
            songId,
        )
        SongCleanupJournal.updateRunState(runId, CleanupRunState.APPROVED, database)

        // Фаза B — SERVER-БД, отдельная транзакция (В-07: чистим и tbl_songs_sync).
        val phaseB = deleteRemote(songId)
        if (phaseB) {
            log.info(
                "event=song.delete.db.server.ok code=SONG-DELETE-003 songId={}",
                songId,
            )
        } else {
            AppLog.error(
                category = CATEGORY,
                code = "song.delete.db.server.failed",
                event = "song.delete.db.server.failed",
                fields = mapOf("songId" to songId),
            )
        }

        // Фоновая уборка: файлы, объекты, альбом, папка альбома (FR-029, FR-031).
        enqueueCleanup(song, runId)

        return Outcome.Deleted(
            songId = songId,
            deletedLocal = true,
            deletedServer = phaseB,
            runId = runId,
            serverError = if (phaseB) null else "server_db_unavailable",
        )
    }

    /**
     * Фаза A: LOCAL-БД, одна транзакция (FR-001, FR-005…FR-009).
     *
     * @return `true`, если транзакция зафиксирована.
     */
    private fun deleteLocal(songId: Long): Boolean {
        val connection = database.getConnection() ?: return false
        val previousAutoCommit = connection.autoCommit
        return try {
            connection.autoCommit = false
            SongDeletionPlanner.plan(songId).forEach { step ->
                connection.prepareStatement(step.sql).use { ps ->
                    step.params.forEachIndexed { index, value ->
                        if (value is Number) ps.setLong(index + 1, value.toLong()) else ps.setString(index + 1, value?.toString())
                    }
                    ps.executeUpdate()
                }
            }
            // FR-008: у потомков обнуляется связь с родителем, сами потомки остаются.
            val descendants = SongDeletionPlanner.descendantsStep(songId)
            connection.prepareStatement(descendants.sql).use { ps ->
                descendants.params.forEachIndexed { index, value ->
                    if (value is Number) ps.setLong(index + 1, value.toLong()) else ps.setString(index + 1, value?.toString())
                }
                ps.executeUpdate()
            }
            // Строка песни — последним: до неё каскад связанных записей.
            val songStep = SongDeletionPlanner.songStep(songId)
            connection.prepareStatement(songStep.sql).use { ps ->
                songStep.params.forEachIndexed { index, value ->
                    if (value is Number) ps.setLong(index + 1, value.toLong()) else ps.setString(index + 1, value?.toString())
                }
                ps.executeUpdate()
            }
            connection.commit()
            true
        } catch (e: Exception) {
            try {
                connection.rollback()
            } catch (rollbackError: Exception) {
                log.error("song.delete.db.local.rollback_failed: songId={}", songId, rollbackError)
            }
            AppLog.error(
                category = CATEGORY,
                code = "song.delete.db.local.failed",
                event = "song.delete.db.local.failed",
                fields = mapOf("songId" to songId),
                error = e,
            )
            false
        } finally {
            try {
                connection.autoCommit = previousAutoCommit
            } catch (e: Exception) {
                log.warn("song.delete.db.local.autocommit_restore_failed: songId={}", songId)
            }
        }
    }

    /**
     * Фаза B: SERVER-БД, отдельная транзакция (FR-001, FR-045, В-07).
     *
     * @return `true`, если строка удалена и в SERVER-БД.
     */
    private fun deleteRemote(songId: Long): Boolean {
        val connection: SqlConnection = remoteDatabaseProvider().getConnection() ?: return false
        val previousAutoCommit = connection.autoCommit
        return try {
            connection.autoCommit = false
            listOf(
                SongDeletionPlanner.songStep(songId, syncTable = true),
                SongDeletionPlanner.songStep(songId, syncTable = false),
            ).forEach { step ->
                connection.prepareStatement(step.sql).use { ps ->
                    step.params.forEachIndexed { index, value ->
                        if (value is Number) ps.setLong(index + 1, value.toLong()) else ps.setString(index + 1, value?.toString())
                    }
                    ps.executeUpdate()
                }
            }
            connection.commit()
            true
        } catch (e: Exception) {
            try {
                connection.rollback()
            } catch (rollbackError: Exception) {
                log.error("song.delete.db.server.rollback_failed: songId={}", songId, rollbackError)
            }
            AppLog.error(
                category = CATEGORY,
                code = "song.delete.db.server.failed",
                event = "song.delete.db.server.failed",
                fields = mapOf("songId" to songId),
                error = e,
            )
            false
        } finally {
            try {
                connection.autoCommit = previousAutoCommit
            } catch (e: Exception) {
                log.warn("song.delete.db.server.autocommit_restore_failed: songId={}", songId)
            }
        }
    }

    /**
     * Постановка фоновой уборки в очередь заданий (FR-029, контракт § 7).
     *
     * Задание: `["runFunctionWithArgs", "songDeleteCleanup", "songId=<id>", "runId=<runId>"]`,
     * lane `-1` (`THREAD_LANE_LIGHT_BACKGROUND`).
     */
    private fun enqueueCleanup(
        song: Song,
        runId: Long,
    ) {
        try {
            val chainId =
                KaraokeProcess.createProcess(
                    song = song,
                    action = KaraokeProcessTypes.SONG_DELETE_CLEANUP,
                    doWait = true,
                    threadId = KaraokeProcess.THREAD_LANE_LIGHT_BACKGROUND,
                    context = mapOf("runId" to runId),
                )
            if (chainId <= 0L) {
                // Задание не создано (например, межсетевая гонка: активный процесс
                // песни появился между проверкой и постановкой). Остаток уборки не
                // теряется: прогон остаётся в состоянии APPROVED, а запись в журнале
                // объясняет, что уборку нужно добрать вручную (FR-033).
                log.warn("song.cleanup.enqueue_failed: runId={} задание уборки не создано", runId)
                SongCleanupJournal.addEntry(
                    runId = runId,
                    kind = SongCleanupJournal.EntryKind.FILE,
                    location = SongCleanupJournal.EntryLocation.DATABASE,
                    pathOrKey = "cleanup:run:$runId",
                    sizeBytes = 0,
                    result = SongCleanupJournal.EntryResult.FAILED,
                    reason = "enqueue_failed",
                    initiator = "song-delete",
                    database = database,
                )
            }
        } catch (e: Exception) {
            AppLog.error(
                category = CATEGORY,
                code = "song.cleanup.enqueue_failed",
                event = "song.cleanup.enqueue_failed",
                fields = mapOf("runId" to runId),
                error = e,
            )
        }
    }

    /**
     * Добор SERVER-удаления (FR-045, В-01): повторная попытка снять строку песни
     * в серверной базе после восстановления связи.
     *
     * @return `true`, если строка удалена в SERVER.
     */
    fun retryServerDeletion(songId: Long): Boolean = deleteRemote(songId)

    /**
     * Проверка «это наша песня» (FR-011, FR-046).
     *
     * @return пара (код отказа, сообщение) либо `null`, если запись наша.
     */
    fun ownershipProblem(song: Song): Pair<String, String>? {
        val rootFolder = song.rootFolder
        if (rootFolder.isBlank() || PROJECT_ROOT_FOLDERS.none { rootFolder.startsWith(it) }) {
            return "not_owned" to "Запись ссылается на папку вне проекта — удаление запрещено."
        }
        val fileName = song.fileName
        if (fileName.isBlank() || fileName.contains("/") || fileName.contains("\\")) {
            return "not_owned" to "Имя файла песни пустое или содержит разделитель пути — удаление запрещено."
        }
        return null
    }
}
