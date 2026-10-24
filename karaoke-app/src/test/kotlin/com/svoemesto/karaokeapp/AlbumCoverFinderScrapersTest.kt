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
    fun `albumCoverSearchScrapersList читает явно подставленное значение настройки`() {
        // Настройка подставляется явно: модульный тест не должен зависеть от
        // /sm-karaoke/system/Karaoke.properties на машине, на которой он запущен
        // (прежний вариант читал живой файл и попутно создавал его по
        // абсолютному системному пути, если его не было).
        withAlbumCoverScrapersProperty("ddg;brave") {
            assertEquals(
                listOf("ddg", "brave"),
                AlbumCoverService.albumCoverSearchScrapersList(),
                "значение настройки должно быть разобрано по правилам parseAlbumCoverScrapers",
            )
        }
    }

    @Test
    fun `albumCoverSearchScrapersList без явного значения берёт defaultValue свойства`() {
        // Ключа нет в настройке — значение приходит из defaultValue свойства
        // (а не из fallback-константы). Сравнение вынесено в отдельное
        // утверждение: прежний тест проверял лишь «список непуст» и «нет пустых
        // токенов», что выполняется при ЛЮБОЙ конфигурации.
        withoutAlbumCoverScrapersProperty {
            val declaredDefault =
                listKaraokeProperties
                    .first { it.key == "albumCoverSearchScrapers" }
                    .defaultValue as String
            assertEquals(
                declaredDefault.split(";"),
                AlbumCoverService.albumCoverSearchScrapersList(),
                "без явного значения должен применяться defaultValue свойства albumCoverSearchScrapers",
            )
        }
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

    // --- helpers ---

    /**
     * Ключ служебной заглушки в глобальной карте свойств.
     *
     * Нужен, чтобы карта не была пустой: `KaraokeProperties.get()` на пустой
     * карте вызывает `loadPropertiesMap()`, который читает (и при отсутствии
     * СОЗДАЁТ) живой `/sm-karaoke/system/Karaoke.properties` — этого в
     * модульном тесте быть не должно.
     */
    private companion object {
        const val SENTINEL_KEY = "__test_sentinel__"
    }

    /**
     * Подставляет значение настройки `albumCoverSearchScrapers` в глобальную
     * карту свойств и возвращает её к прежнему виду.
     *
     * Глобальная карта — единственный способ задать значение настройки в
     * модульном тесте: `albumCoverSearchScrapersList()` читает только
     * `KaraokeProperties`, а читать живой файл машины нельзя.
     */
    private fun <T> withAlbumCoverScrapersProperty(
        value: String,
        block: () -> T,
    ): T = withPropertiesMap(SENTINEL_KEY, "albumCoverSearchScrapers", value, block)

    /** Убирает настройку `albumCoverSearchScrapers` из карты (сработает `defaultValue`). */
    private fun <T> withoutAlbumCoverScrapersProperty(block: () -> T): T =
        withPropertiesMap(SENTINEL_KEY, "albumCoverSearchScrapers", null, block)

    /**
     * Временно меняет содержимое глобальной карты свойств и возвращает её
     * к прежнему виду (в `finally`).
     *
     * @param sentinelKey ключ-заглушка, гарантирующий, что карта не пуста;
     * @param targetKey изменяемый ключ; `null` — ключ удаляется;
     * @param targetValue новое значение ключа.
     */
    private fun <T> withPropertiesMap(
        sentinelKey: String,
        targetKey: String,
        targetValue: String?,
        block: () -> T,
    ): T {
        val map = karaokePropertiesMap
        val hadTarget = map.containsKey(targetKey)
        val oldTarget = map[targetKey]
        val hadSentinel = map.containsKey(sentinelKey)
        val oldSentinel = map[sentinelKey]
        try {
            map[sentinelKey] = "sentinel"
            if (targetValue == null) {
                map.remove(targetKey)
            } else {
                map[targetKey] = targetValue
            }
            return block()
        } finally {
            if (hadTarget && oldTarget != null) {
                map[targetKey] = oldTarget
            } else {
                map.remove(targetKey)
            }
            if (hadSentinel && oldSentinel != null) {
                map[sentinelKey] = oldSentinel
            } else {
                map.remove(sentinelKey)
            }
        }
    }
}
