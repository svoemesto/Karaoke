package com.svoemesto.karaokeapp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Офлайн-проверка конфигурации image-скрапперов fourget для поиска обложек
 * (без сети/fourget): правила разбора настройки `albumCoverSearchScrapers`
 * ([AlbumCoverService.parseAlbumCoverScrapers]) и страж синхронности двух
 * экземпляров её дефолта — `defaultValue` свойства в `KaraokeProperties.kt` и
 * `DEFAULT_ALBUM_COVER_SEARCH_SCRAPERS` в `AlbumCoverFinder.kt`.
 *
 * Расхождение дефолтов означает разное поведение на чистом стенде (ключ
 * отсутствует в `Karaoke.properties` — читается `defaultValue`) и на стенде с
 * испорченной настройкой (значение из одних `;` — срабатывает fallback).
 *
 * `internal` — видна из тестов в том же модуле (но не из других модулей).
 *
 * @see archive/docs/features/llm-lyrics-search.md
 */
internal class AlbumCoverFinderScrapersTest {
    @Test
    fun `parseAlbumCoverScrapers разбирает строку через точку с запятой`() {
        assertEquals(
            listOf("ddg", "yahoo_japan", "brave", "google_cse"),
            AlbumCoverService.parseAlbumCoverScrapers("ddg;yahoo_japan;brave;google_cse"),
        )
    }

    @Test
    fun `parseAlbumCoverScrapers сохраняет порядок и не дедуплицирует`() {
        // Порядок — это и есть настройка (первый scraper, прошедший порог, побеждает),
        // поэтому сортировка/дедупликация здесь были бы ошибкой.
        assertEquals(
            listOf("brave", "ddg", "brave"),
            AlbumCoverService.parseAlbumCoverScrapers("brave;ddg;brave"),
        )
    }

    @Test
    fun `parseAlbumCoverScrapers обрезает пробелы вокруг токенов`() {
        assertEquals(
            listOf("ddg", "brave"),
            AlbumCoverService.parseAlbumCoverScrapers("  ddg ;\tbrave  "),
        )
    }

    @Test
    fun `parseAlbumCoverScrapers отбрасывает пустые токены`() {
        assertEquals(
            listOf("ddg", "brave"),
            AlbumCoverService.parseAlbumCoverScrapers("ddg;;brave;"),
        )
    }

    @Test
    fun `parseAlbumCoverScrapers на пустой строке откатывается на дефолт`() {
        assertEquals(
            AlbumCoverService.DEFAULT_ALBUM_COVER_SEARCH_SCRAPERS,
            AlbumCoverService.parseAlbumCoverScrapers(""),
        )
    }

    @Test
    fun `parseAlbumCoverScrapers на строке из одних разделителей откатывается на дефолт`() {
        // Именно этот случай ловится fallback'ом в коде: настройка формально непустая,
        // но рабочих токенов в ней нет.
        assertEquals(
            AlbumCoverService.DEFAULT_ALBUM_COVER_SEARCH_SCRAPERS,
            AlbumCoverService.parseAlbumCoverScrapers("; ; ;"),
        )
    }

    @Test
    fun `albumCoverSearchScrapersList читает настройку без пустых токенов`() {
        // Ключа нет в Karaoke.properties на чистом стенде — значение придёт из
        // defaultValue свойства, а не из fallback-константы.
        val scrapers = AlbumCoverService.albumCoverSearchScrapersList()
        assertTrue(scrapers.isNotEmpty(), "список image-скрапперов не должен быть пустым")
        assertTrue(scrapers.none { it.isBlank() }, "в списке image-скрапперов не должно быть пустых токенов")
    }

    @Test
    fun `дефолт image-скрапперов в коде и в KaraokeProperties совпадает`() {
        val declaredDefault =
            listKaraokeProperties
                .first { it.key == "albumCoverSearchScrapers" }
                .defaultValue as String
        assertEquals(
            "ddg;yahoo_japan;brave;google_cse",
            declaredDefault,
            "defaultValue свойства albumCoverSearchScrapers изменился — синхронизируйте " +
                "AlbumCoverService.DEFAULT_ALBUM_COVER_SEARCH_SCRAPERS и этот тест",
        )
        assertEquals(
            declaredDefault.split(";"),
            AlbumCoverService.DEFAULT_ALBUM_COVER_SEARCH_SCRAPERS,
            "DEFAULT_ALBUM_COVER_SEARCH_SCRAPERS в AlbumCoverFinder.kt разошёлся с defaultValue " +
                "свойства albumCoverSearchScrapers в KaraokeProperties.kt",
        )
    }

    @Test
    fun `дефолтный порядок не содержит scraper'ов-мемогенераторов`() {
        // baidu (1 из 25 релевантных на замере 2026-09-26) и ftm (поиск мемов по
        // назначению) сознательно исключены; pinterest — 3 из 20.
        val scrapers = AlbumCoverService.DEFAULT_ALBUM_COVER_SEARCH_SCRAPERS
        assertTrue(
            listOf("baidu", "ftm", "pinterest").none { it in scrapers },
            "в дефолтном порядке не должно быть scraper'ов, отдающих нерелевантные картинки: $scrapers",
        )
    }
}
