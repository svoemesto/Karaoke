package com.svoemesto.karaokeapp.services.songdeletion

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Тест неизменности публичного поведения (KARAOKE-429, спека 739, FR-044, SC-010).
 *
 * Публичная часть (`karaoke-web`, `karaoke-public`) при удалении песни не
 * меняется: фича живёт целиком в админском контуре `karaoke-app`.
 *
 * @see specs/739-song-delete-cascade/spec.md FR-044, SC-010
 */
class SongDeletePublicBehaviourTest {
    @Test
    fun `публичные модули не менялись в ветке фичи`() {
        val repositoryRoot =
            repositoryRoot()
                ?: run {
                    Assumptions.assumeTrue(false, "репозиторий недоступен из теста — проверка неприменима")
                    return
                }
        val output =
            runCatching {
                val process =
                    ProcessBuilder(
                        "git",
                        "-C",
                        repositoryRoot,
                        "diff",
                        "--name-only",
                        "origin/master...HEAD",
                        "--",
                        "karaoke-web",
                        "karaoke-public",
                    ).redirectErrorStream(true)
                        .start()
                val output = process.inputStream.bufferedReader().readText()
                process.waitFor()
                output
            }.getOrNull() ?: run {
                Assumptions.assumeTrue(false, "git недоступен из теста — проверка неприменима")
                return
            }
        assertTrue(
            output.isBlank(),
            "публичная часть не должна меняться в фиче удаления песни (FR-044):\n$output",
        )
    }

    @Test
    fun `в коде фичи нет ссылок на публичные модули`() {
        val featureDir = File("src/main/kotlin/com/svoemesto/karaokeapp/services/songdeletion")
        val files = featureDir.listFiles()?.filter { it.extension == "kt" }.orEmpty()
        files.forEach { file ->
            val text = file.readText()
            assertTrue(
                !text.contains("karaokeweb"),
                "код фичи удаления не должен обращаться к публичному модулю: ${file.name}",
            )
        }
    }

    /** Корень репозитория (на два уровня выше `karaoke-app`). */
    private fun repositoryRoot(): String? {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            if (File(dir, ".git").exists()) return dir.path
            dir = dir.parentFile
        }
        return null
    }
}
