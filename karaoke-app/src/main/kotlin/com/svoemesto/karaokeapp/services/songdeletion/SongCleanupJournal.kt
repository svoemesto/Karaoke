package com.svoemesto.karaokeapp.services.songdeletion

import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.WORKING_DATABASE
import org.slf4j.LoggerFactory
import java.sql.Timestamp

/**
 * Журнал уборки в БД (KARAOKE-429, спека 739, FR-034, FR-035).
 *
 * Журнал ведётся в базе данных — не в TSV-файле: он должен переживать рестарт
 * приложения (SC-009) и давать выборку «что осталось» по прогону.
 *
 * **По каждому действию журнал хранит семь полей FR-035:** путь или ключ, размер,
 * результат, дата, инициатор, идентификатор прогона (+ причина и номер попытки).
 *
 * Таблицы НЕ участвуют в LOCAL↔SERVER sync — журнал является локальной хроникой
 * администратора (осознанное исключение из Principle III, см. plan.md).
 *
 * @see specs/739-song-delete-cascade/spec.md FR-034, FR-035
 * @see specs/739-song-delete-cascade/data-model.md § 1.4, § 1.5
 */
object SongCleanupJournal {
    /** Категория логов журнала уборки. */
    private val log = LoggerFactory.getLogger("infra.song.delete")

    /** Таблица прогонов. */
    const val TABLE_RUNS = "tbl_song_cleanup_runs"

    /** Таблица записей журнала. */
    const val TABLE_ENTRIES = "tbl_song_cleanup_entries"

    /** Что за объект удаляется (kind в журнале). */
    enum class EntryKind {
        FILE,
        STORAGE_OBJECT,
        ALBUM,
        PICTURE,
        ALBUM_FOLDER,
        DB_ROWS,
    }

    /** Где лежит объект (location в журнале). */
    enum class EntryLocation {
        LOCAL_FILESYSTEM,
        LOCAL_STORAGE,
        REMOTE_STORAGE,
        DATABASE,
    }

    /** Результат действия (result в журнале). */
    enum class EntryResult {
        DELETED,
        KEPT,
        FAILED,
        SKIPPED,
    }

    /** Сводка прогона — то, что видит администратор (FR-036). */
    data class RunSummary(
        val runId: Long,
        val songId: Long,
        val songName: String,
        val author: String,
        val album: String,
        val rootFolder: String,
        val fileName: String,
        val year: Int,
        val albumId: Long,
        val runKind: CleanupRunKind,
        val state: CleanupRunState,
        val initiator: String,
        val startedAt: String?,
        val finishedAt: String?,
        val filesTotal: Int,
        val filesDeleted: Int,
        val filesKept: Int,
        val filesFailed: Int,
        val objectsTotal: Int,
        val objectsDeleted: Int,
        val objectsKept: Int,
        val objectsFailed: Int,
        val bytesDeleted: Long,
        val error: String?,
    )

    /** Запись журнала. */
    data class Entry(
        val id: Long,
        val runId: Long,
        val kind: EntryKind,
        val location: EntryLocation,
        val pathOrKey: String,
        val sizeBytes: Long,
        val result: EntryResult,
        val reason: String?,
        val attempt: Int,
        val createdAt: String?,
        val initiator: String,
    )

    /**
     * Создать прогон уборки (состояние `PLANNED`).
     *
     * @param songId идентификатор песни (0 — для разовой уборки).
     * @param songName снимок имени песни — журнал должен читаться после удаления строки.
     * @param runKind вид прогона.
     * @param initiator кто инициировал.
     * @param database подключение к БД.
     * @return id прогона (`run_id` в журнале) или `0`, если запись не удалась.
     */
    fun createRun(
        songId: Long,
        songName: String,
        author: String = "",
        album: String = "",
        rootFolder: String = "",
        fileName: String = "",
        year: Int = 0,
        albumId: Long = 0,
        runKind: CleanupRunKind = CleanupRunKind.SONG_DELETE,
        initiator: String,
        database: KaraokeConnection = WORKING_DATABASE,
    ): Long {
        val connection =
            database.getConnection()
                ?: run {
                    log.warn("song.cleanup.run.create_failed: нет соединения с БД {}", database.name)
                    return 0L
                }
        return try {
            val sql =
                "INSERT INTO $TABLE_RUNS " +
                    "(song_id, song_name, author, album, root_folder, file_name, song_year, album_id, " +
                    "run_kind, state, initiator, started_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())"
            connection.prepareStatement(sql).use { ps ->
                ps.setLong(1, songId)
                ps.setString(2, songName)
                ps.setString(3, author)
                ps.setString(4, album)
                ps.setString(5, rootFolder)
                ps.setString(6, fileName)
                ps.setInt(7, year)
                ps.setLong(8, albumId)
                ps.setString(9, runKind.name)
                ps.setString(10, CleanupRunState.PLANNED.name)
                ps.setString(11, initiator)
                ps.executeUpdate()
            }
            connection.createStatement().use { st ->
                st.executeQuery("SELECT lastval()").use { rs ->
                    if (rs.next()) rs.getLong(1) else 0L
                }
            }
        } catch (e: Exception) {
            log.warn("song.cleanup.run.create_failed: songId={} — {}", songId, e.toString())
            0L
        }
    }

    /**
     * Перевести прогон в новое состояние.
     *
     * **SC-003:** переход в `DONE` запрещён, если по прогону есть отказы — тогда
     * состояние `PARTIAL` (см. [CleanupRunStateMachine.canFinishDone]).
     *
     * @return `true`, если состояние выставлено; `false`, если переход запрещён.
     */
    fun updateRunState(
        runId: Long,
        state: CleanupRunState,
        database: KaraokeConnection = WORKING_DATABASE,
        error: String? = null,
    ): Boolean {
        val connection = database.getConnection() ?: return false
        val current = currentState(runId, database) ?: return false
        if (current == state) return true
        if (!CleanupRunStateMachine.canTransition(current, state)) {
            log.warn(
                "song.cleanup.run.transition_rejected: runId={} {} → {} — недопустимый переход",
                runId,
                current,
                state,
            )
            return false
        }
        return try {
            val finished = if (state.isTerminal) "now()" else "NULL"
            val sql = "UPDATE $TABLE_RUNS SET state = ?, error = ?, finished_at = $finished WHERE id = ?"
            connection.prepareStatement(sql).use { ps ->
                ps.setString(1, state.name)
                ps.setString(2, error)
                ps.setLong(3, runId)
                ps.executeUpdate()
            }
            true
        } catch (e: Exception) {
            log.warn("song.cleanup.run.state_failed: runId={} {} — {}", runId, state, e.toString())
            false
        }
    }

    /**
     * Текущее состояние прогона.
     */
    fun currentState(
        runId: Long,
        database: KaraokeConnection = WORKING_DATABASE,
    ): CleanupRunState? {
        val connection = database.getConnection() ?: return null
        return try {
            connection
                .prepareStatement("SELECT state FROM $TABLE_RUNS WHERE id = ?")
                .use { ps ->
                    ps.setLong(1, runId)
                    ps.executeQuery().use { rs ->
                        if (rs.next()) CleanupRunState.valueOf(rs.getString("state")) else null
                    }
                }
        } catch (e: Exception) {
            log.warn("song.cleanup.run.state_read_failed: runId={} — {}", runId, e.toString())
            null
        }
    }

    /**
     * Обновить счётчики прогона.
     */
    fun updateCounters(
        runId: Long,
        filesTotal: Int = -1,
        filesDeleted: Int = -1,
        filesKept: Int = -1,
        filesFailed: Int = -1,
        objectsTotal: Int = -1,
        objectsDeleted: Int = -1,
        objectsKept: Int = -1,
        objectsFailed: Int = -1,
        bytesDeleted: Long = -1,
        database: KaraokeConnection = WORKING_DATABASE,
    ): Boolean {
        val connection = database.getConnection() ?: return false
        return try {
            val assignments =
                mutableListOf<String>()
            val values = mutableListOf<Any?>()

            fun setIf(
                column: String,
                value: Long,
            ) {
                assignments.add("$column = ?")
                values.add(value)
            }
            if (filesTotal >= 0) setIf("files_total", filesTotal.toLong())
            if (filesDeleted >= 0) setIf("files_deleted", filesDeleted.toLong())
            if (filesKept >= 0) setIf("files_kept", filesKept.toLong())
            if (filesFailed >= 0) setIf("files_failed", filesFailed.toLong())
            if (objectsTotal >= 0) setIf("objects_total", objectsTotal.toLong())
            if (objectsDeleted >= 0) setIf("objects_deleted", objectsDeleted.toLong())
            if (objectsKept >= 0) setIf("objects_kept", objectsKept.toLong())
            if (objectsFailed >= 0) setIf("objects_failed", objectsFailed.toLong())
            if (bytesDeleted >= 0) setIf("bytes_deleted", bytesDeleted)
            if (assignments.isEmpty()) return true
            values.add(runId)
            val sql = "UPDATE $TABLE_RUNS SET ${assignments.joinToString(", ")} WHERE id = ?"
            connection.prepareStatement(sql).use { ps ->
                values.forEachIndexed { index, value ->
                    when (value) {
                        is Long -> ps.setLong(index + 1, value)
                        is Int -> ps.setInt(index + 1, value)
                        else -> ps.setString(index + 1, value?.toString())
                    }
                }
                ps.executeUpdate()
            }
            true
        } catch (e: Exception) {
            log.warn("song.cleanup.run.counters_failed: runId={} — {}", runId, e.toString())
            false
        }
    }

    /**
     * Записать действие в журнал (FR-035).
     *
     * @return id записи или `0`, если запись не удалась.
     */
    fun addEntry(
        runId: Long,
        kind: EntryKind,
        location: EntryLocation,
        pathOrKey: String,
        sizeBytes: Long,
        result: EntryResult,
        reason: String? = null,
        attempt: Int = 1,
        initiator: String,
        database: KaraokeConnection = WORKING_DATABASE,
    ): Long {
        val connection = database.getConnection() ?: return 0L
        return try {
            val sql =
                "INSERT INTO $TABLE_ENTRIES " +
                    "(run_id, kind, location, path_or_key, size_bytes, result, reason, attempt, created_at, initiator) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, now(), ?)"
            connection.prepareStatement(sql).use { ps ->
                ps.setLong(1, runId)
                ps.setString(2, kind.name)
                ps.setString(3, location.name)
                ps.setString(4, pathOrKey)
                ps.setLong(5, sizeBytes)
                ps.setString(6, result.name)
                ps.setString(7, reason)
                ps.setInt(8, attempt)
                ps.setString(9, initiator)
                ps.executeUpdate()
            }
            connection.createStatement().use { st ->
                st.executeQuery("SELECT lastval()").use { rs ->
                    if (rs.next()) rs.getLong(1) else 0L
                }
            }
        } catch (e: Exception) {
            log.warn("song.cleanup.entry.write_failed: runId={} '{}' — {}", runId, pathOrKey, e.toString())
            0L
        }
    }

    /**
     * Сводка прогона (FR-036).
     */
    fun runSummary(
        runId: Long,
        database: KaraokeConnection = WORKING_DATABASE,
    ): RunSummary? {
        val connection = database.getConnection() ?: return null
        return try {
            connection
                .prepareStatement("SELECT * FROM $TABLE_RUNS WHERE id = ?")
                .use { ps ->
                    ps.setLong(1, runId)
                    ps.executeQuery().use { rs ->
                        if (!rs.next()) {
                            null
                        } else {
                            RunSummary(
                                runId = rs.getLong("id"),
                                songId = rs.getLong("song_id"),
                                songName = rs.getString("song_name") ?: "",
                                author = rs.getString("author") ?: "",
                                album = rs.getString("album") ?: "",
                                rootFolder = rs.getString("root_folder") ?: "",
                                fileName = rs.getString("file_name") ?: "",
                                year = rs.getInt("song_year"),
                                albumId = rs.getLong("album_id"),
                                runKind = CleanupRunKind.valueOf(rs.getString("run_kind")),
                                state = CleanupRunState.valueOf(rs.getString("state")),
                                initiator = rs.getString("initiator") ?: "",
                                startedAt = rs.getTimestamp("started_at")?.toString(),
                                finishedAt = rs.getTimestamp("finished_at")?.toString(),
                                filesTotal = rs.getInt("files_total"),
                                filesDeleted = rs.getInt("files_deleted"),
                                filesKept = rs.getInt("files_kept"),
                                filesFailed = rs.getInt("files_failed"),
                                objectsTotal = rs.getInt("objects_total"),
                                objectsDeleted = rs.getInt("objects_deleted"),
                                objectsKept = rs.getInt("objects_kept"),
                                objectsFailed = rs.getInt("objects_failed"),
                                bytesDeleted = rs.getLong("bytes_deleted"),
                                error = rs.getString("error"),
                            )
                        }
                    }
                }
        } catch (e: Exception) {
            log.warn("song.cleanup.run.read_failed: runId={} — {}", runId, e.toString())
            null
        }
    }

    /**
     * Список прогонов (FR-036).
     *
     * @param songId фильтр по песне (`null` — без фильтра).
     * @param limit сколько вернуть; @param offset смещение.
     */
    fun listRuns(
        songId: Long? = null,
        limit: Int = 50,
        offset: Int = 0,
        database: KaraokeConnection = WORKING_DATABASE,
    ): List<RunSummary> {
        val connection = database.getConnection() ?: return emptyList()
        return try {
            val where = if (songId != null) " WHERE song_id = ?" else ""
            val sql = "SELECT id FROM $TABLE_RUNS$where ORDER BY id DESC LIMIT ? OFFSET ?"
            connection.prepareStatement(sql).use { ps ->
                var index = 1
                if (songId != null) ps.setLong(index++, songId)
                ps.setInt(index++, limit)
                ps.setInt(index, offset)
                ps.executeQuery().use { rs ->
                    val ids = mutableListOf<Long>()
                    while (rs.next()) ids.add(rs.getLong("id"))
                    ids.mapNotNull { runSummary(it, database) }
                }
            }
        } catch (e: Exception) {
            log.warn("song.cleanup.run.list_failed: — {}", e.toString())
            emptyList()
        }
    }

    /**
     * Все записи журнала по прогону (FR-035).
     */
    fun listEntries(
        runId: Long,
        database: KaraokeConnection = WORKING_DATABASE,
    ): List<Entry> {
        val connection = database.getConnection() ?: return emptyList()
        return try {
            val sql = "SELECT * FROM $TABLE_ENTRIES WHERE run_id = ? ORDER BY id"
            connection.prepareStatement(sql).use { ps ->
                ps.setLong(1, runId)
                ps.executeQuery().use { rs ->
                    val result = mutableListOf<Entry>()
                    while (rs.next()) {
                        result.add(
                            Entry(
                                id = rs.getLong("id"),
                                runId = rs.getLong("run_id"),
                                kind = EntryKind.valueOf(rs.getString("kind")),
                                location = EntryLocation.valueOf(rs.getString("location")),
                                pathOrKey = rs.getString("path_or_key") ?: "",
                                sizeBytes = rs.getLong("size_bytes"),
                                result = EntryResult.valueOf(rs.getString("result")),
                                reason = rs.getString("reason"),
                                attempt = rs.getInt("attempt"),
                                createdAt = rs.getTimestamp("created_at")?.toString(),
                                initiator = rs.getString("initiator") ?: "",
                            ),
                        )
                    }
                    result
                }
            }
        } catch (e: Exception) {
            log.warn("song.cleanup.entry.list_failed: runId={} — {}", runId, e.toString())
            emptyList()
        }
    }

    /**
     * Счётчики отказов прогона — по ним принимается решение о `DONE`/`PARTIAL` (SC-003).
     */
    fun failureCounters(
        runId: Long,
        database: KaraokeConnection = WORKING_DATABASE,
    ): Pair<Int, Int> {
        val connection = database.getConnection() ?: return 0 to 0
        return try {
            val sql =
                "SELECT count(*) FILTER (WHERE result = 'FAILED' AND kind = 'FILE') AS files_failed, " +
                    "count(*) FILTER (WHERE result = 'FAILED' AND kind = 'STORAGE_OBJECT') AS objects_failed " +
                    "FROM $TABLE_ENTRIES WHERE run_id = ?"
            connection.prepareStatement(sql).use { ps ->
                ps.setLong(1, runId)
                ps.executeQuery().use { rs ->
                    if (rs.next()) rs.getInt("files_failed") to rs.getInt("objects_failed") else 0 to 0
                }
            }
        } catch (e: Exception) {
            log.warn("song.cleanup.counters_failed: runId={} — {}", runId, e.toString())
            0 to 0
        }
    }

    /**
     * Есть ли прогон удаления для песни (маркер «удаление уже состоялось» — идемпотентность).
     */
    fun hasDeleteRun(
        songId: Long,
        database: KaraokeConnection = WORKING_DATABASE,
    ): Boolean {
        val connection = database.getConnection() ?: return false
        return try {
            val sql =
                "SELECT count(*) FROM $TABLE_RUNS WHERE song_id = ? AND run_kind = ? AND state <> ?"
            connection.prepareStatement(sql).use { ps ->
                ps.setLong(1, songId)
                ps.setString(2, CleanupRunKind.SONG_DELETE.name)
                ps.setString(3, CleanupRunState.PLANNED.name)
                ps.executeQuery().use { rs ->
                    if (rs.next()) rs.getInt(1) > 0 else false
                }
            }
        } catch (e: Exception) {
            log.warn("song.cleanup.run.lookup_failed: songId={} — {}", songId, e.toString())
            false
        }
    }

    /**
     * Удалить прогон (используется, если фаза A не состоялась — прогон не должен
     * оставаться в журнале как «удаление было»).
     */
    fun deleteRun(
        runId: Long,
        database: KaraokeConnection = WORKING_DATABASE,
    ): Boolean {
        val connection = database.getConnection() ?: return false
        return try {
            connection.prepareStatement("DELETE FROM $TABLE_RUNS WHERE id = ?").use { ps ->
                ps.setLong(1, runId)
                ps.executeUpdate()
            }
            true
        } catch (e: Exception) {
            log.warn("song.cleanup.run.delete_failed: runId={} — {}", runId, e.toString())
            false
        }
    }

    /**
     * Удалить прогон вместе с его записями.
     *
     * Порядок важен: записи журнала ссылаются на прогон (FK), поэтому сначала
     * удаляются записи, потом сам прогон.
     */
    fun deleteRunWithEntries(
        runId: Long,
        database: KaraokeConnection = WORKING_DATABASE,
    ): Boolean {
        val connection = database.getConnection() ?: return false
        return try {
            connection.prepareStatement("DELETE FROM $TABLE_ENTRIES WHERE run_id = ?").use { ps ->
                ps.setLong(1, runId)
                ps.executeUpdate()
            }
            connection.prepareStatement("DELETE FROM $TABLE_RUNS WHERE id = ?").use { ps ->
                ps.setLong(1, runId)
                ps.executeUpdate()
            }
            true
        } catch (e: Exception) {
            log.warn("song.cleanup.run.delete_failed: runId={} — {}", runId, e.toString())
            false
        }
    }

    /** Текущее время БД — для отметок в журнале. */
    fun now(): Timestamp = Timestamp.valueOf(java.time.LocalDateTime.now())
}
