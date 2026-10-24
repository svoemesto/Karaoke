package com.svoemesto.karaokeweb.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest

/**
 * Регрессионные тесты «до/после» для аудита BR-003 «xff-first-element»
 * (OpenProject #384, порция аудита P-37 / P-38).
 *
 * **До правки** `ClientIpResolver.resolve` брал ПЕРВЫЙ элемент `X-Forwarded-For` —
 * ровно тот, который клиент подделывает (nginx дописывает реальный адрес в КОНЕЦ
 * цепочки через `$proxy_add_x_forwarded_for`). На старом коде падали тесты
 * `spoofedForwardedForDoesNotChangeSource` и `forwardedForFallbackUsesLastElement`:
 * адрес источника ехал вместе с подделанным префиксом.
 *
 * **После правки** правило одно на весь проект:
 * `X-Real-IP` (наш nginx перезаписывает его значением `$remote_addr`) →
 * ПОСЛЕДНИЙ элемент `X-Forwarded-For` → TCP-peer (dev без nginx).
 *
 * @see specs/336-code-audit/findings/P-37.md (P-37-03)
 * @see specs/336-code-audit/findings/P-38.md («Сверки» №1)
 */
class ClientIpResolverTest {
    /** Реальный адрес клиента, как его видит наш nginx (`$remote_addr`). */
    private val realClientIp = "203.0.113.7"

    /** Адрес, который клиент подставляет в свой `X-Forwarded-For`. */
    private val spoofedIp = "198.51.100.23"

    /** TCP-peer в контейнере `karaoke-web` — адрес docker-шлюза. */
    private val dockerGateway = "172.18.0.1"

    private fun request(
        xRealIp: String? = null,
        xForwardedFor: String? = null,
        remoteHost: String = dockerGateway,
    ): MockHttpServletRequest {
        val req = MockHttpServletRequest()
        req.setRemoteAddr(remoteHost)
        req.setRemoteHost(remoteHost)
        xRealIp?.let { req.addHeader(ClientIpResolver.HEADER_REAL_IP, it) }
        xForwardedFor?.let { req.addHeader(ClientIpResolver.HEADER_FORWARDED_FOR, it) }
        return req
    }

    @Test
    fun `spoofedForwardedForDoesNotChangeSource`() {
        // Заголовок приходит от nginx: клиентский префикс + реальный адрес в конце.
        val withSpoof =
            request(
                xRealIp = realClientIp,
                xForwardedFor = "$spoofedIp, $realClientIp",
            )
        // Тот же клиент, но подменяет префикс XFF произвольным адресом.
        val withAnotherSpoof =
            request(
                xRealIp = realClientIp,
                xForwardedFor = "10.0.0.1, $realClientIp",
            )

        assertEquals(realClientIp, ClientIpResolver.resolve(withSpoof))
        assertEquals(
            ClientIpResolver.resolve(withSpoof),
            ClientIpResolver.resolve(withAnotherSpoof),
            "подделанный префикс X-Forwarded-For не должен менять адрес источника",
        )
    }

    @Test
    fun `forwardedForFallbackUsesLastElement`() {
        // Прокси, который не выставляет X-Real-IP, но дописывает реальный адрес в конец.
        val req = request(xForwardedFor = "$spoofedIp, $realClientIp")

        assertEquals(
            realClientIp,
            ClientIpResolver.resolve(req),
            "из X-Forwarded-For берётся последний элемент, а не клиентский префикс",
        )
    }

    @Test
    fun `realIpPreferredOverForwardedFor`() {
        // X-Real-IP перезаписывается нашим nginx — он и есть источник правды.
        val req = request(xRealIp = realClientIp, xForwardedFor = spoofedIp)

        assertEquals(realClientIp, ClientIpResolver.resolve(req))
    }

    @Test
    fun `realIpListTakesClosestEntry`() {
        val req = request(xRealIp = "$realClientIp, 10.0.0.1")

        assertEquals(realClientIp, ClientIpResolver.resolve(req))
    }

    @Test
    fun `devWithoutProxyFallsBackToRemoteHost`() {
        // dev-окружение: nginx перед приложением нет, заголовков нет.
        val req = request(remoteHost = "127.0.0.1")

        assertEquals("127.0.0.1", ClientIpResolver.resolve(req))
    }

    @Test
    fun `blankHeadersAreIgnored`() {
        val req = request(xRealIp = "   ", xForwardedFor = " , ", remoteHost = dockerGateway)

        assertEquals(dockerGateway, ClientIpResolver.resolve(req))
    }

    @Test
    fun `emptyElementsInsideForwardedForAreSkipped`() {
        val req = request(xForwardedFor = "$spoofedIp,, $realClientIp")

        assertEquals(realClientIp, ClientIpResolver.resolve(req))
    }

    @Test
    fun `unknownWhenNothingAvailable`() {
        assertEquals(ClientIpResolver.UNKNOWN, ClientIpResolver.resolve(null, null, null))
        assertEquals(ClientIpResolver.UNKNOWN, ClientIpResolver.resolve("  ", "", ""))
    }

    @Test
    fun `differentRealClientsGetDifferentSources`() {
        val first = request(xRealIp = "203.0.113.7")
        val second = request(xRealIp = "203.0.113.8")

        assertNotEquals(ClientIpResolver.resolve(first), ClientIpResolver.resolve(second))
    }
}
