package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.karaokePropertiesMap
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Регрессия BR-140 (аудит #336, PKG-S2-11): сетевой сбой — retryable.
 *
 * До правки `trySendVideo` вызывался без `try/catch`: `ConnectException`/
 * `HttpTimeoutException`/не-JSON ответ вылетали из `sendVideo` с первой попытки,
 * состояние оставалось `PUBLISHING`, а объявленный retry FR-010 работал только
 * для JSON-ответов Telegram с `error_code`. Сервер Telegram заменён на заведомо
 * недоступный `127.0.0.1:1`, backoff — 1 мс, чтобы тест был быстрым.
 */
class TelegramApiClientRetryTest {
    private val keys = listOf("telegramBotApiBaseUrl", "telegramProxyUrl", "telegramProxyModeTtlMs")
    private val saved = mutableMapOf<String, Any?>()

    @BeforeEach
    fun setUp() {
        keys.forEach { saved[it] = karaokePropertiesMap[it] }
        karaokePropertiesMap["telegramBotApiBaseUrl"] = "http://127.0.0.1:1"
        karaokePropertiesMap["telegramProxyUrl"] = ""
        karaokePropertiesMap["telegramProxyModeTtlMs"] = 60_000L
    }

    @AfterEach
    fun tearDown() {
        keys.forEach { key ->
            val value = saved[key]
            if (value == null) karaokePropertiesMap.remove(key) else karaokePropertiesMap[key] = value
        }
    }

    @Test
    fun `connection failure is retried and reported as SEND_FAILED`() {
        val video = File.createTempFile("tg-retry", ".mp4")
        video.writeBytes(ByteArray(16))
        try {
            val result =
                TelegramApiClient().sendVideo(
                    channelId = "test-channel",
                    videoFile = video,
                    caption = "capt",
                    maxFileSizeBytes = 1024 * 1024,
                    maxAttempts = 2,
                    backoffScheduleMs = listOf(1L),
                )
            assertEquals(TelegramAutoPublishState.SEND_FAILED, result.state)
            assertNotNull(result.error)
            assertTrue(result.error!!.contains("retries exhausted"), "error=${result.error}")
        } finally {
            video.delete()
        }
    }
}
