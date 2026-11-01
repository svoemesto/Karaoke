package com.svoemesto.karaokeapp.audit

import com.svoemesto.karaokeapp.FakeKaraokeConnection
import com.svoemesto.karaokeapp.model.Song
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import com.svoemesto.karaokeapp.sync.SongSyncTarget
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

/**
 * KARAOKE-361 (инциденты 2026-10-30 «Секрет» и 2026-10-31 «Лучшее (2026)»): новость «в коллекции»
 * не появлялась на проде, потому что флаг `newsAvailableAnnounced` не доезжал синхронизацией.
 *
 * Флаг живёт в общем JSON-поле `player_readiness_flags`, а `Song.getDiff()` помечает это поле
 * нереальным (`recordDiffRealField = false`) — защита BR-075: приложение не должно писать всё поле
 * целиком, иначе затрёт ключи, записанные параллельным CAS-потоком.
 *
 * Симптом: изменение только этого флага давало дифф из одних «нереальных» полей →
 * `SongSyncTarget.shouldPush()` возвращал false → строка не уходила на прод → прод не видел
 * перехода false→true → `SongReleaseAnnouncementService` новость не создавал.
 *
 * Правка (KARAOKE-361) узкая: `SyncTarget.extraPushableDiff()` добавляет реальный дифф
 * `player_readiness_flags` ТОЛЬКО в синхронизации и ТОЛЬКО когда меняется именно
 * `newsAvailableAnnounced`. `saveToDb()` и защита BR-075 не затронуты.
 *
 * Офлайн: живая БД не нужна (FakeKaraokeConnection).
 */
class SongNewsFlagSyncTest {
    private val storageService: KaraokeStorageService = mock(KaraokeStorageService::class.java)
    private val storageApiClient: StorageApiClient = mock(StorageApiClient::class.java)

    private fun song(
        id: Long,
        announced: Boolean,
        accompanimentReady: Boolean = true,
    ): Song =
        Song(
            database = FakeKaraokeConnection(),
            storageService = storageService,
            storageApiClient = storageApiClient,
        ).apply {
            this.id = id
            stemAccompanimentReady = accompanimentReady
            newsAvailableAnnounced = announced
        }

    @Test
    fun `изменение флага новости уходит на прод реальным диффом`() {
        val local = song(id = 30398, announced = true)
        val prod = song(id = 30398, announced = false)

        val extra = SongSyncTarget.extraPushableDiff(local, prod)

        assertEquals(1, extra.size, "ожидается ровно один дополнительный дифф")
        val diff = extra.single()
        assertEquals("player_readiness_flags", diff.recordDiffName)
        assertTrue(diff.recordDiffRealField, "дифф обязан быть реальным, иначе строка не уйдёт в UPDATE")
        assertTrue(diff.recordDiffValueNew.toString().contains("newsAvailableAnnounced"))

        // Без extraPushableDiff строка не пушилась бы — это и был дефект.
        assertFalse(SongSyncTarget.shouldPush(SongSyncTarget.getDiff(local, prod)))
        assertTrue(SongSyncTarget.shouldPush(SongSyncTarget.getDiff(local, prod) + extra))
    }

    @Test
    fun `изменения только готовности файлов по-прежнему не пушатся`() {
        // BR-075: поле целиком не пишем из приложения, поэтому «шум готовности» не должен
        // превращаться в UPDATE на проде.
        val local = song(id = 1, announced = false, accompanimentReady = true)
        val prod = song(id = 1, announced = false, accompanimentReady = false)

        assertTrue(SongSyncTarget.extraPushableDiff(local, prod).isEmpty())
        assertFalse(SongSyncTarget.shouldPush(SongSyncTarget.getDiff(local, prod)))
    }

    @Test
    fun `флаг совпадает — дополнительного диффа нет`() {
        val local = song(id = 2, announced = true)
        val prod = song(id = 2, announced = true)

        assertTrue(SongSyncTarget.extraPushableDiff(local, prod).isEmpty())
    }
}
