package com.svoemesto.karaokeweb.services

import com.svoemesto.karaokeweb.config.WebShareProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import java.lang.reflect.Field
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Регрессионный тест «до/после» для аудита BR-516 «claim-rate-buckets-leak:share-link»
 * (OpenProject #378).
 *
 * **ДО правки** карта rate-limit'а claim-запросов была `ConcurrentHashMap<String, AtomicInteger>`
 * с ключом `"claim:<ip>:<минута>"` и НИКОГДА не чистилась: каждая пара «IP + минута» навсегда
 * оставалась в памяти, размер карты рос пропорционально числу разных IP за всё время жизни
 * процесса (риск OOM на публичном бэкенде `karaoke-web`).
 *
 * **ПОСЛЕ правки** ключ — сам IP (окно хранится в значении), закрытые окна удаляются ленивой
 * уборкой по времени (раз в минуту), а число отслеживаемых IP ограничено: при превышении новые
 * IP получают отказ по rate-limit (fail-closed), карта не растёт бесконечно.
 */
class SongShareLinkClaimBucketsTest {
    private val props =
        WebShareProperties().also {
            it.claimRateLimitPerIpPerMin = 10
        }
    private val service = SongShareLinkService(props)

    @BeforeEach
    fun reset() {
        service.clearClaimBuckets()
    }

    private fun request(ip: String): MockHttpServletRequest =
        MockHttpServletRequest().apply {
            addHeader("X-Real-IP", ip)
            remoteAddr = ip
        }

    /** Вызывает приватный `checkRateLimit` — единственный вход в карту rate-limit'а. */
    private fun checkRateLimit(ip: String) {
        val method = SongShareLinkService::class.java.getDeclaredMethod("checkRateLimit", jakarta.servlet.http.HttpServletRequest::class.java)
        method.isAccessible = true
        method.invoke(service, request(ip))
    }

    @Suppress("UNCHECKED_CAST")
    private fun bucketsField(): Field =
        SongShareLinkService::class.java.getDeclaredField("claimRateBuckets").apply { isAccessible = true }

    @Suppress("UNCHECKED_CAST")
    private fun buckets(): MutableMap<String, Any> = bucketsField().get(service) as MutableMap<String, Any>

    /** Сдвигает метку последней уборки (чтобы управлять её срабатыванием в тесте). */
    private fun setLastCleanup(atMs: Long) {
        val field = SongShareLinkService::class.java.getDeclaredField("lastClaimCleanupMs")
        field.isAccessible = true
        (field.get(service) as AtomicLong).set(atMs)
    }

    /** Создаёт «закрытое» окно (старше минуты) — как если бы запись осталась с прошлой минуты. */
    private fun staleBucket(): Any {
        val type = Class.forName("com.svoemesto.karaokeweb.services.SongShareLinkService\$ClaimRateBucket")
        val ctor = type.getDeclaredConstructor(Long::class.javaPrimitiveType, AtomicInteger::class.java)
        ctor.isAccessible = true
        return ctor.newInstance(System.currentTimeMillis() - 5 * 60_000L, AtomicInteger(3))
    }

    @Test
    fun `на один IP приходится одна запись, а не запись на каждую минуту`() {
        repeat(5) { checkRateLimit("203.0.113.7") }

        assertEquals(1, service.claimBucketSize(), "ключ карты — IP, окно хранится в значении")
    }

    @Test
    fun `закрытые окна удаляются ленивой уборкой`() {
        val map = buckets()
        map["198.51.100.1"] = staleBucket()
        map["198.51.100.2"] = staleBucket()
        map["198.51.100.3"] = staleBucket()
        assertEquals(3, service.claimBucketSize())

        // Первый же вызов после «пустой» уборки (lastClaimCleanupMs = 0) чистит закрытые окна,
        // затем создаёт запись для нового IP.
        checkRateLimit("203.0.113.9")

        assertEquals(1, service.claimBucketSize(), "закрытые окна удалены, остался только активный IP")
        assertNotNull(buckets()["203.0.113.9"])
    }

    @Test
    fun `превышение лимита по одному IP даёт отказ`() {
        repeat(props.claimRateLimitPerIpPerMin.toInt()) { checkRateLimit("203.0.113.20") }

        val failure =
            runCatching { checkRateLimit("203.0.113.20") }.exceptionOrNull()
                ?: error("ожидался отказ по rate-limit")
        assertTrue(failure.cause is SongShareLinkService.RateLimited, "ожидался RateLimited, получено: $failure")
    }

    @Test
    fun `при достижении предела отслеживаемых IP новые IP получают отказ, карта не растёт`() {
        val map = buckets()
        repeat(SongShareLinkService.MAX_TRACKED_CLAIM_IPS) { index ->
            map["10.0.0.$index"] = staleBucket()
        }
        // Уборку на этом вызове отключаем: проверяем именно поведение при ПЕРЕПОЛНЕННОЙ карте.
        setLastCleanup(System.currentTimeMillis())

        // Новый IP при полной карте: fail-closed (RateLimited), размер не увеличивается.
        val failure =
            runCatching { checkRateLimit("203.0.113.99") }.exceptionOrNull()
                ?: error("ожидался отказ по rate-limit при полной карте")
        assertTrue(failure.cause is SongShareLinkService.RateLimited, "ожидался RateLimited, получено: $failure")
        assertEquals(SongShareLinkService.MAX_TRACKED_CLAIM_IPS, service.claimBucketSize())
    }
}
