package com.svoemesto.karaokeapp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Офлайн-проверка парсинга страницы альбомов Яндекс.Музыки (без сети/Playwright) — на
 * синтетическом HTML, имитирующем реальную структуру (экранированный JSON внутри JSON,
 * та же особенность, что уже учтена [extractBalancedBracesFromString]/[textBetween]).
 */
class AlbumCoverFinderParsingTest {
    private fun buildSyntheticHtml(): String {
        val prefix = "<html><script>window.state = {\"foo\":1,"
        val preloadedAlbums =
            "\\\"preloadedAlbums\\\":{\\\"other\\\":1,\\\"albums\\\":[" +
                "{\\\"id\\\":111,\\\"title\\\":\\\"Album One\\\",\\\"coverUri\\\":" +
                "\\\"avatars.yandex.net/get-music-content/111/%%\\\"}," +
                "{\\\"id\\\":222,\\\"title\\\":\\\"Album Two (Deluxe Edition)\\\",\\\"coverUri\\\":" +
                "\\\"avatars.yandex.net/get-music-content/222/%%\\\"}," +
                "{\\\"id\\\":333,\\\"title\\\":\\\"Album Without Cover\\\",\\\"coverUri\\\":\\\"\\\"}" +
                "]}"
        val suffix = "};</script></html>"
        return prefix + preloadedAlbums + suffix
    }

    @Test
    fun `parseYandexAlbumEntries извлекает все альбомы, а не только первый`() {
        val entries = parseYandexAlbumEntries(buildSyntheticHtml())

        assertEquals(3, entries.size)
        assertEquals("Album One", entries[0].title)
        assertEquals("avatars.yandex.net/get-music-content/111/%%", entries[0].coverUri)
        assertEquals("Album Two (Deluxe Edition)", entries[1].title)
        assertEquals("avatars.yandex.net/get-music-content/222/%%", entries[1].coverUri)
        assertEquals("Album Without Cover", entries[2].title)
        assertEquals("", entries[2].coverUri)
    }

    @Test
    fun `yandexCoverUriToUrl подставляет протокол и максимальный размер`() {
        val url = yandexCoverUriToUrl("avatars.yandex.net/get-music-content/111/%%")
        assertEquals("https://avatars.yandex.net/get-music-content/111/1000x1000", url)
    }

    @Test
    fun `yandexCoverUriToUrl откатывается на orig без плейсхолдера размера`() {
        val url = yandexCoverUriToUrl("avatars.yandex.net/get-music-content/111/fixed")
        assertEquals("https://avatars.yandex.net/get-music-content/111/fixed/orig", url)
    }

    @Test
    fun `yandexCoverUriToUrl возвращает пустую строку для пустого coverUri`() {
        assertEquals("", yandexCoverUriToUrl(""))
    }

    @Test
    fun `extractAllBalancedBraceObjects возвращает пустой список без совпадения startWord`() {
        val objects = "no albums here".extractAllBalancedBraceObjects("""\"albums\":""")
        assertTrue(objects.isEmpty())
    }
}
