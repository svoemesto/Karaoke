package com.svoemesto.karaokeapp

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Регрессионный инвариант BR-029/BR-161 (аудит #336, OpenProject #364): оба
 * места запуска внешнего процесса обязаны проверять код возврата до того, как
 * объявить успех.
 *
 * Поведенческие проверки — [ProcessExitGuardTest] (контракт ожидания/exit code)
 * и `PlayerMp4MuxExitCodeTest` (реальный путь `mixAndMux` со stub-ffmpeg).
 * Здесь — инвариант исходников: он падает, если проверку снова уберут или
 * переставят после `DONE`/`100%`.
 */
class ProcessExitWiringTest {
    private val workerPath = "karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcessWorker.kt"
    private val muxPath = "karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/PlayerMp4MuxService.kt"

    @Test
    fun `воркер очереди проверяет exit code до DONE`() {
        val text = repoRootFile(workerPath).readText()

        val awaitIndex = text.indexOf("awaitProcessExit(startedProcess")
        val failureIndex = text.indexOf("processExitFailure(args, exitCode")
        val doneIndex = text.indexOf("KaraokeProcessStatuses.DONE.name", startIndex = awaitIndex.coerceAtLeast(0))

        assertTrue(awaitIndex > 0, "KaraokeProcessThread.run обязан дожидаться завершения subprocess (awaitProcessExit)")
        assertTrue(failureIndex > awaitIndex, "после ожидания exit code должен проверяться (processExitFailure)")
        assertTrue(doneIndex > failureIndex, "DONE не может выставляться раньше проверки кода возврата")
        assertTrue(
            text.contains("redirectErrorStream(true)"),
            "R-43: stderr обязан сливаться в stdout (ADR-0006), иначе хвост ошибки пуст и процесс блокируется",
        )
    }

    @Test
    fun `мукс проверяет exit code до прогресса 100 и удаляет недописанный файл`() {
        val text = repoRootFile(muxPath).readText()

        val failureIndex = text.indexOf("processExitFailure(args, exitCode")
        val hundredIndex = text.indexOf("onProgress?.invoke(100)")

        assertTrue(failureIndex > 0, "PlayerMp4MuxService.mixAndMux обязан проверять код возврата ffmpeg")
        assertTrue(hundredIndex > failureIndex, "100% прогресса нельзя выставлять до проверки exit code")
        assertTrue(text.contains("File(outputPath).delete()"), "недописанный output.mp4 должен удаляться")
        assertTrue(text.contains("destroyForcibly()"), "зависший ffmpeg должен сниматься destroyForcibly()")

        val bareWaitFor = Regex("""process\.waitFor\(\s*\)""")
        assertTrue(
            !bareWaitFor.containsMatchIn(text),
            "ожидание ffmpeg без таймаута запрещено: нужен watchdog/destroyForcibly",
        )
    }

    /** Файл по пути относительно корня репозитория. */
    private fun repoRootFile(relativePath: String): File {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        while (!File(dir, "settings.gradle.kts").isFile) {
            dir = dir.parentFile ?: error("не найден корень репозитория (settings.gradle.kts)")
        }
        val file = File(dir, relativePath)
        assertTrue(file.isFile, "файл не найден: $relativePath")
        return file
    }
}
