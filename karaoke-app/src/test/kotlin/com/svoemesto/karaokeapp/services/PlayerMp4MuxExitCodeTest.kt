package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.ProcessExitException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.Collections

/**
 * Регрессия BR-161 (аудит #336, OpenProject #364): упавший `ffmpeg` в
 * [PlayerMp4MuxService.mixAndMux] раньше давал «Готово» и 100% прогресса, а
 * вызывающий `executeRenderMp4` копировал недописанный mp4 в `done_files`.
 *
 * ffmpeg подменяется управляемым shell-скриптом (`ffmpegBinary` — тестовый шов).
 *
 * **До правки** тест падал: `mixAndMux` не проверял код возврата и не бросал
 * исключение (см. `specs/364-pkg-err-exitcode/report.md`, раздел «до/после»).
 */
class PlayerMp4MuxExitCodeTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `ненулевой exit ffmpeg — исключение, недописанный mp4 удалён, 100 процентов не выставляется`() {
        val (framesDir, tracks) = prepareInputs()
        val output = File(tempDir, "output.mp4")
        val stub =
            stubScript(
                """
                last=""
                for arg in "${'$'}@"; do last="${'$'}arg"; done
                echo "out_time_ms=500000"
                echo "progress=continue"
                echo "ffmpeg: Error while opening encoder for output stream #0:0" >&2
                printf 'partial' > "${'$'}last"
                exit 1
                """.trimIndent(),
            )
        val progress = Collections.synchronizedList(mutableListOf<Int>())

        val failure =
            assertThrows(ProcessExitException::class.java) {
                PlayerMp4MuxService.mixAndMux(
                    framesDir = framesDir,
                    fps = 30,
                    preroll = 0.0,
                    audioTracks = tracks,
                    outputPath = output.absolutePath,
                    totalDurationSeconds = 60.0,
                    onProgress = { progress.add(it) },
                    ffmpegBinary = stub.absolutePath,
                    inactivityTimeoutSeconds = 30,
                )
            }

        val message = failure.message.orEmpty()
        assertTrue(message.contains("код возврата 1"), "в ошибке должен быть код возврата: $message")
        assertTrue(message.contains("ffmpeg"), "в ошибке должна быть команда: $message")
        assertTrue(
            message.contains("ffmpeg: Error while opening encoder"),
            "stderr-хвост (слитый в stdout, R-43) должен попадать в ошибку: $message",
        )
        assertFalse(output.exists(), "недописанный output.mp4 должен удаляться, иначе он уйдёт в done_files")
        assertFalse(progress.contains(100), "100% не должно выставляться при ошибке: $progress")
    }

    @Test
    fun `успешный exit 0 — без исключения и со 100 процентами в конце`() {
        val (framesDir, tracks) = prepareInputs()
        val output = File(tempDir, "output.mp4")
        val stub =
            stubScript(
                """
                last=""
                for arg in "${'$'}@"; do last="${'$'}arg"; done
                echo "out_time_ms=60000000"
                echo "progress=end"
                printf 'rendered' > "${'$'}last"
                exit 0
                """.trimIndent(),
            )
        val progress = Collections.synchronizedList(mutableListOf<Int>())

        PlayerMp4MuxService.mixAndMux(
            framesDir = framesDir,
            fps = 30,
            preroll = 0.0,
            audioTracks = tracks,
            outputPath = output.absolutePath,
            totalDurationSeconds = 60.0,
            onProgress = { progress.add(it) },
            ffmpegBinary = stub.absolutePath,
            inactivityTimeoutSeconds = 30,
        )

        assertTrue(output.exists(), "успешный ffmpeg оставляет output.mp4")
        assertEquals(100, progress.last(), "100% выставляется только после подтверждённого exit 0: $progress")
    }

    @Test
    fun `зависший ffmpeg убивается по неактивности и не блокирует лейн`() {
        val (framesDir, tracks) = prepareInputs()
        val output = File(tempDir, "output.mp4")
        val stub =
            stubScript(
                """
                trap '' TERM
                sleep 30
                """.trimIndent(),
            )
        val startedAt = System.nanoTime()

        val failure =
            assertThrows(ProcessExitException::class.java) {
                PlayerMp4MuxService.mixAndMux(
                    framesDir = framesDir,
                    fps = 30,
                    preroll = 0.0,
                    audioTracks = tracks,
                    outputPath = output.absolutePath,
                    totalDurationSeconds = 60.0,
                    ffmpegBinary = stub.absolutePath,
                    inactivityTimeoutSeconds = 1,
                )
            }

        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
        assertTrue(
            failure.message.orEmpty().contains("не выдавал прогресс"),
            "ошибка должна объяснять зависание: ${failure.message}",
        )
        assertTrue(elapsedMs < 20_000, "watchdog должен снять зависший ffmpeg, прошло ${elapsedMs}мс")
        assertFalse(output.exists(), "недописанный output.mp4 должен удаляться")
    }

    private fun prepareInputs(): Pair<File, List<PlayerMp4MuxService.AudioTrack>> {
        val framesDir = File(tempDir, "frames").apply { mkdirs() }
        val accompaniment = File(tempDir, "accompaniment.flac").apply { writeBytes(byteArrayOf(0)) }
        val vocals = File(tempDir, "vocals.flac").apply { writeBytes(byteArrayOf(0)) }
        return framesDir to
            listOf(
                PlayerMp4MuxService.AudioTrack(accompaniment.absolutePath, 1.0),
                PlayerMp4MuxService.AudioTrack(vocals.absolutePath, 0.0),
            )
    }

    private fun stubScript(body: String): File {
        val file = File.createTempFile("ffmpeg-stub", ".sh")
        file.writeText("#!/bin/sh\n$body\n")
        file.setExecutable(true)
        file.deleteOnExit()
        return file
    }
}
