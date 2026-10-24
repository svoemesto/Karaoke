package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.karaokePropertiesMap
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Регрессия BR-142 (аудит #336, PKG-S2-11): прокси-HttpClient переиспользуется.
 *
 * До правки `proxyClient()` создавал новый `java.net.http.HttpClient` на каждый
 * запрос (в proxy-режиме — каждые ~25-60 с для getUpdates и на каждый sendVideo),
 * и каждый оставлял живой selector-поток и сокеты до конца жизни процесса.
 */
class TelegramProxyClientCacheTest {
    private var saved: Any? = null

    @BeforeEach
    fun setUp() {
        saved = karaokePropertiesMap["telegramProxyUrl"]
        karaokePropertiesMap["telegramProxyUrl"] = "http://127.0.0.1:1"
    }

    @AfterEach
    fun tearDown() {
        val value = saved
        if (value == null) karaokePropertiesMap.remove("telegramProxyUrl") else karaokePropertiesMap["telegramProxyUrl"] = value
    }

    @Test
    fun `same proxy url reuses one client instance`() {
        val client = TelegramApiClient()
        val method = TelegramApiClient::class.java.getDeclaredMethod("proxyClient")
        method.isAccessible = true
        val first = method.invoke(client)
        val second = method.invoke(client)
        assertNotNull(first)
        assertSame(first, second, "proxyClient() обязан переиспользовать клиент (BR-142)")
    }
}
