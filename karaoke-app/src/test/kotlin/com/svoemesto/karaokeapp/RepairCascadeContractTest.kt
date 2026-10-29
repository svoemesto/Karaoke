package com.svoemesto.karaokeapp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * 674: ремонт доводит песню до конца, а не оставляет «нерешаемые» ошибки.
 *
 * Два регресса со стенда:
 * - mp3 отсутствовал и в хранилищах, и на диске, хотя flac-родитель есть: HR писал
 *   «Невозможно автоматически решить» и не предлагал создать mp3;
 * - после ремонта у песен оставался `song_ms = 0` (нет длительности).
 */
class RepairCascadeContractTest {
    private fun repoFile(relativePath: String): File {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        while (!File(dir, "settings.gradle.kts").isFile) {
            dir = dir.parentFile ?: error("не найден корень репозитория (settings.gradle.kts)")
        }
        val file = File(dir, relativePath)
        assertTrue(file.isFile, "файл не найден: $relativePath")
        return file
    }

    private val healthReport = repoFile("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReport.kt")
    private val utils = repoFile("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt")

    @Test
    fun `both storages offer creating a missing file from its parent`() {
        val text = healthReport.readText()
        assertEquals(
            2,
            text.split("solutionText = \"Создать файл: ").size - 1,
            "ожидалось предложение создания и для локального, и для удалённого хранилища",
        )
        assertEquals(
            2,
            text.split("action = typeToCreate").size - 1,
            "в обоих местах действие должно ставить процесс создания файла",
        )
    }

    @Test
    fun `finalize restores zero duration from the source`() {
        val text = utils.readText()
        assertTrue(text.contains("song.ms <= 0L"), "finalize должен проверять нулевую длительность")
        assertTrue(
            text.contains("refreshDurationMsFromAudioFile()"),
            "нулевая длительность должна восстанавливаться из источника",
        )
    }
}
