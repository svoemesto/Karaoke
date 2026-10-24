package com.svoemesto.karaokeapp.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock

/**
 * Регрессия BR-162 (аудит #336, PKG-S2-11): сбой уборки пустых бакетов при старте
 * не валит Spring-контекст.
 *
 * До правки `KSS_APP.deleteAllEmptyBuckets()` вызывался из `init` бина без
 * `try/catch`: `ConnectException`/`SocketTimeoutException` (это `IOException`, не
 * `MinioException`, который ловится внутри) валил создание бина — `karaoke-app`
 * не стартовал при недоступном MinIO. Тест фиксирует fail-open-контракт
 * [cleanupEmptyBucketsOnStartup]: исключение поглощается и логируется.
 */
class CleanupStartupGuardTest {
    @Test
    fun `storage failure during startup cleanup is logged but not thrown`() {
        val storage = mock(KaraokeStorageService::class.java)
        doThrow(RuntimeException("MinIO is down")).`when`(storage).deleteAllEmptyBuckets()

        val warnings = mutableListOf<String>()
        cleanupEmptyBucketsOnStartup(storage) { message, error ->
            warnings += "$message: ${error.message}"
        }

        assertEquals(1, warnings.size)
        assertTrue(warnings.single().contains("MinIO is down"))
        assertNotNull(warnings.single())
    }
}
