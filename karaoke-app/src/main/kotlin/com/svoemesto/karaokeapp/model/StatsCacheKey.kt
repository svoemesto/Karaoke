package com.svoemesto.karaokeapp.model

import java.time.Instant

/**
 * Ключ кеша для [com.svoemesto.karaokeapp.services.StatsCache].
 *
 * Для чистых агрегатов ([endpoint] без query-параметров) `params` всегда `emptyMap()` —
 * иначе cache key explosion не оправдан (см. spec.md FR-004).
 *
 * `equals`/`hashCode` генерируются компилятором Kotlin, что делает `Map`-lookup
 * в `ConcurrentHashMap` детерминированным по (endpoint, params).
 *
 * @see specs/174-fix-stats-connection-leak/data-model.md § 1.1
 */
data class StatsCacheKey(
    val endpoint: String,
    val params: Map<String, String> = emptyMap(),
) {
    companion object {
        /**
         * BR-195/P-20-01: ключ обязан включать фактические параметры запроса.
         *
         * Раньше `StatsController.respondCached` строил `StatsCacheKey(endpoint, emptyMap())`,
         * поэтому `/api/stats/timeseries?days=7` и `?days=30`, а также `target=local|remote`
         * попадали в одну запись кеша и 60 секунд отдавали чужие цифры (в т.ч. другой БД).
         *
         * `target` нормализуется к тому же значению, по которому [com.svoemesto.karaokeapp.Connection]
         * выбирает БД (`remote` → REMOTE, всё остальное → LOCAL), чтобы `target=REMOTE` и
         * отсутствие параметра не создавали двух разных ключей для одной и той же БД.
         */
        fun forEndpoint(
            endpoint: String,
            target: String?,
            params: Map<String, String> = emptyMap(),
        ): StatsCacheKey =
            StatsCacheKey(
                endpoint = endpoint,
                params =
                    params +
                        mapOf(
                            "target" to if (target == "remote") "remote" else "local",
                        ),
            )
    }
}

/**
 * Значение кеша для [com.svoemesto.karaokeapp.services.StatsCache].
 *
 * Хранит произвольный response body плюс момент истечения TTL.
 * TTL = [com.svoemesto.karaokeapp.services.StatsCache.TTL_SECONDS] (60s) —
 * проверяется lazy на чтении (`expiresAt > Instant.now()`).
 *
 * @see specs/174-fix-stats-connection-leak/data-model.md § 1.2
 */
data class StatsCacheEntry(
    val value: Any,
    val expiresAt: Instant,
)
