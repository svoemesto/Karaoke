package com.svoemesto.karaokeweb.services

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Потокобезопасный in-memory кеш для polling-эндпоинтов (FR-008, D-7).
 *
 * Хранит ключ → (значение, expiresAtMs). При вызове [getOrCompute] проверяет, не истёк ли кеш
 * для ключа. Если жив — возвращает кешированное значение БЕЗ вызова loader. Если истёк или
 * отсутствует — вызывает loader, сохраняет результат и возвращает его.
 *
 * **Per-endpoint TTL** (clarified 2026-08-14, FR-008):
 *  - `/api/public/news/since` — TTL=60s (новости меняются нечасто).
 *  - `/api/public/account/chat/unreadcount` — TTL=10s (UX бейджа, polling 20s).
 *  - `/api/public/share/heartbeat` — TTL=15s (heartbeat 25s, кеш 15 = каждый 2-й no-op).
 *
 * **Lazy cleanup**: на каждом N-ном вызове удаляем истёкшие записи из карты, чтобы не
 * накапливать мусор для редко-посещаемых ключей. Аналогично [DedupCache].
 *
 * Почему НЕ Spring `@Cacheable` (через Caffeine/ConcurrentMapCacheManager):
 *  - Не хотим global cache manager ради 3 endpoints — проще явный бин с понятным контрактом.
 *  - TTL разный per-endpoint — `@Cacheable` поддерживает это только через `@Configuration`
 *    с per-name CacheManager (overkill для нашего случая).
 *  - Явный `loader: () -> V` блок в коде контроллера делает cache-miss path очевидным.
 *
 * @see docs/features/site-traffic-resilience.md
 * @see KaraokeProperties
 */
class PollingCache<V> {
    private data class CacheEntry<V>(
        val value: V,
        val expiresAtMs: Long
    )

    private val store = ConcurrentHashMap<String, CacheEntry<V>>()
    private val callsSinceCleanup = AtomicLong(0L)
    private val cleanupEvery = 500L

    /**
     * Возвращает кешированное значение для [key] если оно живо, иначе вызывает [loader],
     * сохраняет результат с TTL [ttlSeconds] и возвращает его.
     *
     * Параллельные вызовы для одного и того же ключа НЕ дедуплицируются (loader может
     * вызываться дважды в race condition). Это приемлемо для polling-кеша — два SQL-запроса
     * с интервалом <100ms случаются редко и оба попадают в cache после первого завершения.
     *
     * @param key произвольный строковый ключ (контроллер строит его из параметров запроса).
     * @param ttlSeconds TTL в секундах для конкретного endpoint.
     * @param loader блок, выполняющий реальный запрос (DB/HTTP) при cache-miss.
     * @return значение из кеша или результат [loader].
     */
    fun getOrCompute(key: String, ttlSeconds: Long, loader: () -> V): V {
        val now = System.currentTimeMillis()
        val existing = store[key]
        if (existing != null && existing.expiresAtMs > now) {
            return existing.value
        }
        val fresh = loader()
        val expiresAt = now + ttlSeconds * 1000L
        store[key] = CacheEntry(fresh, expiresAt)
        maybeCleanup()
        return fresh
    }

    private fun maybeCleanup() {
        if (callsSinceCleanup.incrementAndGet() % cleanupEvery != 0L) return
        val now = System.currentTimeMillis()
        store.entries.removeIf { it.value.expiresAtMs <= now }
    }

    /** Размер карты (для отладки/метрик). */
    fun size(): Int = store.size

    /** Полная очистка (для тестов). */
    fun clear() {
        store.clear()
        callsSinceCleanup.set(0L)
    }
}
