package com.svoemesto.karaokeapp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Офлайн-проверка чистых кусков поиска текста песни на Яндекс.Музыке (без сети/Playwright) —
 * по образцу AlbumCoverFinderParsingTest.
 */
class YandexLyricsSearchOutcomeTest {
    @Test
    fun `TRACK_HREF_REGEX находит первую ссылку на трек в HTML результатов поиска`() {
        val html =
            "<html><a href=\"/album/111/track/222\">Song</a><a href=\"/album/111/track/333\">Other</a></html>"
        val match = TRACK_HREF_REGEX.find(html)?.value
        assertEquals("/album/111/track/222", match)
    }

    @Test
    fun `TRACK_HREF_REGEX возвращает null без совпадений`() {
        assertNull(TRACK_HREF_REGEX.find("<html>ничего нет</html>"))
    }

    @Test
    fun `detectYandexPageProblem распознаёт истёкшую авторизацию по url`() {
        val result = detectYandexPageProblem("https://passport.yandex.ru/auth", "<html></html>")
        assertTrue(result is YandexLyricsSearchOutcome.NotFound)
        assertTrue((result as YandexLyricsSearchOutcome.NotFound).reason.contains("авторизация"))
    }

    @Test
    fun `detectYandexPageProblem распознаёт региональную блокировку`() {
        val result =
            detectYandexPageProblem(
                "https://music.yandex.ru/search",
                "<html>Сервис недоступна в вашем регионе</html>",
            )
        assertTrue(result is YandexLyricsSearchOutcome.NotFound)
        assertTrue((result as YandexLyricsSearchOutcome.NotFound).reason.contains("VPN"))
    }

    @Test
    fun `detectYandexPageProblem распознаёт бот-детект`() {
        val result =
            detectYandexPageProblem(
                "https://music.yandex.ru/search",
                "<html>Нам очень жаль, но запросы с вашего устройства похожи на автоматические</html>",
            )
        assertTrue(result is YandexLyricsSearchOutcome.NotFound)
        assertTrue((result as YandexLyricsSearchOutcome.NotFound).reason.contains("заблокировала"))
    }

    @Test
    fun `detectYandexPageProblem возвращает null для нормальной страницы`() {
        assertNull(detectYandexPageProblem("https://music.yandex.ru/search", "<html>всё в порядке</html>"))
    }
}
