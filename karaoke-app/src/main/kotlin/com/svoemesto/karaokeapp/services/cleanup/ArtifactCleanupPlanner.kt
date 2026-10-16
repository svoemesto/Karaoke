package com.svoemesto.karaokeapp.services.cleanup

import com.svoemesto.karaokeapp.KaraokeFileType
import com.svoemesto.karaokeapp.KaraokeProperties
import com.svoemesto.karaokeapp.PROJECT_ROOT_FOLDERS
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.streams.asSequence

/**
 * Одна запись отчёта сухого прогона.
 *
 * @param path абсолютный путь файла, который будет удалён;
 * @param sizeBytes размер до удаления;
 * @param reason почему класс подлежит удалению (для владельца, утверждающего набор).
 * @see specs/544-song-artifact-cleanup/data-model.md
 */
data class CleanupPlanEntry(
    val path: String,
    val sizeBytes: Long,
    val reason: String,
)

/**
 * Отчёт сухого прогона по классу (`data-model.md` §3).
 *
 * Владелец утверждает **именно этот** набор файлов: отпечаток [fingerprint] связывает
 * утверждение с конкретным составом (путь + размер), поэтому изменение состава между
 * `plan` и `apply` приводит к отказу.
 *
 * @see specs/544-song-artifact-cleanup/contracts/cleanup-api.md
 */
data class CleanupPlan(
    val planId: String,
    val classId: String,
    val generatedAt: String,
    val entries: List<CleanupPlanEntry>,
    val warnings: List<String>,
) {
    val fileCount: Long get() = entries.size.toLong()

    val totalBytes: Long get() = entries.sumOf { it.sizeBytes }

    /**
     * Отпечаток набора: sha256 по строкам «путь + размер», отсортированным по пути.
     * Устойчив к порядку обхода каталога, чувствителен к составу и размерам.
     */
    val fingerprint: String by lazy {
        val digest = MessageDigest.getInstance("SHA-256")
        entries
            .sortedBy { it.path }
            .forEach { entry ->
                digest.update(entry.path.toByteArray(Charsets.UTF_8))
                digest.update('\t'.code.toByte())
                digest.update(entry.sizeBytes.toString().toByteArray(Charsets.UTF_8))
                digest.update('\n'.code.toByte())
            }
        "sha256:" + digest.digest().joinToString("") { "%02x".format(it) }
    }
}

/**
 * Планировщик разовой чистки: сухой прогон по классу артефактов (FR-011, FR-013).
 *
 * **Ни один вызов этого класса не удаляет файлы** — планировщик только читает каталоги и
 * строит отчёт. Удаление выполняет [ArtifactCleanupService] по утверждённому отчёту.
 *
 * @param roots корни обхода; по умолчанию — рабочие каталоги admin-машины
 *   (`PROJECT_ROOT_FOLDERS`: work/done1/done2).
 * @see specs/544-song-artifact-cleanup/data-model.md
 */
class ArtifactCleanupPlanner(
    private val roots: List<String> = PROJECT_ROOT_FOLDERS,
) {
    /**
     * Построить отчёт сухого прогона по классу.
     *
     * @throws IllegalArgumentException если класс защищён или не участвует в разовой чистке.
     */
    fun plan(classId: ArtifactCleanupClass): CleanupPlan {
        require(!classId.isProtected) { "класс ${classId.name} защищён и не может быть удалён (FR-010)" }
        require(classId.isDeletable) { "класс ${classId.name} не участвует в разовой чистке" }

        val entries = mutableListOf<CleanupPlanEntry>()
        roots
            .map { File(it) }
            .filter { it.isDirectory }
            .forEach { root -> collect(root, classId, entries) }

        val warnings = volumeWarnings(classId, entries)
        return CleanupPlan(
            planId = UUID.randomUUID().toString(),
            classId = classId.name,
            generatedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            entries = entries.sortedBy { it.path },
            warnings = warnings,
        )
    }

    private fun collect(
        root: File,
        classId: ArtifactCleanupClass,
        into: MutableList<CleanupPlanEntry>,
    ) {
        val rootPath = root.toPath()
        try {
            Files.walk(rootPath).use { stream ->
                stream.asSequence().forEach { path ->
                    if (matches(path, classId)) {
                        into.add(
                            CleanupPlanEntry(
                                path = path.toString(),
                                sizeBytes = sizeOf(path),
                                reason = classId.note,
                            ),
                        )
                    }
                }
            }
        } catch (e: Exception) {
            println("[cleanup] план ${classId.name}: обход '$root' прерван: ${e.message}")
        }
    }

    private fun matches(
        path: Path,
        classId: ArtifactCleanupClass,
    ): Boolean {
        val fileName = path.fileName?.toString() ?: return false
        val parentName = path.parent?.fileName?.toString() ?: return false
        val isLink = Files.isSymbolicLink(path)
        val exists = Files.exists(path)
        // Битая символическая ссылка не является regular file — обрабатывается отдельно (FR-015)
        if (isLink && !exists) {
            return ArtifactCleanupClass.SYMLINK_FOLDERS.contains(parentName) && classId == ArtifactCleanupClass.HYGIENE_JUNK
        }
        if (!Files.isRegularFile(path)) return false

        return classId.matchers.any { matcher ->
            parentMatches(matcher, parentName) &&
                extensionMatches(matcher, fileName) &&
                nameMatches(matcher, fileName) &&
                sizeMatches(matcher, path)
        }
    }

    private fun parentMatches(
        matcher: CleanupFileMatcher,
        parentName: String,
    ): Boolean =
        if (matcher.subFolder.isEmpty()) {
            // корень папки песни (там же, где исходный flac) — не известный подкаталог
            !ArtifactCleanupClass.KNOWN_SUBFOLDERS.contains(parentName)
        } else {
            matcher.subFolder == parentName
        }

    private fun extensionMatches(
        matcher: CleanupFileMatcher,
        fileName: String,
    ): Boolean = matcher.extensions.isEmpty() || matcher.extensions.any { fileName.endsWith(it) }

    private fun nameMatches(
        matcher: CleanupFileMatcher,
        fileName: String,
    ): Boolean {
        if (matcher.nameExactly.isNotEmpty() && fileName != matcher.nameExactly) return false
        if (matcher.nameContainsAny.isNotEmpty() && matcher.nameContainsAny.none { fileName.contains(it) }) return false
        return true
    }

    private fun sizeMatches(
        matcher: CleanupFileMatcher,
        path: Path,
    ): Boolean = matcher.maxBytes?.let { sizeOf(path) <= it } ?: true

    private fun sizeOf(path: Path): Long =
        try {
            Files.size(path)
        } catch (_: Exception) {
            0L
        }

    /**
     * Предупреждения отчёта: расхождение объёма/числа файлов с ожидаемым из реестра и
     * невыполненная предпосылка порядка работ для класса локальных mp3-стемов (FR-013).
     */
    private fun volumeWarnings(
        classId: ArtifactCleanupClass,
        entries: List<CleanupPlanEntry>,
    ): List<String> {
        val warnings = mutableListOf<String>()
        val count = entries.size.toLong()
        val bytes = entries.sumOf { it.sizeBytes }

        if (classId.expectedFileCount > 0 && count != classId.expectedFileCount) {
            warnings.add(
                "число файлов $count расходится с ожидаемым ${classId.expectedFileCount} " +
                    "(инвентаризация 2026-10-16)",
            )
        }
        if (classId.expectedBytes > 0) {
            val tolerance = tolerancePercent()
            val deviation = if (classId.expectedBytes == 0L) 0.0 else kotlin.math.abs(bytes - classId.expectedBytes) * 100.0 / classId.expectedBytes
            if (deviation > tolerance) {
                warnings.add(
                    "объём $bytes байт расходится с ожидаемым ${classId.expectedBytes} на " +
                        "${"%.1f".format(deviation)}% (допуск $tolerance%)",
                )
            }
        }
        if (classId == ArtifactCleanupClass.STEMS_MP3_LOCAL && KaraokeFileType.MP3_ACCOMPANIMENT.willBeInFileSystem) {
            warnings.add(
                "класс STEMS_MP3_LOCAL требует правки модели ожидаемых файлов " +
                    "(KaraokeFileType.locations без LOCAL_FILESYSTEM) — сначала код (FR-004/FR-013)",
            )
        }
        if (count == 0L) {
            warnings.add("по классу не найдено ни одного файла — удаление не имеет смысла")
        }
        return warnings
    }

    private fun tolerancePercent(): Int =
        try {
            KaraokeProperties.getInt("cleanupPlanVolumeTolerancePercent").let { if (it <= 0) DEFAULT_TOLERANCE_PERCENT else it }
        } catch (_: Exception) {
            DEFAULT_TOLERANCE_PERCENT
        }

    companion object {
        const val DEFAULT_TOLERANCE_PERCENT = 10
    }
}
