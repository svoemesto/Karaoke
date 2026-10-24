package com.svoemesto.karaokeweb.services

import com.svoemesto.karaokeweb.util.ClientIpResolver
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Spring HandlerInterceptor для rate-limit на эндпоинтах /api/public/song-picture/{id} и
 * /api/public/song-vk-image/{id} (FR-010, SC-008).
 *
 * Регистрируется в WebMvcConfig.addInterceptors (T023) на URL-паттерны
 * /api/public/song-picture/ и /api/public/song-vk-image/ (точные пути см. в
 * WebMvcConfig.kt — Ant-паттерны с double-star тут не пишем, чтобы ktlint не путал их с
 * концом KDoc-комментария, см. AGENTS.md «Q: KDoc с backticks ломает парсер»).
 * Лимит per-IP управляется через KaraokeProperties.rateLimitSongPicturePerMinute
 * и KaraokeProperties.rateLimitSongVkImagePerMinute (default 60 req/мин на IP).
 *
 * **Защита от bot-storm** (Pass 60, SEO HTML for bots уже сделал nginx-redirect по User-Agent):
 * даже если бот пройдёт User-Agent-фильтр и пойдёт напрямую на /song-picture/{id}, он
 * упрётся в 429 после 60 запросов в минуту с одного IP. Это второй уровень защиты.
 *
 * **Алгоритм — fixed window** (минута):
 *  - Key = ip + ":" + endpoint (например, "1.2.3.4:song-picture").
 *  - IP берётся через [ClientIpResolver] (аудит BR-003): `X-Real-IP`, который наш nginx
 *    перезаписывает значением `$remote_addr` → последний элемент `X-Forwarded-For` →
 *    TCP-peer. Клиентский префикс `X-Forwarded-For` не влияет на ключ, поэтому подделка
 *    заголовка не даёт новый bucket и не обходит лимит.
 *  - Хранится (windowStartMs, count).
 *  - Если now - windowStartMs >= 60_000 — окно сбрасывается.
 *  - Иначе инкремент count. Если count > limit — 429 + Retry-After: 60.
 *
 * **Lazy cleanup**: каждые [cleanupEvery] запросов проходим по карте и удаляем записи
 * с истёкшим окном. Полная очистка от старых записей выполняется автоматически.
 *
 * **Не thread-safe ли?** ConcurrentHashMap.compute сериализует доступ к ключу атомарно —
 * параллельные запросы с одного IP не потеряют инкремент.
 *
 * @see archive/docs/features/site-traffic-resilience.md
 * @see docs/features/client-ip-resolution.md
 * @see KaraokeProperties
 */
@Component
class RateLimitInterceptor(
    private val properties: KaraokeProperties,
) : HandlerInterceptor {
    private val log = LoggerFactory.getLogger(RateLimitInterceptor::class.java)

    private data class Bucket(
        var windowStartMs: Long,
        var count: AtomicLong
    )

    /** Хранилище: `ip|endpoint` → Bucket. */
    private val buckets = ConcurrentHashMap<String, Bucket>()
    private val callsSinceCleanup = AtomicLong(0L)
    private val cleanupEvery = 1000L

    /** Имя endpoint'а для ключа (например, "song-picture"). Устанавливается в WebMvcConfig. */
    @Volatile
    var endpointName: String = "default"

    /** Лимит для текущего endpoint'а (req/мин). Устанавливается в WebMvcConfig. */
    @Volatile
    var limitPerMinute: Int = 60

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val now = System.currentTimeMillis()
        val windowMs = 60_000L
        val ip = clientIp(request)
        val key = "$ip|$endpointName"

        val bucket =
            buckets.compute(key) { _, existing ->
                if (existing == null || now - existing.windowStartMs >= windowMs) {
                    Bucket(windowStartMs = now, count = AtomicLong(1L))
                } else {
                    existing.count.incrementAndGet()
                    existing
                }
            }!!

        if (bucket.count.get() > limitPerMinute) {
            response.sendError(429, "rate_limit_exceeded")
            response.setHeader("Retry-After", "60")
            log.warn("Rate-limit превышен для $endpointName: ip=$ip count=${bucket.count.get()} limit=$limitPerMinute")
            return false
        }

        maybeCleanup(now, windowMs)
        return true
    }

    /**
     * Адрес источника запроса — единое правило проекта (аудит BR-003 «xff-first-element»).
     *
     * Раньше здесь была своя копия правила «первый элемент `X-Forwarded-For`»: клиент
     * подделывал префикс XFF, получал новый bucket на каждый запрос и обходил лимит.
     * Теперь адрес берётся через [ClientIpResolver]: `X-Real-IP` (наш nginx перезаписывает
     * его значением `$remote_addr`) → последний элемент `X-Forwarded-For` → TCP-peer.
     */
    private fun clientIp(request: HttpServletRequest): String = ClientIpResolver.resolve(request)

    private fun maybeCleanup(now: Long, windowMs: Long) {
        if (callsSinceCleanup.incrementAndGet() % cleanupEvery != 0L) return
        val cutoff = now - windowMs
        buckets.entries.removeIf { it.value.windowStartMs < cutoff }
    }

    /** Размер карты (для отладки/метрик). */
    fun bucketSize(): Int = buckets.size

    /** Полная очистка (для тестов). */
    fun clear() {
        buckets.clear()
        callsSinceCleanup.set(0L)
    }
}
