package com.svoemesto.karaokeapp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Регрессия BR-029 (аудит #336, OpenProject #364): код возврата внешнего процесса
 * не должен «проглатываться».
 *
 * Внешний процесс подменяется управляемым shell-скриптом: тест проверяет ровно тот
 * контракт, который использует `KaraokeProcessThread.run()` — прочитать слитый
 * stdout/stderr до EOF, дождаться завершения, получить exit code.
 *
 * **До правки** в `KaraokeProcessWorker.kt` не было ни `waitFor()`, ни `exitValue()`:
 * EOF stdout безусловно вёл к `DONE`. Эти тесты падают на старом коде (см. отчёт
 * `specs/364-pkg-err-exitcode/report.md`, раздел «до/после»).
 */
class ProcessExitGuardTest {
    @Test
    fun `ненулевой код возврата возвращается вызывающему, а не превращается в успех`() {
        val script =
            stubScript(
                """
                echo "frame=12 fps=30"
                echo "ffmpeg: Error while opening encoder for output stream #0:0" >&2
                exit 1
                """.trimIndent(),
            )
        val command = listOf(script.absolutePath)
        val process = ProcessBuilder(command).redirectErrorStream(true).start()

        val merged = StringBuilder()
        process.inputStream.bufferedReader().forEachLine { merged.appendLine(it) }

        val exitCode = awaitProcessExit(process, command, timeoutSeconds = 10)

        assertEquals(1, exitCode, "код возврата subprocess должен доходить до вызывающего")
        val failure = processExitFailure(listOf("ffmpeg"), exitCode, outputTail(merged.toString()))
        val message = failure.message.orEmpty()
        assertTrue(message.contains("код возврата 1"), "в ошибке должен быть код возврата: $message")
        assertTrue(message.contains("ffmpeg"), "в ошибке должна быть команда: $message")
        assertTrue(
            message.contains("ffmpeg: Error while opening encoder"),
            "stderr (слитый в stdout, R-43) должен попадать в ошибку: $message",
        )
        assertTrue(
            message.contains("ошибка кодека/параметров/входного файла"),
            "типовой код ffmpeg должен распознаваться: $message",
        )
    }

    @Test
    fun `R-43 — большой stderr не блокирует процесс, пока stdout читается слитым потоком`() {
        val script =
            stubScript(
                """
                i=1
                while [ "${'$'}i" -le 2000 ]; do
                  echo "stdout ${'$'}i"
                  echo "stderr noise ${'$'}i" >&2
                  i=$((i + 1))
                done
                exit 0
                """.trimIndent(),
            )
        val command = listOf(script.absolutePath)
        val process = ProcessBuilder(command).redirectErrorStream(true).start()

        val lines = StringBuilder()
        process.inputStream.bufferedReader().forEachLine { lines.appendLine(it) }

        val exitCode = awaitProcessExit(process, command, timeoutSeconds = 15)

        assertEquals(0, exitCode)
        assertTrue(lines.lines().count { it.isNotBlank() } >= 2000, "слитый поток должен содержать и stdout, и stderr")
    }

    @Test
    fun `зависший процесс убивается по таймауту и даёт внятное исключение`() {
        val script =
            stubScript(
                """
                trap '' TERM
                sleep 30
                """.trimIndent(),
            )
        val command = listOf(script.absolutePath)
        val process = ProcessBuilder(command).start()
        val startedAt = System.nanoTime()

        val failure =
            assertThrows(ProcessExitException::class.java) {
                awaitProcessExit(process, command, timeoutSeconds = 1)
            }

        val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
        assertTrue(
            failure.message.orEmpty().contains("не завершился за 1 с"),
            "ошибка должна объяснять таймаут: ${failure.message}",
        )
        assertTrue(elapsedMs < 15_000, "destroyForcibly должен снимать зависший процесс, прошло ${elapsedMs}мс")
        assertFalse(process.isAlive, "процесс должен быть убит")
    }

    @Test
    fun `outputTail оставляет последние строки вывода`() {
        val text = (1..50).joinToString("\n") { "line $it" }

        assertEquals("line 48\nline 49\nline 50", outputTail(text, maxLines = 3))
        assertEquals("", outputTail(""))
    }

    @Test
    fun `коды docker распознаются по имени бинаря`() {
        assertTrue(describeExternalExitCode(listOf("/usr/bin/docker"), 127).contains("не найдена"))
        assertTrue(describeExternalExitCode(listOf("docker"), 137).contains("SIGKILL"))
        assertTrue(describeExternalExitCode(listOf("melt"), 1).contains("MLT"))
    }

    private fun stubScript(body: String): File {
        val file = File.createTempFile("process-exit-stub", ".sh")
        file.writeText("#!/bin/sh\n$body\n")
        file.setExecutable(true)
        file.deleteOnExit()
        return file
    }
}
