package com.svoemesto.karaokeapp.services

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Потокобезопасный in-memory TTL-кеш общего назначения (FR-008, D-7).
 *
 * Хранит ключ → (значение, expiresAtMs). При вызове [getOrCompute] проверяет, не истёк ли кеш
 * для ключа. Если жив — возвращает кешированное значение БЕЗ вызова loader. Если истёк или
 * отсутствует — вызывает loader, сохраняет результат и возвращает его.
 *
 * **Где живёт** (Pass 456, 2026-09-26): класс перенесён в `karaoke-app` из `karaoke-web`.
 * Причина — направление зависимостей Gradle: `karaoke-web` зависит от `karaoke-app`, но не
 * наоборот, поэтому копия в `karaoke-web` была недоступна ядру. До переноса это было
 * зафиксировано как known gap в `knowledge/domains/caching/components/web-caches.md`
 * («`PollingCache` в `karaoke-app` — есть или только в web?»). Копия в `karaoke-web`
 * удалена, оба модуля используют эту реализацию.
 *
 * **Per-endpoint TTL** (clarified 2026-08-14, FR-008; значения — для публичных эндпоинтов):
 *  - `/api/public/news/since` — TTL=60s (новости меняются нечасто).
 *  - `/api/public/account/chat/unreadcount` — TTL=10s (UX бейджа, polling 20s).
 *  - `/api/public/share/heartbeat` — TTL=15s (heartbeat 25s, кеш 15 = каждый 2-й no-op).
 *
 * **Условное кэширование** (Pass 456): параметр [getOrCompute]`shouldCache` позволяет НЕ
 * сохранять результат, который кэшировать нельзя. Мотивирующий случай — детект ВПН
 * ([com.svoemesto.karaokeapp.isVpnActive]): «страну определить не удалось» — это fail-open
 * (ВПН не считаем), и если закэшировать эту неудачу, разовый сетевой сбой «залипнет» на весь
 * TTL: машина с включённым ВПН пойдёт в Яндекс.Музыку, где её заблокируют. Успешный результат
 * кэшируется, неуспешный — нет.
 *
 * **Lazy cleanup**: на каждом N-ном промахе удаляем истёкшие записи из карты, чтобы не
 * накапливать мусор для редко-посещаемых ключей. Аналогично [DedupCache]
 * (`karaoke-web/services/DedupCache.kt`).
 *
 * Почему НЕ Spring `@Cacheable` (через Caffeine/ConcurrentMapCacheManager):
 *  - Не хотим global cache manager ради нескольких endpoints — проще явный бин с понятным контрактом.
 *  - TTL разный per-endpoint — `@Cacheable` поддерживает это только через `@Configuration`
 *    с per-name CacheManager (overkill для нашего случая).
 *  - Явный `loader: () -> V` блок в коде вызывающего делает cache-miss path очевидным.
 *
 * @see archive/docs/features/site-traffic-resilience.md
 * @see com.svoemesto.karaokeapp.KaraokeProperties
 * @see com.svoemesto.karaokeweb.services.DedupCache
 */
class PollingCache<V> {
    private data class CacheEntry<V>(
        val value: V,
        val expiresAtMs: Long,
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
     * @param key произвольный строковый ключ (вызывающий строит его из параметров запроса).
     * @param ttlSeconds TTL в секундах для конкретного ключа/endpoint.
     * @param shouldCache предикат «сохранять ли результат loader'а». По умолчанию сохраняем
     *   всё. Вернув `false` (например, для неудачи — см. KDoc класса), вызывающий получает
     *   значение, но в кеше его не остаётся: следующий вызов снова дёрнет loader.
     * @param loader блок, выполняющий реальный запрос (DB/HTTP) при cache-miss.
     * @return значение из кеша или результат [loader].
     */
    fun getOrCompute(
        key: String,
        ttlSeconds: Long,
        shouldCache: (V) -> Boolean = { true },
        loader: () -> V,
    ): V {
        val existing = store[key]
        if (existing != null && existing.expiresAtMs > System.currentTimeMillis()) {
            return existing.value
        }
        val fresh = loader()
        if (shouldCache(fresh)) {
            // expiresAt считаем ПОСЛЕ loader'а (Pass 456). Раньше `now` снимался до вызова,
            // поэтому медленный loader съедал часть TTL: у детекта ВПН loader может идти
            // до 5+5 с на сервис, то есть до 20 с на вызов.
            store[key] = CacheEntry(fresh, System.currentTimeMillis() + ttlSeconds * 1000L)
        }
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
