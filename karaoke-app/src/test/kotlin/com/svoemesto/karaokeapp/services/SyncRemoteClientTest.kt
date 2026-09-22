package com.svoemesto.karaokeapp.services

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.ConnectException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpHeaders
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Optional
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLSession

/**
 * Pass 431 (#155): тесты устойчивости [SyncRemoteClient] — retry на транзиентные
 * ошибки, отсутствие исключений наружу, отсутствие retry на нетранзиентные.
 */
class SyncRemoteClientTest {
    private fun response(status: Int): HttpResponse<String> =
        object : HttpResponse<String> {
            override fun statusCode(): Int = status

            override fun request(): HttpRequest =
                HttpRequest
                    .newBuilder()
                    .uri(URI.create("http://x"))
                    .build()

            override fun previousResponse(): Optional<HttpResponse<String>> = Optional.empty()

            override fun headers(): HttpHeaders = HttpHeaders.of(emptyMap()) { _, _ -> true }

            override fun body(): String = ""

            override fun sslSession(): Optional<SSLSession> = Optional.empty()

            override fun uri(): URI = URI.create("http://x")

            override fun version(): HttpClient.Version = HttpClient.Version.HTTP_1_1
        }

    @Test
    fun `retries once on transient SSL error then succeeds`() {
        val attempts = AtomicInteger(0)
        val ok =
            SyncRemoteClient.postChangeRecords("{}", retryDelayMs = 0L) {
                if (attempts.incrementAndGet() == 1) throw SSLHandshakeException("Remote host terminated the handshake")
                response(200)
            }
        assertTrue(ok, "second attempt should succeed")
        assertTrue(attempts.get() >= 2, "must retry once")
    }

    @Test
    fun `returns false when both attempts fail, without throwing`() {
        val attempts = AtomicInteger(0)
        val ok =
            SyncRemoteClient.postChangeRecords("{}", retryDelayMs = 0L) {
                attempts.incrementAndGet()
                throw SSLHandshakeException("Remote host terminated the handshake")
            }
        assertFalse(ok)
        assertTrue(attempts.get() >= 2, "should attempt twice")
    }

    @Test
    fun `does not retry on non-transient error`() {
        val attempts = AtomicInteger(0)
        val ok =
            SyncRemoteClient.postChangeRecords("{}", retryDelayMs = 0L) {
                attempts.incrementAndGet()
                throw IllegalArgumentException("bad payload")
            }
        assertFalse(ok)
        assertTrue(attempts.get() == 1, "non-transient must not retry, was ${attempts.get()}")
    }

    @Test
    fun `connect exception is transient and retried`() {
        val attempts = AtomicInteger(0)
        val ok =
            SyncRemoteClient.postChangeRecords("{}", retryDelayMs = 0L) {
                if (attempts.incrementAndGet() == 1) throw ConnectException("Connection refused")
                response(200)
            }
        assertTrue(ok)
        assertTrue(attempts.get() >= 2)
    }

    @Test
    fun `non-2xx response returns false`() {
        assertFalse(SyncRemoteClient.postChangeRecords("{}", retryDelayMs = 0L) { response(500) })
    }

    @Test
    fun `success on first attempt`() {
        val attempts = AtomicInteger(0)
        assertTrue(
            SyncRemoteClient.postChangeRecords("{}", retryDelayMs = 0L) {
                attempts.incrementAndGet()
                response(200)
            },
        )
        assertTrue(attempts.get() == 1)
    }

    @Test
    fun `isTransient classifies errors`() {
        assertTrue(SyncRemoteClient.isTransient(SSLHandshakeException("x")))
        assertTrue(SyncRemoteClient.isTransient(ConnectException("x")))
        assertTrue(SyncRemoteClient.isTransient(java.net.http.HttpTimeoutException("x")))
        assertTrue(SyncRemoteClient.isTransient(java.io.IOException("Connection timed out")))
        assertFalse(SyncRemoteClient.isTransient(IllegalArgumentException("x")))
    }
}
