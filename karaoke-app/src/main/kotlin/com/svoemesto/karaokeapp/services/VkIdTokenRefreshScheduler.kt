package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.KaraokeProperties
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Фоновый job для автоматического обновления VK ID access_token через
 * `refresh_token` (specs/151-vk-id-personal-token, FR-004).
 *
 * Запускается каждый час (cron `0 0 * * * *` — в 0 минут каждого часа).
 * Если до `vkIdAccessTokenExpiresAt` осталось меньше 30 минут —
 * вызывает [VkApiClient.refreshVkIdAccessToken] и сохраняет новые токены.
 *
 * Поведение при ошибках:
 * - `error=invalid_grant` (refresh_token истёк) → устанавливает
 *   `vkIdRefreshNeeded=true`, `vkIdRefreshLastError`. Админ должен повторить
 *   OAuth flow через `/api/public/utils/vkIdOAuthUrl`.
 * - 5xx / тайм-аут → повторяет через backoff (не реализовано в первой версии,
 *   следующая попытка через час).
 * - Настройки не заданы (`vkIdClientId <= 0` или `vkIdRefreshToken.isBlank()`) →
 *   молча выходит (нечего refresh'ить).
 *
 * Защита от параллельного выполнения: метод помечен `synchronized` на
 * инстансе (lock `this`). При горизонтальном масштабировании потребуется
 * distributed lock (отдельная задача).
 *
 * @see specs/151-vk-id-personal-token/spec.md (FR-004)
 * @see archive/docs/features/vk-id-auth.md
 */
@Component
class VkIdTokenRefreshScheduler {
    private val log = LoggerFactory.getLogger(VkIdTokenRefreshScheduler::class.java)

    /** За сколько минут до истечения access_token делать refresh. */
    private val refreshThresholdMinutes = 30L

    /**
     * Cron `0 0 * * * *` — в 0 минут каждого часа. Выполняется в потоке
     * `karaoke-scheduler` (см. `KaraokeAppApplication.taskScheduler`).
     */
    @Scheduled(cron = "0 0 * * * *")
    fun refreshIfNeeded() {
        // Защита от параллельного выполнения (если scheduler тикает чаще,
        // или два инстанса — упрощённая защита для одного инстанса).
        synchronized(this) {
            val clientId = KaraokeProperties.getLong("vkIdClientId")
            val accessToken = KaraokeProperties.getString("vkIdAccessToken")
            val refreshToken = KaraokeProperties.getString("vkIdRefreshToken")
            val expiresAtStr = KaraokeProperties.getString("vkIdAccessTokenExpiresAt")

            // Молча выходим, если VK ID flow не настроен или токены ещё не получены.
            if (clientId <= 0) {
                log.debug("VkIdTokenRefreshScheduler: vkIdClientId is empty, skipping")
                return
            }
            if (accessToken.isBlank() || refreshToken.isBlank()) {
                log.debug("VkIdTokenRefreshScheduler: no tokens yet, skipping")
                return
            }
            if (expiresAtStr.isBlank()) {
                log.warn("VkIdTokenRefreshScheduler: vkIdAccessTokenExpiresAt is empty, skipping")
                return
            }

            // Парсим ISO datetime.
            val expiresAt =
                try {
                    Instant.parse(expiresAtStr)
                } catch (e: Exception) {
                    log.warn(
                        "VkIdTokenRefreshScheduler: cannot parse vkIdAccessTokenExpiresAt='{}', skipping",
                        expiresAtStr,
                    )
                    return
                }
            val now = Instant.now()
            val minutesUntilExpiry =
                java.time.Duration
                    .between(now, expiresAt)
                    .toMinutes()
            if (minutesUntilExpiry > refreshThresholdMinutes) {
                log.debug(
                    "VkIdTokenRefreshScheduler: expires in {} min, threshold {} min, skipping",
                    minutesUntilExpiry,
                    refreshThresholdMinutes,
                )
                return
            }

            log.info(
                "VkIdTokenRefreshScheduler: refreshing VK ID access_token (expires in {} min)",
                minutesUntilExpiry,
            )
            try {
                val result = VkApiClient().refreshVkIdAccessToken()
                val newExpiresAt = now.plusSeconds(result.expiresIn).toString()
                KaraokeProperties.set("vkIdAccessToken", result.accessToken)
                KaraokeProperties.set("vkIdRefreshToken", result.refreshToken)
                KaraokeProperties.set("vkIdAccessTokenExpiresAt", newExpiresAt)
                KaraokeProperties.set("vkIdRefreshNeeded", false)
                KaraokeProperties.set("vkIdRefreshLastError", "")
                if (result.idToken != null) {
                    KaraokeProperties.set("vkIdIdToken", result.idToken)
                }
                log.info(
                    "VkIdTokenRefreshScheduler: refresh success, new expiresAt={}",
                    newExpiresAt,
                )
            } catch (e: VkIdRefreshFailedException) {
                log.warn(
                    "VkIdTokenRefreshScheduler: refresh failed ({}): {}",
                    e.errorCode,
                    e.errorMsg,
                )
                KaraokeProperties.set("vkIdRefreshNeeded", true)
                KaraokeProperties.set("vkIdRefreshLastError", "${e.errorCode}: ${e.errorMsg}")
            } catch (e: IllegalStateException) {
                log.warn(
                    "VkIdTokenRefreshScheduler: refresh skipped (config): {}",
                    e.message,
                )
            } catch (e: Exception) {
                log.error(
                    "VkIdTokenRefreshScheduler: unexpected error during refresh",
                    e,
                )
                KaraokeProperties.set("vkIdRefreshNeeded", true)
                KaraokeProperties.set(
                    "vkIdRefreshLastError",
                    "unexpected: ${e.message ?: e.javaClass.simpleName}",
                )
            }
        }
    }
}
