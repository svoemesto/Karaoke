package com.svoemesto.karaokeapp

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.type.TypeReference
import java.net.URL
import java.util.Base64
import java.nio.file.Files
import java.nio.file.Paths
import javax.net.ssl.HttpsURLConnection
import com.fasterxml.jackson.databind.ObjectMapper
import com.svoemesto.karaokeapp.model.SearchAsync
import com.svoemesto.karaokeapp.model.SearchResponseFormat
import com.svoemesto.karaokeapp.model.Settings
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.File
import java.sql.Timestamp
import java.time.LocalDateTime
import kotlin.String
import kotlin.text.replace

suspend fun main000() {
//    println(findSongText("Nautilus Pompilius", "Мальчик зима"))
}

fun parseXmlUrls(xmlText: String): List<String> {
    val urls = mutableListOf<String>()
    val urlRegex = Regex("<url>(.*?)</url>")

    urlRegex.findAll(xmlText)
        .forEach { match ->
            urls.add(match.groupValues[1])
        }

    return urls
}

fun parseHtmlUrls(xmlText: String): List<String> {
    val urls = mutableListOf<String>()
    val urlRegex = Regex("<url>(.*?)</url>")

    urlRegex.findAll(xmlText)
        .forEach { match ->
            urls.add(match.groupValues[1])
        }

    return urls
}

@Serializable
data class FindSongResult(
    val id: Int,
    val author: String,
    val songName: String,
    val link: String,
    val domain: String,
    val findedText: String
)

fun getIamToken(): String {
    // Получаем дату последнего получения токена
    val requestIamTokenLastTimeMs = Karaoke.requestIamTokenLastTimeMs
    // Получаем таймаут между запросами токенов
    val requestIamTokenTimeoutMs = Karaoke.requestIamTokenTimeoutMs
    // Получаем текущее время
    val currentDateTime = LocalDateTime.now().toEpochMillis()
    // Проверяем, нужно ли перезапрашивать токен
    val needReRequestIamToken = (currentDateTime - requestIamTokenLastTimeMs) > requestIamTokenTimeoutMs
    // Перезапрашиваем токен, если нужно
    if (needReRequestIamToken) {
        createNewIamToken()
    }
    return Karaoke.requestIamToken
}

fun getYandexSearch(settings: Settings, countInPage: Int = 100, responseFormat: SearchResponseFormat = SearchResponseFormat.FORMAT_XML, async: Boolean = false): SearchAsync {
    // Ищем, есть ли уже в наличии для заданной песни SearchAsync. Если есть - возвращаем первый/единственный
    println("Начинаем получение ${if (async) "АСИНХРОННОГО" else "СИНХРОННОГО"} запроса поиска для песни ${settings.fileName}.")
    val searchAsyncList = SearchAsync.getSearchAsyncListBySongId(
        songId = settings.id,
        database = settings.database,
        storageApiClient = settings.storageApiClient,
        storageService = settings.storageService
    )
    if (searchAsyncList.isNotEmpty()) {
        println("Ранее созданный запрос найден в базе данных, возвращаем его.")
        return searchAsyncList.first()
    }

    val iamToken = getIamToken()
    val folderId = Karaoke.yandexCloudFolderId

    val author = settings.author
    val songName = settings.songName
    val songNameForFind = songName.replace(Regex("""\([^)]*\)"""), "").trim()

    val queryText = "$author текст песни $songNameForFind"

    println("Запрос будет выполнен для поисковой сроки: '$queryText'")

    val requestUrl = if (async) {
        Karaoke.requestAsyncUrl
    } else {
        Karaoke.requestSyncUrl
    }
    val url = URL(requestUrl)

    val connection = url.openConnection() as HttpsURLConnection

    try {
        connection.apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Bearer $iamToken")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            doOutput = true
            connectTimeout = 10000
            readTimeout = 30000
        }

        val body = """
            {
                "query": {
                  "searchType": "SEARCH_TYPE_RU",
                  "queryText": "$queryText",
                  "familyMode": "FAMILY_MODE_NONE",
                  "page": "0",
                  "fixTypoMode": "FIX_TYPO_MODE_OFF"
                },
                "sortSpec": {
                  "sortMode": "SORT_MODE_BY_RELEVANCE",
                  "sortOrder": "SORT_ORDER_DESC"
                },
                "groupSpec": {
                  "groupMode": "GROUP_MODE_FLAT",
                  "groupsOnPage": "$countInPage",
                  "docsInGroup": "1"
                },
                "maxPassages": "4",
                "region": "RU",
                "l10N": "LOCALIZATION_RU",
                "folderId": "$folderId",
                "responseFormat": "$responseFormat"
            }
        """.trimIndent()

        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

        val responseCode = connection.responseCode

        if (responseCode == 200) {
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val mapper = ObjectMapper()

            if (async) {

                val apiResponse: ApiResponseAsync = mapper.readValue(response, object : TypeReference<ApiResponseAsync>() {})
                return apiResponse.id?.let { operationId ->
                    if (operationId.isNotEmpty()) {
                        println("Получен operationId = '$operationId'")
                        val result = SearchAsync()
                        result.songId = settings.id
                        result.url = requestUrl
                        result.iamToken = iamToken
                        result.query = queryText
                        result.body = body
                        result.responseFormat = responseFormat.name
                        result.operationId = operationId
                        result.done = apiResponse.done ?: false
                        val savedResult = SearchAsync.createNewSearchAsync(newSearchAsync = result, database = settings.database)
                        if (savedResult != null) {
                            println("Асинхронный запрос успешно создан. id = '${savedResult.id}', operationId = '${savedResult.operationId}'")
                        } else {
                            println("Не удалось создать SearchAsync в базе данных. $result")
                        }
                        savedResult ?: throw RuntimeException("Не удалось создать SearchAsync в базе данных. $result")
                    } else {
                        println("Пустой id operations в ответе")
                        throw RuntimeException("Пустой id operations в ответе")
                    }
                } ?: throw RuntimeException("Поле id не найдено в ответе")

            } else {
                val apiResponse: ApiResponseSync = mapper.readValue(response, object : TypeReference<ApiResponseSync>() {})
                return apiResponse.rawData?.let { rawData ->
                    if (rawData.isNotEmpty()) {

                        val result = SearchAsync()
                        result.songId = settings.id
                        result.url = requestUrl
                        result.iamToken = iamToken
                        result.query = queryText
                        result.body = body
                        result.responseFormat = responseFormat.name
                        result.operationId = ""
                        result.done = true
                        result.rawData = String(Base64.getDecoder().decode(rawData))
                        val savedResult = SearchAsync.createNewSearchAsync(newSearchAsync = result, database = settings.database)
                        if (savedResult != null) {
                            println("Синхронный запрос успешно создан. id = '${savedResult.id}', символов в rawData = '${savedResult.rawData.length}'")
                        } else {
                            println("Не удалось создать SearchAsync (синхронный) в базе данных. $result")
                        }
                        savedResult ?: throw RuntimeException("Не удалось создать SearchAsync (синхронный) в базе данных. $result")

                    } else {
                        println("Пустой id operations в ответе")
                        throw RuntimeException("Пустой rawData в ответе")
                    }
                } ?: throw RuntimeException("Поле rawData не найдено в ответе")
            }

        } else {
            throw RuntimeException("Failed to search: $responseCode")
        }


    } catch (e: Exception) {
        println("Exception details: ${e.message}")
        e.printStackTrace()
        throw RuntimeException("HTTP request failed: ${e.message}", e)
    } finally {
        connection.disconnect()
    }

}

fun findSongText(settings: Settings, countInPage: Int = 100, countInResult: Int = 0): List<FindSongResult> {

    val author = settings.author
    val songName = settings.songName
    val songNameForFind = songName.replace(Regex("""\([^)]*\)"""), "").trim()
    val fileSearchedLinksAbsolutePath = settings.fileSearchedLinksAbsolutePath
    val xmlText = if (File(fileSearchedLinksAbsolutePath).exists()) {
        File(fileSearchedLinksAbsolutePath).readText()
    } else {
        val result = searchSongInYandex(author = author, songName = songNameForFind, countInPage = countInPage)
        File(fileSearchedLinksAbsolutePath).writeText(result, Charsets.UTF_8)
        result
    }
    println("Яндексе вернул документ размером ${xmlText.length} символов.")

    val links = parseXmlUrls(xmlText)
    val resultList = mutableListOf<FindSongResult>()
    println("Ссылок в документе: ${links.size}")
    var id = 0
    var skipedLinks = 0
    links.forEach { link ->

//        println(link)
        val html = getHtml(link)
        val domain = extractDomain(link)
//        println(domain)
        val classNamePrefixes = when (domain) {
            "genius.com" -> listOf("Lyrics__Container")
            "tekst-pesni.online" -> listOf("entry-content", "clearfix")
            "www.shazam.com" -> listOf("AppleMusicLyrics_lyricsBlock")
            "vk.com" -> listOf("vkitFeedShowMoreText")
            "darktexts.ru" -> listOf("full-text")
            "www.beesona.pro" -> listOf("copys")
            "alllyr.ru" -> listOf("inline")
            "lyricsworld.ru" -> listOf("songLyrics")
            "www.5lad.net" -> listOf("textofsong")
            "blatata.com" -> listOf("value")
            "lyrhub.com" -> listOf("lyric")
            "ru.ilyrics.net" -> listOf("space-y-4", "text-gray-700", "leading-relaxed")
            "singme.ru" -> listOf("song-text")
            "rush-sound.ru" -> listOf("chords")
            "guitarchords.ru", "muzbank.net" -> listOf("song")
            "rus-songs.com" -> listOf("post-content", "entry-content")
            "www.ukulele-akkordy.ru" -> listOf("textofsong")
            "teksty-pesenok.pro" -> listOf("tab-pane", "fade", "active", "in", "text_song")
            "texta-pesni.ru" -> listOf("mid_cont_left")
            "tekstmuz.ru" -> listOf("articles")
            "www.anekdotov-mnogo.ru" -> listOf("tmpLineUnderContent")

            "txtsong.ru",
            "my.mail.ru",
            "akkordus.ru",
            "l-hit.com",
            "textypesen.com",
            "m.song.guru",
            "ukula.ru",
            "text-pesni-perevod.ru",
            "www.songslyrics.ru",
            "guitary.ru",
            "music.yandex.ru",
            "www.oduvanchik.net" -> emptyList()
            else -> emptyList()
        }
        if (classNamePrefixes.isNotEmpty()) {
            val result = (findElementByText(html, classNamePrefixes, emptyList())?:"").trim()
            if (result.isNotBlank()) {
                id++
                resultList.add(
                    FindSongResult(
                        id = id,
                        author = author,
                        songName = songName,
                        link = link,
                        domain = domain,
                        findedText = result
                    )
                )
                if (countInResult in 1..id) {
                    println("Из ${links.size} ссылок пропущено $skipedLinks, возвращено ${resultList.size}, запрошено к возврату было $countInResult")
                    return resultList
                }
            } else {
                skipedLinks++
            }
        } else {
            skipedLinks++
        }
    }
    println("Из ${links.size} ссылок пропущено $skipedLinks, возвращено ${resultList.size}, запрошено к возврату было $countInResult")
    return resultList
}

@Serializable
@JsonIgnoreProperties(ignoreUnknown = true)
data class ApiResponseSync(
    @JsonProperty("rawData")
    val rawData: String? = null
)

@Serializable
@JsonIgnoreProperties(ignoreUnknown = true)
data class ApiResponseAsync(

    @SerialName("id")
    val id: String? = null,

    @SerialName("created_at")
    val createdAt: String? = null,

    @SerialName("created_by")
    val createdBy: String? = null,

    @SerialName("done")
    val done: Boolean? = null,

    @SerialName("response")
    val response: ApiResponseSync? = null,

    @SerialName("error")
    val error: String? = null,

    @SerialName("metadata")
    val metadata: String? = null,

    @SerialName("description")
    val description: String? = null

)

fun searchSongInYandex(author: String, songName: String, countInPage: Int = 10, page: Int = 0, async: Boolean = false): String {

    // Получаем дату последнего получения токена
    val requestIamTokenLastTimeMs = Karaoke.requestIamTokenLastTimeMs

    // Получаем таймаут между запросами токенов
    val requestIamTokenTimeoutMs = Karaoke.requestIamTokenTimeoutMs

    // Получаем текущее время
    val currentDateTime = LocalDateTime.now().toEpochMillis()

    // Проверяем, нужно ли перезапрашивать токен
    val needReRequestIamToken = (currentDateTime - requestIamTokenLastTimeMs) > requestIamTokenTimeoutMs

    if (needReRequestIamToken) {
        createNewIamToken()
    }
    val iamToken = Karaoke.requestIamToken
    val folderId = Karaoke.yandexCloudFolderId

    val queryText = "$author текст песни $songName"

    val url = if (async) {
        URL(Karaoke.requestAsyncUrl)
    } else {
        URL(Karaoke.requestSyncUrl)
    }

    val connection = url.openConnection() as HttpsURLConnection

    try {
        connection.apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Bearer $iamToken")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            doOutput = true
            connectTimeout = 10000
            readTimeout = 30000
        }

        val body = """
            {
                "query": {
                  "searchType": "SEARCH_TYPE_RU",
                  "queryText": "$queryText",
                  "familyMode": "FAMILY_MODE_NONE",
                  "page": "$page",
                  "fixTypoMode": "FIX_TYPO_MODE_OFF"
                },
                "sortSpec": {
                  "sortMode": "SORT_MODE_BY_RELEVANCE",
                  "sortOrder": "SORT_ORDER_DESC"
                },
                "groupSpec": {
                  "groupMode": "GROUP_MODE_FLAT",
                  "groupsOnPage": "$countInPage",
                  "docsInGroup": "1"
                },
                "maxPassages": "4",
                "region": "RU",
                "l10N": "LOCALIZATION_RU",
                "folderId": "$folderId",
                "responseFormat": "FORMAT_XML"
            }
        """.trimIndent()

        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

        val responseCode = connection.responseCode

        if (responseCode == 200) {
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val mapper = ObjectMapper()

            if (async) {

                val apiResponse: ApiResponseAsync = mapper.readValue(response, object : TypeReference<ApiResponseAsync>() {})
                return apiResponse.id?.let {
                    it.ifEmpty {
                        throw RuntimeException("Пустой id operations в ответе")
                    }
                } ?: throw RuntimeException("Поле id не найдено в ответе")

            } else {
                val apiResponse: ApiResponseSync = mapper.readValue(response, object : TypeReference<ApiResponseSync>() {})
                return apiResponse.rawData?.let {
                    if (it.isNotEmpty()) {
                        String(Base64.getDecoder().decode(it))
                    } else {
                        throw RuntimeException("Пустой rawData в ответе")
                    }
                } ?: throw RuntimeException("Поле rawData не найдено в ответе")
            }

        } else {
            throw RuntimeException("Failed to search: $responseCode")
        }


    } catch (e: Exception) {
        println("Exception details: ${e.message}")
        e.printStackTrace()
        throw RuntimeException("HTTP request failed: ${e.message}", e)
    } finally {
        connection.disconnect()
    }
}

fun createNewIamToken() {
    createScriptForHost(args = listOf("/home/nsa/yandex-cloud/bin/yc iam create-token > /sm-karaoke/system/yandex/iam_token.txt"), waitToDone = true)
    Karaoke.requestIamToken = Files.readString(Paths.get(Karaoke.iamTokenFilePath)).trim()
}

fun getHtml(link: String): String {
    val document = try {
        Jsoup.connect(link)
            .timeout(5000) // 5 секунд таймаут
            .get()
    } catch (e: Exception) {
        return ""
    }
    return document.html()
}

fun findElementByText(html: String, classNamePrefixes: List<String>, idNamePrefixes: List<String>): String? {
    val document: Document = Jsoup.parse(html)
    // Сначала ищем элементы с точным совпадением класса
    val exactMatches = classNamePrefixes.flatMap { prefix ->
        document.select(".$prefix")
    }
    // Затем ищем элементы, содержащие класс
    val containsMatches = classNamePrefixes.flatMap { prefix ->
        document.select("[class*=\"$prefix\"]")
    }
    // Ищем элементы с точным совпадением id
    val exactIdMatches = idNamePrefixes.flatMap { prefix ->
        document.select("#$prefix")
    }

    // Ищем элементы, содержащие id
    val containsIdMatches = idNamePrefixes.flatMap { prefix ->
        document.select("[id*=\"$prefix\"]")
    }

    val allElements = exactMatches + containsMatches + exactIdMatches + containsIdMatches
    return allElements.joinToString("\n") { it.wholeText().cleanAndNormalizeNewlines() }
}

fun extractDomain(url: String): String {
    return URL(url).host
}

fun String.normalizeNewlines(): String {
    return this.replace(Regex("\n+"), "\n")
}

fun String.cleanAndNormalizeNewlines(): String {
    return this
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString("\n")
}