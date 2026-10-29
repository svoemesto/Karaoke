package com.svoemesto.karaokeapp.tuple

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Измерение длительности аудиофайла через `ffprobe`.
 *
 * Отличие от `Song.refreshDurationMsFromAudioFile`: тот возвращает закэшированное `ms` из БД,
 * а проверке содержимого нужна фактическая длительность **каждого** файла (источника и стема).
 * Ошибка измерения — `null` (fail-closed), а не `0`.
 *
 * @see specs/674-kortege-execution/contracts/artifact-verification.md §2
 */
object AudioProbe {
    private const val TIMEOUT_SECONDS = 30L

    /** Фактическая длительность в миллисекундах или `null`, если файл/ffprobe недоступны. */
    fun durationMs(path: String): Long? {
        if (path.isBlank() || !File(path).exists()) return null
        return try {
            val process =
                ProcessBuilder(
                    "ffprobe",
                    "-v",
                    "error",
                    "-show_entries",
                    "format=duration",
                    "-of",
                    "default=noprint_wrappers=1:nokey=1",
                    path,
                ).redirectErrorStream(true)
                    .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return null
            }
            if (process.exitValue() != 0) return null
            val seconds = output.trim().toDoubleOrNull() ?: return null
            if (seconds <= 0.0) null else (seconds * 1000.0).toLong()
        } catch (e: Exception) {
            null
        }
    }
}
