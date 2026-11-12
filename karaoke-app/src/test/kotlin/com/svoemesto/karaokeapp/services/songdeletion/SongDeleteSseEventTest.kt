package com.svoemesto.karaokeapp.services.songdeletion

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Наличие SSE-события об удалении строки песни (KARAOKE-429, спека 739).
 *
 * **Дефект, найденный владельцем на живой приёмке 2026-11-12.** После удаления
 * песни её строка оставалась в таблице админки до ручной перезагрузки таблицы.
 * Причина: событие `recordDelete` слал прежний путь (`Song.deleteFromDb`), а новая
 * точка удаления обходит этот метод — событие потерялось. Админка убирает строку
 * из таблицы по этому событию (`App.vue` → `deleteSongByUserEvent` → splice в
 * `songsDigest`).
 *
 * Тест — «страховка от тихой потери»: пока удаление идёт через
 * `SongDeletionService`, событие обязано слаться именно там. Событие проверяется
 * по исходнику (как и другие структурные проверки фичи), потому что рассылка SSE
 * требует Spring-контекста и живых вкладок.
 *
 * @see specs/739-song-delete-cascade/spec.md FR-003
 */
class SongDeleteSseEventTest {
    private val serviceSource =
        File("src/main/kotlin/com/svoemesto/karaokeapp/services/songdeletion/SongDeletionService.kt")

    @Test
    fun `путь удаления шлёт событие recordDelete`() {
        assertTrue(serviceSource.isFile, "исходник сервиса удаления не найден: ${serviceSource.path}")
        val text = serviceSource.readText()
        assertTrue(
            text.contains("SseNotification.recordDelete"),
            "путь удаления должен слать SSE-событие recordDelete — иначе строка остаётся " +
                "в таблице админки до ручной перезагрузки",
        )
        assertTrue(
            text.contains("tableName = \"tbl_songs\""),
            "событие удаления должно указывать таблицу tbl_songs (как прежний путь Song.deleteFromDb)",
        )
        assertTrue(
            text.contains("recordId = songId"),
            "в событии должен быть id удалённой песни — по нему админка убирает строку из таблицы",
        )
    }
}
