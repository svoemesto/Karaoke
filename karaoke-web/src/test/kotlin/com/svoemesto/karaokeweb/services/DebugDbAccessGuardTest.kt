package com.svoemesto.karaokeweb.services

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.test.util.ReflectionTestUtils

/**
 * Регрессионные тесты «до/после» для аудита BR-003 «xff-first-element»
 * (OpenProject #384): IP-allowlist debug-endpoint'а `/api/public/debug/db`
 * не должен открываться подделкой `X-Forwarded-For`.
 *
 * **До правки** адрес брался из ПЕРВОГО элемента `X-Forwarded-For` — клиент
 * подставлял разрешённый адрес в свой префикс и получал доступ к debug-endpoint'у
 * (при включённом `KARAOKE_WEB_DEBUG_DB_ENABLED`).
 *
 * **После правки** адрес источника определяется по `X-Real-IP` (наш nginx перезаписывает
 * его значением `$remote_addr`), а из `X-Forwarded-For` берётся последний элемент.
 */
class DebugDbAccessGuardTest {
    /** Адрес, который разрешён в allowlist (адрес админской машины). */
    private val allowedIp = "203.0.113.7"

    /** Адрес постороннего клиента. */
    private val foreignIp = "198.51.100.23"

    private fun properties(enabled: Boolean, allowedIps: String): KaraokeProperties {
        val props = KaraokeProperties()
        ReflectionTestUtils.setField(props, "debugDbEnabledInternal", enabled)
        ReflectionTestUtils.setField(props, "debugDbAllowedIpsInternal", allowedIps)
        return props
    }

    private fun request(
        xRealIp: String? = null,
        xForwardedFor: String? = null,
        remoteHost: String = "172.18.0.1",
    ): MockHttpServletRequest {
        val req = MockHttpServletRequest()
        req.setRemoteAddr(remoteHost)
        req.setRemoteHost(remoteHost)
        xRealIp?.let { req.addHeader("X-Real-IP", it) }
        xForwardedFor?.let { req.addHeader("X-Forwarded-For", it) }
        return req
    }

    @Test
    fun `allowedClientIsAllowedDespiteSpoofedForwardedFor`() {
        val props = properties(enabled = true, allowedIps = allowedIp)
        val req = request(xRealIp = allowedIp, xForwardedFor = "$foreignIp, $allowedIp")

        assertTrue(
            DebugDbAccessGuard.isAllowed(props, req),
            "адрес из X-Real-IP в allowlist — доступ разрешён",
        )
    }

    @Test
    fun `spoofedForwardedForCannotGrantAccessToDebugEndpoint`() {
        val props = properties(enabled = true, allowedIps = allowedIp)
        // Клиент подставляет разрешённый адрес в префикс XFF, но реальный адрес — чужой.
        val req = request(xRealIp = foreignIp, xForwardedFor = "$allowedIp, $foreignIp")

        assertFalse(
            DebugDbAccessGuard.isAllowed(props, req),
            "подделка X-Forwarded-For не должна давать доступ к debug-endpoint'у",
        )
    }

    @Test
    fun `disabledFlagBlocksEvenAllowedIp`() {
        val props = properties(enabled = false, allowedIps = allowedIp)
        val req = request(xRealIp = allowedIp)

        assertFalse(DebugDbAccessGuard.isAllowed(props, req))
    }

    @Test
    fun `emptyAllowlistBlocksEveryone`() {
        val props = properties(enabled = true, allowedIps = "")
        val req = request(xRealIp = allowedIp)

        assertFalse(DebugDbAccessGuard.isAllowed(props, req))
    }
}
