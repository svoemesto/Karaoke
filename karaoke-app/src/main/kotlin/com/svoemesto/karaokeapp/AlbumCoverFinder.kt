package com.svoemesto.karaokeapp

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Playwright
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import javax.imageio.ImageIO

/**
 * Поиск и сохранение картинки альбома (LogoAlbum.png): поиск обложки на странице артиста
 * в Яндекс.Музыке (переиспользует Playwright-профиль из [UtilsPlaywright]), фолбэк — поиск
 * картинок через SearXNG, скачивание кандидата и defensive center-crop + resize до 400×400.
 *
 * @see docs/features/llm-lyrics-search.md (аналогичная инфраструктура поиска)
 */
enum class AlbumCoverSource { YANDEX_MUSIC, SEARXNG }

data class AlbumCoverCandidate(
    val sourceUrl: String,
    val source: AlbumCoverSource,
)

sealed class AlbumCoverSearchOutcome {
    data class Found(
        val candidates: List<AlbumCoverCandidate>,
        val note: String = "",
    ) : AlbumCoverSearchOutcome()

    data class NotFound(
        val reason: String,
    ) : AlbumCoverSearchOutcome()
}

data class YandexAlbumEntry(
    val title: String,
    val coverUri: String,
)

private sealed class YandexAlbumsPageResult {
    data class Html(
        val html: String,
    ) : YandexAlbumsPageResult()

    object VpnBlocked : YandexAlbumsPageResult()

    object AuthExpired : YandexAlbumsPageResult()

    object BotDetected : YandexAlbumsPageResult()
}

/**
 * Заход на страницу альбомов автора Яндекс.Музыки — по образцу [searchLastAlbumYm3], но
 * возвращает сырой HTML (нужны ВСЕ альбомы, а не только заголовок первого/последнего).
 */
private fun fetchYandexArtistAlbumsHtml(authorYmId: String): YandexAlbumsPageResult {
    val authorUrl = "https://music.yandex.ru/artist/$authorYmId"
    val searchUrl = "$authorUrl/albums"

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
            page.navigate(searchUrl)
            val currentUrl = page.url()
            val html = page.content()

            if (currentUrl.contains("passport.yandex") || currentUrl.contains("id.yandex")) {
                return YandexAlbumsPageResult.AuthExpired
            }
            if (html.contains("недоступна в вашем регионе", ignoreCase = true)) {
                return YandexAlbumsPageResult.VpnBlocked
            }
            if (html.contains("Нам очень жаль, но запросы с вашего устройства похожи на автоматические")) {
                return YandexAlbumsPageResult.BotDetected
            }
            return YandexAlbumsPageResult.Html(html)
        } finally {
            context.close()
        }
    }
}

/**
 * Разбирает HTML страницы альбомов артиста: достаёт `title`+`coverUri` КАЖДОГО альбома
 * (не только первого, как [searchLastAlbumYm3]).
 */
fun parseYandexAlbumEntries(html: String): List<YandexAlbumEntry> {
    // Конечный маркер — просто следующая экранированная кавычка (не завязан на порядок полей,
    // в отличие от searchLastAlbumYm3, который берёт следующую как ",\""). Обычная (не raw)
    // строка здесь — чтобы не считать вручную кавычки в тройных raw-литералах.
    val escapedQuote = "\\\""
    val preloadedAlbums = html.extractBalancedBracesFromString("""\"preloadedAlbums\":""")
    val albumObjects = preloadedAlbums.extractAllBalancedBraceObjects("""\"albums\":""")
    return albumObjects.mapNotNull { obj ->
        val title = obj.textBetween("""\"title\":\"""", escapedQuote)
        if (title.isEmpty()) return@mapNotNull null
        val coverUri = obj.textBetween("""\"coverUri\":\"""", escapedQuote)
        YandexAlbumEntry(title = title, coverUri = coverUri)
    }
}

/**
 * `coverUri` с Яндекс.Музыки обычно приходит без протокола, вида
 * `avatars.yandex.net/get-music-content/.../%%` — `%%` заменяется на желаемый размер.
 * Берём максимальное качество (1000x1000), с фолбэком на `/orig`, если плейсхолдера нет.
 */
fun yandexCoverUriToUrl(coverUri: String): String {
    if (coverUri.isBlank()) return ""
    val withoutSlashes = coverUri.trim().trimStart('/')
    val withHost = if (withoutSlashes.startsWith("http")) withoutSlashes else "https://$withoutSlashes"
    return if (withHost.contains("%%")) withHost.replace("%%", "1000x1000") else "$withHost/orig"
}

/**
 * Ищет обложку альбома [albumTitle] в списке альбомов автора [authorYmId] на Яндекс.Музыке.
 * Совпадение по названию — case-insensitive, `contains` в обе стороны (учитывает суффиксы вроде
 * "(Deluxe Edition)"). Если совпало несколько альбомов — возвращает кандидатов от каждого,
 * не гадает.
 */
fun findYandexAlbumCovers(
    authorYmId: String,
    albumTitle: String,
): AlbumCoverSearchOutcome {
    if (authorYmId.isBlank()) return AlbumCoverSearchOutcome.NotFound("у автора не указан ymId (id на Яндекс.Музыке)")

    return when (val pageResult = fetchYandexArtistAlbumsHtml(authorYmId)) {
        is YandexAlbumsPageResult.VpnBlocked ->
            AlbumCoverSearchOutcome.NotFound("Яндекс.Музыка недоступна из-за включённого VPN")
        is YandexAlbumsPageResult.AuthExpired ->
            AlbumCoverSearchOutcome.NotFound("истекла авторизация в Яндекс.Музыке — требуется переавторизация")
        is YandexAlbumsPageResult.BotDetected ->
            AlbumCoverSearchOutcome.NotFound("Яндекс.Музыка временно заблокировала автоматические запросы")
        is YandexAlbumsPageResult.Html -> {
            val entries = parseYandexAlbumEntries(pageResult.html)
            if (entries.isEmpty()) {
                println(
                    "findYandexAlbumCovers: не удалось распознать список альбомов автора '$authorYmId'. " +
                        "html[0..500]='${pageResult.html.take(500)}'",
                )
                return AlbumCoverSearchOutcome.NotFound("не удалось получить список альбомов автора")
            }
            val normalizedTarget = albumTitle.trim().lowercase()
            val matched =
                entries.filter { entry ->
                    val normalizedEntry = entry.title.trim().lowercase()
                    normalizedEntry.isNotEmpty() &&
                        (normalizedEntry.contains(normalizedTarget) || normalizedTarget.contains(normalizedEntry))
                }
            if (matched.isEmpty()) {
                return AlbumCoverSearchOutcome.NotFound(
                    "альбом «$albumTitle» не найден в списке альбомов автора на Яндекс.Музыке",
                )
            }
            val candidates =
                matched
                    .mapNotNull { entry ->
                        val url = yandexCoverUriToUrl(entry.coverUri)
                        if (url.isEmpty()) null else AlbumCoverCandidate(sourceUrl = url, source = AlbumCoverSource.YANDEX_MUSIC)
                    }.distinctBy { it.sourceUrl }
            if (candidates.isEmpty()) {
                AlbumCoverSearchOutcome.NotFound("альбом найден, но обложка отсутствует на странице Яндекс.Музыки")
            } else {
                AlbumCoverSearchOutcome.Found(candidates)
            }
        }
    }
}

/** Скачивает картинку по внешнему URL. Используется и для сохранения, и для CORS-прокси. */
fun downloadImageBytes(url: String): ByteArray? =
    try {
        val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
        val request =
            HttpRequest
                .newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36",
                ).GET()
                .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofByteArray())
        if (response.statusCode() in 200..299) response.body() else null
    } catch (e: Exception) {
        println("downloadImageBytes: ошибка скачивания '$url': ${e.message}")
        null
    }

/**
 * Defensive-обработка на бэкенде: если пришедшая картинка не ровно 400×400 (обычно фронт уже
 * присылает готовый кроп 400×400, но не полагаемся на это) — вырезаем центральный квадрат
 * и масштабируем.
 */
fun cropCenterSquareAndResize(
    bytes: ByteArray,
    targetSize: Int = 400,
): BufferedImage? =
    try {
        val original = ImageIO.read(ByteArrayInputStream(bytes))
        if (original == null) {
            null
        } else if (original.width == targetSize && original.height == targetSize) {
            original
        } else {
            val side = minOf(original.width, original.height)
            val x = (original.width - side) / 2
            val y = (original.height - side) / 2
            val square = original.getSubimage(x, y, side, side)
            if (side == targetSize) square else resizeBufferedImage(square, targetSize, targetSize)
        }
    } catch (e: Exception) {
        println("cropCenterSquareAndResize: ошибка обработки изображения: ${e.message}")
        null
    }

@JsonIgnoreProperties(ignoreUnknown = true)
data class SearxngImageSearchResponse(
    val results: List<SearxngImageSearchResult> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SearxngImageSearchResult(
    @JsonProperty("img_src") val imgSrc: String = "",
    @JsonProperty("thumbnail_src") val thumbnailSrc: String = "",
    val title: String = "",
)

/**
 * Оркестратор поиска обложки альбома: Яндекс.Музыка (если у автора есть `ymId`) с фолбэком на
 * SearXNG image-поиск. Вынесен в отдельный Spring-компонент (а не top-level функция), т.к.
 * нужен `searxng.base-url` из конфигурации — по образцу `llm/Tools.kt#SearchTool`.
 */
@Component
class AlbumCoverService(
    @Value("\${searxng.base-url:http://searxng:8080}")
    private val searxngBaseUrl: String,
    private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(AlbumCoverService::class.java)
    private val httpClient =
        HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build()

    fun defaultSearchQuery(
        author: String,
        album: String,
    ): String = "$author $album обложка альбома"

    fun search(
        authorYmId: String?,
        author: String,
        album: String,
        skipYandex: Boolean = false,
        customQuery: String? = null,
    ): AlbumCoverSearchOutcome {
        if (!skipYandex && !authorYmId.isNullOrBlank()) {
            if (isVpnActive()) {
                logger.info("AlbumCoverService: VPN активен на этой машине — пропускаем Яндекс.Музыку для '$author - $album'")
            } else {
                val yandexResult = findYandexAlbumCovers(authorYmId, album)
                if (yandexResult is AlbumCoverSearchOutcome.Found) return yandexResult
                logger.info(
                    "AlbumCoverService: Яндекс.Музыка не дала результата для '$author - $album': " +
                        (yandexResult as AlbumCoverSearchOutcome.NotFound).reason,
                )
            }
        }
        val query = customQuery?.trim()?.takeIf { it.isNotEmpty() } ?: defaultSearchQuery(author, album)
        val fallbackCandidates = searchSearxngImages(query)
        return if (fallbackCandidates.isNotEmpty()) {
            AlbumCoverSearchOutcome.Found(fallbackCandidates, note = "Найдено через общий веб-поиск (не Яндекс.Музыка)")
        } else {
            AlbumCoverSearchOutcome.NotFound("ничего не найдено ни на Яндекс.Музыке, ни через общий веб-поиск")
        }
    }

    fun searchSearxngImages(query: String): List<AlbumCoverCandidate> =
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$searxngBaseUrl/search?q=$encodedQuery&format=json&language=ru&categories=images"
            val request =
                HttpRequest
                    .newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "application/json")
                    .GET()
                    .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) {
                logger.error("AlbumCoverService.searchSearxngImages: SearXNG вернул статус ${response.statusCode()}")
                emptyList()
            } else {
                val searchResponse = objectMapper.readValue(response.body(), SearxngImageSearchResponse::class.java)
                searchResponse.results
                    .mapNotNull { r -> if (r.imgSrc.isBlank()) null else AlbumCoverCandidate(sourceUrl = r.imgSrc, source = AlbumCoverSource.SEARXNG) }
                    .distinctBy { it.sourceUrl }
                    .take(24)
            }
        } catch (e: Exception) {
            logger.error("AlbumCoverService.searchSearxngImages: ошибка: ${e.message}", e)
            emptyList()
        }
}
