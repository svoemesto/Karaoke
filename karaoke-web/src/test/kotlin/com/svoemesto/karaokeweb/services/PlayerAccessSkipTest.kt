package com.svoemesto.karaokeweb.services

import com.svoemesto.karaokeapp.model.Song
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito

/**
 * Регрессионный тест «до/после» для аудита BR-022 «skip-tag-not-checked:player» (OpenProject #378,
 * карточка P-36-05, статус NEEDS-VERDICT).
 *
 * **ДО правки** `access` не проверял тег `SKIP`: плеер выдавал полный токен, если песня «в эфире»
 * (или пользователь премиум/подписан), даже когда контент снят по требованию правообладателя.
 * Остальные публичные пути SKIP уважали (share-ссылки, SEO-страница, листинги, SPA скрывает
 * страницу) — то есть правило было несогласованным.
 *
 * **ПОСЛЕ правки** SKIP-контент закрыт и в решении о доступе, и в момент отдачи байт
 * ([PlayerAccessService]): аноним/премиум/подписчик получают отказ (`song_skipped`), демо-фрагмент
 * не выдаётся. Исключения — те же, что в публичных выборках: `canWorkWithSkipped` (автор/владелец)
 * и предпросмотр по СВОЕМУ заданию редактора.
 *
 * **За владельцем:** подтвердить, что снятие контента всегда сопровождается снятием публикации и
 * удалением стемов (карточка допускает и такой ответ — тогда правка остаётся defense-in-depth).
 */
class PlayerAccessSkipTest {
    private val songId = 42L
    private val premiumUserId = 7L
    private val editorUserId = 9L

    private val tokenService = PlayerGestureUnlockService()
    private val shareLinkService = Mockito.mock(SongShareLinkService::class.java)
    private val rightsProvider = Mockito.mock(PlayerRightsProvider::class.java)

    private val service = PlayerAccessService(tokenService, shareLinkService, rightsProvider)

    private fun song(
        free: Boolean = false,
        tags: String = "",
    ): Song =
        Mockito.mock(Song::class.java).also {
            Mockito.`when`(it.id).thenReturn(songId)
            Mockito.`when`(it.tags).thenReturn(tags)
            Mockito.`when`(it.isFreelyAvailableNow).thenReturn(free)
            Mockito.`when`(it.demoFragmentStartSeconds).thenReturn(0.0)
            Mockito.`when`(it.demoFragmentEndSeconds).thenReturn(30.0)
        }

    @Test
    fun `SKIP-песня в эфире не отдаётся анониму`() {
        val decision = service.evaluate(song(free = true, tags = "SKIP"), token = null, session = null)

        assertFalse(decision.full, "SKIP-контент не отдаётся, даже если песня в окне бесплатного доступа")
        assertNull(decision.demoRange, "демо-фрагмент SKIP-контента тоже не выдаётся")
        assertEquals(PlayerAccessService.REASON_SONG_SKIPPED, decision.reason)
    }

    @Test
    fun `SKIP-песня не отдаётся премиум-пользователю`() {
        val song = song(free = false, tags = "POPULAR SKIP")
        val token = tokenService.issueDirectAccessToken(songId, premiumUserId)
        Mockito.`when`(rightsProvider.isPremium(premiumUserId)).thenReturn(true)
        Mockito.`when`(rightsProvider.canWorkWithSkipped(premiumUserId)).thenReturn(false)

        val decision = service.evaluate(song, token = token, session = null)

        assertFalse(decision.full)
        assertEquals(PlayerAccessService.REASON_SONG_SKIPPED, decision.reason)
    }

    @Test
    fun `пользователь с canWorkWithSkipped видит SKIP-контент`() {
        val song = song(free = true, tags = "SKIP")
        val token = tokenService.issueDirectAccessToken(songId, premiumUserId)
        Mockito.`when`(rightsProvider.canWorkWithSkipped(premiumUserId)).thenReturn(true)

        val decision = service.evaluate(song, token = token, session = null)

        assertTrue(decision.full, "автор/владелец SKIP-контента сохраняет доступ")
    }

    @Test
    fun `предпросмотр по своему заданию редактора не блокируется SKIP-тегом`() {
        val song = song(free = false, tags = "SKIP")
        val token = tokenService.issueDirectAccessTokenForAssignment(songId, assignmentId = 555, userId = editorUserId)
        Mockito.`when`(rightsProvider.canWorkWithSkipped(editorUserId)).thenReturn(false)
        Mockito.`when`(rightsProvider.isAssignmentOwner(555, editorUserId, songId)).thenReturn(true)

        val decision = service.evaluate(song, token = token, session = null)

        assertTrue(decision.full, "редактор в своём задании работает с контентом, в т.ч. SKIP")
        assertEquals(PlayerAccessService.REASON_ASSIGNMENT, decision.reason)
    }

    @Test
    fun `песня без SKIP-тега работает как раньше`() {
        val decision = service.evaluate(song(free = true, tags = "POPULAR"), token = null, session = null)

        assertTrue(decision.full)
        assertEquals(PlayerAccessService.REASON_SONG_PUBLIC, decision.reason)
    }

    @Test
    fun `чужое задание с SKIP-песней не даёт доступа`() {
        val song = song(free = false, tags = "SKIP")
        val token = tokenService.issueDirectAccessTokenForAssignment(songId, assignmentId = 555, userId = editorUserId)
        Mockito.`when`(rightsProvider.canWorkWithSkipped(editorUserId)).thenReturn(false)
        Mockito.`when`(rightsProvider.isAssignmentOwner(555, editorUserId, songId)).thenReturn(false)

        val decision = service.evaluate(song, token = token, session = null)

        assertFalse(decision.full)
        assertEquals(PlayerAccessService.REASON_SONG_SKIPPED, decision.reason)
        assertNotNull(decision.assignmentId, "id задания сохраняется для диагностики")
    }
}
