package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.KaraokeProperties
import com.svoemesto.karaokeapp.PATH_TO_LOGS
import com.svoemesto.karaokeapp.WORKING_DATABASE
import com.svoemesto.karaokeapp.tuple.ArtifactIntegrity
import com.svoemesto.karaokeapp.tuple.IntegrityFinding
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.io.File
import java.time.Instant
import java.time.LocalDate

/** Отчёт ежедневной проверки целостности (read-only). */
data class IntegrityReport(
    val generatedAt: String,
    val scannedSongs: Int,
    val findings: List<IntegrityFinding>,
    val reportPath: String,
)

/**
 * 674 (FR-014): ежедневная проверка целостности артефактов.
 *
 * Только сообщает: артефакты не правит, заданий не ставит. Выключается
 * `queue.integrity.enabled=false` (по умолчанию выключена).
 *
 * @see specs/674-kortege-execution/contracts/artifact-verification.md §6
 */
@Component
class ArtifactIntegrityScheduler {
    @Volatile
    private var report: IntegrityReport? = null

    /** Последний отчёт (read-only для контроллера). */
    val lastReport: IntegrityReport?
        get() = report

    @Scheduled(cron = "\${karaoke.integrity.cron:0 0 4 * * *}")
    fun scheduled() {
        if (!KaraokeProperties.getBoolean("queue.integrity.enabled")) return
        runCatching { runOnce() }
            .onFailure { println("[ArtifactIntegrity] проверка не выполнена: ${it.message}") }
    }

    /** Один прогон (для теста и ручного запуска); ничего не меняет в артефактах. */
    fun runOnce(database: KaraokeConnection = WORKING_DATABASE): IntegrityReport {
        val songIds = collectSongIds(database)
        val findings = songIds.flatMap { ArtifactIntegrity.verifySong(it, database) }
        val generatedAt = Instant.now().toString()
        val date = LocalDate.now().toString()
        val reportFile = File("$PATH_TO_LOGS/artifact-integrity/$date.json")
        ArtifactIntegrity.writeReport(findings, generatedAt, reportFile)
        val report =
            IntegrityReport(
                generatedAt = generatedAt,
                scannedSongs = songIds.size,
                findings = findings,
                reportPath = reportFile.absolutePath,
            )
        this.report = report
        println(
            "[ArtifactIntegrity] песен проверено: ${report.scannedSongs}, находок: ${findings.size}, " +
                "отчёт: ${report.reportPath}",
        )
        return report
    }

    private fun collectSongIds(database: KaraokeConnection): List<Long> {
        val connection = database.getConnection() ?: return emptyList()
        return try {
            connection
                .prepareStatement(
                    "SELECT DISTINCT song_id FROM tbl_processes " +
                        "WHERE process_type IN ('DEMUCS2', 'DEMUCS5', 'KEY_BPM_FROM_FILE') " +
                        "AND process_status = 'DONE' AND song_id IS NOT NULL ORDER BY song_id LIMIT 5000",
                ).use { ps ->
                    ps.executeQuery().use { rs ->
                        val ids = mutableListOf<Long>()
                        while (rs.next()) ids += rs.getLong(1)
                        ids
                    }
                }
        } catch (e: Exception) {
            println("[ArtifactIntegrity] не удалось получить список песен: ${e.message}")
            emptyList()
        }
    }
}
