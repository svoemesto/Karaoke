package com.svoemesto.karaokeweb.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Офлайн-проверка выбора знаменателя прогресса NDJSON-стрима
 * (specs/444-fix-album-progress, issue #179). Без Spring-контекста и БД —
 * чистый [ZakromaStreamProgress.resolveExpectedCount].
 */
class ZakromaStreamProgressTest {
    private val counters = ZakromaStreamProgress.AlbumCounters(readySongCount = 4, totalSongCount = 10)

    @Test
    fun `albumId guest uses readySongCount of album`() {
        // issue #179: альбом с 10 песнями, но у гостя готовы только 4 -> знаменатель 4,
        // а не 2485 песен автора.
        val actual =
            ZakromaStreamProgress.resolveExpectedCount(
                albumId = 2777L,
                album = counters,
                onlyPublished = true,
                providedExpectedCount = 2485L,
            ) { 2485L }
        assertEquals(4L, actual)
    }

    @Test
    fun `albumId editor uses totalSongCount of album`() {
        val actual =
            ZakromaStreamProgress.resolveExpectedCount(
                albumId = 2777L,
                album = counters,
                onlyPublished = false,
                providedExpectedCount = 2485L,
            ) { 2485L }
        assertEquals(10L, actual)
    }

    @Test
    fun `albumId not found returns zero not author count`() {
        // FR-005: альбом удалён между загрузкой плашки и кликом -> 0, не 2485.
        val actual =
            ZakromaStreamProgress.resolveExpectedCount(
                albumId = 999999L,
                album = null,
                onlyPublished = true,
                providedExpectedCount = 2485L,
            ) { 2485L }
        assertEquals(0L, actual)
    }

    @Test
    fun `no albumId trusts provided author count`() {
        var fallbackCalled = false
        val actual =
            ZakromaStreamProgress.resolveExpectedCount(
                albumId = null,
                album = null,
                onlyPublished = true,
                providedExpectedCount = 2485L,
            ) {
                fallbackCalled = true
                2485L
            }
        assertEquals(2485L, actual)
        assertEquals(false, fallbackCalled, "fallback MUST NOT run when provided count > 0")
    }

    @Test
    fun `no albumId falls back to author count when provided is zero`() {
        // Deep-link без тайла: фронт не прислал expectedCount -> lazy DB fallback.
        var fallbackCalled = false
        val actual =
            ZakromaStreamProgress.resolveExpectedCount(
                albumId = null,
                album = null,
                onlyPublished = true,
                providedExpectedCount = 0L,
            ) {
                fallbackCalled = true
                2485L
            }
        assertEquals(2485L, actual)
        assertEquals(true, fallbackCalled)
    }

    @Test
    fun `albumId does not invoke author fallback`() {
        // Даже при providedExpectedCount=0 и найденном альбоме fallback по автору не нужен.
        var fallbackCalled = false
        val actual =
            ZakromaStreamProgress.resolveExpectedCount(
                albumId = 2777L,
                album = counters,
                onlyPublished = false,
                providedExpectedCount = 0L,
            ) {
                fallbackCalled = true
                2485L
            }
        assertEquals(10L, actual)
        assertEquals(false, fallbackCalled)
    }
}
