package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.model.Song
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

/**
 * specs/126-key-from-file (#126, OpenProject): unit-тесты для
 * Song.parseKeyBpmFileOrNull() — pure-parse helper без side-effects.
 *
 * Применение key/bpm в БД (`applyKeyBpmFromFileIfExists`) тестируется отдельно
 * через integration-сценарий (Pass 401 follow-up); здесь — только file-cache
 * логика, которая и есть смысловое ядро #126 (см. research.md D-006).
 *
 * Покрывает 4 ветки:
 * 1. Файл есть, валиден → возвращает AudioAnalysisResult.
 * 2. Файла нет → возвращает null.
 * 3. Файл есть, поля null → возвращает null.
 * 4. Файл есть, невалидный JSON → возвращает null (без crash).
 */
class KeyBpmFromFileCacheTest {
    private lateinit var tempDir: File

    @BeforeEach
    fun setUp() {
        tempDir = Files.createTempDirectory("keybpm-test-").toFile()
    }

    @AfterEach
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `parseKeyBpmFileOrNull returns AudioAnalysisResult when file exists and valid`() {
        val keyFile = File(tempDir, "TestSong [key].json")
        keyFile.writeText("""{"bpm": 120, "key": "Am", "error": null}""")

        val data = Song.parseKeyBpmFileOrNull(keyFile.absolutePath)

        assertNotNull(data, "should return AudioAnalysisResult when file exists and valid")
        assertEquals("Am", data!!.key)
        assertEquals(120, data.bpm)
    }

    @Test
    fun `parseKeyBpmFileOrNull returns null when file missing`() {
        // Файла нет, ничего не создаём.
        val missingPath = File(tempDir, "DoesNotExist [key].json").absolutePath

        val data = Song.parseKeyBpmFileOrNull(missingPath)

        assertNull(data, "should return null when file missing")
    }

    @Test
    fun `parseKeyBpmFileOrNull returns null when file has null fields`() {
        val keyFile = File(tempDir, "TestSong [key].json")
        keyFile.writeText("""{"bpm": null, "key": null, "error": null}""")

        val data = Song.parseKeyBpmFileOrNull(keyFile.absolutePath)

        assertNull(data, "should return null when key/bpm fields are null")
    }

    @Test
    fun `parseKeyBpmFileOrNull returns null when file is invalid JSON`() {
        val keyFile = File(tempDir, "TestSong [key].json")
        keyFile.writeText("not-json{{")

        // Helper не должен бросать исключение; просто возвращает null.
        var data: com.svoemesto.karaokeapp.model.AudioAnalysisResult? = null
        try {
            data = Song.parseKeyBpmFileOrNull(keyFile.absolutePath)
        } catch (e: Exception) {
            org.junit.jupiter.api.Assertions
                .fail<Unit>("should not throw on invalid JSON, but threw: ${e.message}")
        }

        assertNull(data, "should return null when file is invalid JSON (no crash)")
    }
}
