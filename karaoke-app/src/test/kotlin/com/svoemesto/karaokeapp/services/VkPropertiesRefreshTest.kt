package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.karaokePropertiesMap
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Регрессия BR-148 (аудит #336, PKG-S2-11): изменяемые настройки читаются на
 * каждом вызове, а не кэшируются в конструкторе object-синглтона.
 *
 * До правки `VkPreviewWarmupClient` фиксировал `vkPreviewWarmupEnabled` при
 * создании, поэтому документированный аварийный откат (выключить флаг в админке)
 * не действовал до перезапуска: warmup продолжал вызываться и блокировал
 * публикацию `SEND_FAILED`. Тест: клиент создан при `true`, затем флаг выключен —
 * `warmup` обязан вернуть BYPASS, не идя в сеть.
 */
class VkPropertiesRefreshTest {
    private var saved: Any? = null

    @BeforeEach
    fun setUp() {
        saved = karaokePropertiesMap["vkPreviewWarmupEnabled"]
        karaokePropertiesMap["vkPreviewWarmupEnabled"] = true
    }

    @AfterEach
    fun tearDown() {
        val value = saved
        if (value == null) karaokePropertiesMap.remove("vkPreviewWarmupEnabled") else karaokePropertiesMap["vkPreviewWarmupEnabled"] = value
    }

    @Test
    fun `turning the flag off after construction takes effect immediately`() {
        val client =
            VkPreviewWarmupClient(
                baseUrl = "http://127.0.0.1:1",
                timeoutMs = 200,
                maxAttempts = 1,
            )
        karaokePropertiesMap["vkPreviewWarmupEnabled"] = false

        val result = client.warmup(1L)
        assertEquals(
            VkPreviewWarmupStatus.BYPASS,
            result.status,
            "выключенный флаг должен обходить warmup без перезапуска (BR-148); error=${result.error}",
        )
    }
}
