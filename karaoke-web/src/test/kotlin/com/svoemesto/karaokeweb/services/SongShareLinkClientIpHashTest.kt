package com.svoemesto.karaokeweb.services

import com.svoemesto.karaokeweb.config.WebShareProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest

/**
 * Регрессионные тесты «до/после» для аудита BR-003 «xff-first-element»
 * (OpenProject #384): `client_ip_hash` в `tbl_song_share_sessions` должен
 * соответствовать реальному адресу клиента и не зависеть от подделанного
 * `X-Forwarded-For` (иначе след для расследований обесценивается).
 *
 * **До правки** адрес брался из ПЕРВОГО элемента `X-Forwarded-For` — клиент мог
 * подделать значение хэша в БД. **После правки** хэш считается от единого
 * правила проекта: `X-Real-IP` → последний элемент `X-Forwarded-For` → TCP-peer.
 */
class SongShareLinkClientIpHashTest {
    private val service = SongShareLinkService(WebShareProperties())

    /** Реальный адрес клиента, как его видит наш nginx (`$remote_addr`). */
    private val realClientIp = "203.0.113.7"

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
    fun `clientIpHashIgnoresSpoofedForwardedForPrefix`() {
        val first = request(xRealIp = realClientIp, xForwardedFor = "198.51.100.23, $realClientIp")
        val second = request(xRealIp = realClientIp, xForwardedFor = "10.0.0.1, $realClientIp")

        assertEquals(
            service.clientIpHash(first),
            service.clientIpHash(second),
            "подделанный префикс X-Forwarded-For не должен менять client_ip_hash",
        )
    }

    @Test
    fun `clientIpHashDiffersForDifferentClients`() {
        val first = request(xRealIp = "203.0.113.7")
        val second = request(xRealIp = "203.0.113.8")

        assertNotEquals(service.clientIpHash(first), service.clientIpHash(second))
    }

    @Test
    fun `clientIpHashUsesRealIpNotSpoofedEntry`() {
        val spoofed = request(xRealIp = "203.0.113.8", xForwardedFor = "198.51.100.23, 203.0.113.8")
        val honest = request(xRealIp = "203.0.113.8")

        assertEquals(service.clientIpHash(honest), service.clientIpHash(spoofed))
    }
}
