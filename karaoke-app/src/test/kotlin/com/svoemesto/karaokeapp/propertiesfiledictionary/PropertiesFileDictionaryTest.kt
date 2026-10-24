package com.svoemesto.karaokeapp.propertiesfiledictionary

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

/**
 * Регрессия BR-133 (аудит #336, PKG-S2-11): запись значений словаря свойств
 * экранируется и атомарна, а разбор устойчив к битой строке.
 *
 * До правки `set()` писал `"$key$SEP$value"` без экранирования: значение с
 * переводом строки (легальный POST-параметр `/api/setwebvueprop`) давало вторую
 * строку без разделителя, а `loadMap()` падал с `IndexOutOfBoundsException` —
 * следующий старт `karaoke-app` не поднимался.
 */
class PropertiesFileDictionaryTest {
    private class TestDictionary(
        private val path: String,
    ) : PropertiesFileDictionary {
        override fun pathToFile(): String = path

        override var props: MutableMap<String, String> = mutableMapOf()
    }

    @Test
    fun `values with newline survive save and load`(
        @TempDir tempDir: Path,
    ) {
        val file = tempDir.resolve("webvue_properties.txt")
        val dict = TestDictionary(file.toString())
        dict.set("multi", "a\nb")
        dict.set("plain", "x")

        val loaded = TestDictionary(file.toString()).loadMap()
        assertEquals("a\nb", loaded["multi"])
        assertEquals("x", loaded["plain"])
    }

    @Test
    fun `broken legacy line is skipped and other settings survive`(
        @TempDir tempDir: Path,
    ) {
        val file = tempDir.resolve("webvue_properties.txt")
        val separator = TestDictionary(file.toString()).separator()
        File(file.toString()).writeText("good${separator}1\nbroken-line\nother${separator}2")

        val loaded = TestDictionary(file.toString()).loadMap()
        assertEquals("1", loaded["good"])
        assertEquals("2", loaded["other"])
        assertFalse(loaded.containsKey("broken-line"))
        assertNull(loaded["broken-line"])
    }
}
