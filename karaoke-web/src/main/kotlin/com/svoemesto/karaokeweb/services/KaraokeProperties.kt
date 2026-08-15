@file:Suppress("ktlint:standard:max-line-length")

package com.svoemesto.karaokeweb.services

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration

/**
 * Централизованные env-переменные для site-traffic resilience (FR-006/007/010/011/013, contracts/C9).
 *
 * Все значения биндятся через Spring `@Value` (стандартная конвенция проекта, см.
 * `karaoke-web/.../services/StemJobTempCleanupScheduler.kt:8` и др.). Defaults разумные
 * для production — оператор может переопределить в `deploy/do.env` или `application.yml`.
 *
 * **Новые переменные для site-traffic-resilience**:
 *  - `KARAOKE_WEB_EVENTS_SAMPLING_ANON` (int, default `20`) — 1 из N запросов для анонимов.
 *  - `KARAOKE_WEB_EVENTS_SAMPLING_LOGGED` (int, default `5`) — 1 из N для залогиненных.
 *  - `KARAOKE_WEB_EVENTS_SAMPLING_ADMIN` (int, default `1`) — 1 из N для admin (1 = всё пишем).
 *  - `KARAOKE_WEB_EVENTS_DEDUP_TTL_SECONDS` (long, default `30`) — TTL для DedupCache.
 *  - `KARAOKE_WEB_EVENTS_RETENTION_DAYS` (long, default `7`) — retention для tbl_events.
 *  - `KARAOKE_WEB_DEBUG_DB_ENABLED` (bool, default `false`) — мастер-флаг для `/api/public/debug/db`.
 *  - `KARAOKE_WEB_DEBUG_DB_ALLOWED_IPS` (string, default `""`) — comma-separated CIDR/IPv4 allowlist.
 *  - `KARAOKE_WEB_RATE_LIMIT_SONG_PICTURE_PER_MINUTE` (int, default `60`) — для `/api/public/song-picture/{id}`.
 *  - `KARAOKE_WEB_RATE_LIMIT_SONG_VK_IMAGE_PER_MINUTE` (int, default `60`) — для `/api/public/song-vk-image/{id}`.
 *
 * @see archive/archive/docs/features/site-traffic-resilience.md
 * @see SamplingConfig
 * @see SamplingFilter
 * @see DedupCache
 * @see PollingCache
 * @see EventsRetentionScheduler
 * @see RateLimitInterceptor
 * @see DebugDbAccessGuard
 */
@Configuration
class KaraokeProperties {
    @Value("\${karaoke-web.events.sampling-anon:20}")
    @Suppress("ktlint:standard:backing-property-naming")
    private val samplingAnonymousInternal: Int = 20

    @Value("\${karaoke-web.events.sampling-logged:5}")
    @Suppress("ktlint:standard:backing-property-naming")
    private val samplingLoggedInternal: Int = 5

    @Value("\${karaoke-web.events.sampling-admin:1}")
    @Suppress("ktlint:standard:backing-property-naming")
    private val samplingAdminInternal: Int = 1

    @Value("\${karaoke-web.events.dedup-ttl-seconds:30}")
    @Suppress("ktlint:standard:backing-property-naming")
    private val dedupTtlSecondsInternal: Long = 30L

    @Value("\${karaoke-web.events.retention-days:7}")
    @Suppress("ktlint:standard:backing-property-naming")
    private val eventsRetentionDaysInternal: Long = 7L

    @Value("\${karaoke-web.debug-db.enabled:false}")
    @Suppress("ktlint:standard:backing-property-naming")
    private val debugDbEnabledInternal: Boolean = false

    @Value("\${karaoke-web.debug-db.allowed-ips:}")
    @Suppress("ktlint:standard:backing-property-naming")
    private val debugDbAllowedIpsInternal: String = ""

    @Value("\${karaoke-web.rate-limit.song-picture-per-minute:60}")
    @Suppress("ktlint:standard:backing-property-naming")
    private val rateLimitSongPicturePerMinuteInternal: Int = 60

    @Value("\${karaoke-web.rate-limit.song-vk-image-per-minute:60}")
    @Suppress("ktlint:standard:backing-property-naming")
    private val rateLimitSongVkImagePerMinuteInternal: Int = 60

    /** Текущая конфигурация sampling/dedup (читается на каждый вызов [SamplingFilter]). */
    val samplingConfig: SamplingConfig
        get() =
            SamplingConfig(
                samplingAnonymous = samplingAnonymousInternal.coerceAtLeast(1),
                samplingLogged = samplingLoggedInternal.coerceAtLeast(1),
                samplingAdmin = samplingAdminInternal.coerceAtLeast(1),
                dedupTtlSeconds = dedupTtlSecondsInternal.coerceAtLeast(1L),
            )

    /** TTL для DedupCache в миллисекундах (для передачи в [DedupCache]). */
    fun eventsDedupTtlMs(): Long = dedupTtlSecondsInternal.coerceAtLeast(1L) * 1000L

    /** Retention period для [EventsRetentionScheduler] в днях. */
    val eventsRetentionDays: Long
        get() = eventsRetentionDaysInternal.coerceAtLeast(1L)

    /** Включён ли debug endpoint (FR-013). Master-flag, см. также [debugDbAllowedIps]. */
    val debugDbEnabled: Boolean
        get() = debugDbEnabledInternal

    /** Comma-separated список IP, разделённых запятой. Пустая строка → endpoint отключён. */
    val debugDbAllowedIps: String
        get() = debugDbAllowedIpsInternal.trim()

    /** Лимит для /api/public/song-picture/{id} (req/min на IP). */
    val rateLimitSongPicturePerMinute: Int
        get() = rateLimitSongPicturePerMinuteInternal.coerceAtLeast(1)

    /** Лимит для /api/public/song-vk-image/{id} (req/min на IP). */
    val rateLimitSongVkImagePerMinute: Int
        get() = rateLimitSongVkImagePerMinuteInternal.coerceAtLeast(1)
}
