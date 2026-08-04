package com.svoemesto.karaokeapp.services

import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import javax.imageio.ImageIO

/**
 * Офлайн-тесты [VkPreviewWarmupClient] (specs/130-vk-preview-generation) —
 * без сети/VK/БД. Локальный `com.sun.net.httpserver.HttpServer` поднимается на
 * случайном порту для каждого теста и эмулирует все ответы из
 * `contracts/vk-preview-warmup.md` (200/3xx/4xx/5xx, тайм-аут, пустое/повреждённое
 * тело, повторный успех). Живой VK проверяется только вручную — quickstart
 * сценарии 1-5.
 */
class VkPreviewWarmupClientTest {
    private lateinit var server: HttpServer
    private lateinit var baseUrl: String
    private val responses = mutableListOf<Pair<Int, ByteArray>>()
    private val responseContentType = mutableListOf<String>()
    private var delayMs: Long = 0L

    private val songId: Long = 42L

    @BeforeEach
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext(
            "/song-vk-image/",
            HttpHandler { exchange ->
                if (delayMs > 0) {
                    try {
                        Thread.sleep(delayMs)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                }
                val idx = Math.min(responses.size - 1, currentIndex.getAndIncrement())
                val (status, body) = responses[idx]
                val ct = responseContentType.getOrNull(idx) ?: "image/png"
                exchange.responseHeaders.add("Content-Type", ct)
                exchange.sendResponseHeaders(status, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            },
        )
        server.start()
        baseUrl = "http://127.0.0.1:${server.address.port}/song-vk-image"
    }

    @AfterEach
    fun tearDown() {
        server.stop(0)
        currentIndex.set(0)
        responses.clear()
        responseContentType.clear()
        delayMs = 0L
    }

    private val currentIndex =
        java.util.concurrent.atomic
            .AtomicInteger(0)

    private fun enqueue(
        status: Int,
        body: ByteArray = ByteArray(0),
        contentType: String = "image/png",
    ) {
        responses.add(status to body)
        responseContentType.add(contentType)
    }

    private fun validPng(): ByteArray {
        val image = BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB)
        val g: Graphics2D = image.createGraphics()
        g.color = Color.RED
        g.fillRect(0, 0, 8, 8)
        g.dispose()
        val out = ByteArrayOutputStream()
        ImageIO.write(image, "png", out)
        return out.toByteArray()
    }

    @Test
    fun `200 plus valid PNG returns SUCCESS`() {
        enqueue(200, validPng(), "image/png")
        val client = VkPreviewWarmupClient(baseUrl = baseUrl, timeoutMs = 5_000L, maxAttempts = 2, enabled = true)
        val result = client.warmup(songId)
        assertEquals(VkPreviewWarmupStatus.SUCCESS, result.status)
        assertEquals(songId, result.songId)
        assertEquals(200, result.httpStatus)
        assertEquals("image/png", result.contentType)
        assertTrue(result.bytes > 0)
        assertEquals(1, result.attempts)
        assertTrue(result.durationMs >= 0)
        assertNull(result.error)
    }

    @Test
    fun `enabled=false returns BYPASS without network call`() {
        val client = VkPreviewWarmupClient(baseUrl = baseUrl, timeoutMs = 5_000L, maxAttempts = 2, enabled = false)
        val result = client.warmup(songId)
        assertEquals(VkPreviewWarmupStatus.BYPASS, result.status)
        assertEquals(0, result.attempts)
        assertNotNull(result.error)
        assertTrue(result.error!!.contains("bypass"))
    }

    @Test
    fun `empty baseUrl returns FAILED`() {
        val client = VkPreviewWarmupClient(baseUrl = "", timeoutMs = 5_000L, maxAttempts = 2, enabled = true)
        val result = client.warmup(songId)
        assertEquals(VkPreviewWarmupStatus.FAILED, result.status)
        assertTrue(result.error!!.contains("empty"))
    }

    @Test
    fun `302 redirect without follow returns FAILED`() {
        enqueue(302, "redirect-body".toByteArray(), "text/html")
        val client = VkPreviewWarmupClient(baseUrl = baseUrl, timeoutMs = 5_000L, maxAttempts = 3, enabled = true)
        val result = client.warmup(songId)
        assertEquals(VkPreviewWarmupStatus.FAILED, result.status)
        assertEquals(302, result.httpStatus)
        assertEquals(1, result.attempts, "3xx should NOT be retried")
        assertTrue(result.error!!.contains("redirect"))
    }

    @Test
    fun `404 returns FAILED without retry`() {
        enqueue(404, ByteArray(0), "text/plain")
        val client = VkPreviewWarmupClient(baseUrl = baseUrl, timeoutMs = 5_000L, maxAttempts = 3, enabled = true)
        val result = client.warmup(songId)
        assertEquals(VkPreviewWarmupStatus.FAILED, result.status)
        assertEquals(404, result.httpStatus)
        assertEquals(1, result.attempts, "4xx should NOT be retried")
        assertTrue(result.error!!.contains("404"))
    }

    @Test
    fun `500 is retried up to maxAttempts and returns FAILED`() {
        repeat(3) { enqueue(500, ByteArray(0), "text/plain") }
        val client = VkPreviewWarmupClient(baseUrl = baseUrl, timeoutMs = 5_000L, maxAttempts = 3, enabled = true)
        val result = client.warmup(songId)
        assertEquals(VkPreviewWarmupStatus.FAILED, result.status)
        assertEquals(500, result.httpStatus)
        assertEquals(3, result.attempts)
    }

    @Test
    fun `500 then 200 returns SUCCESS on retry`() {
        enqueue(500, ByteArray(0), "text/plain")
        enqueue(200, validPng(), "image/png")
        val client = VkPreviewWarmupClient(baseUrl = baseUrl, timeoutMs = 5_000L, maxAttempts = 3, enabled = true)
        val result = client.warmup(songId)
        assertEquals(VkPreviewWarmupStatus.SUCCESS, result.status)
        assertEquals(2, result.attempts)
        assertEquals(200, result.httpStatus)
    }

    @Test
    fun `timeout returns FAILED after retries`() {
        delayMs = 1_500L
        enqueue(200, validPng(), "image/png")
        val client = VkPreviewWarmupClient(baseUrl = baseUrl, timeoutMs = 300L, maxAttempts = 2, enabled = true)
        val result = client.warmup(songId)
        assertEquals(VkPreviewWarmupStatus.FAILED, result.status)
        assertEquals(2, result.attempts)
        assertTrue(result.error!!.contains("timeout"))
    }

    @Test
    fun `200 with empty body returns FAILED`() {
        enqueue(200, ByteArray(0), "image/png")
        val client = VkPreviewWarmupClient(baseUrl = baseUrl, timeoutMs = 5_000L, maxAttempts = 1, enabled = true)
        val result = client.warmup(songId)
        assertEquals(VkPreviewWarmupStatus.FAILED, result.status)
        assertEquals(0, result.bytes)
        assertTrue(result.error!!.contains("empty"))
    }

    @Test
    fun `200 with corrupt PNG body returns FAILED`() {
        val garbage = byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07)
        enqueue(200, garbage, "image/png")
        val client = VkPreviewWarmupClient(baseUrl = baseUrl, timeoutMs = 5_000L, maxAttempts = 1, enabled = true)
        val result = client.warmup(songId)
        assertEquals(VkPreviewWarmupStatus.FAILED, result.status)
        assertTrue(result.error!!.contains("not a valid PNG"))
    }

    @Test
    fun `non-PNG content-type but decodable image returns SUCCESS`() {
        enqueue(200, validPng(), "application/octet-stream")
        val client = VkPreviewWarmupClient(baseUrl = baseUrl, timeoutMs = 5_000L, maxAttempts = 1, enabled = true)
        val result = client.warmup(songId)
        assertEquals(VkPreviewWarmupStatus.SUCCESS, result.status, "декодирование должно выиграть над content-type")
    }

    @Test
    fun `connect to closed port returns FAILED with connect error`() {
        server.stop(0)
        val client = VkPreviewWarmupClient(baseUrl = baseUrl, timeoutMs = 2_000L, maxAttempts = 1, enabled = true)
        val result = client.warmup(songId)
        assertEquals(VkPreviewWarmupStatus.FAILED, result.status)
        assertEquals(1, result.attempts)
    }
}
