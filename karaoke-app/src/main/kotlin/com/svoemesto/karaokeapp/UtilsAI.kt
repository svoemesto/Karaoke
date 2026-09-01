package com.svoemesto.karaokeapp

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.svoemesto.karaokeapp.llm.LyricsFinderService
import com.svoemesto.karaokeapp.model.SearchAsync
import com.svoemesto.karaokeapp.model.SearchResponseFormat
import com.svoemesto.karaokeapp.model.SearchResult
import com.svoemesto.karaokeapp.model.SongField
import com.svoemesto.karaokeapp.model.Song
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.File
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDateTime
import java.util.Base64
import javax.net.ssl.HttpsURLConnection
import kotlin.String
import kotlin.text.replace

suspend fun main000() {
//    println(findSongText("Nautilus Pompilius", "Мальчик зима"))
}

/**
 * Движок поиска URL с текстами песен — выбираемый в настройках
 * (`KaraokeProperties.lyricsSearchEngine`) или явно для конкретного запуска
 * (specs/015-search-engine-selection).
 *
 * @see archive/docs/features/llm-lyrics-search.md
 */
enum class LyricsSearchEngine {
    YANDEX_SYNC,
    YANDEX_ASYNC,
    SEARXNG,
    FOURGET,
}

/**
 * Разрешает движок поиска текстов песен: явно переданное [engine] приоритетнее настройки
 * `KaraokeProperties.lyricsSearchEngine`; некорректное/отсутствующее значение (в обоих
 * источниках) — фолбэк на [LyricsSearchEngine.FOURGET] (specs/015-search-engine-selection).
 *
 * @see archive/docs/features/llm-lyrics-search.md
 */
fun resolveLyricsSearchEngine(engine: String? = null): LyricsSearchEngine =
    (engine ?: KaraokeProperties.getString("lyricsSearchEngine")).let {
        try {
            enumValueOf<LyricsSearchEngine>(it)
        } catch (e: IllegalArgumentException) {
            LyricsSearchEngine.FOURGET
        }
    }

fun parseXmlUrls(xmlText: String): List<String> {
    val urls = mutableListOf<String>()
    val urlRegex = Regex("<url>(.*?)</url>")

    urlRegex
        .findAll(xmlText)
        .forEach { match ->
            urls.add(match.groupValues[1])
        }

    return urls
}

fun parseHtmlUrls(xmlText: String): List<String> {
    val urls = mutableListOf<String>()
    val urlRegex = Regex("<url>(.*?)</url>")

    urlRegex
        .findAll(xmlText)
        .forEach { match ->
            urls.add(match.groupValues[1])
        }

    return urls
}

/**
 * Класс Find Song Result.
 *
 * @see archive/docs/features/dual-db-sync.md
 */
@Serializable
data class FindSongResult(
    val id: Int,
    val author: String,
    val songName: String,
    val link: String,
    val domain: String,
    val findedText: String,
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

/**
 * Единая точка автоподстановки найденного текста песни (specs/020-fix-search-lyrics-autofill).
 * Вызывается из всех точек завершения поиска текста (YANDEX_SYNC, YANDEX_ASYNC, SEARXNG,
 * FOURGET), чтобы поведение не расходилось между движками. Использует [Song.haveSourceText]
 * как единственный источник истины о том, есть ли у песни текст (учитывает оба представления
 * "текста ещё нет" — пустую строку `""` и значение-заглушку `["\"\"]"`) — НЕ дублировать эту
 * проверку через `sourceText.isBlank()` в новом коде.
 *
 * **Race condition защита (specs/281-find-lyrics-overwrites-key-bpm)**: объект `song` мог быть
 * загружен из БД задолго до того, как мы сюда дошли (например, в `ApiController.searchsongtextall`
 * он приходит из `Song.loadFromDbById`, потом идёт Playwright/HTTP-парсинг десятки секунд). За это
 * время параллельные процессы (`KEY_BPM_FROM_FILE`, `DEMUCS2`, `Sheetsage` и т.п.) могут успеть
 * обновить `song_tone`/`song_bpm`/URL'ы стемов через свой собственный экземпляр `Song.saveToDb()`.
 * Если бы мы вызывали `song.saveToDb()` напрямую — `Song.getDiff(this, savedSong)` увидел бы
 * `this.key == ""` против `savedSong.key == "Am"` и включил бы `song_tone` в UPDATE → перезатирание
 * параллельно найденных значений пустыми. Поэтому ПЕРЕД `saveToDb()` объект перезагружается из БД;
 * паттерн fallback на исходный объект при `null` (на случай удаления песни) — точно как в Pass 278
 * (`specs/278-fix-key-loss-on-lyrics-search/spec.md`). Глобальный фикс покрывает все 4 движка +
 * фоновый `KaraokeProcessWorker` одной правкой.
 *
 * @see specs/281-find-lyrics-overwrites-key-bpm/spec.md
 * @see specs/278-fix-key-loss-on-lyrics-search/spec.md
 * @see archive/docs/features/llm-lyrics-search.md
 */
fun applyFoundLyricsIfMissing(
    song: Song,
    candidateTexts: List<String>,
) {
    val firstNonEmpty = candidateTexts.firstOrNull { it.isNotBlank() } ?: return
    if (!song.haveSourceText && song.idStatus == 0L) {
        println("Первое из найденных не пустых значений применяем для текста песни ${song.fileName}")
        // specs/281-find-lyrics-overwrites-key-bpm: reload-from-db-before-save — иначе параллельно
        // записанные key/bpm/url'ы стемов попадут в getDiff и перезатрутся пустыми значениями из
        // stale in-memory объекта (объект загружен в начале searchsongtextall десятки секунд назад).
        val songToSave =
            Song.loadFromDbById(
                id = song.id,
                database = song.database,
                storageService = song.storageService,
                storageApiClient = song.storageApiClient,
            ) ?: song
        songToSave.sourceText = firstNonEmpty
        songToSave.fields[SongField.ID_STATUS] = "1"
        songToSave.saveToDb()
    }
}

/**
 * Диспетчер поиска текста песни по выбранному движку (specs/015-search-engine-selection).
 * Заменяет собой прежний `getSearXNGSearch`, который после фичи 014 уже реально ходил
 * в fourget, а не в SearXNG — имя стало вводящим в заблуждение.
 *
 * `forceResearch=true` — сначала удаляет уже сохранённые `SearchResult`/`SearchAsync`
 * для этой песни (см. [SearchResult.deleteBySongId]/[SearchAsync.deleteBySongId]), чтобы
 * обойти кэширующую проверку «уже есть запрос — вернуть его», которая иначе сработала бы
 * и в [getYandexSearch], и в [getLyricsSearchViaSearchTool].
 *
 * @see archive/docs/features/llm-lyrics-search.md
 */
fun getLyricsSearch(
    song: Song,
    lyricsFinderService: LyricsFinderService,
    engine: LyricsSearchEngine,
    forceResearch: Boolean = false,
): SearchAsync {
    if (forceResearch) {
        SearchResult.deleteBySongId(song.id, song.database, song.storageService, song.storageApiClient)
        SearchAsync.deleteBySongId(song.id, song.database, song.storageService, song.storageApiClient)
    }
    return when (engine) {
        LyricsSearchEngine.YANDEX_SYNC -> getYandexSearch(song = song, async = false)
        LyricsSearchEngine.YANDEX_ASYNC -> getYandexSearch(song = song, async = true)
        LyricsSearchEngine.SEARXNG -> getLyricsSearchViaSearchTool(song, lyricsFinderService, useSearxng = true)
        LyricsSearchEngine.FOURGET -> getLyricsSearchViaSearchTool(song, lyricsFinderService, useSearxng = false)
    }
}

/**
 * Общая реализация поиска текста песни через [SearchTool] (движки `SEARXNG`/`FOURGET`,
 * см. [getLyricsSearch]) — прежнее тело `getSearXNGSearch`, параметризованное выбором
 * между [LyricsFinderService.searchUrlsViaSearxng] и [LyricsFinderService.searchUrls] (fourget).
 *
 * @see archive/docs/features/llm-lyrics-search.md
 */
private fun getLyricsSearchViaSearchTool(
    song: Song,
    lyricsFinderService: LyricsFinderService,
    useSearxng: Boolean,
): SearchAsync {
    println("Начинаем получение запроса поиска для песни ${song.fileName}.")
    val searchAsyncList =
        SearchAsync.getSearchAsyncListBySongId(
            songId = song.id,
            database = song.database,
            storageApiClient = song.storageApiClient,
            storageService = song.storageService,
        )
    if (searchAsyncList.isNotEmpty()) {
        println("Ранее созданный запрос найден в базе данных, возвращаем его.")
        return searchAsyncList.first()
    }

    val author = song.author
    val songName = song.songName
    val songNameForFind = songName.replace(Regex("""\([^)]*\)"""), "").trim()

    val queryText = "$author текст песни $songNameForFind"
    println("Запрос будет выполнен для поисковой сроки: '$queryText'")

    // Получаем список URL
    val urls =
        if (useSearxng) {
            lyricsFinderService.searchUrlsViaSearxng(author = author, songName = songNameForFind)
        } else {
            lyricsFinderService.searchUrls(author = author, songName = songNameForFind)
        }

    val result = SearchAsync()
    result.songId = song.id
    result.query = queryText
    result.operationId = ""
    result.done = true
    result.rawData = urls.joinToString("\n")

    val savedResult = SearchAsync.createNewSearchAsync(newSearchAsync = result, database = song.database)
    if (savedResult != null) {
        println("Запрос успешно создан. id = '${savedResult.id}', найдено ссылок - ${urls.size}")
    } else {
        println("Не удалось создать SearchAsync (синхронный) в базе данных. $result")
    }
    savedResult ?: throw RuntimeException("Не удалось создать SearchAsync (синхронный) в базе данных. $result")

    // specs/287-stop-lyrics-after-first (FR-001, FR-002, FR-003):
    // ШАГ 1: создаём запись SearchResult для КАЖДОГО URL из urls (с пустым text/html) — это нужно,
    // чтобы ВСЕ ссылки попали в список модалки (даже те, до которых мы не дойдём из-за остановки).
    val searchedRightResults = mutableListOf<SearchResult>()
    for (url in urls) {
        val searchResult = SearchResult()
        searchResult.searchAsyncId = savedResult.id
        searchResult.songId = savedResult.songId
        searchResult.url = url
        val savedSearchResult =
            SearchResult.createNewSearchResult(
                newSearchResult = searchResult,
                database = song.database,
            )
        if (savedSearchResult != null) {
            searchedRightResults.add(searchResult)
        } else {
            println("Не удалось создать SearchResult для $url, пропускаем.")
        }
    }
    println("Создано ${searchedRightResults.size} записей SearchResult для обработки (из ${urls.size} URL)")

    // ШАГ 2: обходим записи и пытаемся извлечь текст через LLM-парсер; после первого успеха —
    // прекращаем обработку остальных URL (HTTP-запрос НЕ делается).
    for (searchResult in searchedRightResults) {
        val url = searchResult.url
        val lyrics = lyricsFinderService.extractLyricsFromUrl(url)
        if (!lyrics.isNullOrBlank()) {
            println("Успешное извлечение текста по ссылке $url, символов: ${lyrics.length}")
            searchResult.text = lyrics
            searchResult.save()  // UPDATE
            // FR-001: первый успех — прекращаем обработку остальных URL.
            break
        } else {
            println("Не удалось извлечь текст по ссылке $url (пустой результат LLM-парсера).")
        }
    }

    val searchedRightResultsNotEmpty = searchedRightResults.filter { it.text != "" }
    applyFoundLyricsIfMissing(song, searchedRightResultsNotEmpty.map { it.text })

    return savedResult
}

fun getYandexSearch(
    song: Song,
    countInPage: Int = 100,
    responseFormat: SearchResponseFormat = SearchResponseFormat.FORMAT_XML,
    async: Boolean = false,
): SearchAsync {
    // Ищем, есть ли уже в наличии для заданной песни SearchAsync. Если есть - возвращаем первый/единственный
    println("Начинаем получение ${if (async) "АСИНХРОННОГО" else "СИНХРОННОГО"} запроса поиска для песни ${song.fileName}.")
    val searchAsyncList =
        SearchAsync.getSearchAsyncListBySongId(
            songId = song.id,
            database = song.database,
            storageApiClient = song.storageApiClient,
            storageService = song.storageService,
        )
    if (searchAsyncList.isNotEmpty()) {
        println("Ранее созданный запрос найден в базе данных, возвращаем его.")
        return searchAsyncList.first()
    }

    val iamToken = getIamToken()
    val folderId = Karaoke.yandexCloudFolderId

    val author = song.author
    val songName = song.songName
    val songNameForFind = songName.replace(Regex("""\([^)]*\)"""), "").trim()

    val queryText = "$author текст песни $songNameForFind"

    println("Запрос будет выполнен для поисковой сроки: '$queryText'")

    val requestUrl =
        if (async) {
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

        val body =
            """
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
                        result.songId = song.id
                        result.url = requestUrl
                        result.iamToken = iamToken
                        result.query = queryText
                        result.body = body
                        result.responseFormat = responseFormat.name
                        result.operationId = operationId
                        result.done = apiResponse.done ?: false
                        val savedResult = SearchAsync.createNewSearchAsync(newSearchAsync = result, database = song.database)
                        if (savedResult != null) {
                            println(
                                "Асинхронный запрос успешно создан. id = '${savedResult.id}', operationId = '${savedResult.operationId}'",
                            )
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
                        result.songId = song.id
                        result.url = requestUrl
                        result.iamToken = iamToken
                        result.query = queryText
                        result.body = body
                        result.responseFormat = responseFormat.name
                        result.operationId = ""
                        result.done = true
                        result.rawData = String(Base64.getDecoder().decode(rawData))
                        val savedResult = SearchAsync.createNewSearchAsync(newSearchAsync = result, database = song.database)
                        if (savedResult != null) {
                            println(
                                "Синхронный запрос успешно создан. id = '${savedResult.id}', символов в rawData = '${savedResult.rawData.length}'",
                            )
                        } else {
                            println("Не удалось создать SearchAsync (синхронный) в базе данных. $result")
                        }
                        val nonNullSavedResult =
                            savedResult
                                ?: throw RuntimeException("Не удалось создать SearchAsync (синхронный) в базе данных. $result")
                        // specs/020-fix-search-lyrics-autofill: у YANDEX_SYNC (в отличие от SEARXNG/FOURGET
                        // и завершения YANDEX_ASYNC) этого шага раньше не было вообще — результат сохранялся,
                        // но не разбирался в SearchResult и не подставлялся в текст песни.
                        val searchResults = SearchResult.getSearchResultsForSearchAsync(searchAsync = nonNullSavedResult)
                        applyFoundLyricsIfMissing(song, searchResults.filter { !it.wrongResult }.map { it.text })
                        nonNullSavedResult
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

/**
 * Возвращает CSS-селекторы классов для извлечения текста песни по домену URL
 * (Yandex-путь поиска, спека specs/015-search-engine-selection). Используется
 * ручной попыткой «Получить текст по ссылке» (specs/287-stop-lyrics-after-first)
 * — дублирует словарь из `SearchResult.getSearchResultsForSearchAsync`, чтобы
 * избежать рефакторинга чужой зоны ответственности.
 *
 * @see specs/287-stop-lyrics-after-first/spec.md (FR-020)
 * @see specs/015-search-engine-selection/spec.md
 */
private fun getClassNamePrefixesForDomain(domain: String): List<String> =
    when {
        domain == "genius.com" -> listOf("Lyrics__Container")
        domain == "tekst-pesni.online" -> listOf("entry-content", "clearfix")
        domain == "www.shazam.com" -> listOf("AppleMusicLyrics_lyricsBlock")
        domain == "vk.ru" || domain == "vk.com" -> listOf("vkitFeedShowMoreText")
        domain == "darktexts.ru" -> listOf("full-text")
        domain == "www.beesona.pro" -> listOf("copys")
        domain == "alllyr.ru" -> listOf("inline")
        domain == "lyricsworld.ru" -> listOf("songLyrics")
        domain == "www.5lad.net" -> listOf("textofsong")
        domain == "blatata.com" -> listOf("value")
        domain == "lyrhub.com" -> listOf("lyric")
        domain == "ru.ilyrics.net" -> listOf("space-y-4", "text-gray-700", "leading-relaxed")
        domain == "singme.ru" -> listOf("song-text")
        domain == "rush-sound.ru" -> listOf("chords")
        domain in listOf("muzbank.net") -> listOf("song")
        domain == "rus-songs.com" -> listOf("post-content", "entry-content")
        domain == "www.ukulele-akkordy.ru" -> listOf("textofsong")
        domain == "teksty-pesenok.pro" -> listOf("tab-pane", "fade", "active", "in", "text_song")
        domain == "texta-pesni.ru" -> listOf("mid_cont_left")
        domain == "tekstmuz.ru" -> listOf("articles")
        domain == "www.anekdotov-mnogo.ru" -> listOf("tmpLineUnderContent")
        domain == "stihi.ru" -> listOf("diarytext")
        domain in listOf("maximum.ru", "rusradio.ru") -> listOf("relative")
        domain == "www.az-lyrics.ru" -> listOf("article-song-text")
        domain == "txt-pesen.ru" -> listOf("articleBody")
        domain == "lyricshare.net" -> listOf("textpesnidiv")
        domain == "www.pesni.net" -> listOf("song-block-text")
        domain == "guitarchords.ru" -> listOf("song_container")
        domain == "mp3folderx.com" -> listOf("text")
        domain == "akkordbard.ru" -> listOf("song")
        domain == "alloflyrics.cc" -> listOf("container")
        domain == "reproduktor.net" -> listOf("content-wrap")
        domain == "rerura.com" -> listOf("block-content")

        domain.endsWith(".amdm.ru") -> listOf("b-podbor__text")

        else -> emptyList()
    }

/**
 * Возвращает CSS-селекторы id-шников для извлечения текста песни по домену URL.
 * Дублирует словарь из `SearchResult.getSearchResultsForSearchAsync`.
 *
 * @see getClassNamePrefixesForDomain
 */
private fun getIdNamePrefixesForDomain(domain: String): List<String> =
    when {
        domain == "musictxt.ru" -> listOf("layer2")
        domain == "akkordus.ru" -> listOf("chord_prev")
        domain == "mysongs.pro" -> listOf("text")
        domain == "ukula.ru" -> listOf("del_prob")

        else -> emptyList()
    }

/**
 * Ручная попытка извлечения текста песни для одной конкретной записи `tbl_search_results`.
 * Используется кнопкой «Получить текст по ссылке» в модалке «Поиск текста песни в интернете»
 * (FR-020..FR-024, спека specs/287-stop-lyrics-after-first). Вызывается ТОЛЬКО для одной
 * записи — никакого перебора остальных URL-ов.
 *
 * Алгоритм:
 * 1. Загрузить `SearchResult` по `searchResultId` через [SearchResult.getSearchResultById].
 *    Если не найден — вернуть null (caller получит 404).
 * 2. Если `searchResult.text.isNotBlank()` — вернуть запись как есть (FR-022, идемпотентность:
 *    повторный HTTP-запрос НЕ делается).
 * 3. Выбрать парсер по домену URL:
 *    - если домен есть в словаре CSS-селекторов (Yandex-путь) — `getHtml(link)` + `findElementByText`;
 *    - иначе (Search-tool-путь) — `lyricsFinderService.extractLyricsFromUrl(url)` через LLM-парсер.
 * 4. Сохранить результат в БД (`searchResult.text` / `searchResult.html` / `searchResult.wrongResult`).
 *    `lastError` в DTO (выставляется вызывающим кодом, см. ApiController) отражает ошибку
 *    HTTP-запроса или парсинга.
 *
 * Возвращает обновлённый [SearchResult] или null если запись не найдена.
 *
 * @see specs/287-stop-lyrics-after-first/spec.md
 * @see specs/287-stop-lyrics-after-first/contracts/api-endpoints.md
 */
fun extractLyricsBySearchResultId(
    searchResultId: Long,
    lyricsFinderService: LyricsFinderService,
    database: KaraokeConnection,
    storageService: KaraokeStorageService,
    storageApiClient: StorageApiClient,
): SearchResult? {
    val searchResult =
        SearchResult.getSearchResultById(
            id = searchResultId,
            database = database,
            storageService = storageService,
            storageApiClient = storageApiClient,
        ) ?: return null

    if (searchResult.text.isNotBlank()) {
        println("extractLyricsBySearchResultId: для записи $searchResultId текст уже есть, возвращаем как есть.")
        return searchResult
    }

    val link = searchResult.url
    val domain = extractDomain(link)
    val classNamePrefixes = getClassNamePrefixesForDomain(domain)
    val idNamePrefixes = getIdNamePrefixesForDomain(domain)

    return try {
        if (classNamePrefixes.isNotEmpty() || idNamePrefixes.isNotEmpty()) {
            // Yandex-путь: парсинг по CSS-селекторам.
            val html = getHtml(link)
            if (html.isNotEmpty()) {
                searchResult.html = html
                val text = (findElementByText(html, classNamePrefixes, idNamePrefixes) ?: "").trim()
                if (text.isNotBlank()) {
                    println("extractLyricsBySearchResultId: успешное извлечение по $link, символов: ${text.length}")
                    searchResult.text = text
                } else {
                    println("extractLyricsBySearchResultId: парсер вернул пустой результат для $link")
                }
            } else {
                println("extractLyricsBySearchResultId: не удалось получить html по $link")
            }
        } else {
            // Search-tool-путь: LLM-парсер.
            val lyrics = lyricsFinderService.extractLyricsFromUrl(link)
            if (!lyrics.isNullOrBlank()) {
                println("extractLyricsBySearchResultId: успешное LLM-извлечение по $link, символов: ${lyrics.length}")
                searchResult.text = lyrics
            } else {
                println("extractLyricsBySearchResultId: LLM-парсер вернул пустой результат для $link")
            }
        }
        searchResult.save()
        searchResult
    } catch (e: Exception) {
        println("extractLyricsBySearchResultId: ошибка при извлечении по $link: ${e.message}")
        e.printStackTrace()
        searchResult.save()
        searchResult
    }
}

fun findSongText(
    song: Song,
    countInPage: Int = 100,
    countInResult: Int = 0,
): List<FindSongResult> {
    val author = song.author
    val songName = song.songName
    val songNameForFind = songName.replace(Regex("""\([^)]*\)"""), "").trim()
    val fileSearchedLinksAbsolutePath = song.fileSearchedLinksAbsolutePath
    val xmlText =
        if (File(fileSearchedLinksAbsolutePath).exists()) {
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
        val classNamePrefixes =
            when (domain) {
                "genius.com" -> listOf("Lyrics__Container")
                "tekst-pesni.online" -> listOf("entry-content", "clearfix")
                "www.shazam.com" -> listOf("AppleMusicLyrics_lyricsBlock")
                "vk.ru", "vk.com" -> listOf("vkitFeedShowMoreText")
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
                "www.oduvanchik.net",
                -> emptyList()
                else -> emptyList()
            }
        if (classNamePrefixes.isNotEmpty()) {
            val result = (findElementByText(html, classNamePrefixes, emptyList()) ?: "").trim()
            if (result.isNotBlank()) {
                id++
                resultList.add(
                    FindSongResult(
                        id = id,
                        author = author,
                        songName = songName,
                        link = link,
                        domain = domain,
                        findedText = result,
                    ),
                )
                if (countInResult in 1..id) {
                    println(
                        "Из ${links.size} ссылок пропущено $skipedLinks, возвращено ${resultList.size}, запрошено к возврату было $countInResult",
                    )
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

/**
 * Класс Api Response Sync.
 *
 * @see archive/docs/features/dual-db-sync.md
 */
@Serializable
@JsonIgnoreProperties(ignoreUnknown = true)
data class ApiResponseSync(
    @JsonProperty("rawData")
    val rawData: String? = null,
)

/**
 * Класс Api Response Async.
 *
 * @see archive/docs/features/dual-db-sync.md
 */
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
    val description: String? = null,
)

fun searchSongInYandex(
    author: String,
    songName: String,
    countInPage: Int = 10,
    page: Int = 0,
    async: Boolean = false,
): String {
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

    val url =
        if (async) {
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

        val body =
            """
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
    createScriptForHost(
        args = listOf("~/yandex-cloud/bin/yc iam create-token > /sm-karaoke/system/yandex/iam_token.txt"),
        waitToDone = true,
    )
    Karaoke.requestIamToken = Files.readString(Paths.get(Karaoke.iamTokenFilePath)).trim()
}

fun getHtml(link: String): String {
    val document =
        try {
            Jsoup
                .connect(link)
                .timeout(5000) // 5 секунд таймаут
                .get()
        } catch (e: Exception) {
            return ""
        }
    return document.html()
}

fun findElementByText(
    html: String,
    classNamePrefixes: List<String>,
    idNamePrefixes: List<String>,
): String? {
    val document: Document = Jsoup.parse(html)
    // Сначала ищем элементы с точным совпадением класса
    val exactMatches =
        classNamePrefixes.flatMap { prefix ->
            document.select(".$prefix")
        }
    // Затем ищем элементы, содержащие класс
    val containsMatches =
        classNamePrefixes.flatMap { prefix ->
            document.select("[class*=\"$prefix\"]")
        }
    // Ищем элементы с точным совпадением id
    val exactIdMatches =
        idNamePrefixes.flatMap { prefix ->
            document.select("#$prefix")
        }

    // Ищем элементы, содержащие id
    val containsIdMatches =
        idNamePrefixes.flatMap { prefix ->
            document.select("[id*=\"$prefix\"]")
        }

    val allElements = exactMatches + containsMatches + exactIdMatches + containsIdMatches
    return allElements.joinToString("\n") { it.wholeText().cleanAndNormalizeNewlines() }
}

fun extractDomain(url: String): String = URL(url).host

fun String.normalizeNewlines(): String = this.replace(Regex("\n+"), "\n")

fun String.cleanAndNormalizeNewlines(): String =
    this
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString("\n")
