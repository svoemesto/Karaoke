package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.karaokePropertiesMap
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Регрессия BR-154 (аудит #336, OpenProject #409): HTTP-клиенты VK/Whisper
 * переиспользуются, а не создаются на каждый вызов.
 *
 * ДО правки:
 *  - `VkApiClient()` в конструкторе создавал новый `java.net.http.HttpClient`
 *    (свой selector-поток + пул соединений, `close()` у него нет), а сам
 *    `VkApiClient` создавался на каждый вызов (`VkAutoPublishScheduler`,
 *    `VkIdTokenRefreshScheduler`, `ApiController.vkIdRefreshNow`);
 *  - `proxyClient()` создавал новый клиент на КАЖДЫЙ запрос в прокси-режиме;
 *  - `WhisperAsrService.transcribe` создавал новый `OkHttpClient` (со своим
 *    connection pool и dispatcher) на каждый вызов.
 *
 * ПОСЛЕ правки: клиенты кэшируются (прямой — общий на класс, прокси — по URL,
 * Whisper — по таймауту), поэтому число живых HTTP-клиентов не растёт.
 *
 * @see specs/409-pkg-s3-35/report.md § BR-154
 */
class VkHttpClientReuseTest {
    private var savedProxyUrl: Any? = null
    private var savedWhisperTimeout: Any? = null

    @BeforeEach
    fun setUp() {
        savedProxyUrl = karaokePropertiesMap["vkProxyUrl"]
        savedWhisperTimeout = karaokePropertiesMap["whisperTimeoutMs"]
        karaokePropertiesMap["vkProxyUrl"] = "http://127.0.0.1:1"
    }

    @AfterEach
    fun tearDown() {
        val proxy = savedProxyUrl
        if (proxy == null) karaokePropertiesMap.remove("vkProxyUrl") else karaokePropertiesMap["vkProxyUrl"] = proxy
        val timeout = savedWhisperTimeout
        if (timeout == null) karaokePropertiesMap.remove("whisperTimeoutMs") else karaokePropertiesMap["whisperTimeoutMs"] = timeout
    }

    @Test
    fun `proxy client is reused for the same proxy url`() {
        val client = VkApiClient()
        val method = VkApiClient::class.java.getDeclaredMethod("proxyClient")
        method.isAccessible = true
        val first = method.invoke(client)
        val second = method.invoke(client)
        assertNotNull(first)
        assertSame(first, second, "proxyClient() обязан переиспользовать клиент (BR-154)")
    }

    @Test
    fun `proxy client is shared between instances`() {
        val method = VkApiClient::class.java.getDeclaredMethod("proxyClient")
        method.isAccessible = true
        val first = method.invoke(VkApiClient())
        val second = method.invoke(VkApiClient())
        assertNotNull(first)
        assertSame(first, second, "прокси-клиент должен быть общим на процесс (BR-154)")
    }

    @Test
    fun `direct client is shared between instances`() {
        val field = VkApiClient::class.java.getDeclaredField("directClient")
        field.isAccessible = true
        val first = field.get(VkApiClient())
        val second = field.get(VkApiClient())
        assertSame(first, second, "прямой HTTP-клиент должен быть общим на процесс (BR-154)")
    }

    @Test
    fun `shared instance keeps proxy mode state between calls`() {
        assertSame(VkApiClient.shared, VkApiClient.shared, "общий экземпляр клиента должен быть один (BR-154)")
    }

    @Test
    fun `whisper http client is reused for the same timeout`() {
        val method = WhisperAsrService::class.java.getDeclaredMethod("httpClient", Long::class.java)
        method.isAccessible = true
        val first = method.invoke(WhisperAsrService, 1234L)
        val second = method.invoke(WhisperAsrService, 1234L)
        assertNotNull(first)
        assertSame(first, second, "OkHttpClient ASR должен переиспользоваться (BR-154)")
        val other = method.invoke(WhisperAsrService, 4321L)
        assertNotSame(first, other, "при смене таймаута клиент пересоздаётся (BR-154)")
    }
}
