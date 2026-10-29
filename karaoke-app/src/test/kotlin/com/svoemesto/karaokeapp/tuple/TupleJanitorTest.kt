package com.svoemesto.karaokeapp.tuple

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * 674 (FR-005): uborка только осиротевших scope-каталогов по TTL; живые и корень не трогаются.
 *
 * @see specs/674-kortege-execution/tasks.md T014
 */
class TupleJanitorTest {
    @TempDir
    lateinit var root: Path

    private fun dir(
        name: String,
        ageHours: Long,
    ): Path {
        val path = root.resolve(name)
        Files.createDirectories(path)
        Files.writeString(path.resolve("file.flac"), "x")
        Files.setLastModifiedTime(
            path,
            FileTime.from(Instant.now().minus(ageHours, ChronoUnit.HOURS)),
        )
        return path
    }

    @Test
    fun `старый осиротевший каталог удаляется, живой и корень целы`() {
        val live = dir("100", 48)
        val orphan = dir("200", 48)
        val fresh = dir("300", 1)
        val nonNumeric = dir("not-a-scope", 48)
        val rootFile = root.resolve("keep.txt")
        Files.writeString(rootFile, "x")

        val removed =
            TupleJanitor(
                roots = listOf(root.toString()),
                ttlHours = 24,
                isLive = { it == 100L },
            ).sweep()

        assertTrue(removed.any { it.endsWith("200") }, "осиротевший старый каталог должен быть удалён")
        assertTrue(Files.exists(live), "каталог живого кортежа не трогается")
        assertTrue(Files.exists(fresh), "молодой каталог не трогается по TTL")
        assertTrue(Files.exists(nonNumeric), "нечисловые каталоги не наши")
        assertTrue(Files.exists(rootFile), "файлы в корне не трогаются")
        assertTrue(Files.exists(root), "корень не удаляется")
        assertFalse(Files.exists(orphan))
    }

    @Test
    fun `ошибки удаления не роняют уборку`() {
        val report = mutableListOf<String>()
        TupleJanitor(
            roots = listOf(root.resolve("nope").toString()),
            ttlHours = 0,
            isLive = { false },
            log = { report += it },
        ).sweep()
        assertEquals(emptyList<String>(), report)
    }
}
