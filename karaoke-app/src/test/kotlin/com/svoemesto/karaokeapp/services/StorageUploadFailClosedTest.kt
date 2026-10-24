package com.svoemesto.karaokeapp.services

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

/**
 * Регрессия BR-141 (аудит #336, PKG-S2-11): ошибка загрузки не превращается в
 * `null`/«успех».
 *
 * До правки `uploadFile(path)` при отсутствии локального файла (и при любой
 * другой ошибке) печатал сообщение и возвращал `null`; `executeFinalizeStemJob`
 * игнорировал результат и ставил заданию `DONE` — результат платной функции
 * терялся. Теперь контракт как у `downloadFile`: исключение.
 */
class StorageUploadFailClosedTest {
    private fun client(): StorageApiClientImpl =
        StorageApiClientImpl(
            remoteEndpoint = "http://127.0.0.1:1",
            storageKey = "key",
            storageSecret = "secret",
            storageMetadataCache = mock(StorageMetadataCache::class.java),
            storageCircuitBreaker = mock(StorageCircuitBreaker::class.java),
            fileExistsTimeoutSeconds = 1,
            uploadReadTimeoutSeconds = 1,
        )

    @Test
    fun `missing local file throws instead of returning null`() {
        assertThrows(IllegalArgumentException::class.java) {
            client().uploadFile(
                bucketName = "karaoke",
                fileName = "stem.mp3",
                pathToFileOnDisk = "/nonexistent/pkg-s2-11/stem.mp3",
            )
        }
    }
}
