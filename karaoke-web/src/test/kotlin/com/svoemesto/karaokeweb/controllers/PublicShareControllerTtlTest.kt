package com.svoemesto.karaokeweb.controllers

import com.svoemesto.karaokeapp.model.SiteUser
import com.svoemesto.karaokeweb.WORKING_DATABASE
import com.svoemesto.karaokeweb.services.KaraokeWebTestEnv
import com.svoemesto.karaokeweb.services.SongShareLinkService
import com.svoemesto.karaokeweb.services.SiteUserResolver
import com.svoemesto.karaokeweb.util.ShareErrorCode
import com.svoemesto.karaokeweb.util.ShareTtlOptions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.mock.web.MockHttpServletRequest

/**
 * KARAOKE-399: коды ошибок share-флоу — из enum `ShareErrorCode`, а не строковые
 * литералы; недопустимый срок действия ссылки даёт **свой** код `share.invalidTtl`.
 *
 * **Что было.** При недопустимом `ttlSeconds` контроллер отдавал `400` с кодом
 * `share.tokenMissing` — про отсутствие токена, хотя токен тут ни при чём. Фронт по
 * коду выбирает пользовательский текст, поэтому показывался текст не про то.
 */
class PublicShareControllerTtlTest {
    companion object {
        private const val PUBLIC_SITE_URL = "https://sm-karaoke.ru"
        private const val SITE_USER_ID = 77L
        private const val SONG_ID = 42L

        @JvmStatic
        @BeforeAll
        fun setUpEnv() {
            // WORKING_DATABASE инициализируется лениво и читает WEB_WORK_ON_SERVER —
            // без этих значений он падает с IllegalStateException.
            KaraokeWebTestEnv.init()
        }
    }

    /** Премиум-пользователь: `isEffectivePremium` — вычисляемое свойство, мокается геттер. */
    private fun premiumUser(): SiteUser =
        Mockito.mock(SiteUser::class.java).also {
            Mockito.`when`(it.id).thenReturn(SITE_USER_ID)
            Mockito.`when`(it.isEffectivePremium).thenReturn(true)
        }

    /**
     * Контроллер, у которого резолвер отдаёт премиум-пользователя для КОНКРЕТНОГО
     * объекта запроса (Правило 6: `MockHttpServletRequest` не переопределяет `equals`,
     * поэтому стабим той же ссылкой, что уйдёт в контроллер).
     */
    private fun controllerFor(
        request: MockHttpServletRequest,
        shareService: SongShareLinkService = Mockito.mock(SongShareLinkService::class.java),
    ): PublicShareController {
        val resolver = Mockito.mock(SiteUserResolver::class.java)
        Mockito.doReturn(premiumUser()).`when`(resolver).resolve(request)
        return PublicShareController(
            shareService = shareService,
            siteUserResolver = resolver,
            publicSiteUrl = PUBLIC_SITE_URL,
        )
    }

    @Test
    fun `недопустимый TTL даёт 400 с кодом share invalidTtl`() {
        val request = MockHttpServletRequest()
        val response = controllerFor(request).create(songId = SONG_ID, ttlSeconds = 60L, request = request)

        assertEquals(400, response.statusCode.value())
        assertEquals(
            ShareErrorCode.INVALID_TTL.dbValue,
            response.body?.get("errorCode"),
            "недопустимый срок должен давать отдельный код, а не share.tokenMissing",
        )
    }

    @Test
    fun `недопустимый TTL не равен коду про отсутствие токена`() {
        val request = MockHttpServletRequest()
        val response = controllerFor(request).create(songId = SONG_ID, ttlSeconds = 120L, request = request)

        assertTrue(
            response.body?.get("errorCode") != ShareErrorCode.TOKEN_MISSING.dbValue,
            "код про недопустимый срок не должен совпадать с кодом про отсутствие токена",
        )
    }

    @Test
    fun `все допустимые TTL проходят проверку и доходят до создания ссылки`() {
        val shareService = Mockito.mock(SongShareLinkService::class.java)

        for (ttl in ShareTtlOptions.ALLOWED_SECONDS) {
            // Стаб на КОНКРЕТНЫЕ аргументы (как в PkgS329BehaviorTest): матчеры
            // Mockito возвращают null, а параметры Kotlin — non-null.
            // Сервис бросает доменную ошибку — значит, контроллер ДОШЁЛ до вызова
            // createLink (проверка TTL не отсекла запрос).
            Mockito
                .doThrow(SongShareLinkService.SongUnavailable())
                .`when`(shareService)
                .createLink(SITE_USER_ID, SONG_ID, ttl, PUBLIC_SITE_URL, WORKING_DATABASE)

            val request = MockHttpServletRequest()
            val response = controllerFor(request, shareService).create(songId = SONG_ID, ttlSeconds = ttl, request = request)

            assertEquals(
                409,
                response.statusCode.value(),
                "при TTL=$ttl контроллер должен дойти до создания ссылки (409 — SongUnavailable), а не отсечь запрос",
            )
        }
    }

    @Test
    fun `недопустимый TTL не доходит до создания ссылки`() {
        val shareService = Mockito.mock(SongShareLinkService::class.java)
        val request = MockHttpServletRequest()

        val response = controllerFor(request, shareService).create(songId = SONG_ID, ttlSeconds = 999L, request = request)

        assertEquals(400, response.statusCode.value())
        Mockito.verify(shareService, Mockito.never()).createLink(SITE_USER_ID, SONG_ID, 999L, PUBLIC_SITE_URL, WORKING_DATABASE)
    }

    @Test
    fun `набор допустимых TTL не изменился`() {
        assertEquals(setOf(3600L, 86_400L, 604_800L), ShareTtlOptions.ALLOWED_SECONDS)
        assertEquals(3600L, ShareTtlOptions.DEFAULT_SECONDS)
    }

    @Test
    fun `код недопустимого TTL зарегистрирован в enum`() {
        assertEquals("share.invalidTtl", ShareErrorCode.INVALID_TTL.dbValue)
    }
}
