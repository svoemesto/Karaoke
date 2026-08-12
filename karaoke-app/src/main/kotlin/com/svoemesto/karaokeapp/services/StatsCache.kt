package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.model.StatsCacheEntry
import com.svoemesto.karaokeapp.model.StatsCacheKey
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * In-process TTL-кеш для 6 чистых stats-агрегатов
 * ([com.svoemesto.karaokeapp.controllers.StatsController]):
 * `/summary`, `/timeseries`, `/channels`, `/countries`, `/referrers`,
 * `/monetization`. Устраняет повторный `DriverManager.getConnection +
 * SELECT count(*) over tbl_events` в течение [TTL_SECONDS] секунд.
 *
 * **Thread-safety контракт.** Бэкграунд использует Tomcat thread pool
 * (~200 потоков), при `mounted()` StatsView из webvue3 раньше уходило
 * 10-12 параллельных HTTP — каждый открывал новое JDBC-соединение.
 * `ConcurrentHashMap` гарантирует atomic put/get без блокировок;
 * lazy expiration (`expiresAt > now()`) под lock-free read даёт
 * stale-once-after-expiry — допустимо: следующий `get()` увидит
 * expired и caller пересчитает.
 *
 * **Не persistence.** Перезапуск `karaoke-app` обнуляет кеш. TTL=60s
 * достаточен для агрессивно обновляемой статистики.
 *
 * @see specs/174-fix-stats-connection-leak/data-model.md § 1.3
 * @see specs/174-fix-stats-connection-leak/research.md § 1.2
 */
object StatsCache {
    /** TTL для всех записей — 60 секунд per spec.md FR-004. */
    const val TTL_SECONDS: Long = 60L

    private val log = LoggerFactory.getLogger(StatsCache::class.java)

    private val cache = ConcurrentHashMap<StatsCacheKey, StatsCacheEntry>()

    /**
     * Возвращает кешированный `value`, если запись существует и не истекла.
     * При истечении возвращает `null` (lazy expiration) — caller пересчитывает.
     */
    fun get(key: StatsCacheKey): Any? {
        val entry =
            cache[key] ?: run {
                log.debug("stats.cache endpoint={} hit=false reason=missing", key.endpoint)
                return null
            }
        val now = Instant.now()
        return if (entry.expiresAt > now) {
            val ageSec =
                TTL_SECONDS -
                    java.time.Duration
                        .between(now, entry.expiresAt)
                        .seconds
            log.debug(
                "stats.cache endpoint={} hit=true age={}s",
                key.endpoint,
                ageSec,
            )
            entry.value
        } else {
            log.debug("stats.cache endpoint={} hit=false reason=expired", key.endpoint)
            null
        }
    }

    /**
     * Кладёт значение в кеш с [TTL_SECONDS] от текущего момента.
     * Перезаписывает, если ключ уже есть.
     */
    fun put(key: StatsCacheKey, value: Any) {
        cache[key] = StatsCacheEntry(value, Instant.now().plusSeconds(TTL_SECONDS))
    }

    /**
     * Очищает весь кеш. Зарезервировано под будущую SSE-инвалидацию
     * (см. research.md § 1) — сейчас не вызывается.
     */
    fun invalidateAll() {
        cache.clear()
    }

    /**
     * Возвращает все записи (включая expired) для [POST /api/stats/debug]
     * (spec.md FR-010). Map iteration — weak consistency в
     * `ConcurrentHashMap`, для observability этого достаточно.
     */
    fun snapshot(): Map<StatsCacheKey, StatsCacheEntry> = cache.toMap()
}
