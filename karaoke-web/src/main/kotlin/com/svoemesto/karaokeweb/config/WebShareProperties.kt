package com.svoemesto.karaokeweb.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

/**
 * Настройки функции «Временный полный доступ к песне» (add-song-share-link).
 *
 * Загружаются из `karaoke.share.*` в `application.yml`. Эти настройки **локальны
 * для модуля karaoke-web** — НЕ наследуются из karaoke-app/KaraokeProperties, потому
 * что karaoke-web не должен зависеть от karaoke-app (см. AGENTS.md «KaraokeProperties
 * в karaoke-web не используется»).
 *
 * @see docs/features/guest-share-link.md
 */
@Component
@ConfigurationProperties(prefix = "karaoke.share")
class WebShareProperties {
    /** Максимум активных (не отозванных, не истёкших) ссылок у одного владельца. */
    var maxActivePerUser: Long = 5

    /** Лимит созданий ссылок в сутки (защита от спама). */
    var maxGenerationsPerDay: Long = 30

    /** Лимит пересозданий ссылок на одну и ту же песню в час. */
    var maxReissuesPerSongPerHour: Long = 3

    /** Rate-limit claim-запросов с одного IP в минуту. */
    var claimRateLimitPerIpPerMin: Long = 10

    /** Максимум одновременных playback-сессий (устройств) на одну ссылку. */
    var maxConcurrentSessions: Long = 2

    /** TTL lease в секундах — как долго сессия считается живой без heartbeat. */
    var leaseTtlSeconds: Long = 90

    /** Grace-пауза в секундах перед пометкой сессии как `timeout`. */
    var gracePauseSeconds: Long = 120

    /** Интервал фонового sweep'а в секундах. Минимум — 10 секунд. */
    var sweepIntervalSeconds: Long = 60

    /** Интервал heartbeat от плеера гостя в секундах. Должен быть меньше leaseTtlSeconds,
     *  чтобы один пропущенный heartbeat не отзывал lease. Дефолт 25 при leaseTtlSeconds=90
     *  даёт запас ~65 сек на джиттер/пропуски. См. docs/features/guest-share-link.md. */
    var heartbeatIntervalSeconds: Long = 25
}
