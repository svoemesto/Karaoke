package com.svoemesto.karaokeweb.services

import com.svoemesto.karaokeapp.model.Song
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito

/**
 * Матрица решений правила доступа к байтам/разметке онлайн-плеера (спека 557, OpenProject #358,
 * аудит BR-018).
 *
 * Проверяются все основания доступа из FR-003 + FR-009 и все отказы:
 *  - публичность песни / живой премиум / живая подписка на песню / гостевая share-сессия /
 *    предпросмотр по своему заданию редактора → полный доступ;
 *  - демо-токен → фрагмент;
 *  - «полный» токен без действующего права → понижение до фрагмента (НЕ полный доступ);
 *  - токен от другой песни / потерянное задание / отсутствие токена и сессии → отказ.
 *
 * БД в тесте не нужна: живой источник прав подменён (PlayerRightsProvider), share-сессия —
 * заглушкой, токены — настоящим PlayerGestureUnlockService (он in-memory, без БД).
 *
 * @see specs/557-close-gesture-token-paywall/spec.md
 */
class PlayerAccessServiceTest {
    companion object {
        /**
         * Глобалы karaoke-web (WORKING_DATABASE читает их лениво) — иначе проверка гостевой
         * share-сессии падает с IllegalStateException ещё до обращения к БД.
         */
        @JvmStatic
        @BeforeAll
        fun setUpEnv() {
            KaraokeWebTestEnv.init()
        }
    }

    private val songId = 42L
    private val otherSongId = 43L
    private val premiumUserId = 7L
    private val editorUserId = 9L

    private val tokenService = PlayerGestureUnlockService()
    private val shareLinkService = Mockito.mock(SongShareLinkService::class.java)
    private val rightsProvider = Mockito.mock(PlayerRightsProvider::class.java)

    private val service = PlayerAccessService(tokenService, shareLinkService, rightsProvider)

    /** Песня: по умолчанию платная (не публично доступна), демо-фрагмент — первые 30 секунд. */
    private fun song(free: Boolean = false): Song {
        val song = Mockito.mock(Song::class.java)
        Mockito.`when`(song.id).thenReturn(songId)
        Mockito.`when`(song.isFreelyAvailableNow).thenReturn(free)
        Mockito.`when`(song.demoFragmentStartSeconds).thenReturn(0.0)
        Mockito.`when`(song.demoFragmentEndSeconds).thenReturn(30.0)
        return song
    }

    private fun shareSessionValid(hash: String = "share-hash") {
        Mockito.`when`(shareLinkService.validateShareSession(hash, songId)).thenReturn(1L)
    }

    @Test
    fun `публично доступная песня — полный доступ`() {
        val token = tokenService.issueDirectAccessToken(songId, null)

        val decision = service.evaluate(song(free = true), token, null)

        assertTrue(decision.full, "публичная песня доступна всем")
        assertNull(decision.demoRange)
        assertEquals(PlayerAccessService.REASON_SONG_PUBLIC, decision.reason)
    }

    @Test
    fun `действующий премиум — полный доступ`() {
        Mockito.`when`(rightsProvider.isPremium(premiumUserId)).thenReturn(true)
        val token = tokenService.issueDirectAccessToken(songId, premiumUserId)

        val decision = service.evaluate(song(), token, null)

        assertTrue(decision.full, "у премиум-пользователя полный доступ")
        assertNull(decision.demoRange)
        assertEquals(PlayerAccessService.REASON_PREMIUM, decision.reason)
    }

    @Test
    fun `бессрочная подписка на эту песню — полный доступ`() {
        Mockito.`when`(rightsProvider.isSubscribedToSong(premiumUserId, songId)).thenReturn(true)
        val token = tokenService.issueDirectAccessToken(songId, premiumUserId)

        val decision = service.evaluate(song(), token, null)

        assertTrue(decision.full, "подписчик этой песни слушает её целиком")
        assertNull(decision.demoRange)
        assertEquals(PlayerAccessService.REASON_SUBSCRIPTION, decision.reason)
    }

    @Test
    fun `подписка на другую песню не открывает доступ к этой`() {
        // Подписка оформлена на ДРУГУЮ песню — доступа к этой нет.
        Mockito.`when`(rightsProvider.isSubscribedToSong(premiumUserId, otherSongId)).thenReturn(true)
        // Песня платная, премиума нет.
        val token = tokenService.issueDemoAccessToken(songId, 0.0, 30.0, premiumUserId)

        val decision = service.evaluate(song(), token, null)

        assertFalse(decision.full, "подписка на другую песню не даёт полного доступа")
        assertNotNull(decision.demoRange, "посетитель получает демо-фрагмент, а не ошибку")
    }

    @Test
    fun `гостевая share-сессия — полный доступ`() {
        shareSessionValid()
        val token = tokenService.issueDirectAccessToken(songId, null)

        val decision = service.evaluate(song(), token, "share-hash")

        assertTrue(decision.full, "гость по публичной share-ссылке смотрит плеер")
        assertTrue(decision.shareGuest, "гость по share-ссылке не экспортирует")
        assertEquals(PlayerAccessService.REASON_SHARE_GUEST, decision.reason)
    }

    @Test
    fun `предпросмотр по своему заданию редактора — полный доступ`() {
        Mockito.`when`(rightsProvider.isAssignmentOwner(100L, editorUserId, songId)).thenReturn(true)
        val token = tokenService.issueDirectAccessTokenForAssignment(songId, 100L, editorUserId)

        val decision = service.evaluate(song(), token, null)

        assertTrue(decision.full, "редактор с собственным заданием слышит свою разметку")
        assertNull(decision.demoRange)
        assertEquals(PlayerAccessService.REASON_ASSIGNMENT, decision.reason)
        assertEquals(100L, decision.assignmentId)
    }

    @Test
    fun `отозванное задание редактора — отказ`() {
        Mockito.`when`(rightsProvider.isAssignmentOwner(100L, editorUserId, songId)).thenReturn(false)
        val token = tokenService.issueDirectAccessTokenForAssignment(songId, 100L, editorUserId)

        val decision = service.evaluate(song(), token, null)

        assertFalse(decision.full)
        assertNull(decision.demoRange, "потерянное задание — отказ, а не фрагмент вместо черновика")
        assertFalse(decision.allowed)
        assertEquals(PlayerAccessService.REASON_ASSIGNMENT_REVOKED, decision.reason)
    }

    @Test
    fun `демо-токен — фрагмент, а не полный доступ`() {
        val token = tokenService.issueDemoAccessToken(songId, 12.0, 40.0, null)

        val decision = service.evaluate(song(), token, null)

        assertFalse(decision.full, "демо-токен не даёт полного доступа")
        assertNotNull(decision.demoRange)
        assertEquals(12.0, decision.demoRange!!.startSeconds)
        assertEquals(40.0, decision.demoRange!!.endSeconds)
        assertEquals(PlayerAccessService.REASON_DEMO_TOKEN, decision.reason)
    }

    @Test
    fun `полный токен без действующего права — понижение до фрагмента, а не полный доступ`() {
        // Именно этот случай закрывает обход оплаты: токен, выданный анонимному посетителю
        // «скрытым жестом», структурно неотличим от легитимного — но права у посетителя нет.
        val token = tokenService.issueDirectAccessToken(songId, null)

        val decision = service.evaluate(song(), token, null)

        assertFalse(decision.full, "токен без действующего права не открывает полный доступ")
        assertNotNull(decision.demoRange, "посетитель переходит в демо-режим, а не получает ошибку")
        assertEquals(0.0, decision.demoRange!!.startSeconds)
        assertEquals(30.0, decision.demoRange!!.endSeconds)
        assertEquals(PlayerAccessService.REASON_NO_RIGHTS, decision.reason)
    }

    @Test
    fun `токен от другой песни не открывает доступ`() {
        val token = tokenService.issueDirectAccessToken(otherSongId, null)

        val decision = service.evaluate(song(), token, null)

        assertFalse(decision.full)
        assertNull(decision.demoRange)
        assertFalse(decision.allowed)
        assertEquals(PlayerAccessService.REASON_NO_CREDENTIALS, decision.reason)
    }

    @Test
    fun `без токена и без сессии — отказ`() {
        val decision = service.evaluate(song(), null, null)

        assertFalse(decision.full)
        assertNull(decision.demoRange)
        assertFalse(decision.allowed)
        assertEquals(PlayerAccessService.REASON_NO_CREDENTIALS, decision.reason)
    }

    @Test
    fun `пустой токен не считается доступом`() {
        val decision = service.evaluate(song(), "", null)

        assertFalse(decision.allowed)
    }
}
