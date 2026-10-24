package com.svoemesto.karaokeweb.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

/**
 * Регрессионные тесты «до/после» для аудита BR-003 «xff-first-element»
 * (OpenProject #384): rate-limit не должен обходиться подделкой `X-Forwarded-For`.
 *
 * **До правки** `RateLimitInterceptor.clientIp` брал ПЕРВЫЙ элемент `X-Forwarded-For` —
 * подделанный клиентом. Каждый новый префикс давал новый bucket, лимит 60/мин
 * не срабатывал никогда (публичный `GET /api/public/song-picture/{id}` и
 * `/api/public/song-vk-image/{id}`).
 *
 * **После правки** адрес источника — единое правило проекта ([com.svoemesto.karaokeweb.util.ClientIpResolver]):
 * `X-Real-IP` (наш nginx перезаписывает его значением `$remote_addr`) → последний
 * элемент `X-Forwarded-For` → TCP-peer.
 */
class RateLimitInterceptorTest {
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

    private fun interceptor(limitPerMinute: Int): RateLimitInterceptor =
        RateLimitInterceptor(KaraokeProperties()).apply {
            endpointName = "song-picture"
            this.limitPerMinute = limitPerMinute
        }

    @Test
    fun `spoofedForwardedForDoesNotBypassRateLimit`() {
        val interceptor = interceptor(limitPerMinute = 2)
        val response = MockHttpServletResponse()

        // Два запроса в лимит — каждый с разным подделанным префиксом XFF.
        repeat(2) { i ->
            val allowed =
                interceptor.preHandle(
                    request(xRealIp = realClientIp, xForwardedFor = "10.0.0.$i, $realClientIp"),
                    response,
                    Any(),
                )
            assertTrue(allowed, "запрос №${i + 1} в пределах лимита должен проходить")
        }

        // Третий запрос — тот же клиент, тоже с подделанным префиксом: лимит сработал.
        val blocked =
            interceptor.preHandle(
                request(xRealIp = realClientIp, xForwardedFor = "10.0.0.99, $realClientIp"),
                response,
                Any(),
            )

        assertFalse(blocked, "подделка X-Forwarded-For не должна обходить лимит")
        assertEquals(429, response.status)
    }

    @Test
    fun `spoofedForwardedForDoesNotCreateNewBuckets`() {
        val interceptor = interceptor(limitPerMinute = 100)
        val response = MockHttpServletResponse()

        repeat(5) { i ->
            interceptor.preHandle(
                request(xRealIp = realClientIp, xForwardedFor = "10.0.0.$i, $realClientIp"),
                response,
                Any(),
            )
        }

        assertEquals(
            1,
            interceptor.bucketSize(),
            "подделанный префикс X-Forwarded-For не должен плодить bucket'ы (ключ ip|endpoint)",
        )
    }

    @Test
    fun `differentRealClientsGetSeparateBuckets`() {
        val interceptor = interceptor(limitPerMinute = 100)
        val response = MockHttpServletResponse()

        interceptor.preHandle(request(xRealIp = "203.0.113.7"), response, Any())
        interceptor.preHandle(request(xRealIp = "203.0.113.8"), response, Any())

        assertEquals(2, interceptor.bucketSize())
    }
}
