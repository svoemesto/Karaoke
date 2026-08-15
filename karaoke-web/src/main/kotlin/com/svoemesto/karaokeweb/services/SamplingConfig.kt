package com.svoemesto.karaokeweb.services

/**
 * Конфигурация sampling + dedup для REST-событий [com.svoemesto.karaokeapp.model.EventType.CALL_REST].
 *
 * Значения читаются из [KaraokeProperties] (env-переменные `KARAOKE_WEB_EVENTS_SAMPLING_*`,
 * `KARAOKE_WEB_EVENTS_DEDUP_TTL_SECONDS`, см. contracts/C9). Изменения применяются при следующем
 * вызове [SamplingFilter.shouldSkip] — без перезапуска приложения.
 *
 * **Differentiated sampling** (clarified 2026-08-14, FR-006):
 *  - `samplingAnonymous`: 1 из N запросов сохраняется (default 20 — i.e. 5% записываются).
 *  - `samplingLogged`: 1 из N (default 5 — 20%).
 *  - `samplingAdmin`: 1 из N (default 1 — 100%, всё пишется для отладки).
 *
 * **Dedup TTL** (FR-007): сколько секунд считать повторный запрос "тем же" — пропускать INSERT.
 * Per-(anonId/userId) scope (clarified Q2).
 *
 * @see archive/docs/features/site-traffic-resilience.md
 * @see SamplingFilter
 * @see KaraokeProperties
 */
data class SamplingConfig(
    val samplingAnonymous: Int,
    val samplingLogged: Int,
    val samplingAdmin: Int,
    val dedupTtlSeconds: Long,
)
