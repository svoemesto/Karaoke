package com.svoemesto.karaokeweb.services

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Потокобезопасный in-memory кеш для дедупликации событий tbl_events (FR-007).
 *
 * Хранит ключ (например, "restName|anonId|canonical(params)") → `lastSeenAtMs`.
 * При вызове [isDuplicate] проверяет, был ли тот же ключ за последние [ttlMs] миллисекунд.
 * Если да — возвращает `true` (нужно пропустить INSERT).
 *
 * **Lazy cleanup**: при каждом `isDuplicate` для ключа, который уже истёк, удаляется старая запись.
 * Полная очистка от старых записей выполняется на каждом N-ном вызове (cleanupEvery),
 * чтобы не раздувать карту при низкой активности. Это даёт O(1) среднюю стоимость и
 * O(1) amortized cleanup.
 *
 * Per-US3 dedup (см. `archive/archive/docs/features/site-traffic-resilience.md`):
 *  - Ключ формируется в [SamplingFilter.shouldSkip] как `(restName, canonical(parameters), anonId-or-userId)`.
 *  - TTL управляется через [com.svoemesto.karaokeweb.services.KaraokeProperties.eventsDedupTtlSeconds].
 *  - Используется ТОЛЬКО для анонимных/логин-юзеров (для admin — пропуск через sampling rate 1/1).
 *
 * Почему НЕ [java.util.concurrent.ConcurrentHashMap] с TTL на каждую запись (Caffeine/Guava cache):
 * намеренный минимум зависимостей. Для текущей нагрузки (~30 req/min в пике) ConcurrentHashMap
 * с lazy cleanup достаточен — N записей при N=10k = ~500 KB heap.
 *
 * @see archive/archive/docs/features/site-traffic-resilience.md
 * @see SamplingFilter
 * @see KaraokeProperties
 */
class DedupCache(
    private val ttlMs: () -> Long,
) {
    private val store = ConcurrentHashMap<String, Long>()
    private val callsSinceCleanup = AtomicLong(0L)
    private val cleanupEvery = 1000L

    /**
     * Проверяет, был ли ключ за последние [ttlMs] миллисекунд. Если да — возвращает `true`.
     * Если нет — записывает текущее время как "last seen" и возвращает `false`.
     *
     * Параллельные вызовы для одного и того же ключа потокобезопасны — `ConcurrentHashMap.compute`
     * сериализует доступ к ключу атомарно.
     *
     * @param key дедуп-ключ (формируется в [SamplingFilter.shouldSkip]).
     * @return `true` если дубликат (нужно пропустить INSERT), `false` если новый.
     */
    fun isDuplicate(key: String): Boolean {
        val now = System.currentTimeMillis()
        val ttl = ttlMs()
        val cutoff = now - ttl

        var isDuplicate = false
        store.compute(key) { _, lastSeen ->
            if (lastSeen != null && lastSeen >= cutoff) {
                isDuplicate = true
                lastSeen
            } else {
                isDuplicate = false
                now
            }
        }
        maybeCleanup()
        return isDuplicate
    }

    /**
     * Lazy cleanup: каждые [cleanupEvery] вызовов [isDuplicate] проходим по карте и удаляем
     * записи старше TTL. Это позволяет карте не раздуваться в долгоживущем процессе без
     * затрат на отдельный scheduled-поток.
     */
    private fun maybeCleanup() {
        if (callsSinceCleanup.incrementAndGet() % cleanupEvery != 0L) return
        val cutoff = System.currentTimeMillis() - ttlMs()
        store.entries.removeIf { it.value < cutoff }
    }

    /** Размер карты (для отладки/метрик). Не сериализуется в JSON. */
    fun size(): Int = store.size

    /** Полная очистка (для тестов). */
    fun clear() {
        store.clear()
        callsSinceCleanup.set(0L)
    }
}
