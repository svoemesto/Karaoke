package com.svoemesto.karaokeapp.services

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Pass 429 (#153): два независимых [StorageCircuitBreaker] — для **локального** и
 * **удалённого** MinIO.
 *
 * **Зачем разделять**: раньше был один breaker, который защищал remote
 * (`StorageApiClientImpl.decorate`), но `HealthReport.actionsLocalStorage` читал
 * его же для локального пути → сбой remote давал `FATAL_ERROR` на local, а сам
 * локальный путь вообще не был защищён. Требование владельца: проблемы одного
 * хранилища не должны влиять на другое.
 *
 * **Общий config, раздельное состояние**: оба bean'а получают одни и те же
 * пороги из `application.yml` (`storage.circuit-breaker-*`,
 * `storage.file-exists-timeout-seconds`), но `state`/счётчики/watchdog у каждого
 * свои — это два разных экземпляра.
 *
 * @see StorageCircuitBreaker
 * @see specs/429-split-local-remote-circuit-breakers/spec.md
 */
@Configuration
class StorageCircuitBreakerConfig {
    @Bean("localStorageCircuitBreaker")
    fun localStorageCircuitBreaker(
        @Value("\${storage.file-exists-timeout-seconds:20}") timeoutSeconds: Long,
        @Value("\${storage.circuit-breaker-threshold:5}") threshold: Int,
        @Value("\${storage.circuit-breaker-cooldown-seconds:30}") cooldownSeconds: Long,
        @Value("\${storage.circuit-breaker-watchdog-enabled:true}") watchdogEnabled: Boolean,
        @Value("\${storage.circuit-breaker-watchdog-buffer-seconds:10}") watchdogBufferSeconds: Long,
        @Value("\${storage.circuit-breaker-watchdog-check-interval-seconds:1}") checkIntervalSeconds: Long,
    ): StorageCircuitBreaker =
        StorageCircuitBreaker(
            timeoutSeconds = timeoutSeconds,
            threshold = threshold,
            cooldownSeconds = cooldownSeconds,
            watchdogEnabled = watchdogEnabled,
            watchdogBufferSeconds = watchdogBufferSeconds,
            checkIntervalSeconds = checkIntervalSeconds,
            storageType = "local",
        )

    @Bean("remoteStorageCircuitBreaker")
    fun remoteStorageCircuitBreaker(
        @Value("\${storage.file-exists-timeout-seconds:20}") timeoutSeconds: Long,
        @Value("\${storage.circuit-breaker-threshold:5}") threshold: Int,
        @Value("\${storage.circuit-breaker-cooldown-seconds:30}") cooldownSeconds: Long,
        @Value("\${storage.circuit-breaker-watchdog-enabled:true}") watchdogEnabled: Boolean,
        @Value("\${storage.circuit-breaker-watchdog-buffer-seconds:10}") watchdogBufferSeconds: Long,
        @Value("\${storage.circuit-breaker-watchdog-check-interval-seconds:1}") checkIntervalSeconds: Long,
    ): StorageCircuitBreaker =
        StorageCircuitBreaker(
            timeoutSeconds = timeoutSeconds,
            threshold = threshold,
            cooldownSeconds = cooldownSeconds,
            watchdogEnabled = watchdogEnabled,
            watchdogBufferSeconds = watchdogBufferSeconds,
            checkIntervalSeconds = checkIntervalSeconds,
            storageType = "remote",
        )
}
