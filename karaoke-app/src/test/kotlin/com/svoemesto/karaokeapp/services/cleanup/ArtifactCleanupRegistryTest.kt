package com.svoemesto.karaokeapp.services.cleanup

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Юнит-тесты реестра классов артефактов песни (specs/544-song-artifact-cleanup, T006).
 *
 * Покрывает FR-010 (защищённые классы не удаляются), FR-013 (порядок чистки)
 * и инвариант 1 из `data-model.md` §2.
 *
 * @see specs/544-song-artifact-cleanup/data-model.md
 */
class ArtifactCleanupRegistryTest {
    @Test
    fun `реестр содержит все 16 классов из data-model`() {
        assertEquals(16, ArtifactCleanupClass.entries.size)
    }

    @Test
    fun `исходники и flac-стемы защищены от удаления`() {
        // FR-010: 768 ГБ исходников и 965 ГБ flac-стемов не удаляются ничем
        assertEquals(
            setOf(ArtifactCleanupClass.SOURCE_FLAC, ArtifactCleanupClass.STEMS_FLAC),
            ArtifactCleanupClass.entries.filter { ArtifactCleanupVerdict.PROTECTED in it.verdicts }.toSet(),
        )
    }

    @Test
    fun `классы с читателем помечены NOT_TOUCH`() {
        assertEquals(
            setOf(ArtifactCleanupClass.KEY_BPM_CSV, ArtifactCleanupClass.MANUAL_FILES),
            ArtifactCleanupClass.entries.filter { ArtifactCleanupVerdict.NOT_TOUCH in it.verdicts }.toSet(),
        )
    }

    @Test
    fun `инвариант 1 - ни один защищённый класс не удаляется`() {
        ArtifactCleanupClass.entries
            .filter { it.isProtected }
            .forEach { cls ->
                assertFalse(cls.isDeletable, "${cls.name}: защищённый класс не может быть DELETE_EXISTING")
                assertFalse(cls.isDeletedAfterStage, "${cls.name}: защищённый класс не может быть DELETE_AFTER_STAGE")
            }
    }

    @Test
    fun `порядок чистки неубывает и покрывает диапазон 1-7`() {
        // data-model §1: классы 3 (PUBLICATION_PNG/CHORDS_PNG) и 7 (HYGIENE_WAV/HYGIENE_JUNK)
        // делят номер — поэтому проверяем неубывание, а не строгое возрастание.
        val ordered = ArtifactCleanupClass.deletable()
        assertTrue(ordered.isNotEmpty(), "список классов разовой чистки не должен быть пуст")
        val orders = ordered.map { it.cleanupOrder }
        assertEquals(orders.sorted(), orders, "cleanupOrder должен быть неубывающим в порядке чистки")
        assertTrue(orders.all { it in 1..7 }, "cleanupOrder участвующих классов лежит в 1..7: $orders")
    }

    @Test
    fun `демо-ролики чистятся первыми, локальные mp3 - после`() {
        val ordered = ArtifactCleanupClass.deletable()
        assertEquals(ArtifactCleanupClass.DEMO_MP4, ordered.first())
        val demoIndex = ordered.indexOf(ArtifactCleanupClass.DEMO_MP4)
        val mp3Index = ordered.indexOf(ArtifactCleanupClass.STEMS_MP3_LOCAL)
        assertTrue(demoIndex < mp3Index, "DEMO_MP4 должен идти раньше STEMS_MP3_LOCAL (низкий риск первым)")
    }

    @Test
    fun `byId находит класс регистронезависимо и возвращает null на неизвестном`() {
        assertEquals(ArtifactCleanupClass.DEMO_MP4, ArtifactCleanupClass.byId("demo_mp4"))
        assertEquals(ArtifactCleanupClass.DEMO_MP4, ArtifactCleanupClass.byId("  DEMO_MP4 "))
        assertNull(ArtifactCleanupClass.byId("NO_SUCH_CLASS"))
    }

    @Test
    fun `классы с ожидаемым числом файлов объявляют и ожидаемый объём`() {
        ArtifactCleanupClass.entries.forEach { cls ->
            if (cls.expectedFileCount > 0L) {
                assertTrue(cls.expectedBytes > 0L, "${cls.name}: при известном числе файлов должен быть известен объём")
            }
        }
    }

    @Test
    fun `matchers позволяют найти файлы только у классов с известной раскладкой`() {
        assertNotNull(ArtifactCleanupClass.DEMO_MP4.matchers.firstOrNull { it.nameContainsAny.contains("[demo]") })
        // MANUAL_FILES — класс без писателя в коде, раскладка неизвестна: матчеров нет
        assertTrue(ArtifactCleanupClass.MANUAL_FILES.matchers.isEmpty())
    }
}
