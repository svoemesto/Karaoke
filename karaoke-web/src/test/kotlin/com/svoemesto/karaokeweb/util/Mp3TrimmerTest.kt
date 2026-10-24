package com.svoemesto.karaokeweb.util

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Реальная (не мок) проверка: генерирует VBR mp3 через ffmpeg (как реально кодируются стемы —
 * см. ApiController.pushMp3ToStorage, "-codec:a libmp3lame -qscale:a 2") и проверяет РЕАЛЬНУЮ
 * длительность обрезанного файла через ffprobe — не полагается на внутреннюю логику самого
 * Mp3Trimmer для проверки самого себя.
 *
 * Гигиена внешних процессов (аудит #336, BR-511):
 * - каждый запуск имеет ЖЁСТКИЙ таймаут; зависший процесс принудительно убивается, а тест
 *   падает с внятным сообщением вместо бесконечного ожидания (раннер/CI не виснет намертво);
 * - вывод процесса пишется в ФАЙЛ, а не в pipe: переполнение pipe-буфера прогрессингом ffmpeg
 *   больше не может заблокировать процесс — тот же класс проблемы, что разбирает ADR-0006 для
 *   продуктового кода (`redirectErrorStream(true)` обязателен, pipe не должен оставаться
 *   непрочитанным).
 *
 * Отсутствие ffmpeg/ffprobe — ЭТО ПАДЕНИЕ теста, а не «пропуск» (аудит #336, BR-512):
 * раньше `assumeTrue(...)` молча помечал тесты skipped, и в окружениях без ffmpeg проверка
 * обрезки mp3 не выполнялась НИ РАЗУ, хотя прогон оставался зелёным. Теперь зелёный прогон
 * означает «проверка обрезки mp3 действительно выполнилась».
 */
class Mp3TrimmerTest {
    companion object {
        /** Таймаут рабочего вызова внешнего инструмента (ffmpeg/ffprobe), с. */
        private const val TOOL_TIMEOUT_SECONDS = 60L

        /** Таймаут проверки доступности бинарника (`-version`), с. */
        private const val PROBE_TIMEOUT_SECONDS = 15L

        /** Пауза после `destroyForcibly`, чтобы процесс успел умереть, с. */
        private const val KILL_TIMEOUT_SECONDS = 5L
    }

    /**
     * Запускает внешний инструмент с жёстким таймаутом.
     *
     * @param cmd команда с аргументами
     * @param log файл для всего вывода процесса (stdout + stderr)
     * @param timeoutSeconds предел ожидания, с
     * @return код возврата процесса
     */
    private fun runTool(
        cmd: List<String>,
        log: File,
        timeoutSeconds: Long = TOOL_TIMEOUT_SECONDS,
    ): Int {
        val process =
            ProcessBuilder(cmd)
                // ADR-0006: stderr не должен оставаться отдельным непрочитанным pipe.
                .redirectErrorStream(true)
                // Вывод в файл: pipe не переполняется (нет взаимной блокировки) + текст доступен в диагностике.
                .redirectOutput(log)
                .start()
        val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            process.waitFor(KILL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            fail<Unit>(
                "Внешний инструмент '${cmd.first()}' не завершился за $timeoutSeconds с — процесс убит. " +
                    "Вывод: ${readLog(log)}",
            )
        }
        return process.exitValue()
    }

    private fun readLog(log: File): String = if (log.exists()) log.readText() else ""

    /**
     * Проверяет доступность внешнего инструмента. Отсутствие бинарника — падение теста,
     * а не пропуск (BR-512): иначе дефект `Mp3Trimmer` (битый фрейм-граница, обрезка не по
     * кадру) проходил бы незамеченным.
     */
    private fun assertToolAvailable(
        name: String,
        tmp: Path,
    ) {
        val log = tmp.resolve("$name-version.log").toFile()
        val available =
            try {
                runTool(listOf(name, "-version"), log, PROBE_TIMEOUT_SECONDS) == 0
            } catch (e: IOException) {
                false
            }
        assertTrue(
            available,
            "Внешний инструмент '$name' обязателен для этого теста: без него проверка обрезки mp3 " +
                "не выполняется (BR-512: молчаливый skip прятал регрессии Mp3Trimmer). " +
                "Установите ffmpeg/ffprobe (apt install ffmpeg) или запустите тест в окружении, " +
                "где они есть. Вывод проверки: ${readLog(log)}",
        )
    }

    /** Генерирует VBR mp3 тем же кодеком/пресетом, что и реальные стемы. */
    private fun generateVbrFixture(
        tmp: Path,
        name: String = "source.mp3",
    ): File {
        val source = tmp.resolve(name).toFile()
        val log = tmp.resolve("ffmpeg-generate.log").toFile()
        val exit =
            runTool(
                listOf(
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
                ),
                log,
            )
        assertTrue(
            exit == 0 && source.exists() && source.length() > 0,
            "ffmpeg не создал фикстуру (код возврата $exit). Вывод: ${readLog(log)}",
        )
        return source
    }

    /** Реальная длительность mp3-файла по данным ffprobe, с. */
    private fun probeDurationSeconds(
        file: File,
        tmp: Path,
    ): Double {
        val log = tmp.resolve("ffprobe-duration.log").toFile()
        val exit =
            runTool(
                listOf(
                    "ffprobe",
                    "-v",
                    "error",
                    "-show_entries",
                    "format=duration",
                    "-of",
                    "default=noprint_wrappers=1:nokey=1",
                    file.absolutePath,
                ),
                log,
            )
        val output = readLog(log).trim()
        assertTrue(exit == 0, "ffprobe завершился с кодом $exit. Вывод: $output")
        val duration =
            output
                .lineSequence()
                .map { it.trim() }
                .firstOrNull { it.isNotEmpty() && it.toDoubleOrNull() != null }
                ?.toDoubleOrNull()
        return duration ?: fail("ffprobe не вернул длительность: '$output'")
    }

    @Test
    fun `trims a real VBR mp3 to the requested end at a frame boundary`(
        @TempDir tmp: Path,
    ) {
        assertToolAvailable("ffmpeg", tmp)
        assertToolAvailable("ffprobe", tmp)

        val source = generateVbrFixture(tmp)
        val sourceBytes = source.readBytes()
        val targetSeconds = 5.0
        val trimmedBytes = Mp3Trimmer.trimToRange(sourceBytes, 0.0, targetSeconds)
        assertTrue(trimmedBytes.size < sourceBytes.size, "trimmed output should be smaller than the 20s source")

        val trimmed = tmp.resolve("trimmed.mp3").toFile()
        trimmed.writeBytes(trimmedBytes)

        val actualDuration = probeDurationSeconds(trimmed, tmp)
        // Допуск: обрезка идёт по границе mp3-фрейма (~26мс на кадр при 44.1kHz), не секунды в секунду.
        assertTrue(actualDuration in 4.5..5.5, "expected ~${targetSeconds}s, got ${actualDuration}s")
    }

    @Test
    fun `trims a real VBR mp3 to a mid-track range at a frame boundary`(
        @TempDir tmp: Path,
    ) {
        assertToolAvailable("ffmpeg", tmp)
        assertToolAvailable("ffprobe", tmp)

        val source = generateVbrFixture(tmp)
        val sourceBytes = source.readBytes()
        // Демо-фрагмент "куплет минус 5 секунд отступа": начинается НЕ с нуля.
        val trimmedBytes = Mp3Trimmer.trimToRange(sourceBytes, 8.0, 13.0)
        assertTrue(trimmedBytes.isNotEmpty(), "mid-track range should not be empty")
        assertTrue(trimmedBytes.size < sourceBytes.size, "trimmed range should be smaller than the 20s source")

        val trimmed = tmp.resolve("trimmed.mp3").toFile()
        trimmed.writeBytes(trimmedBytes)

        val actualDuration = probeDurationSeconds(trimmed, tmp)
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
