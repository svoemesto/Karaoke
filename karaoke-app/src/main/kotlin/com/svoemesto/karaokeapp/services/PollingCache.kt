package com.svoemesto.karaokeapp.services

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.LongAdder

/**
 * Потокобезопасный in-memory кеш для polling-эндпоинтов (Pass 341 + Pass 343 FIFO hard-cap).
 *
 * **Локальная копия** `PollingCache` из `karaoke-web`, используемая в `StorageMetadataCache`
 * (см. спеку #344, OpenProject #69). Контракт идентичен оригиналу. Сознательная копия —
 * shared Gradle-модуль для 80 строк overengineering. KDoc ссылается на источник.
 *
 * @see karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/PollingCache.kt
 * @see knowledge/domains/caching/components/web-caches.md
 * @see knowledge/domains/caching/components/caching-patterns.md
 * @see docs/features/storage-metadata-cache.md (FR-009 per-feature doc)
 */
class PollingCache<V>(
    private val maxEntries: Int = 50_000,
) {
    private data class CacheEntry<V>(
        val value: V,
        val expiresAtMs: Long,
    )

    private val store = ConcurrentHashMap<String, CacheEntry<V>>()
    private val callsSinceCleanup = AtomicLong(0L)
    private val cleanupEvery = 500L

    private val localHits = LongAdder()
    private val localMisses = LongAdder()
    private val localEvictions = LongAdder()

    /**
     * Возвращает кешированное значение для [key] если оно живо, иначе вызывает [loader],
     * сохраняет результат с TTL [ttlSeconds] и возвращает его.
     *
     * Параллельные вызовы для одного и того же ключа НЕ дедуплицируются (loader может
     * вызываться дважды в race condition). Приемлемо для metadata cache.
     *
     * @param key произвольный строковый ключ.
     * @param ttlSeconds TTL в секундах.
     * @param loader блок, выполняющий реальный запрос при cache-miss.
     * @return значение из кеша или результат [loader].
     */
    @Suppress("MagicNumber")
    fun getOrCompute(
        key: String,
        ttlSeconds: Long,
        loader: () -> V,
    ): V {
        val now = System.currentTimeMillis()
        val existing = store[key]
        if (existing != null && existing.expiresAtMs > now) {
            localHits.increment()
            return existing.value
        }
        localMisses.increment()
        val fresh = loader()
        val expiresAt = now + ttlSeconds * 1000L
        store[key] = CacheEntry(fresh, expiresAt)
        enforceMaxEntriesIfNeeded()
        maybeCleanup()
        return fresh
    }

    /**
     * Hard-cap по размеру (Pass 343, NFR-002 спеки #344). При превышении `maxEntries`
     * — удаляем ~10% (extra headroom). NB: ConcurrentHashMap не гарантирует FIFO,
     * eviction может быть в произвольном порядке.
     */
    @Suppress("MagicNumber")
    private fun enforceMaxEntriesIfNeeded() {
        if (maxEntries <= 0 || store.size <= maxEntries) return
        val toRemove = (store.size - maxEntries).coerceAtLeast(1) + (maxEntries / 10)
        val it = store.entries.iterator()
        var removed = 0
        while (it.hasNext() && removed < toRemove) {
            it.next()
            it.remove()
            removed++
        }
        if (removed > 0) {
            localEvictions.add(removed.toLong())
        }
    }

    /**
     * Lazy cleanup TTL'd entries (через каждые 500 вызовов).
     */
    private fun maybeCleanup() {
        if (callsSinceCleanup.incrementAndGet() % cleanupEvery != 0L) return
        val now = System.currentTimeMillis()
        var removed = 0
        val it = store.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            if (e.value.expiresAtMs <= now) {
                it.remove()
                removed++
            }
        }
        if (removed > 0) {
            localEvictions.add(removed.toLong())
        }
    }

    /** Размер карты (для метрик). */
    fun size(): Int = store.size

    /** Метрики (для `StorageMetadataCache.stats()`). */
    fun hits(): Long = localHits.sum()

    fun misses(): Long = localMisses.sum()

    fun evictions(): Long = localEvictions.sum()

    /** Полная очистка (для тестов). */
    fun clear() {
        store.clear()
        callsSinceCleanup.set(0L)
        localHits.reset()
        localMisses.reset()
        localEvictions.reset()
    }
}
