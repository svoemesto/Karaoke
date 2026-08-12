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
)

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
