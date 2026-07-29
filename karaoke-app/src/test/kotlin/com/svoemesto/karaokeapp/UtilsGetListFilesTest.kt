package com.svoemesto.karaokeapp

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory

/**
 * Офлайн-проверка [getListFiles] (без сети/БД) — специально для
 * `specs/082-fix-import-folder-oom`: обход большого/вложенного дерева не должен
 * буферизовать нефильтрованный список всех файлов и обязан корректно фильтровать
 * по расширению за один проход (см. `research.md`, Находка A).
 */
class UtilsGetListFilesTest {
    private lateinit var root: java.nio.file.Path

    @AfterEach
    fun cleanup() {
        if (::root.isInitialized) File(root.toString()).deleteRecursively()
    }

    @Test
    fun `getListFiles с несколькими расширениями отбирает только целевые файлы среди тысяч посторонних`() {
        root = createTempDirectory("getListFiles-test")
        val targetExtensions = listOf("flac", "mp3", "m4a")
        val expected = mutableSetOf<String>()

        repeat(20) { albumIdx ->
            val albumDir = root.resolve("2020 - Album $albumIdx").also { it.createDirectories() }
            repeat(50) { fileIdx ->
                // Целевые файлы (должны попасть в результат)
                val targetExt = targetExtensions[fileIdx % targetExtensions.size]
                val targetFile = albumDir.resolve("$fileIdx (1) [Author] - Song.$targetExt")
                File(targetFile.toString()).writeBytes(ByteArray(0))
                expected.add(targetFile.toString())

                // Посторонние файлы (НЕ должны попасть в результат) — их в 3 раза больше, чтобы
                // покрыть регрессию «скрытый вызов с extension="" буферизует вообще все файлы»
                listOf("jpg", "txt", "tmp").forEach { otherExt ->
                    File(albumDir.resolve("other-$fileIdx.$otherExt").toString()).writeBytes(ByteArray(0))
                }
            }
        }

        val result = getListFiles(root.toString(), targetExtensions)

        assertEquals(expected.size, result.size)
        assertEquals(expected.sorted(), result)
        assertTrue(result.all { path -> targetExtensions.any { path.endsWith(it) } })
    }

    @Test
    fun `getListFiles не падает и корректно работает на глубоко вложенном дереве`() {
        root = createTempDirectory("getListFiles-deep-test")
        var current = root
        repeat(30) { depth ->
            current = current.resolve("level-$depth").also { it.createDirectories() }
        }
        val deepFile = current.resolve("deep.flac")
        File(deepFile.toString()).writeBytes(ByteArray(0))
        File(current.resolve("deep.jpg").toString()).writeBytes(ByteArray(0))

        val result = getListFiles(root.toString(), listOf("flac"))

        assertEquals(listOf(deepFile.toString()), result)
    }

    @Test
    fun `однорасширенческая перегрузка фильтрует по extension и startWith`() {
        root = createTempDirectory("getListFiles-single-ext-test")
        val subDir = root.resolve("sub").also { it.createDirectories() }
        val matching = subDir.resolve("song.flac")
        File(matching.toString()).writeBytes(ByteArray(0))
        File(subDir.resolve("song.mp3").toString()).writeBytes(ByteArray(0))
        File(root.resolve("root.flac").toString()).writeBytes(ByteArray(0))

        val result = getListFiles(root.absolutePathString(), extension = "flac", startWith = "sub")

        assertEquals(listOf(matching.toString()), result)
    }
}
