package com.svoemesto.karaokeapp.audit

import com.svoemesto.karaokeapp.model.News
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * KARAOKE-361 (инцидент 2026-10-31, альбом «Лучшее (2026)»): новость «в коллекции» создавалась
 * в админке, но не доезжала до прода.
 *
 * ДО ПРАВКИ админка создавала новость через `News.createAutoAnnouncement`, который жёстко ставил
 * `source = "auto"`, а `News.listHashes()` пропускал в hash-diff только `source = 'manual'`.
 * Итог: строка ложилась в локальную БД и навсегда оставалась невидимой для sync-движка
 * `news` (LOCAL→SERVER), тогда как прод-путь создания зависит от флага
 * `newsAvailableAnnounced`, живущего в несинкаемом `player_readiness_flags`.
 *
 * Тест фиксирует контракт: админский источник участвует в hash-diff (иначе доставки нет),
 * `auto` — по-прежнему нет (защита specs/089 от mirror-delete), и выбор источника зависит
 * от роли процесса.
 *
 * Офлайн: живая БД не нужна (проверяются чистые функции `News.companion`).
 */
class NewsSyncSourceTest {
    @Test
    fun `hash-diff пропускает manual и admin, но не auto`() {
        val filter = News.hashSyncSourceFilter()

        assertTrue(filter.contains("'${News.SOURCE_MANUAL}'"), "ручные новости участвуют в синхронизации")
        assertTrue(filter.contains("'${News.SOURCE_ADMIN}'"), "KARAOKE-361: админские новости обязаны участвовать")
        assertFalse(filter.contains("'${News.SOURCE_AUTO}'"), "specs/089: авто-новости прода вне hash-diff")
    }

    @Test
    fun `админка создаёт доставляемую новость, прод — локальную`() {
        assertEquals(News.SOURCE_ADMIN, News.collectionAnnouncementSource(isAdminRuntime = true))
        assertEquals(News.SOURCE_AUTO, News.collectionAnnouncementSource(isAdminRuntime = false))
    }

    @Test
    fun `источники различаются по значению`() {
        // Регресс-защита: если значения совпадут, фильтр перестанет разделять «доедет» и «не доедет».
        val sources = setOf(News.SOURCE_MANUAL, News.SOURCE_AUTO, News.SOURCE_ADMIN)
        assertEquals(3, sources.size)
    }
}
