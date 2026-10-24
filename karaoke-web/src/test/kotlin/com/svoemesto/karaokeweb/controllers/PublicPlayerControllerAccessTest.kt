package com.svoemesto.karaokeweb.controllers

import com.svoemesto.karaokeapp.model.Song
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import com.svoemesto.karaokeweb.services.PlayerAccessService
import com.svoemesto.karaokeweb.services.PlayerGestureUnlockService
import com.svoemesto.karaokeweb.services.PlayerRightsProvider
import com.svoemesto.karaokeweb.services.PlayerSongLoader
import com.svoemesto.karaokeweb.services.SiteUserResolver
import com.svoemesto.karaokeweb.services.SongShareLinkService
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.mock.web.MockHttpServletRequest
import java.net.InetSocketAddress

/**
 * Регрессионный тест «до/после» для закрытия обхода оплаты по «скрытому жесту»
 * (спека 557, OpenProject #358, аудит BR-018).
 *
 * **ДО правки** (код без живой проверки права) оба теста падали:
 *  - публичный эндпоинт событий возвращал в `meta` токен доступа (жест выдавал доступ);
 *  - анонимный запрос полного стема платной песни отдавал полные байты.
 *
 * **ПОСЛЕ правки**: значение доступа в ответе событий отсутствует, а запрос полного стема
 * либо отклоняется, либо отдаёт демо-фрагмент — полный файл не покидает сервер.
 *
 * Тест не ходит в БД: загрузка песни подменена (PlayerSongLoader), живой источник прав — заглушкой,
 * а «хранилище» — локальным HTTP-сервером на случайном порту (nginx-proxy в проде).
 *
 * @see specs/557-close-gesture-token-paywall/spec.md
 */
class PublicPlayerControllerAccessTest {
    private val songId = 42L

    // Настоящий сервис токенов: он in-memory, без БД — так же, как в проде.
    private val gestureUnlockService = PlayerGestureUnlockService()
    private val shareLinkService = Mockito.mock(SongShareLinkService::class.java)
    private val rightsProvider = Mockito.mock(PlayerRightsProvider::class.java)
    private val siteUserResolver = Mockito.mock(SiteUserResolver::class.java)
    private val mainController = Mockito.mock(MainController::class.java)
    private val storageService = Mockito.mock(KaraokeStorageService::class.java)
    private val storageApiClient = Mockito.mock(StorageApiClient::class.java)
    private val songLoader = Mockito.mock(PlayerSongLoader::class.java)

    private lateinit var storageStub: HttpServer
    private lateinit var storageUrl: String

    /** Полный «стем»: 100 корректных MPEG-1 Layer III кадров (128 kbps, 44100 Hz) ≈ 2.6 с. */
    private val fullStemBytes: ByteArray by lazy { mp3Frames(100) }

    @BeforeEach
    fun setUp() {
        storageStub = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        storageStub.createContext("/") { exchange ->
            if (exchange.requestMethod == "HEAD") {
                exchange.sendResponseHeaders(200, -1)
            } else {
                exchange.sendResponseHeaders(200, fullStemBytes.size.toLong())
                exchange.responseBody.use { it.write(fullStemBytes) }
            }
            exchange.close()
        }
        storageStub.start()
        storageUrl = "http://127.0.0.1:${storageStub.address.port}"

        // Платная песня (не публично доступна), демо-фрагмент — первая секунда.
        val song = Mockito.mock(Song::class.java)
        Mockito.`when`(song.id).thenReturn(songId)
        Mockito.`when`(song.isFreelyAvailableNow).thenReturn(false)
        Mockito.`when`(song.isContentReady).thenReturn(true)
        Mockito.`when`(song.demoFragmentStartSeconds).thenReturn(0.0)
        Mockito.`when`(song.demoFragmentEndSeconds).thenReturn(1.0)
        Mockito.`when`(song.storageFileName).thenReturn("Author/2000 - Album/Title")
        Mockito.`when`(songLoader.load(songId)).thenReturn(song)
    }

    @AfterEach
    fun tearDown() {
        storageStub.stop(0)
    }

    private fun playerController(): PublicPlayerController =
        PublicPlayerController(
            storageService = storageService,
            storageApiClient = storageApiClient,
            gestureUnlockService = gestureUnlockService,
            accessService = PlayerAccessService(gestureUnlockService, shareLinkService, rightsProvider),
            songLoader = songLoader,
            siteUserResolver = siteUserResolver,
            mainController = mainController,
            shareLinkService = shareLinkService,
            minioProxyUrl = storageUrl,
        )

    private fun apiController(): PublicApiController =
        PublicApiController(
            mainController = mainController,
            storageService = storageService,
            storageApiClient = storageApiClient,
            siteUserResolver = siteUserResolver,
            minioProxyUrl = storageUrl,
        )

    private fun request(): MockHttpServletRequest = MockHttpServletRequest().apply { remoteAddr = "10.0.0.5" }

    /** Клик по полю метаданных песни — тот самый beacon, на котором раньше минтился «жест». */
    private fun metaClick(): Map<String, Any> =
        mapOf(
            "eventType" to "clickToLink",
            "linkType" to "songMeta",
            "linkName" to "key",
            "songId" to songId.toString(),
            "shiftKey" to "true",
            "clientId" to "anon-client",
        )

    @Test
    fun `скрытый жест не выдаёт значение доступа`() {
        val api = apiController()
        val req = request()

        // Три «жеста» подряд — ровно столько, сколько требовал прежний распознаватель.
        val answers = (1..3).map { api.events(metaClick(), req) }

        answers.forEachIndexed { index, answer ->
            assertNull(
                answer["meta"],
                "после $index-го клика публичный эндпоинт событий не должен возвращать значение доступа",
            )
        }
    }

    @Test
    fun `анонимный посетитель не получает полный стем платной песни`() {
        val api = apiController()
        val controller = playerController()
        val req = request()

        // Токен, который раньше выдавал жест (структурно неотличим от легитимного).
        val gestureToken = api.events(metaClick(), req)["meta"] as String?

        // 1. С токеном из жеста — доступа нет вовсе.
        val byGesture = controller.fileAccompaniment(songId, gestureToken, null)
        assertNotEquals(
            200,
            byGesture.statusCode.value(),
            "токен из скрытого жеста не должен открывать доступ к платному контенту",
        )

        // 2. С токеном, выданным напрямую (тот же сервис, тот же TTL — но права у посетителя нет):
        //    сервер отдаёт демо-фрагмент, а НЕ полный файл.
        val directToken = gestureUnlockService.issueDirectAccessToken(songId, null)
        val byDirectToken = controller.fileAccompaniment(songId, directToken, null)
        val body = byDirectToken.body
        assertTrue(
            byDirectToken.statusCode.value() != 200 || body == null || body.size < fullStemBytes.size,
            "анонимный посетитель не должен получать полный стем: " +
                "status=${byDirectToken.statusCode.value()}, size=${body?.size}, full=${fullStemBytes.size}",
        )
    }

    /**
     * Синтезирует mp3-поток из корректных MPEG-1 Layer III кадров (128 kbps, 44100 Hz, без
     * padding): длина кадра 144*128000/44100 = 417 байт, длительность кадра 1152/44100 ≈ 0.0261 с.
     * Настоящий mp3 нужен, чтобы обрезка демо-фрагмента (Mp3Trimmer) действительно резала поток
     * по границам кадров, а не возвращала исходные байты.
     */
    private fun mp3Frames(count: Int): ByteArray {
        val frameLength = 144 * 128000 / 44100
        val out = ByteArray(frameLength * count)
        for (i in 0 until count) {
            val offset = i * frameLength
            out[offset] = 0xFF.toByte()
            out[offset + 1] = 0xFB.toByte() // MPEG-1, Layer III, без CRC
            out[offset + 2] = 0x90.toByte() // 128 kbps, 44100 Hz, без padding
            out[offset + 3] = 0x00.toByte() // stereo
        }
        return out
    }
}
