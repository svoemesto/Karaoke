package com.svoemesto.karaokeapp.services.songdeletion

import com.svoemesto.karaokeapp.services.APP_WORK_IN_CONTAINER
import com.svoemesto.karaokeapp.services.APP_WORK_ON_SERVER
import com.svoemesto.karaokeapp.services.DB_LOCAL_POSTGRES_PASSWORD
import com.svoemesto.karaokeapp.services.DB_LOCAL_POSTGRES_USER
import com.svoemesto.karaokeapp.services.DB_REMOTE_HOST
import com.svoemesto.karaokeapp.services.DB_REMOTE_PORT
import com.svoemesto.karaokeapp.services.DB_REMOTE_SSL_MODE
import com.svoemesto.karaokeapp.services.DB_SERVER_POSTGRES_PASSWORD
import com.svoemesto.karaokeapp.services.DB_SERVER_POSTGRES_USER
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito

/**
 * Тест идемпотентности повторного удаления (KARAOKE-429, спека 739, FR-002).
 *
 * Повторное удаление уже удалённой песни — **успех** (`already_deleted`), а не
 * ошибка; первое обращение к несуществующей песне — `404 song_not_found`.
 *
 * @see specs/739-song-delete-cascade/spec.md FR-002 (edge case «Песня уже удалена»)
 */
class SongDeleteIdempotencyTest {
    /** Заведомо несуществующая песня (для сценария «уже удалена»). */
    private val missingSongId = 900_000_201L

    /**
     * Вторая заведомо несуществующая песня (для сценария «никогда не удалялась»).
     *
     * Идентификаторы разные намеренно: прогон «уже удалена» создаёт запись журнала
     * для своей песни, и общий id сделал бы второй тест зависимым от порядка
     * выполнения тестов.
     */
    private val unknownSongId = 900_000_202L

    companion object {
        /** Подготовка окружения БД (реквизиты — из переменных окружения). */
        @JvmStatic
        @BeforeAll
        fun prepareEnvironment() {
            val user = System.getenv("DB_LOCAL_POSTGRES_USER")
            val password = System.getenv("DB_LOCAL_POSTGRES_PASSWORD")
            if (user.isNullOrBlank() || password.isNullOrBlank()) return
            APP_WORK_IN_CONTAINER = false
            APP_WORK_ON_SERVER = false
            DB_LOCAL_POSTGRES_USER = user
            DB_LOCAL_POSTGRES_PASSWORD = password
            DB_SERVER_POSTGRES_USER = System.getenv("DB_SERVER_POSTGRES_USER") ?: user
            DB_SERVER_POSTGRES_PASSWORD = System.getenv("DB_SERVER_POSTGRES_PASSWORD") ?: password
            DB_REMOTE_HOST = System.getenv("DB_REMOTE_HOST") ?: "localhost"
            DB_REMOTE_PORT = System.getenv("DB_REMOTE_PORT") ?: "5432"
            DB_REMOTE_SSL_MODE = System.getenv("DB_REMOTE_SSL_MODE") ?: "prefer"
        }
    }

    /** Сервис с подменёнными хранилищами: глобали `KSS_APP`/`SAC_APP` в тесте не инициализированы. */
    private fun serviceWithMocks(): SongDeletionService =
        SongDeletionService(
            database = SongDeleteTestDatabase.local(),
            storageServiceProvider = { Mockito.mock(KaraokeStorageService::class.java) },
            storageApiClientProvider = { Mockito.mock(StorageApiClient::class.java) },
        )

    @Test
    fun `первое обращение к несуществующей песне - song_not_found`() {
        Assumptions.assumeTrue(
            !System.getenv("DB_LOCAL_POSTGRES_USER").isNullOrBlank(),
            "реквизиты LOCAL-БД не заданы в окружении — интеграционный тест неприменим",
        )
        val service = serviceWithMocks()

        val outcome = service.delete(unknownSongId, initiator = "test")

        assertTrue(
            outcome is SongDeletionService.Outcome.NotFound,
            "первое обращение к несуществующей песне — not_found: $outcome",
        )
    }

    @Test
    fun `повторное удаление уже удалённой песни - already_deleted`() {
        Assumptions.assumeTrue(
            !System.getenv("DB_LOCAL_POSTGRES_USER").isNullOrBlank(),
            "реквизиты LOCAL-БД не заданы в окружении — интеграционный тест неприменим",
        )
        val runId =
            SongCleanupJournal.createRun(
                songId = missingSongId,
                songName = "Тестовая песня 739 (повтор)",
                author = "",
                album = "",
                rootFolder = "",
                fileName = "",
                year = 0,
                albumId = 0,
                runKind = CleanupRunKind.SONG_DELETE,
                initiator = "test",
                database = SongDeleteTestDatabase.local(),
            )
        Assumptions.assumeTrue(runId > 0, "журнал уборки недоступен — тест неприменим")
        // Прогон состояния PLANNED означает «удаление не состоялось» — повтор не считается успехом.
        SongCleanupJournal.updateRunState(runId, CleanupRunState.APPROVED, SongDeleteTestDatabase.local())

        try {
            val service = serviceWithMocks()
            val outcome = service.delete(missingSongId, initiator = "test")

            assertTrue(
                outcome is SongDeletionService.Outcome.AlreadyDeleted,
                "повторное удаление — already_deleted, а не ошибка: $outcome",
            )
        } finally {
            SongCleanupJournal.deleteRunWithEntries(runId, SongDeleteTestDatabase.local())
        }
    }
}
