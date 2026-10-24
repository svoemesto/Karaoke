package com.svoemesto.karaokeapp.controllers

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/**
 * BR-410 (аудит #336, пакет PKG-S2-17, OpenProject #380): белый список сортировки
 * дайджеста share-ссылок.
 *
 * ДО ПРАВКИ в `ShareLinksAdminController.digest` было два допустимых имени
 * (`created_at`, `expires_at`), а UI присылал ключи вида `songName`/`ownerEmail`.
 * Неизвестный ключ молча заменялся на `created_at` — админ «сортировал по песне» и
 * получал сортировку по дате. Тест фиксирует два свойства контракта:
 *
 * 1. Каждая колонка таблицы, объявленная серверно-сортируемой, имеет своё SQL-выражение.
 * 2. Вход из запроса НИКОГДА не попадает в SQL: неизвестное/вредоносное значение
 *    приводит к серверному дефолту `l.created_at`.
 *
 * Офлайн: Spring-контекст не поднимается, БД не нужна.
 */
class ShareLinksSortWhitelistTest {
    @Test
    fun `ключи UI резолвятся в свои SQL-выражения`() {
        assertEquals("l.id", shareLinksSortExpression("id"))
        assertEquals("l.created_at", shareLinksSortExpression("createdAt"))
        assertEquals("l.expires_at", shareLinksSortExpression("expiresAt"))
        assertEquals("u.email", shareLinksSortExpression("ownerEmail"))
        assertEquals("s.song_name", shareLinksSortExpression("songName"))
        assertEquals("l.revoke_reason", shareLinksSortExpression("revokeReason"))
        assertEquals("l.token_hash", shareLinksSortExpression("secret"))
        assertEquals(
            "(l.active_session_token_hash IS NOT NULL AND l.active_session_lease_until > now())",
            shareLinksSortExpression("hasActiveSession"),
        )
    }

    @Test
    fun `неизвестный ключ уходит в серверный дефолт`() {
        assertEquals("l.created_at", shareLinksSortExpression("status"))
        assertEquals("l.created_at", shareLinksSortExpression(null))
        assertEquals("l.created_at", shareLinksSortExpression(""))
    }

    @Test
    fun `пользовательский ввод не попадает в SQL`() {
        val attacks =
            listOf(
                "l.id; DROP TABLE tbl_song_share_links",
                "created_at UNION SELECT password FROM tbl_users",
                "song_name FROM tbl_songs --",
            )
        for (attack in attacks) {
            assertEquals("l.created_at", shareLinksSortExpression(attack), "инъекция не прошла: $attack")
            assertFalse(shareLinksSortExpression(attack).contains("DROP"))
            assertFalse(shareLinksSortExpression(attack).contains("UNION"))
        }
    }
}
