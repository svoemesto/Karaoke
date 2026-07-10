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

    private fun toolAvailable(name: String): Boolean = try {
        ProcessBuilder(name, "-version").redirectErrorStream(true).start().waitFor() == 0
    } catch (e: Exception) { false }

    private fun probeDurationSeconds(file: File): Double {
        val proc = ProcessBuilder(
            "ffprobe", "-v", "error", "-show_entries", "format=duration",
            "-of", "default=noprint_wrappers=1:nokey=1", file.absolutePath
        ).redirectErrorStream(true).start()
        val output = proc.inputStream.bufferedReader().readText().trim()
        proc.waitFor()
        return output.toDoubleOrNull() ?: error("ffprobe returned no duration: $output")
    }

    @Test
    fun `trims a real VBR mp3 to the requested duration at a frame boundary`(@TempDir tmp: java.nio.file.Path) {
        assumeTrue(toolAvailable("ffmpeg"), "ffmpeg not available — skipping")
        assumeTrue(toolAvailable("ffprobe"), "ffprobe not available — skipping")

        val source = tmp.resolve("source.mp3").toFile()
        // Тот же кодек/пресет, что и у реальных стемов (VBR, не CBR) — см. ApiController.pushMp3ToStorage.
        val gen = ProcessBuilder(
            "ffmpeg", "-y", "-f", "lavfi", "-i", "sine=frequency=440:duration=20",
            "-codec:a", "libmp3lame", "-qscale:a", "2", source.absolutePath
        ).redirectErrorStream(true).start()
        gen.waitFor()
        assertTrue(source.exists() && source.length() > 0, "ffmpeg failed to generate fixture")

        val sourceBytes = source.readBytes()
        val targetSeconds = 5.0
        val trimmedBytes = Mp3Trimmer.trimToSeconds(sourceBytes, targetSeconds)
        assertTrue(trimmedBytes.size < sourceBytes.size, "trimmed output should be smaller than the 20s source")

        val trimmed = tmp.resolve("trimmed.mp3").toFile()
        trimmed.writeBytes(trimmedBytes)

        val actualDuration = probeDurationSeconds(trimmed)
        // Допуск: обрезка идёт по границе mp3-фрейма (~26мс на кадр при 44.1kHz), не секунды в секунду.
        assertTrue(actualDuration in 4.5..5.5, "expected ~${targetSeconds}s, got ${actualDuration}s")
    }

    @Test
    fun `falls back to original bytes on unparsable input`() {
        val garbage = ByteArray(100) { it.toByte() }
        val result = Mp3Trimmer.trimToSeconds(garbage, 5.0)
        assertTrue(result.contentEquals(garbage), "unparsable input should fall back to the original bytes")
    }

    @Test
    fun `zero or negative seconds returns empty`() {
        val garbage = ByteArray(10)
        assertTrue(Mp3Trimmer.trimToSeconds(garbage, 0.0).isEmpty())
        assertTrue(Mp3Trimmer.trimToSeconds(garbage, -1.0).isEmpty())
    }
}
