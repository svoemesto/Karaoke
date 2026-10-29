package com.svoemesto.karaokeapp.tuple

import com.svoemesto.karaokeapp.KaraokeProcessTypes
import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.WORKING_DATABASE
import com.svoemesto.karaokeapp.model.Song
import com.svoemesto.karaokeapp.services.KSS_APP
import com.svoemesto.karaokeapp.services.SAC_APP
import java.io.File

/**
 * Находка проверки содержимого артефактов песни. Правок не влечёт (FR-014).
 *
 * @see specs/674-kortege-execution/contracts/artifact-verification.md §6
 */
data class IntegrityFinding(
    val songId: Long,
    val kind: String,
    val artifact: String,
    val expected: String = "",
    val actual: String = "",
)

/**
 * Проверка «артефакт принадлежит этой песне и создан из её файла».
 *
 * Read-only: используется ежедневной проверкой целостности и dry-run ремонта.
 * Ничего не удаляет и не перегенерирует.
 *
 * @see specs/674-kortege-execution/spec.md FR-012, FR-014
 */
object ArtifactIntegrity {
    const val KIND_MISSING_STEM = "missingStem"
    const val KIND_FOREIGN_DURATION = "foreignDuration"
    const val KIND_STALE_ARTIFACT = "staleArtifact"
    const val KIND_MISSING_SIDECAR = "missingSidecar"

    /** 674: легаси `[key].json` без отпечатка — не ошибка и не «чужое», отдельная строка отчёта. */
    const val KIND_LEGACY_NO_SIDECAR = "legacyNoSidecar"
    const val KIND_SIDECAR_MISMATCH = "sidecarMismatch"
    const val KIND_EMPTY_KEY_BPM = "emptyKeyBpm"

    private const val TOLERANCE_MS = 500L

    fun verifySong(
        songId: Long,
        database: KaraokeConnection = WORKING_DATABASE,
    ): List<IntegrityFinding> {
        val song =
            Song.loadFromDbById(
                id = songId,
                database = database,
                sync = false,
                storageService = KSS_APP,
                storageApiClient = SAC_APP,
            ) ?: return emptyList()
        return verifySong(song)
    }

    /** Проверка одной загруженной песни (удобно для unit-тестов без БД). */
    fun verifySong(song: Song): List<IntegrityFinding> {
        val result = mutableListOf<IntegrityFinding>()
        val source = File(song.fileAbsolutePath)
        if (!source.exists()) return result
        val sourceDuration = AudioProbe.durationMs(song.fileAbsolutePath)
        val sourceMtime = source.lastModified()

        val expectedStems =
            buildList {
                add("accompaniment" to song.accompanimentNameFlac)
                add("vocals" to song.vocalsNameFlac)
                if (song.drumsNameFlac.isNotBlank()) add("drums" to song.drumsNameFlac)
                if (song.bassNameFlac.isNotBlank()) add("bass" to song.bassNameFlac)
                if (song.otherNameFlac.isNotBlank()) add("other" to song.otherNameFlac)
            }

        expectedStems.forEach { (name, path) ->
            if (path.isBlank()) return@forEach
            val stem = File(path)
            if (!stem.exists() || stem.length() == 0L) {
                if (name == "accompaniment" || name == "vocals" || stem.exists()) {
                    result += IntegrityFinding(song.id, KIND_MISSING_STEM, path)
                }
                return@forEach
            }
            if (sourceDuration != null) {
                val stemDuration = AudioProbe.durationMs(path)
                if (stemDuration == null || kotlin.math.abs(stemDuration - sourceDuration) > TOLERANCE_MS) {
                    result +=
                        IntegrityFinding(
                            songId = song.id,
                            kind = KIND_FOREIGN_DURATION,
                            artifact = path,
                            expected = "${sourceDuration}ms",
                            actual = "${stemDuration ?: "?"}ms",
                        )
                }
            }
            if (sourceMtime > 0 && stem.lastModified() < sourceMtime) {
                result += IntegrityFinding(song.id, KIND_STALE_ARTIFACT, path)
            }
        }

        val keyJson = File(song.pathToFileKeyBpmFinder)
        if (keyJson.exists()) {
            val sidecar = KeyBpmSidecar.read(song.pathToFileKeyBpmFinder)
            if (sidecar == null) {
                // Легаси без отпечатка: не считаем чужим и не правим; отдельная строка отчёта.
                result += IntegrityFinding(song.id, KIND_LEGACY_NO_SIDECAR, keyJson.absolutePath)
            } else if (!sidecar.matches(song.fileAbsolutePath, sourceDuration)) {
                result +=
                    IntegrityFinding(
                        songId = song.id,
                        kind = KIND_SIDECAR_MISMATCH,
                        artifact = keyJson.absolutePath,
                        expected = "source=${song.fileAbsolutePath}",
                        actual = "sidecarSource=${sidecar.sourcePath}",
                    )
            }
            val parsed = Song.parseKeyBpmFileOrNull(song.pathToFileKeyBpmFinder)
            if (parsed == null || parsed.key.isNullOrBlank() || (parsed.bpm ?: 0) <= 0) {
                result += IntegrityFinding(song.id, KIND_EMPTY_KEY_BPM, keyJson.absolutePath)
            }
        }
        return result
    }

    /** Снимок стема для чистой проверки (инъекция в тестах вместо ffprobe/File). */
    data class StemProbe(
        val path: String,
        val exists: Boolean,
        val size: Long,
        val durationMs: Long?,
        val lastModified: Long,
    )

    /**
     * Чистая классификация стемов: пустой список — всё подтверждено.
     * Допуск 500 мс и свежесть относительно источника — контракт FR-008.
     */
    fun classifyStems(
        stems: List<StemProbe>,
        sourceDurationMs: Long?,
        sourceMtime: Long,
    ): List<IntegrityFinding> {
        val result = mutableListOf<IntegrityFinding>()
        stems.forEach { stem ->
            if (!stem.exists || stem.size == 0L) {
                result += IntegrityFinding(0, KIND_MISSING_STEM, stem.path)
                return@forEach
            }
            if (sourceDurationMs == null) {
                result += IntegrityFinding(0, KIND_FOREIGN_DURATION, stem.path, "source unreadable")
                return@forEach
            }
            if (stem.durationMs == null || kotlin.math.abs(stem.durationMs - sourceDurationMs) > TOLERANCE_MS) {
                result +=
                    IntegrityFinding(
                        0,
                        KIND_FOREIGN_DURATION,
                        stem.path,
                        "${sourceDurationMs}ms",
                        "${stem.durationMs ?: "?"}ms",
                    )
            }
            if (sourceMtime > 0 && stem.lastModified < sourceMtime) {
                result += IntegrityFinding(0, KIND_STALE_ARTIFACT, stem.path)
            }
        }
        return result
    }

    /** Проверка «стемы этого кортежа подтверждены» (используется финализатором демукса). */
    fun verifyDemucsStems(
        song: Song,
        demucsType: KaraokeProcessTypes,
        sourceDurationMs: Long?,
    ): List<IntegrityFinding> {
        val source = File(song.fileAbsolutePath)
        if (!source.exists()) {
            return listOf(IntegrityFinding(song.id, KIND_FOREIGN_DURATION, song.fileAbsolutePath, "source missing"))
        }
        val paths =
            buildList {
                add(song.accompanimentNameFlac)
                add(song.vocalsNameFlac)
                if (demucsType == KaraokeProcessTypes.DEMUCS5) {
                    add(song.drumsNameFlac)
                    add(song.bassNameFlac)
                    add(song.otherNameFlac)
                }
            }
        val probes =
            paths.map { path ->
                val file = File(path)
                StemProbe(
                    path = path,
                    exists = file.exists(),
                    size = if (file.exists()) file.length() else 0L,
                    durationMs = if (file.exists()) AudioProbe.durationMs(path) else null,
                    lastModified = if (file.exists()) file.lastModified() else 0L,
                )
            }
        return classifyStems(probes, sourceDurationMs, source.lastModified())
            .map { it.copy(songId = song.id) }
    }

    /** Отчёт в файл `PATH_TO_LOGS/artifact-integrity/<date>.json` (без правок артефактов). */
    fun writeReport(
        findings: List<IntegrityFinding>,
        generatedAt: String,
        path: File,
    ): Boolean =
        try {
            path.parentFile?.mkdirs()
            val body =
                buildString {
                    append("{\"generatedAt\":\"").append(generatedAt).append("\",")
                    append("\"findingsTotal\":").append(findings.size).append(",\"findings\":[")
                    append(
                        findings.joinToString(",") { f ->
                            "{\"songId\":${f.songId},\"kind\":\"${f.kind}\",\"artifact\":\"${escape(f.artifact)}\"," +
                                "\"expected\":\"${escape(f.expected)}\",\"actual\":\"${escape(f.actual)}\"}"
                        },
                    )
                    append("]}")
                }
            path.writeText(body)
            true
        } catch (e: Exception) {
            false
        }

    private fun escape(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")
}
