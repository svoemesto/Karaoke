package com.svoemesto.karaokeapp.services.cleanup

import com.svoemesto.karaokeapp.KaraokeProperties
import com.svoemesto.karaokeapp.runCommand
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Одна строка журнала удалений (FR-012, `specs/544-song-artifact-cleanup/data-model.md` §4).
 *
 * @param result `deleted` для успешного удаления, `failed:<причина>` для неудачного —
 *   журнал фиксирует и неудачи, чтобы обрыв процесса не оставлял «слепое» удаление.
 * @see specs/544-song-artifact-cleanup/data-model.md
 */
data class CleanupJournalRecord(
    val path: String,
    val sizeBytes: Long,
    val classId: String,
    val result: String,
    val deletedAt: String,
    val approvedBy: String,
    val runId: String,
)

/**
 * Журнал удалений артефактов песни: TSV-файл
 * `/sm-karaoke/system/cleanup-logs/<YYYY-MM-DD>-<classId>.tsv`
 * (FR-012; `specs/544-song-artifact-cleanup/data-model.md` §4).
 *
 * Таблица БД не заводится — журнал живёт файлом (Constitution II/III).
 * Строка дописывается сразу после обработки файла (write-behind на каждый файл),
 * поэтому журнал всегда полнее фактического состояния, но никогда не беднее.
 *
 * @param journalDir каталог журнала; по умолчанию берётся из `cleanupJournalDir`
 *   (`KaraokeProperties`), с жёстким fallback на `/sm-karaoke/system/cleanup-logs`.
 * @see specs/544-song-artifact-cleanup/contracts/cleanup-api.md
 */
class CleanupJournal(
    val journalDir: String = defaultJournalDir(),
) {
    /**
     * Путь журнала для класса и даты.
     *
     * @param classId идентификатор класса из [ArtifactCleanupClass].
     * @param date дата прогона (по умолчанию — сегодня).
     */
    fun journalPath(
        classId: String,
        date: LocalDate = LocalDate.now(),
    ): Path = Path.of(journalDir, "${date.format(DateTimeFormatter.ISO_LOCAL_DATE)}-$classId.tsv")

    /** Каталог журнала существует или создан и доступен для записи. */
    fun journalDirWritable(): Boolean =
        try {
            val dir = File(journalDir)
            if (!dir.exists()) dir.mkdirs()
            dir.isDirectory && dir.canWrite()
        } catch (_: Exception) {
            false
        }

    /**
     * Дописать одну запись в журнал класса.
     *
     * Заголовок TSV пишется один раз при создании файла. Значения санитизируются:
     * табуляции и переводы строк в путях ломали бы формат.
     *
     * @return `true`, если запись добавлена; `false` — если каталог недоступен
     *   или запись не удалась (вызывающая сторона отвечает `503`/считает прогон `FAILED`).
     */
    fun append(record: CleanupJournalRecord): Boolean {
        if (!journalDirWritable()) return false
        val path = journalPath(record.classId)
        return try {
            val isNew = !Files.exists(path)
            val line =
                buildString {
                    if (isNew) append(HEADER_LINE)
                    append(formatRecord(record))
                }
            Files.write(
                path,
                line.toByteArray(Charsets.UTF_8),
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND,
            )
            if (isNew) runCommand(listOf("chmod", "666", path.toString()), ignoreErrors = true)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Удобная обёртка: записать успешное удаление пути. */
    fun appendDeleted(
        path: String,
        sizeBytes: Long,
        classId: String,
        approvedBy: String,
        runId: String,
        deletedAt: LocalDateTime = LocalDateTime.now(),
    ): Boolean =
        append(
            CleanupJournalRecord(
                path = path,
                sizeBytes = sizeBytes,
                classId = classId,
                result = "deleted",
                deletedAt = formatDateTime(deletedAt),
                approvedBy = approvedBy,
                runId = runId,
            ),
        )

    /** Удобная обёртка: записать неудачное удаление пути. */
    fun appendFailed(
        path: String,
        sizeBytes: Long,
        classId: String,
        approvedBy: String,
        runId: String,
        reason: String,
        deletedAt: LocalDateTime = LocalDateTime.now(),
    ): Boolean =
        append(
            CleanupJournalRecord(
                path = path,
                sizeBytes = sizeBytes,
                classId = classId,
                result = "failed:${sanitize(reason)}",
                deletedAt = formatDateTime(deletedAt),
                approvedBy = approvedBy,
                runId = runId,
            ),
        )

    private fun formatRecord(record: CleanupJournalRecord): String =
        listOf(
            sanitize(record.path),
            record.sizeBytes.toString(),
            sanitize(record.classId),
            sanitize(record.result),
            sanitize(record.deletedAt),
            sanitize(record.approvedBy),
            sanitize(record.runId),
        ).joinToString("\t") + "\n"

    private fun sanitize(value: String): String =
        value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')

    private fun formatDateTime(value: LocalDateTime): String = value.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

    companion object {
        const val DEFAULT_JOURNAL_DIR = "/sm-karaoke/system/cleanup-logs"
        const val HEADER = "path\tsizeBytes\tclassId\tresult\tdeletedAt\tapprovedBy\trunId"
        private const val HEADER_LINE = "$HEADER\n"

        /** Каталог журнала из настроек, с безопасным fallback. */
        fun defaultJournalDir(): String =
            try {
                KaraokeProperties.getString("cleanupJournalDir").ifEmpty { DEFAULT_JOURNAL_DIR }
            } catch (_: Exception) {
                DEFAULT_JOURNAL_DIR
            }

        /** Идентификатор прогона: `r-<YYYYMMDD>-<classId>`. */
        fun newRunId(
            classId: String,
            date: LocalDate = LocalDate.now(),
        ): String = "r-${date.format(DateTimeFormatter.BASIC_ISO_DATE)}-$classId"
    }
}
