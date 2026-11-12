package com.svoemesto.karaokeapp.services.songdeletion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Юнит-тесты нормализации имён для разовой уборки (KARAOKE-429, спека 739, FR-042).
 *
 * Нормализация применяется ТОЛЬКО в разовой уборке и только в одну сторону: объект,
 * имя которого совпадает с именем живой песни после нормализации написания, НЕ должен
 * попасть в перечень кандидатов на удаление (SC-008: «ни один живой объект хранилища
 * и ни один файл живой песни не удалён ни одним из разовых проходов»).
 *
 * @see specs/739-song-delete-cascade/spec.md FR-042, SC-008
 */
class ArtifactNormalizationTest {
    @Test
    fun `регистр не влияет на нормализованное имя`() {
        assertEquals(
            SongArtifactOwnership.normalizeForMatch("Песня"),
            SongArtifactOwnership.normalizeForMatch("песня"),
        )
    }

    @Test
    fun `ё и е дают одинаковое нормализованное имя`() {
        assertEquals(
            SongArtifactOwnership.normalizeForMatch("Пёсня"),
            SongArtifactOwnership.normalizeForMatch("Песня"),
        )
    }

    @Test
    fun `разные кавычки дают одинаковое нормализованное имя`() {
        val expected = SongArtifactOwnership.normalizeForMatch("Песня")
        listOf("«Песня»", "\"Песня\"", "„Песня“", "'Песня'").forEach {
            assertEquals(expected, SongArtifactOwnership.normalizeForMatch(it), "кавычки: $it")
        }
    }

    @Test
    fun `ведущие нули в номере не различаются`() {
        assertEquals(
            SongArtifactOwnership.normalizeForMatch("Песня 01"),
            SongArtifactOwnership.normalizeForMatch("Песня 1"),
        )
    }

    @Test
    fun `разные имена остаются разными после нормализации`() {
        assertNotEquals(
            SongArtifactOwnership.normalizeForMatch("Песня, длинная версия"),
            SongArtifactOwnership.normalizeForMatch("Песня"),
        )
    }

    @Test
    fun `near-match живой песни не признаётся кандидатом`() {
        val live = setOf(SongArtifactOwnership.normalizeForMatch("Песня"))
        listOf("песня.mp3", "Пёсня.mp3", "«Песня».mp3", "Песня.vocals.mp3", "Песня [karaoke].png").forEach {
            assertTrue(
                SongArtifactOwnership.isNearMatchOfLiveSong(live, it),
                "near-match должен исключаться из кандидатов: $it",
            )
        }
    }

    @Test
    fun `near-match с другим номером песни - не near-match`() {
        // «Песня 01» и «Песня» — РАЗНЫЕ песни (FR-011: 01/1 значимы).
        val live = setOf(SongArtifactOwnership.normalizeForMatch("Песня"))
        assertFalse(SongArtifactOwnership.isNearMatchOfLiveSong(live, "Песня 01.mp3"))
        assertFalse(SongArtifactOwnership.isNearMatchOfLiveSong(live, "Песня 1.mp3"))
    }

    @Test
    fun `одинаковый номер с разными ведущими нулями - near-match`() {
        val live = setOf(SongArtifactOwnership.normalizeForMatch("Песня 01"))
        assertTrue(SongArtifactOwnership.isNearMatchOfLiveSong(live, "Песня 1.mp3"))
        assertTrue(SongArtifactOwnership.isNearMatchOfLiveSong(live, "Песня 01.mp3"))
    }

    @Test
    fun `файл чужой песни не является near-match живой`() {
        val live = setOf(SongArtifactOwnership.normalizeForMatch("Песня"))
        assertFalse(SongArtifactOwnership.isNearMatchOfLiveSong(live, "Песня, длинная версия.mp3"))
        assertFalse(SongArtifactOwnership.isNearMatchOfLiveSong(live, "Другая песня.mp3"))
    }

    @Test
    fun `пустой набор живых имён ничего не исключает`() {
        assertFalse(SongArtifactOwnership.isNearMatchOfLiveSong(emptySet(), "Песня.mp3"))
    }
}
