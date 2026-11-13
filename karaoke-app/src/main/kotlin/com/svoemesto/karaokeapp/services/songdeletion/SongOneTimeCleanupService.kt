package com.svoemesto.karaokeapp.services.songdeletion

import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.WORKING_DATABASE
import com.svoemesto.karaokeapp.services.KSS_APP
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.SAC_APP
import com.svoemesto.karaokeapp.services.StorageApiClient
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Применение разовой уборки по УТВЕРЖДЁННОМУ набору (KARAOKE-429, спека 739, US4).
 *
 * **Протокол утверждения (FR-040).** Уборка выполняется ТОЛЬКО по отпечатку набора,
 * который владелец утвердил. Если состав набора изменился (кандидат появился или
 * исчез) — уборка отказывается выполняться и требует нового утверждения: молча
 * удалять другой набор нельзя.
 *
 * Каждое удаление фиксируется в журнале с `run_kind = ONE_TIME_CLEANUP` (FR-043).
 *
 * @see specs/739-song-delete-cascade/contracts/song-delete-api.md § 1.1
 */
class SongOneTimeCleanupService(
    private val database: KaraokeConnection = WORKING_DATABASE,
    private val storageService: KaraokeStorageService = KSS_APP,
    private val storageApiClient: StorageApiClient = SAC_APP,
) {
    companion object {
        /** Категория логов разовой уборки. */
        const val CATEGORY = "infra.song.delete"

        private val log = LoggerFactory.getLogger(CATEGORY)

        /**
         * Удалить файл с диска — **сам симлинк**, а не его цель.
         *
         * `File.exists()` идёт ПО ССЫЛКЕ: битый симлинк (цель уже удалена) даёт
         * `exists() = false`, и проверка «`!file.exists() || file.delete()`» короткозамыкает —
         * симлинк помечался удалённым, но оставался на диске (инцидент 2026-11-13,
         * прогон №210: 60 битых симлинков в `symlink_sponsr`).
         *
         * `Files.deleteIfExists` удаляет сам симлинк и не различает «уже удалено» и
         * «нечего удалять» — оба случая считаются успехом.
         *
         * @param path путь к файлу или симлинку.
         * @return `true`, если файл удалён или его уже не было.
         */
        internal fun deleteLocalFile(path: String): Boolean =
            try {
                Files.deleteIfExists(Paths.get(path))
                true
            } catch (e: Exception) {
                false
            }
    }

    /** Итог применения. */
    data class ApplyResult(
        val runId: Long,
        val applied: Boolean,
        val deletedCount: Int,
        val failedCount: Int,
        val bytesDeleted: Long,
        val reason: String?,
    )

    /**
     * Применить уборку по утверждённому отпечатку.
     *
     * @param approvedFingerprint отпечаток набора, утверждённый владельцем.
     * @param initiator кто утвердил.
     * @param planner планировщик (пересобирает перечень для сверки отпечатка).
     */
    fun apply(
        approvedFingerprint: String,
        initiator: String,
        planner: SongOneTimeCleanupPlanner = SongOneTimeCleanupPlanner(),
    ): ApplyResult {
        if (approvedFingerprint.isBlank()) {
            return ApplyResult(0, applied = false, deletedCount = 0, failedCount = 0, bytesDeleted = 0, reason = "not_confirmed")
        }
        val plan = planner.plan()
        if (plan.fingerprint != approvedFingerprint) {
            log.warn(
                "event=song.cleanup.one_time.set_changed code=SONG-CLEANUP-010 expected={} actual={}",
                approvedFingerprint,
                plan.fingerprint,
            )
            return ApplyResult(0, applied = false, deletedCount = 0, failedCount = 0, bytesDeleted = 0, reason = "one_time_set_changed")
        }
        val runId =
            SongCleanupJournal.createRun(
                songId = 0,
                songName = "Разовая уборка",
                author = "",
                album = "",
                rootFolder = "",
                runKind = CleanupRunKind.ONE_TIME_CLEANUP,
                initiator = initiator,
                database = database,
            )
        if (runId <= 0L) {
            return ApplyResult(0, applied = false, deletedCount = 0, failedCount = 0, bytesDeleted = 0, reason = "db_error")
        }
        // Прогон создан в состоянии PLANNED, а машина состояний допускает PLANNED → APPROVED →
        // RUNNING → DONE/PARTIAL. Совпадение отпечатка с утверждённым — это и есть утверждение
        // набора владельцем, поэтому сначала APPROVED. Без этого переход PLANNED → RUNNING
        // отвергался, и завершённый прогон оставался в состоянии PLANNED без finished_at
        // (инцидент 2026-11-13, прогон №210).
        SongCleanupJournal.updateRunState(runId, CleanupRunState.APPROVED, database)
        SongCleanupJournal.updateRunState(runId, CleanupRunState.RUNNING, database)

        var deleted = 0
        var failed = 0
        var bytes = 0L
        plan.candidates.forEach { candidate ->
            val result = deleteCandidate(runId, candidate, initiator)
            when (result.first) {
                true -> {
                    deleted++
                    bytes += result.second
                }
                else -> failed++
            }
        }
        SongCleanupJournal.updateCounters(
            runId = runId,
            filesTotal = plan.candidates.size,
            filesDeleted = deleted,
            filesFailed = failed,
            objectsTotal = plan.candidates.size,
            objectsDeleted = deleted,
            objectsFailed = failed,
            bytesDeleted = bytes,
            database = database,
        )
        val state = CleanupRunStateMachine.terminalState(failed > 0)
        SongCleanupJournal.updateRunState(runId, state, database)
        return ApplyResult(runId, applied = true, deletedCount = deleted, failedCount = failed, bytesDeleted = bytes, reason = null)
    }

    /** Удаление одного кандидата с записью в журнал (FR-043). */
    private fun deleteCandidate(
        runId: Long,
        candidate: SongOneTimeCleanupPlanner.Candidate,
        initiator: String,
    ): Pair<Boolean, Long> =
        try {
            when (candidate.location) {
                SongOneTimeCleanupPlanner.CandidateLocation.LOCAL_STORAGE -> {
                    storageService.deleteFile(SongOneTimeCleanupPlanner.BUCKET, candidate.pathOrKey)
                    true to candidate.sizeBytes
                }
                SongOneTimeCleanupPlanner.CandidateLocation.REMOTE_STORAGE -> {
                    storageApiClient.deleteFile(SongOneTimeCleanupPlanner.BUCKET, candidate.pathOrKey).block()
                    true to candidate.sizeBytes
                }
                SongOneTimeCleanupPlanner.CandidateLocation.LOCAL_FILESYSTEM -> {
                    val ok = deleteLocalFile(candidate.pathOrKey)
                    if (!ok) {
                        log.warn(
                            "event=song.cleanup.one_time.delete_failed code=SONG-CLEANUP-011 key='{}' — {}",
                            candidate.pathOrKey,
                            "не удалось удалить файл",
                        )
                    }
                    ok to candidate.sizeBytes
                }
                SongOneTimeCleanupPlanner.CandidateLocation.DATABASE -> true to 0L
            }.also { (ok, size) ->
                SongCleanupJournal.addEntry(
                    runId = runId,
                    kind =
                        if (candidate.kind == SongOneTimeCleanupPlanner.CandidateKind.FILE) {
                            SongCleanupJournal.EntryKind.FILE
                        } else {
                            SongCleanupJournal.EntryKind.STORAGE_OBJECT
                        },
                    location =
                        when (candidate.location) {
                            SongOneTimeCleanupPlanner.CandidateLocation.LOCAL_STORAGE -> SongCleanupJournal.EntryLocation.LOCAL_STORAGE
                            SongOneTimeCleanupPlanner.CandidateLocation.REMOTE_STORAGE -> SongCleanupJournal.EntryLocation.REMOTE_STORAGE
                            SongOneTimeCleanupPlanner.CandidateLocation.LOCAL_FILESYSTEM -> SongCleanupJournal.EntryLocation.LOCAL_FILESYSTEM
                            SongOneTimeCleanupPlanner.CandidateLocation.DATABASE -> SongCleanupJournal.EntryLocation.DATABASE
                        },
                    pathOrKey = candidate.pathOrKey,
                    sizeBytes = size,
                    result =
                        if (ok) {
                            SongCleanupJournal.EntryResult.DELETED
                        } else {
                            SongCleanupJournal.EntryResult.FAILED
                        },
                    reason = if (ok) null else "delete_failed",
                    attempt = 1,
                    initiator = initiator,
                    database = database,
                )
            }
        } catch (e: Exception) {
            log.warn(
                "event=song.cleanup.one_time.delete_failed code=SONG-CLEANUP-011 key='{}' — {}",
                candidate.pathOrKey,
                e.toString(),
            )
            SongCleanupJournal.addEntry(
                runId = runId,
                kind = SongCleanupJournal.EntryKind.STORAGE_OBJECT,
                location = SongCleanupJournal.EntryLocation.LOCAL_STORAGE,
                pathOrKey = candidate.pathOrKey,
                sizeBytes = candidate.sizeBytes,
                result = SongCleanupJournal.EntryResult.FAILED,
                reason = "delete_failed",
                attempt = 1,
                initiator = initiator,
                database = database,
            )
            false to 0L
        }

    /**
     * Сводка последнего прохода разовой уборки (FR-043, SC-007).
     *
     * @return прогон и сумма освобождённого объёма; `null`, если проходов не было.
     */
    fun status(): SongCleanupJournal.RunSummary? =
        SongCleanupJournal
            .listRuns(limit = 200, database = database)
            .firstOrNull { it.runKind == CleanupRunKind.ONE_TIME_CLEANUP }

    /**
     * Суммарный освобождённый объём по всем проходам разовой уборки (SC-007).
     */
    fun totalBytesDeleted(): Long =
        SongCleanupJournal
            .listRuns(limit = 1000, database = database)
            .filter { it.runKind == CleanupRunKind.ONE_TIME_CLEANUP }
            .sumOf { it.bytesDeleted }
}
