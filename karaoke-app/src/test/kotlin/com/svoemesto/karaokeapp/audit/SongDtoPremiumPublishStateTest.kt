package com.svoemesto.karaokeapp.audit

import com.svoemesto.karaokeapp.FakeKaraokeConnection
import com.svoemesto.karaokeapp.model.Song
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

/**
 * BR-087 (аудит #336, пакет PKG-S2-17, OpenProject #380): состояние премиум-автопубликации
 * в контракте `/api/song` / `/api/songs`.
 *
 * ДО ПРАВКИ ни одного из полей `newsPremium*`, `premiumAttempt*`, `*AutoPublishState`,
 * `premiumAutoPublishLastError` не было в `SongDTO` — единственном DTO, который отдают оба
 * эндпоинта карточки и списка. `SongEdit.vue` вычисляет из них
 * `premiumTelegramPublishState`/`premiumVkPublishState`, получал `undefined` и всегда
 * возвращал `null`: блоки статуса и кнопки «Повторить» (FR-006/FR-007/FR-008 спеки 122)
 * не рендерились никогда, админ не видел ни состояния публикации, ни текста ошибки.
 *
 * Тест фиксирует контракт: значения readiness-флагов `Song` доезжают до DTO, и `toDTO()`
 * по-прежнему не ходит в БД (иначе список из 18k песен получил бы N+1).
 *
 * Офлайн: живая БД не нужна (JDBC-слой подменяется FakeKaraokeConnection).
 */
class SongDtoPremiumPublishStateTest {
    private val storageService: KaraokeStorageService = mock(KaraokeStorageService::class.java)
    private val storageApiClient: StorageApiClient = mock(StorageApiClient::class.java)

    @Test
    fun `toDTO отдаёт состояние премиум-публикации (BR-087)`() {
        val database = FakeKaraokeConnection()
        val song =
            Song(
                database = database,
                storageService = storageService,
                storageApiClient = storageApiClient,
            ).apply {
                id = 11718
                newsPremiumPublishPending = true
                newsPremiumTelegramSent = true
                newsPremiumVkSent = false
                premiumAttemptCountTelegram = 1
                premiumAttemptCountVk = 3
                premiumAutoPublishLastError = "VK: community token expired"
                telegramAutoPublishState = "published"
                vkAutoPublishState = "error"
            }

        // BR-075 (аудит #336, пакет PKG-S3-22): установка readiness-флага теперь пишет флаг
        // в БД атомарно (compare-and-swap по колонке) — то есть НАСТРОЙКА теста ходит в БД.
        // Проверяем именно toDTO(): он обязан остаться без обращений к БД (иначе список из
        // 18k песен получил бы N+1).
        val callsBeforeDto = database.getConnectionCalls.get()

        val dto = song.toDTO()

        assertEquals(true, dto.newsPremiumPublishPending, "без этого поля статус в карточке всегда null")
        assertEquals(true, dto.newsPremiumTelegramSent)
        assertEquals(false, dto.newsPremiumVkSent)
        assertEquals(1, dto.premiumAttemptCountTelegram)
        assertEquals(3, dto.premiumAttemptCountVk)
        assertEquals("VK: community token expired", dto.premiumAutoPublishLastError)
        assertEquals("published", dto.telegramAutoPublishState)
        assertEquals("error", dto.vkAutoPublishState)
        assertEquals(
            callsBeforeDto,
            database.getConnectionCalls.get(),
            "toDTO() не должен обращаться к БД: список песен отдаётся пакетно",
        )
    }
}
