package com.svoemesto.karaokeapp.model

/**
 * Состояние stats-инфраструктуры для [POST /api/stats/debug] —
 * используется при ручной диагностике инцидентов
 * (см. spec.md FR-010, contracts/stats-debug.md).
 *
 * @see specs/174-fix-stats-connection-leak/contracts/stats-debug.md
 */
data class StatsDebugDto(
    /** Текущий размер кеша (включая expired). */
    val cacheSize: Int,
    /** Список ключей с возрастом и признаком истечения TTL. */
    val cacheKeys: List<CacheKeyInfo>,
    /** `SELECT count(*) FROM pg_stat_activity` — сколько соединений сейчас открыто. */
    val pgActiveConnections: Int,
    /** `SELECT setting FROM pg_settings WHERE name='max_connections'`. */
    val pgMaxConnections: Int,
    /** ISO-8601 UTC, e.g. `"2026-08-12T10:23:45.123Z"`. */
    val timestamp: String,
)

/**
 * Запись о кешированном ответе — endpoint, params, возраст и флаг истечения.
 *
 * @see specs/174-fix-stats-connection-leak/data-model.md § 1.5
 */
data class CacheKeyInfo(
    val endpoint: String,
    val params: Map<String, String>,
    /** Секунд с момента создания записи (положительное значение). */
    val ageSeconds: Long,
    /** `true` если `ageSeconds > [com.svoemesto.karaokeapp.services.StatsCache.TTL_SECONDS]`. */
    val expired: Boolean,
)
