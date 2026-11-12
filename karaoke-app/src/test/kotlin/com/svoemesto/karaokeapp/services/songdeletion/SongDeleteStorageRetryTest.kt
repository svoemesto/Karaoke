package com.svoemesto.karaokeapp.services.songdeletion

import com.svoemesto.karaokeapp.WORKING_DATABASE
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import com.svoemesto.karaokeapp.services.StorageFileInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito
import reactor.core.publisher.Mono
import java.nio.file.Path

/**
 * Интеграционный тест повторов при недоступности хранилища
 * (KARAOKE-429, спека 739, FR-032, FR-033, SC-006).
 *
 * Сценарий: хранилище отвечает отказом → уборка делает 5 попыток, каждая
 * попытка — отдельная запись журнала; по исчерпании попыток прогон получает
 * статус `PARTIAL`, а остаток виден в журнале.
 *
 * Тест работает с живой LOCAL-БД (журнал уборки) и с подменённым хранилищем
 * (Mockito): реальные объекты MinIO не трогаются.
 *
 * @see specs/739-song-delete-cascade/spec.md FR-032, FR-033, SC-006
 */
class SongDeleteStorageRetryTest {
    /** Идентификатор прогона — уникальный, чтобы не пересекаться с другими тестами. */
    private val runId = 900_000_101L

    private val author = "Тестовый автор"
    private val album = "Тестовый альбом"
    private val year = 2020
    private val songName = "Тестовая песня"

    companion object {
        /**
         * Подготовка окружения БД (см. пояснение в `SongDeleteCascadeDbTest`):
         * реквизиты читаются из переменных окружения, в репозиторий не попадают.
         */
        @JvmStatic
        @BeforeAll
        fun prepareEnvironment() {
            val user = System.getenv("DB_LOCAL_POSTGRES_USER")
            val password = System.getenv("DB_LOCAL_POSTGRES_PASSWORD")
            if (user.isNullOrBlank() || password.isNullOrBlank()) return
            com.svoemesto.karaokeapp.services.APP_WORK_IN_CONTAINER = false
            com.svoemesto.karaokeapp.services.APP_WORK_ON_SERVER = false
            com.svoemesto.karaokeapp.services.DB_LOCAL_POSTGRES_USER = user
            com.svoemesto.karaokeapp.services.DB_LOCAL_POSTGRES_PASSWORD = password
            com.svoemesto.karaokeapp.services.DB_SERVER_POSTGRES_USER = System.getenv("DB_SERVER_POSTGRES_USER") ?: user
            com.svoemesto.karaokeapp.services.DB_SERVER_POSTGRES_PASSWORD = System.getenv("DB_SERVER_POSTGRES_PASSWORD") ?: password
            com.svoemesto.karaokeapp.services.DB_REMOTE_HOST = System.getenv("DB_REMOTE_HOST") ?: "localhost"
            com.svoemesto.karaokeapp.services.DB_REMOTE_PORT = System.getenv("DB_REMOTE_PORT") ?: "5432"
            com.svoemesto.karaokeapp.services.DB_REMOTE_SSL_MODE = System.getenv("DB_REMOTE_SSL_MODE") ?: "prefer"
        }
    }

    @Test
    fun `недоступное хранилище даёт пять попыток и статус PARTIAL`(
        @TempDir tmp: Path,
    ) {
        Assumptions.assumeTrue(
            !System.getenv("DB_LOCAL_POSTGRES_USER").isNullOrBlank(),
            "реквизиты LOCAL-БД не заданы в окружении — интеграционный тест неприменим",
        )
        val storage = Mockito.mock(KaraokeStorageService::class.java)
        val key = "$author/$year - $album/$songName.vocals.mp3"
        val objects = listOf(StorageFileInfo(SongOneTimeCleanupPlanner.BUCKET, key, "etag", 1024L))
        Mockito
            .`when`(storage.listFilesInfo(Mockito.anyString()))
            .thenReturn(objects)
        Mockito
            .`when`(storage.fileExists(Mockito.anyString(), Mockito.anyString()))
            .thenReturn(true)
        val failure = RuntimeException("хранилище недоступно")
        Mockito
            .doThrow(failure)
            .`when`(storage)
            .deleteFile(Mockito.anyString(), Mockito.anyString())

        val storageApi = Mockito.mock(StorageApiClient::class.java)
        val emptyList = emptyList<StorageFileInfo>()
        val noObjects = Mono.just(emptyList)
        Mockito
            .`when`(storageApi.listFilesInfo(Mockito.anyString()))
            .thenReturn(noObjects)

        // Прогон создаём вручную: состояние PLANNED → RUNNING, как это делает сервис.
        val createdRun =
            SongCleanupJournal.createRun(
                songId = 0,
                songName = songName,
                author = author,
                album = album,
                rootFolder = tmp.toFile().absolutePath,
                fileName = songName,
                year = year,
                albumId = 0,
                runKind = CleanupRunKind.SONG_DELETE,
                initiator = "test",
                database = WORKING_DATABASE,
            )
        Assumptions.assumeTrue(createdRun > 0, "журнал уборки недоступен — тест неприменим")

        try {
            val executor =
                SongCleanupExecutor(
                    database = WORKING_DATABASE,
                    storageService = storage,
                    storageApiClient = storageApi,
                    backoffMs = listOf(1L, 1L, 1L, 1L),
                )
            executor.execute(runId = createdRun, songId = 0, initiator = "test")

            val entries = SongCleanupJournal.listEntries(createdRun, WORKING_DATABASE)
            val attempts =
                entries.filter { it.pathOrKey == key && it.result == SongCleanupJournal.EntryResult.FAILED }
            assertEquals(5, attempts.size, "должно быть ровно 5 попыток (FR-032)")
            assertEquals(
                listOf(1, 2, 3, 4, 5),
                attempts.map { it.attempt }.sorted(),
                "номера попыток возрастают",
            )
            assertTrue(
                attempts.all { it.reason == "storage_unavailable" },
                "причина отказа фиксируется в журнале",
            )
            assertEquals(
                CleanupRunState.PARTIAL,
                SongCleanupJournal.currentState(createdRun, WORKING_DATABASE),
                "прогон с отказами не может быть DONE (SC-003)",
            )
        } finally {
            SongCleanupJournal.deleteRun(createdRun, WORKING_DATABASE)
        }
    }
}
