package com.svoemesto.karaokeapp

import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import java.net.URLEncoder

/**
 * Поиск текста песни на Яндекс.Музыке: тот же Playwright-профиль/подход к детекту
 * VPN/бот-детекта/истёкшей авторизации, что и [findYandexAlbumCovers] в AlbumCoverFinder.kt,
 * но вместо страницы альбомов автора — страница поиска треков и сама страница трека.
 *
 * Порядок: поиск по "автор название" (music.yandex.ru/search?type=tracks) → первая ссылка на
 * трек → открытие вкладки "Текст" на странице трека → извлечение видимого текста из DOM.
 * Сознательно НЕ парсим встроенный в HTML JSON (как для обложек) — значение текста песни там
 * многократно JSON-экранировано, и риск сохранить в базу битый/наполовину-escaped текст выше,
 * чем риск просто не найти текст (в этом случае вызывающий код падает на обычный SearXNG-поиск).
 */
sealed class YandexLyricsSearchOutcome {
    data class Found(
        val text: String,
    ) : YandexLyricsSearchOutcome()

    data class NotFound(
        val reason: String,
    ) : YandexLyricsSearchOutcome()
}

internal val TRACK_HREF_REGEX = Regex("""/album/\d+/track/\d+""")

/** Общая для findYandexAlbumCovers/findYandexSongLyrics проверка "страница показывает проблему, а не контент". */
internal fun detectYandexPageProblem(
    currentUrl: String,
    html: String,
): YandexLyricsSearchOutcome.NotFound? {
    if (currentUrl.contains("passport.yandex") || currentUrl.contains("id.yandex")) {
        return YandexLyricsSearchOutcome.NotFound("истекла авторизация в Яндекс.Музыке — требуется переавторизация")
    }
    if (html.contains("недоступна в вашем регионе", ignoreCase = true)) {
        return YandexLyricsSearchOutcome.NotFound("Яндекс.Музыка недоступна из-за включённого VPN")
    }
    if (html.contains("Нам очень жаль, но запросы с вашего устройства похожи на автоматические")) {
        return YandexLyricsSearchOutcome.NotFound("Яндекс.Музыка временно заблокировала автоматические запросы")
    }
    return null
}

fun findYandexSongLyrics(
    author: String,
    songName: String,
): YandexLyricsSearchOutcome {
    val songNameForFind = songName.replace(Regex("""\([^)]*\)"""), "").trim()
    val query = "$author $songNameForFind".trim()
    if (query.isBlank()) return YandexLyricsSearchOutcome.NotFound("пустой автор и название песни")

    // Прогрев профиля - по образцу fetchYandexArtistAlbumsHtml (AlbumCoverFinder.kt): первый заход
    // на главную в отдельном персистентном контексте, только затем полноценный запрос.
    Playwright.create().use { playwright ->
        val context =
            playwright.chromium().launchPersistentContext(
                USER_DATA_DIR,
                BrowserType
                    .LaunchPersistentContextOptions()
                    .setHeadless(true)
                    .setLocale("ru-RU")
                    .setTimezoneId("Europe/Moscow"),
            )
        val page = context.pages().firstOrNull() ?: context.newPage()
        try {
            page.navigate("https://music.yandex.ru/")
        } finally {
            context.close()
        }
    }

    Playwright.create().use { playwright ->
        val context =
            playwright.chromium().launchPersistentContext(
                USER_DATA_DIR,
                BrowserType
                    .LaunchPersistentContextOptions()
                    .setHeadless(true)
                    .setLocale("ru-RU")
                    .setTimezoneId("Europe/Moscow"),
            )
        val page = context.newPage()
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            page.navigate("https://music.yandex.ru/search?text=$encodedQuery&type=tracks")

            var currentUrl = page.url()
            var html = page.content()
            detectYandexPageProblem(currentUrl, html)?.let { return it }

            val trackHref =
                TRACK_HREF_REGEX.find(html)?.value
                    ?: return YandexLyricsSearchOutcome.NotFound("трек «$query» не найден в поиске Яндекс.Музыки")

            page.navigate("https://music.yandex.ru$trackHref")
            currentUrl = page.url()
            html = page.content()
            detectYandexPageProblem(currentUrl, html)?.let { return it }

            // Вкладка "Текст" на странице трека - best-effort клик, если сразу не раскрыта.
            // Не критично, если не получится (не бросаем исключение) - просто попытаемся
            // прочитать DOM как есть.
            try {
                val lyricsTab = page.locator("button:has-text('Текст'), a:has-text('Текст')").first()
                if (lyricsTab.count() > 0) {
                    lyricsTab.click(Locator.ClickOptions().setTimeout(3000.0))
                    page.waitForTimeout(1500.0)
                }
            } catch (e: Exception) {
                println("findYandexSongLyrics: не удалось открыть вкладку 'Текст' на '$currentUrl': ${e.message}")
            }

            val lyricsText = extractLyricsFromDom(page)
            if (!lyricsText.isNullOrBlank()) return YandexLyricsSearchOutcome.Found(lyricsText)

            println(
                "findYandexSongLyrics: текст не найден на странице трека '$currentUrl'. " +
                    "html[0..500]='${page.content().take(500)}'",
            )
            return YandexLyricsSearchOutcome.NotFound("текст не найден на странице трека Яндекс.Музыки")
        } finally {
            context.close()
        }
    }
}

/**
 * Ищет на странице трека блок с текстом песни: перебирает элементы, у которых класс содержит
 * "Lyrics" (устойчивый BEM-префикс React-компонентов Яндекс.Музыки, см. уже используемый в
 * этом проекте `AlbumCard_titleLink` в [getAlbumCardTitle]), и берёт самый длинный видимый
 * текст среди них (короткие совпадения - обычно подписи вкладок/кнопок, а не сам текст).
 */
private fun extractLyricsFromDom(page: Page): String? {
    val locator = page.locator("[class*='Lyrics']")
    val count =
        try {
            locator.count()
        } catch (e: Exception) {
            0
        }
    if (count == 0) return null

    var longest = ""
    for (i in 0 until minOf(count, 15)) {
        val text =
            try {
                locator.nth(i).innerText()
            } catch (e: Exception) {
                ""
            }
        if (text.trim().length > longest.trim().length) longest = text
    }
    return longest.trim().takeIf { it.length > 40 }
}
