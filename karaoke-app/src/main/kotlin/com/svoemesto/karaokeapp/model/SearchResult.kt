package com.svoemesto.karaokeapp.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.WORKING_DATABASE
import com.svoemesto.karaokeapp.extractDomain
import com.svoemesto.karaokeapp.findElementByText
import com.svoemesto.karaokeapp.getHtml
import com.svoemesto.karaokeapp.model.KaraokeDbTable.Companion.getListHashes
import com.svoemesto.karaokeapp.parseHtmlUrls
import com.svoemesto.karaokeapp.parseXmlUrls
import com.svoemesto.karaokeapp.services.KSS_APP
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.SAC_APP
import com.svoemesto.karaokeapp.services.StorageApiClient
import java.io.Serializable

/**
 * Класс Search Result.
 *
 * @see archive/docs/features/llm-lyrics-search.md
 */
@JsonIgnoreProperties(value = ["database", "sqlToInsert"])
class SearchResult(
    override val database: KaraokeConnection = WORKING_DATABASE,
    override val storageService: KaraokeStorageService = KSS_APP,
    override val storageApiClient: StorageApiClient = SAC_APP,
) : Serializable,
    Comparable<SearchResult>,
    KaraokeDbTable {
    override fun compareTo(other: SearchResult): Int = id.compareTo(other.id)

    override fun getTableName() = "tbl_search_results"

    @KaraokeDbTableField(name = "id", isId = true)
    override var id: Long = 0

    @KaraokeDbTableField(name = "search_async_id")
    var searchAsyncId: Long = 0

    @KaraokeDbTableField(name = "song_id")
    var songId: Long = 0

    @KaraokeDbTableField(name = "url")
    var url: String = ""

    @KaraokeDbTableField(name = "html")
    var html: String = ""

    @KaraokeDbTableField(name = "text")
    var text: String = ""

    @KaraokeDbTableField(name = "wrong_result")
    var wrongResult: Boolean = false

    override fun toDTO(): SearchResultDTO =
        SearchResultDTO(
            id = id,
            searchAsyncId = searchAsyncId,
            songId = songId,
            url = url,
            html = "",
            text = text,
            wrongResult = wrongResult,
            lastError = null,
        )

    companion object {
        fun getSearchResultsForSearchAsync(searchAsync: SearchAsync): List<SearchResult> {
            val result = mutableListOf<SearchResult>()

            // Если searchAsync еще не готов или имеет пустой rawData - возвращаем пустой список
            if (!searchAsync.done || searchAsync.rawData == "") return emptyList()

            val database = searchAsync.database
            val storageService = searchAsync.storageService
            val storageApiClient = searchAsync.storageApiClient
            // Находим в базе данных список уже имеющихся SearchResult для переданного searchAsync
            val storedList =
                getSearchResultListBySearchAsyncId(
                    searchAsyncId = searchAsync.id,
                    database = database,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                )
            // Если список не пустой - возвращаем его
            if (storedList.isNotEmpty()) return storedList

            // Собираем ссылки из searchAsync и осуществляем по ним поиск
            val links =
                when (searchAsync.responseFormat) {
                    "FORMAT_XML" -> parseXmlUrls(searchAsync.rawData)
                    "FORMAT_HTML" -> parseHtmlUrls(searchAsync.rawData)
                    else -> emptyList()
                }
            println("Ссылок в документе: ${links.size}")

            // specs/287-stop-lyrics-after-first (FR-001, FR-002, FR-003):
            // ШАГ 1: создаём запись в tbl_search_results для КАЖДОЙ ссылки из searchAsync (с пустым
            // text/html) — это нужно, чтобы ВСЕ ссылки попали в список модалки «Поиск текста песни
            // в интернете» (даже те, до которых мы не дойдём из-за остановки после первого успеха).
            val records = mutableListOf<SearchResult>()
            for (link in links) {
                val searchResult = SearchResult()
                searchResult.searchAsyncId = searchAsync.id
                searchResult.songId = searchAsync.songId
                searchResult.url = link
                val savedSearchResult =
                    SearchResult.createNewSearchResult(
                        newSearchResult = searchResult,
                        database = database,
                    )
                if (savedSearchResult != null) {
                    records.add(searchResult)
                } else {
                    println("Не удалось создать SearchResult для $link, пропускаем.")
                }
            }
            println("Создано ${records.size} записей SearchResult для обработки (из ${links.size} ссылок)")

            // ШАГ 2: обходим записи и пытаемся извлечь текст; после первого успеха — прекращаем
            // обработку остальных (HTTP-запрос НЕ делается, парсер НЕ запускается).
            var id = 0
            var skipedLinks = 0
            for ((idx, searchResult) in records.withIndex()) {
                val link = searchResult.url
                val domain = extractDomain(link)
                println("Обрабатываем ссылку (${idx + 1} из ${records.size}) в домене $domain [ $link ]")

                val classNamePrefixes =
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

                        domain in
                            listOf(
                                "txtsong.ru",
                                "my.mail.ru",
                                "l-hit.com",
                                "textypesen.com",
                                "m.song.guru",
                                "ukula.ru",
                                "text-pesni-perevod.ru",
                                "www.songslyrics.ru",
                                "guitary.ru",
                                "music.yandex.ru",
                                "www.oduvanchik.net",
                                "lyrhub.com",
                                "m.ok.ru",
                                "flowlez.com",
                                "slushat-tekst-pesni.ru",
                                "pesnihi.com",
                                "lalatracker.com",
                                "tabius.ru",
                            )
                        -> emptyList()
                        else -> emptyList()
                    }

                val idNamePrefixes =
                    when {
                        domain == "musictxt.ru" -> listOf("layer2")
                        domain == "akkordus.ru" -> listOf("chord_prev")
                        domain == "mysongs.pro" -> listOf("text")
                        domain == "ukula.ru" -> listOf("del_prob")

                        else -> emptyList()
                    }

                if (classNamePrefixes.isNotEmpty() || idNamePrefixes.isNotEmpty()) {
                    val html = getHtml(link)
                    if (html.isNotEmpty()) {
                        println("Получен html размером ${html.length} символов.")
                        searchResult.html = html
                        val text = (findElementByText(html, classNamePrefixes, idNamePrefixes) ?: "").trim()
                        if (text.isNotBlank()) {
                            id++
                            searchResult.text = text
                            println("Обработка ссылки в домене $domain вернула текст длиной ${text.length} символов")
                            // specs/287-stop-lyrics-after-first: обновить запись с непустым text и html.
                            searchResult.save()
                            result.add(searchResult)
                            // FR-001: первый успех — прекращаем обработку остальных ссылок.
                            println("Прекращаем обработку остальных ${records.size - result.size} ссылок после первой успешной.")
                            return result
                        } else {
                            println("Обработка ссылки в домене $domain вернула пустой результат, пропускаем.")
                            skipedLinks++
                            // Сохраняем html даже если текст пустой — для последующего ручного извлечения.
                            searchResult.save()
                        }
                    } else {
                        println("Не удалось получить html")
                    }
                } else {
                    println("Пропускаем ссылку в домене $domain")
                    skipedLinks++
                }
                result.add(searchResult)
            }
            println("Из ${records.size} ссылок пропущено $skipedLinks")

            return result
        }

        const val TABLE_NAME = "tbl_search_results"

        @Suppress("unused")
        fun listHashes(
            database: KaraokeConnection,
            whereText: String = "",
        ): List<RecordHash>? = getListHashes(tableName = TABLE_NAME, database = database, whereText = whereText)

        private fun getWhereList(whereArgs: Map<String, String>): List<String> {
            val where: MutableList<String> = mutableListOf()

            if (whereArgs.containsKey("id")) where += "id = ${whereArgs["id"]}"
            if (whereArgs.containsKey("search_async_id")) where += "search_async_id = ${whereArgs["search_async_id"]}"
            if (whereArgs.containsKey("song_id")) where += "song_id = ${whereArgs["song_id"]}"
            if (whereArgs.containsKey("url")) where += "url = '${whereArgs["url"]}'"
            if (whereArgs.containsKey("html")) where += "html = '${whereArgs["html"]}'"
            if (whereArgs.containsKey("text")) where += "text = '${whereArgs["text"]}'"
            if (whereArgs.containsKey("wrong_result")) {
                if (whereArgs["wrong_result"] == "+" || whereArgs["wrong_result"] == "true") {
                    where += "wrong_result = true"
                } else if (whereArgs["wrong_result"] == "-" || whereArgs["wrong_result"] == "false") {
                    where += "wrong_result = false"
                }
            }

            return where
        }

        fun loadList(
            whereArgs: Map<String, String>,
            limit: Int = 0,
            offset: Int = 0,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
            ignoreUseInList: Boolean,
        ): List<SearchResult> =
            KaraokeDbTable
                .loadList(
                    clazz = SearchResult::class,
                    tableName = TABLE_NAME,
                    whereList = getWhereList(whereArgs),
                    limit = limit,
                    offset = offset,
                    database = database,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                    ignoreUseInList = ignoreUseInList,
                ).map { it as SearchResult }

        fun delete(
            id: Long,
            database: KaraokeConnection,
        ): Boolean =
            KaraokeDbTable.delete(
                tableName = TABLE_NAME,
                id = id,
                database = database,
            )

        fun createNewSearchResult(
            newSearchResult: SearchResult,
            database: KaraokeConnection,
        ): SearchResult? {
            val newSearchResultInDb =
                KaraokeDbTable.createDbInstance(
                    entity = newSearchResult,
                    database = database,
                ) as? SearchResult?
            newSearchResultInDb?.let {
                return it
            }
            return null
        }

        fun getSearchResultById(
            id: Long,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): SearchResult? =
            KaraokeDbTable.loadById(
                clazz = SearchResult::class,
                tableName = TABLE_NAME,
                id = id,
                database = database,
                storageService = storageService,
                storageApiClient = storageApiClient,
            ) as? SearchResult?

        fun getSearchResultListBySongId(
            songId: Long,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): List<SearchResult> =
            loadList(
                whereArgs = mapOf(Pair("song_id", songId.toString())),
                database = database,
                storageService = storageService,
                storageApiClient = storageApiClient,
                ignoreUseInList = true,
            )

        /**
         * Удаляет все [SearchResult] для конкретной песни — используется для «переискать»/
         * «удалить результаты поиска» (specs/015-search-engine-selection) и для автоочистки
         * при достижении песней статуса готовности. По образцу [com.svoemesto.karaokeapp.model.CartItem.deleteByUserAndSongs].
         * Должен вызываться ДО [SearchAsync.deleteBySongId] (дочерние записи ссылаются на
         * `search_async_id`).
         *
         * @see archive/docs/features/llm-lyrics-search.md
         */
        fun deleteBySongId(
            songId: Long,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ) {
            getSearchResultListBySongId(songId, database, storageService, storageApiClient)
                .forEach { delete(it.id, database) }
        }

        fun getSearchResultListBySearchAsyncId(
            searchAsyncId: Long,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): List<SearchResult> =
            loadList(
                whereArgs = mapOf(Pair("search_async_id", searchAsyncId.toString())),
                database = database,
                storageService = storageService,
                storageApiClient = storageApiClient,
                ignoreUseInList = true,
            )
    }
}
