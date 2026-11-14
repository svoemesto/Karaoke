package com.svoemesto.karaokeweb.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.svoemesto.karaokeapp.model.AuthorAliasMatch
import com.svoemesto.karaokeapp.model.SiteUser
import com.svoemesto.karaokeapp.model.Song
import com.svoemesto.karaokeapp.services.KSS_APP
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.SAC_APP
import com.svoemesto.karaokeapp.services.StorageApiClient
import com.svoemesto.karaokeweb.dto.PagedSongsDto
import com.svoemesto.karaokeweb.services.KaraokeWebTestEnv
import com.svoemesto.karaokeweb.services.SiteUserResolver
import jakarta.servlet.http.HttpServletRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping

/**
 * Тесты `GET /api/public/songs/count` (KARAOKE-390): число песен по тем же фильтрам, что и у
 * `GET /api/public/songs`, с той же видимостью и тем же порогом «< 3 символов».
 *
 * **Почему без БД.** Логику за статикой в проекте не мокают (`mockStatic`/`mockito-inline`/
 * `mockk` в проекте нет), а тестируют шов: счётчик, загрузчик и резолв автора — зависимости-
 * параметры (`knowledge/domains/karaoke-web/components/testing-controllers.md`, Правило 3).
 * Тест подставляет счётчик, который считает ФИКСТУРЫ по той же карте аргументов, которую
 * собрал эндпоинт. Поэтому если `/songs` и `/songs/count` соберут разные фильтры — числа
 * разойдутся и тест это поймает; если соберут одинаковый — совпадут.
 *
 * **Видимость.** Аноним (`resolve()` → null) видит только `id_status >= 6`; редактор — все.
 * Роль задаётся моком `SiteUser` (класс финальный, но Mockito 5 мокает final-классы — так же,
 * как в `SubscriptionRenewalSchedulerTest`). Резолвер стабится КОНКРЕТНЫМ объектом запроса —
 * та же ссылка, что уходит в контроллер (Правило 6: `MockHttpServletRequest` не переопределяет
 * `equals`, поэтому матчер `any()` здесь не годится).
 *
 * @see specs/390-songs-count
 */
class PublicApiSongsCountTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun setUpEnv() {
            // Глобалы karaoke-web (WEB_WORK_ON_SERVER, DB_* …) — как в остальных тестах модуля.
            KaraokeWebTestEnv.init()
            // Моки хранилища karaoke-app: без них конструктор модели уходит в хранилище (Правило 5).
            KSS_APP = Mockito.mock(KaraokeStorageService::class.java)
            SAC_APP = Mockito.mock(StorageApiClient::class.java)
        }
    }

    /** Песня-фикстура: ровно те поля, по которым считается счётчик. */
    private data class FixtureSong(
        val name: String,
        val author: String,
        val album: String,
        val idStatus: Long,
    )

    private val fixtures =
        listOf(
            FixtureSong("Розовый слон", "Наутилус", "Альбом 1", 6),
            FixtureSong("Синий слон", "Наутилус", "Альбом 1", 6),
            FixtureSong("Жёлтый слон", "Наутилус", "Альбом 2", 7),
            FixtureSong("Черновик песни", "Наутилус", "Альбом 2", 3),
            FixtureSong("Другая песня", "Аквариум", "Альбом 3", 6),
        )

    private val mainController = Mockito.mock(MainController::class.java)
    private val storageService = Mockito.mock(KaraokeStorageService::class.java)
    private val storageApiClient = Mockito.mock(StorageApiClient::class.java)
    private val siteUserResolver = Mockito.mock(SiteUserResolver::class.java)

    private fun controller(): PublicApiController =
        PublicApiController(
            mainController = mainController,
            storageService = storageService,
            storageApiClient = storageApiClient,
            siteUserResolver = siteUserResolver,
            minioProxyUrl = "",
        )

    /** Запрос анонимного посетителя: `resolve()` не стабится → возвращает null. */
    private fun anonymousRequest(): MockHttpServletRequest = MockHttpServletRequest()

    /** Запрос редактора: резолвер отдаёт пользователя с `isEditor = true`. */
    private fun editorRequest(): MockHttpServletRequest {
        val request = MockHttpServletRequest()
        val editor = Mockito.mock(SiteUser::class.java)
        Mockito.`when`(editor.isEditor).thenReturn(true)
        // doReturn, а не when(...): метод не вызывается при настройке (Правило 4).
        Mockito.doReturn(editor).`when`(siteUserResolver).resolve(request)
        return request
    }

    /**
     * Счётчик по фикстурам: применяет к фикстурам ровно те условия, которые пришли в карте
     * аргументов (`song_name`, `author`, `author_in`, `song_album`, `id_status`).
     * Заодно запоминает переданные аргументы — так видно, какой фильтр ушёл в счёт.
     */
    private fun fixtureCounter(captured: MutableList<Map<String, String>> = mutableListOf()): (Map<String, String>) -> Int = { args ->
        captured += args
        fixtures.count { song -> matchesFixture(song, args) }
    }

    private fun matchesFixture(
        song: FixtureSong,
        args: Map<String, String>,
    ): Boolean {
        args["song_name"]?.let { if (!song.name.lowercase().contains(it.lowercase())) return false }
        args["author"]?.let { if (!song.author.equals(it, ignoreCase = true)) return false }
        args["author_in"]?.let {
            val names = it.split(Song.AUTHOR_IN_DELIMITER).map { name -> name.trim() }
            if (!names.any { name -> song.author.equals(name, ignoreCase = true) }) return false
        }
        args["song_album"]?.let { if (!song.album.lowercase().contains(it.lowercase())) return false }
        args["id_status"]?.let {
            val min = it.removePrefix(">=").trim().toLongOrNull() ?: return false
            if (song.idStatus < min) return false
        }
        return true
    }

    /** Список песен с пагинацией — тот же путь, что и у фронта (`page=1&pageSize=10`). */
    private fun pagedSongs(
        songName: String?,
        author: String?,
        album: String?,
        request: MockHttpServletRequest,
        countSongs: (Map<String, String>) -> Int,
    ): PagedSongsDto =
        controller().songsWith(
            songName = songName,
            author = author,
            text = null,
            album = album,
            anonId = null,
            referrer = null,
            page = 1,
            pageSize = 10,
            request = request,
            resolveAuthor = { emptyList() },
            loadSongs = { emptyList() },
            countSongs = countSongs,
        ) as PagedSongsDto

    // ---- Паритет: счётчик == totalCount из списка для тех же фильтров ----

    @Test
    fun `счётчик совпадает с totalCount из списка для тех же фильтров`() {
        val request = anonymousRequest()
        val counter = fixtureCounter()

        val list = pagedSongs(songName = "слон", author = null, album = null, request = request, countSongs = counter)
        val count =
            controller().songsCountWith(
                songName = "слон",
                author = null,
                text = null,
                album = null,
                request = request,
                countSongs = counter,
            )

        assertEquals(3L, count.count, "по фикстуре «слон» у анонимного посетителя 3 опубликованные песни")
        assertEquals(count.count, list.totalCount, "счётчик и totalCount должны совпадать для одних фильтров")
    }

    @Test
    fun `список и счётчик собирают одинаковый фильтр`() {
        val captured = mutableListOf<Map<String, String>>()
        val counter = fixtureCounter(captured)
        val request = anonymousRequest()

        pagedSongs(songName = "песня", author = null, album = null, request = request, countSongs = counter)
        controller().songsCountWith(
            songName = "песня",
            author = null,
            text = null,
            album = null,
            request = request,
            countSongs = counter,
        )

        assertEquals(2, captured.size, "счётчик позван обоими эндпоинтами")
        assertEquals(captured[0], captured[1], "фильтр для списка и для счётчика должен совпадать")
        assertEquals(">=6", captured[0]["id_status"], "анонимный посетитель — только опубликованные")
    }

    // ---- Видимость: аноним не видит неготовые песни, редактор видит ----

    @Test
    fun `анонимный посетитель не видит неготовые песни, редактор видит`() {
        val anonymous =
            controller().songsCountWith(
                songName = null,
                author = null,
                text = null,
                album = "Альбом",
                request = anonymousRequest(),
                countSongs = fixtureCounter(),
            )
        val editor =
            controller().songsCountWith(
                songName = null,
                author = null,
                text = null,
                album = "Альбом",
                request = editorRequest(),
                countSongs = fixtureCounter(),
            )

        assertEquals(4L, anonymous.count, "анонимный видит только id_status >= 6 (4 из 5 фикстур)")
        assertEquals(5L, editor.count, "редактор видит все песни (5 фикстур)")
    }

    @Test
    fun `редактор получает больше песен, чем анонимный посетитель, и в списке`() {
        val anonymousList = pagedSongs(null, null, "Альбом", anonymousRequest(), fixtureCounter())
        val editorList = pagedSongs(null, null, "Альбом", editorRequest(), fixtureCounter())

        assertEquals(4L, anonymousList.totalCount, "анонимный — только опубликованные")
        assertEquals(5L, editorList.totalCount, "редактор — все")
    }

    // ---- Порог «< 3 символов»: count = 0 без обращения к БД ----

    @Test
    fun `запрос короче трёх символов даёт count 0 без обращения к БД`() {
        val response =
            controller().songsCountWith(
                songName = "а",
                author = null,
                text = null,
                album = null,
                request = anonymousRequest(),
                resolveAuthor = { throw AssertionError("резолв автора при коротком запросе не вызывается") },
                countSongs = { throw AssertionError("счётчик при коротком запросе не вызывается") },
            )

        assertEquals(0L, response.count)
    }

    // ---- Мусорные параметры: без исключения и без 500 ----

    @Test
    fun `мусорные параметры не дают исключения`() {
        val response =
            controller().songsCountWith(
                songName = "!!!",
                author = "###",
                text = "%%%",
                album = "^^^",
                request = anonymousRequest(),
                resolveAuthor = { emptyList() },
                countSongs = fixtureCounter(),
            )

        assertEquals(0L, response.count, "мусорные фильтры не находят песен, но и не ломают ответ")
    }

    // ---- Форма ответа: ровно одно поле {"count": N} ----

    @Test
    fun `ответ содержит ровно одно поле count`() {
        val response =
            controller().songsCountWith(
                songName = null,
                author = null,
                text = null,
                album = "Альбом",
                request = anonymousRequest(),
                countSongs = fixtureCounter(),
            )

        assertEquals("""{"count":4}""", ObjectMapper().writeValueAsString(response))
    }

    @Test
    fun `счётчик не пишет событие аналитики`() {
        // Свой контроллер со СВОИМ моком mainController: мок в классе общий, и другие тесты
        // (паритет со списком) пишут в него событие — иначе проверка зависела бы от порядка тестов.
        val isolatedMainController = Mockito.mock(MainController::class.java)
        val isolated =
            PublicApiController(
                mainController = isolatedMainController,
                storageService = storageService,
                storageApiClient = storageApiClient,
                siteUserResolver = siteUserResolver,
                minioProxyUrl = "",
            )

        isolated.songsCountWith(
            songName = null,
            author = null,
            text = null,
            album = "Альбом",
            request = anonymousRequest(),
            countSongs = fixtureCounter(),
        )

        assertTrue(
            Mockito.mockingDetails(isolatedMainController).invocations.isEmpty(),
            "счётчик не должен писать события в tbl_events (фильтр в аналитике считается по /songs)",
        )
    }

    // ---- Автор: резолв алиасов и фолбэк на строгое равенство ----

    @Test
    fun `автор-алиас ищется по набору реальных имён`() {
        val captured = mutableListOf<Map<String, String>>()
        val response =
            controller().songsCountWith(
                songName = null,
                author = "Мумий Тролль",
                text = null,
                album = null,
                request = anonymousRequest(),
                resolveAuthor = { term ->
                    if (term == "Мумий Тролль") listOf(AuthorAliasMatch("Наутилус", listOf("Мумий Тролль"))) else emptyList()
                },
                countSongs = fixtureCounter(captured),
            )

        assertEquals("Наутилус", captured.single()["author_in"], "алиас резолвится в набор реальных имён")
        assertFalse(captured.single().containsKey("author"), "при найденном авторе ключа author нет")
        assertEquals(3L, response.count, "у «Наутилуса» 4 песни, из них 3 опубликованы")
    }

    @Test
    fun `без совпадений в tbl_authors автор ищется строгим равенством`() {
        val captured = mutableListOf<Map<String, String>>()
        controller().songsCountWith(
            songName = null,
            author = "Мумий Тролль",
            text = null,
            album = null,
            request = anonymousRequest(),
            resolveAuthor = { emptyList() },
            countSongs = fixtureCounter(captured),
        )

        assertEquals("Мумий Тролль", captured.single()["author"], "фолбэк — строгое равенство по имени")
        assertFalse(captured.single().containsKey("author_in"), "без совпадений ключа author_in нет")
    }

    // ---- Маппинг эндпоинта ----

    @Test
    fun `эндпоинт счётчика размечен как GET api public songs count`() {
        val type = PublicApiController::class.java
        assertEquals("/api/public", type.getAnnotation(RequestMapping::class.java).value.first())

        val method =
            type.getMethod(
                "songsCount",
                String::class.java,
                String::class.java,
                String::class.java,
                String::class.java,
                HttpServletRequest::class.java,
            )
        assertEquals("/songs/count", method.getAnnotation(GetMapping::class.java).value.first())
    }
}
