package com.svoemesto.karaokeapp.tuple

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

/**
 * ИНЦИДЕНТ 2026-11-03 (песня 30428): перенос стемов demucs вынесен из отдельных
 * шагов цепочки в проверку — иначе пустой результат demucs (CUDA OOM на занятом GPU)
 * рвал цепочку на `mv`, и повтор без GPU (спека 674 FR-009) не запускался.
 */
class DemucsStemsTest {
    private fun scopeWith(vararg files: Pair<String, ByteArray>): File {
        val dir = Files.createTempDirectory("demucs-scope").toFile()
        files.forEach { (name, content) -> File(dir, name).writeBytes(content) }
        return dir
    }

    @Test
    fun `стемы переносятся из папки кортежа в целевые файлы`() {
        val scope = scopeWith("file-accompaniment.flac" to byteArrayOf(1, 2, 3))
        val destDir = Files.createTempDirectory("demucs-dest").toFile()
        val accompaniment = File(destDir, "song-accompaniment.flac")
        val vocals = File(destDir, "song-vocals.flac")

        val result =
            DemucsStems.move(
                scope,
                mapOf("accompaniment" to accompaniment, "vocals" to vocals),
            )

        assertEquals(listOf("accompaniment"), result.moved)
        assertEquals(listOf("vocals"), result.missing)
        assertTrue(accompaniment.isFile, "accomp перенесён")
        assertTrue(accompaniment.readBytes().contentEquals(byteArrayOf(1, 2, 3)), "содержимое сохранено")
    }

    @Test
    fun `пустой файл считается отсутствующим`() {
        val scope = scopeWith("file-accompaniment.flac" to ByteArray(0))
        val dest = File(Files.createTempDirectory("demucs-dest").toFile(), "song-accompaniment.flac")

        val result = DemucsStems.move(scope, mapOf("accompaniment" to dest))

        assertTrue(result.missing.contains("accompaniment"), "пустой стем — не стем")
        assertFalse(result.complete, "результат неполный")
        assertFalse(dest.exists(), "пустой файл не переносим")
    }

    @Test
    fun `отсутствующая папка кортежа — все стемы отсутствуют`() {
        val dest = File(Files.createTempDirectory("demucs-dest").toFile(), "song-vocals.flac")

        val result = DemucsStems.move(File("/nonexistent/demucs-scope"), mapOf("vocals" to dest))

        assertEquals(listOf("vocals"), result.missing)
        assertFalse(result.complete, "результат неполный")
    }

    @Test
    fun `перенос забирает источник — как прежний шаг mv`() {
        val scope = scopeWith("file-vocals.flac" to byteArrayOf(9))
        val dest = File(Files.createTempDirectory("demucs-dest").toFile(), "song-vocals.flac")

        DemucsStems.move(scope, mapOf("vocals" to dest))
        val second = DemucsStems.move(scope, mapOf("vocals" to dest))

        // Источник в scope-папке перемещён (семантика прежнего `mv`): повторный перенос
        // находит источник пустым, но результат на месте не теряется.
        assertTrue(second.missing.contains("vocals"), "источник уже перенесён")
        assertTrue(dest.isFile, "результат на месте")
        assertTrue(dest.readBytes().contentEquals(byteArrayOf(9)), "содержимое не пострадало")
    }
}
