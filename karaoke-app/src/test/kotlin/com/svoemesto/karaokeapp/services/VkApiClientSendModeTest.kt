package com.svoemesto.karaokeapp.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Офлайн-тесты решения «direct vs proxy» в [VkApiClient] (specs/437, #161) —
 * без сети/VK/БД.
 *
 * Баг (лог 2026-09-23): при пустом `vkProxyUrl` и транзиентном сбое прямого
 * запроса `VkApiClient.send()` бросал `IllegalStateException`, который пролетал
 * через `@Scheduled`-тик. Теперь `decideSendMode` при `hasProxy=false` всегда
 * возвращает `DIRECT`, а повторный сбой даёт типизированную [VkNetworkException].
 */
class VkApiClientSendModeTest {
    @Test
    fun `no proxy always returns DIRECT even when useProxy is set`() {
        assertEquals(VkSendMode.DIRECT, VkApiClient.decideSendMode(hasProxy = false, useProxy = true, modeSetAtMs = 0L, now = 1_000L, ttl = 60_000L))
        assertEquals(VkSendMode.DIRECT, VkApiClient.decideSendMode(hasProxy = false, useProxy = false, modeSetAtMs = 0L, now = 1_000L, ttl = 60_000L))
    }

    @Test
    fun `no proxy never returns PROXY regardless of ttl`() {
        for (now in listOf(0L, 30_000L, 10_000_000L)) {
            assertEquals(
                VkSendMode.DIRECT,
                VkApiClient.decideSendMode(hasProxy = false, useProxy = true, modeSetAtMs = 0L, now = now, ttl = 60_000L),
                "now=$now must still be DIRECT without proxy",
            )
        }
    }

    @Test
    fun `proxy configured and not in proxy mode returns DIRECT`() {
        assertEquals(
            VkSendMode.DIRECT,
            VkApiClient.decideSendMode(hasProxy = true, useProxy = false, modeSetAtMs = 0L, now = 5_000L, ttl = 60_000L),
        )
    }

    @Test
    fun `proxy configured and in proxy mode within ttl returns PROXY`() {
        assertEquals(
            VkSendMode.PROXY,
            VkApiClient.decideSendMode(hasProxy = true, useProxy = true, modeSetAtMs = 10_000L, now = 20_000L, ttl = 60_000L),
        )
    }

    @Test
    fun `proxy configured and proxy mode expired returns DIRECT`() {
        assertEquals(
            VkSendMode.DIRECT,
            VkApiClient.decideSendMode(hasProxy = true, useProxy = true, modeSetAtMs = 10_000L, now = 80_000L, ttl = 60_000L),
        )
    }

    @Test
    fun `proxy configured and ttl boundary still returns PROXY`() {
        // now - modeSetAtMs == ttl → «строго больше» не выполняется → остаёмся на PROXY.
        assertEquals(
            VkSendMode.PROXY,
            VkApiClient.decideSendMode(hasProxy = true, useProxy = true, modeSetAtMs = 0L, now = 60_000L, ttl = 60_000L),
        )
    }

    @Test
    fun `VkNetworkException carries the original cause`() {
        val cause = java.net.ConnectException("Connection reset")
        val ex = VkNetworkException("VK недоступен", cause)
        assertEquals(cause, ex.cause)
        assertTrue(ex.message!!.contains("VK недоступен"))
    }

    @Test
    fun `VkNetworkException is a RuntimeException`() {
        assertTrue(RuntimeException::class.java.isAssignableFrom(VkNetworkException::class.java))
        assertThrows(VkNetworkException::class.java) { throw VkNetworkException("x") }
    }
}
