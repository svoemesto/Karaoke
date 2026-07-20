package com.svoemesto.karaokeweb.util

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

// Реальная (не мок) проверка: генерирует VBR mp3 через ffmpeg (как реально кодируются стемы —
// см. ApiController.pushMp3ToStorage, "-codec:a libmp3lame -qscale:a 2") и проверяет РЕАЛЬНУЮ
// длительность обрезанного файла через ffprobe — не полагается на внутреннюю логику самого
// Mp3Trimmer для проверки самого себя. Пропускается (не падает), если ffmpeg/ffprobe недоступны в
// окружении (по тому же паттерну, что и существующие "ручные" тесты karaoke-app,
// SearchLastAlbumVkTest/PlaywrightTests, которым нужны внешние живые зависимости).
class Mp3TrimmerTest {
    private fun toolAvailable(name: String): Boolean =
        try {
            ProcessBuilder(name, "-version").redirectErrorStream(true).start().waitFor() == 0
        } catch (e: Exception) {
            false
        }

    private fun probeDurationSeconds(file: File): Double {
        val proc =
            ProcessBuilder(
                "ffprobe",
                "-v",
                "error",
                "-show_entries",
                "format=duration",
                "-of",
                "default=noprint_wrappers=1:nokey=1",
                file.absolutePath,
            ).redirectErrorStream(true).start()
        val output =
            proc.inputStream
                .bufferedReader()
                .readText()
                .trim()
        proc.waitFor()
        return output.toDoubleOrNull() ?: error("ffprobe returned no duration: $output")
    }

    @Test
    fun `trims a real VBR mp3 to the requested end at a frame boundary`(
        @TempDir tmp: java.nio.file.Path,
    ) {
        assumeTrue(toolAvailable("ffmpeg"), "ffmpeg not available — skipping")
        assumeTrue(toolAvailable("ffprobe"), "ffprobe not available — skipping")

        val source = tmp.resolve("source.mp3").toFile()
        // Тот же кодек/пресет, что и у реальных стемов (VBR, не CBR) — см. ApiController.pushMp3ToStorage.
        val gen =
            ProcessBuilder(
                "ffmpeg",
                "-y",
                "-f",
                "lavfi",
                "-i",
                "sine=frequency=440:duration=20",
                "-codec:a",
                "libmp3lame",
                "-qscale:a",
                "2",
                source.absolutePath,
            ).redirectErrorStream(true).start()
        gen.waitFor()
        assertTrue(source.exists() && source.length() > 0, "ffmpeg failed to generate fixture")

        val sourceBytes = source.readBytes()
        val targetSeconds = 5.0
        val trimmedBytes = Mp3Trimmer.trimToRange(sourceBytes, 0.0, targetSeconds)
        assertTrue(trimmedBytes.size < sourceBytes.size, "trimmed output should be smaller than the 20s source")

        val trimmed = tmp.resolve("trimmed.mp3").toFile()
        trimmed.writeBytes(trimmedBytes)

        val actualDuration = probeDurationSeconds(trimmed)
        // Допуск: обрезка идёт по границе mp3-фрейма (~26мс на кадр при 44.1kHz), не секунды в секунду.
        assertTrue(actualDuration in 4.5..5.5, "expected ~${targetSeconds}s, got ${actualDuration}s")
    }

    @Test
    fun `trims a real VBR mp3 to a mid-track range at a frame boundary`(
        @TempDir tmp: java.nio.file.Path,
    ) {
        assumeTrue(toolAvailable("ffmpeg"), "ffmpeg not available — skipping")
        assumeTrue(toolAvailable("ffprobe"), "ffprobe not available — skipping")

        val source = tmp.resolve("source.mp3").toFile()
        val gen =
            ProcessBuilder(
                "ffmpeg",
                "-y",
                "-f",
                "lavfi",
                "-i",
                "sine=frequency=440:duration=20",
                "-codec:a",
                "libmp3lame",
                "-qscale:a",
                "2",
                source.absolutePath,
            ).redirectErrorStream(true).start()
        gen.waitFor()
        assertTrue(source.exists() && source.length() > 0, "ffmpeg failed to generate fixture")

        val sourceBytes = source.readBytes()
        // Демо-фрагмент "куплет минус 5 секунд отступа": начинается НЕ с нуля.
        val trimmedBytes = Mp3Trimmer.trimToRange(sourceBytes, 8.0, 13.0)
        assertTrue(trimmedBytes.isNotEmpty(), "mid-track range should not be empty")
        assertTrue(trimmedBytes.size < sourceBytes.size, "trimmed range should be smaller than the 20s source")

        val trimmed = tmp.resolve("trimmed.mp3").toFile()
        trimmed.writeBytes(trimmedBytes)

        val actualDuration = probeDurationSeconds(trimmed)
        assertTrue(actualDuration in 4.5..5.5, "expected ~5s range, got ${actualDuration}s")
    }

    @Test
    fun `returns empty when no valid mp3 frame is found (garbage input, no exception)`() {
        // Мусорные байты не бросают исключение при разборе — цикл просто не находит ни одного
        // валидного фрейма и завершается штатно. Пустой результат безопаснее, чем отдать исходные
        // (потенциально непредсказуемые) байты целиком для демо-фрагмента.
        val garbage = ByteArray(100) { it.toByte() }
        val result = Mp3Trimmer.trimToRange(garbage, 0.0, 5.0)
        assertTrue(result.isEmpty(), "unparsable input with no exception should return empty, not leak original bytes")
    }

    @Test
    fun `empty or inverted range returns empty`() {
        val garbage = ByteArray(10)
        assertTrue(Mp3Trimmer.trimToRange(garbage, 0.0, 0.0).isEmpty())
        assertTrue(Mp3Trimmer.trimToRange(garbage, 5.0, 2.0).isEmpty())
    }
}
