package com.svoemesto.karaokeapp.services.songdeletion

import com.svoemesto.karaokeapp.FakeKaraokeConnection
import com.svoemesto.karaokeapp.model.Song
import com.svoemesto.karaokeapp.model.SongDeleteStatus
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.io.File
import java.nio.file.Files
import java.sql.Connection

/**
 * Тест отказа базы данных при удалении (KARAOKE-429, спека 739, FR-002, FR-003, SC-003).
 *
 * **Суть:** отказ БД не должен выглядеть как успех. Нижний уровень
 * ([Song.deleteFromDb]) обязан вернуть признак `DB_ERROR`, а эндпоинт — ответить
 * `500 db_error`, а не `200`. Файлы при этом не трогаются (fail-closed).
 *
 * @see specs/739-song-delete-cascade/spec.md FR-002, FR-003, SC-003
 */
class SongDeleteDbFailureTest {
    /** Соединение, на котором любой запрос падает. */
    private fun brokenConnection(): Connection {
        val connection = Mockito.mock(Connection::class.java)
        val failure = RuntimeException("БД недоступна")
        Mockito
            .`when`(connection.prepareStatement(Mockito.anyString()))
            .thenThrow(failure)
        return connection
    }

    @Test
    fun `отказ базы данных даёт DB_ERROR, а не успех`() {
        val brokenDb = FakeKaraokeConnection(brokenConnection(), name = "LOCAL-BROKEN")
        val song =
            Song(
                database = brokenDb,
                storageService = Mockito.mock(KaraokeStorageService::class.java),
                storageApiClient = Mockito.mock(StorageApiClient::class.java),
            )

        val result = song.deleteFromDb()

        assertEquals(SongDeleteStatus.DB_ERROR, result.status, "отказ БД — это DB_ERROR, а не успех")
        assertFalse(result.isDeleted, "удаления не было")
        assertFalse(result.isNotFound, "это не «строки нет», а отказ БД")
        assertTrue(result.isDbError, "признак отказа БД выставлен")
        assertTrue(result.error?.isNotBlank() == true, "причина отказа видна вызывающему")
    }

    @Test
    fun `отказ базы данных не удаляет файлы песни`() {
        val tmp = Files.createTempDirectory("song-delete-db-failure")
        try {
            val file = File(tmp.toFile(), "Песня.flac")
            file.writeText("x")
            val brokenDb = FakeKaraokeConnection(brokenConnection(), name = "LOCAL-BROKEN")
            val song =
                Song(
                    database = brokenDb,
                    storageService = Mockito.mock(KaraokeStorageService::class.java),
                    storageApiClient = Mockito.mock(StorageApiClient::class.java),
                )
            song.rootFolder = tmp.toFile().path

            song.deleteFromDb()

            assertTrue(file.exists(), "при отказе БД файлы не трогаются (fail-closed)")
        } finally {
            tmp.toFile().deleteRecursively()
        }
    }

    @Test
    fun `эндпоинт отдаёт 500 db_error, а не 200`() {
        val controller = File("src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt")
        val text = controller.readText()
        assertTrue(
            text.contains("\"db_error\""),
            "эндпоинт удаления должен отдавать код db_error",
        )
        assertTrue(
            Regex("""\.status\(500\)[\s\S]{0,400}"db_error"""").containsMatchIn(text),
            "отказ БД должен отдавать HTTP 500 с кодом db_error, а не 200",
        )
    }
}
