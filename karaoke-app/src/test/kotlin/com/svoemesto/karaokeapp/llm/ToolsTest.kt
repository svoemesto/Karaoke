package com.svoemesto.karaokeapp.llm

import com.svoemesto.karaokeapp.listKaraokeProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Офлайн-проверка чистой функции [filterUselessLyricsUrls] (без сети/fourget) —
 * по образцу AlbumCoverFinderParsingTest. Покрывает все 7 правил FR-004 спеки 294,
 * happy path и edge cases.
 *
 * `internal` — видна из тестов в том же модуле (но не из других модулей).
 */
internal class ToolsTest {
    @Test
    fun `filter отбрасывает невалидный URL`() {
        // Правило FR-004 п.1: URI.create throws → отбрасываем.
        val input =
            listOf(
                "not a url",
                "http://",
                "https://example.com/song",
            )
        val expected = listOf("https://example.com/song")
        assertEquals(expected, filterUselessLyricsUrls(input))
    }

    @Test
    fun `filter отбрасывает неподдерживаемые схемы`() {
        // Правило FR-004 п.2: scheme ≠ http/https.
        val input =
            listOf(
                "ftp://example.com/file",
                "mailto:user@example.com",
                "javascript:alert(1)",
                "https://example.com/page",
            )
        val expected = listOf("https://example.com/page")
        assertEquals(expected, filterUselessLyricsUrls(input))
    }

    @Test
    fun `filter отбрасывает homepage без path`() {
        // Правило FR-004 п.3: URI.path == "" или "/".
        val input =
            listOf(
                "https://example.com",
                "https://example.com/",
                "https://example.com/page",
            )
        val expected = listOf("https://example.com/page")
        assertEquals(expected, filterUselessLyricsUrls(input))
    }

    @Test
    fun `filter отбрасывает служебные path case-insensitive`() {
        // Правило FR-004 п.4: substring match (case-insensitive).
        val input =
            listOf(
                "https://example.com/login",
                "https://example.com/wp-login.php",
                "https://example.com/sitemap.xml",
                "https://example.com/feed",
                "https://example.com/Search",
                "https://example.com/real-song",
            )
        val expected = listOf("https://example.com/real-song")
        assertEquals(expected, filterUselessLyricsUrls(input))
    }

    @Test
    fun `filter отбрасывает файлы по расширению`() {
        // Правило FR-004 п.5: расширения в URL.
        val input =
            listOf(
                "https://example.com/song.pdf",
                "https://example.com/track.mp3",
                "https://example.com/cover.jpg",
                "https://example.com/page",
            )
        val expected = listOf("https://example.com/page")
        assertEquals(expected, filterUselessLyricsUrls(input))
    }

    @Test
    fun `filter отбрасывает tracking-маркеры но оставляет легитимные query-параметры`() {
        // Правило FR-004 п.6: tracking-маркеры → отбрасываем; ?id=, ?page= → оставляем.
        val input =
            listOf(
                "https://example.com/song?utm_source=vk",
                "https://example.com/song?fbclid=abc",
                "https://example.com/song?id=12345",
                "https://example.com/song?page=2",
            )
        val expected =
            listOf(
                "https://example.com/song?id=12345",
                "https://example.com/song?page=2",
            )
        assertEquals(expected, filterUselessLyricsUrls(input))
    }

    @Test
    fun `filter дедуплицирует сохраняя порядок первого появления`() {
        // Правило FR-004 п.7: LinkedHashSet сохраняет порядок.
        val input =
            listOf(
                "https://a.com/x",
                "https://b.com/y",
                "https://a.com/x",
            )
        val expected = listOf("https://a.com/x", "https://b.com/y")
        assertEquals(expected, filterUselessLyricsUrls(input))
    }

    @Test
    fun `filter happy path — все URL чистые и в исходном порядке`() {
        val input =
            listOf(
                "https://amalgama-lab.com/texts/po/kino/gruppakrovi",
                "https://www.lyrics.com/lyric/12345",
                "https://genius.com/Kino-gruppa-krovi-lyrics",
                "https://tekst-pesni.online/kino/gruppakrovi",
                "https://example.com/songs/123",
            )
        assertEquals(input, filterUselessLyricsUrls(input))
    }

    @Test
    fun `filter edge case — пустой вход даёт пустой выход`() {
        assertEquals(emptyList<String>(), filterUselessLyricsUrls(emptyList()))
    }

    @Test
    fun `filter edge case — все URL мусор даёт пустой выход`() {
        // Провоцирует fallback в SearchTool.searchUrls на следующий scraper.
        val input =
            listOf(
                "https://example.com/",
                "https://example.com/login",
                "https://example.com/sitemap.xml",
                "https://example.com/song.pdf",
            )
        assertEquals(emptyList<String>(), filterUselessLyricsUrls(input))
    }

    @Test
    fun `tracking-маркер ловится на границе параметра, а не как голая подстрока`() {
        // Pass 461. Раньше проверка была голым contains по всему URL, поэтому паттерн
        // `ref=` находился и внутри честного параметра `?pref=...` — живая ссылка на
        // текст песни отбрасывалась как мусор.
        val input =
            listOf(
                "https://example.com/song?pref=abc", // НЕ должен отбрасываться
                "https://example.com/song?ref=abc", // должен
                "https://example.com/song?a=1&ref=abc", // должен
                "https://example.com/song?utm_source=vk", // должен (?)
                "https://example.com/song?a=1&utm_source=vk", // должен (&)
            )
        val expected =
            listOf(
                "https://example.com/song?pref=abc",
            )
        assertEquals(expected, filterUselessLyricsUrls(input))
    }

    @Test
    fun `matchesUselessUrlPattern — чистая проверка обоих режимов`() {
        // Паттерн с '=' требует границы параметра.
        assertTrue(matchesUselessUrlPattern("https://e.com/s?ref=1", "ref="))
        assertTrue(matchesUselessUrlPattern("https://e.com/s?a=1&ref=1", "ref="))
        assertFalse(matchesUselessUrlPattern("https://e.com/s?pref=1", "ref="), "pref= не является ref=")
        assertFalse(matchesUselessUrlPattern("https://e.com/ref=1", "ref="), "в path границы параметра нет")
        // Паттерн без '=' работает подстрокой, как раньше.
        assertTrue(matchesUselessUrlPattern("https://e.com/sitemap.xml", "/sitemap.xml"))
        assertTrue(matchesUselessUrlPattern("https://e.com/a/report.pdf", ".pdf"))
        assertFalse(matchesUselessUrlPattern("https://e.com/song", ".pdf"))
    }

    @Test
    fun `паттерны без '=' по-прежнему ловятся подстрокой в любой части URL`() {
        val input =
            listOf(
                "https://example.com/wp-login.php",
                "https://example.com/files/song.pdf",
                "https://example.com/song/lyrics",
            )
        assertEquals(listOf("https://example.com/song/lyrics"), filterUselessLyricsUrls(input))
    }

    @Test
    fun `дефолт scrapers в Tools совпадает с дефолтом в KaraokeProperties`() {
        // Дефолт порядка scrapers объявлен ДВАЖДЫ: как defaultValue одноимённого
        // KaraokeProperty (читается, когда ключа нет в Karaoke.properties) и как
        // fallback-константа SearchTool.DEFAULT_LYRICS_SEARCH_SCRAPERS (срабатывает,
        // когда значение в Karaoke.properties пустое или из одних ';').
        // Расхождение означает разное поведение на чистом стенде и на стенде с
        // испорченной настройкой — этот тест его ловит.
        val declaredDefault =
            listKaraokeProperties
                .first { it.key == "lyricsSearchScrapers" }
                .defaultValue as String
        assertEquals(
            "google_cse;yahoo_japan;brave;yep",
            declaredDefault,
            "defaultValue свойства lyricsSearchScrapers изменился — синхронизируйте " +
                "SearchTool.DEFAULT_LYRICS_SEARCH_SCRAPERS и этот тест",
        )
        assertEquals(
            declaredDefault.split(";"),
            SearchTool.DEFAULT_LYRICS_SEARCH_SCRAPERS,
            "DEFAULT_LYRICS_SEARCH_SCRAPERS в Tools.kt разошёлся с defaultValue " +
                "свойства lyricsSearchScrapers в KaraokeProperties.kt",
        )
    }
}
