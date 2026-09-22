package com.svoemesto.karaokeapp.services

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment

/**
 * Pass 429 (#153): Spring-контекст обязан создавать **два разных** bean
 * [StorageCircuitBreaker] (local/remote) по квалификаторам. Тест ловит ошибку
 * DI-конфигурации до продакшн-рестарта (NoSuchBean/NoUniqueBean).
 */
class StorageCircuitBreakerConfigTest {
    @Test
    fun `config provides two distinct qualified breakers`() {
        val ctx = AnnotationConfigApplicationContext()
        try {
            val env = StandardEnvironment()
            env.propertySources.addFirst(
                MapPropertySource(
                    "test",
                    mapOf(
                        "storage.file-exists-timeout-seconds" to "5",
                        "storage.circuit-breaker-threshold" to "5",
                        "storage.circuit-breaker-cooldown-seconds" to "30",
                        "storage.circuit-breaker-watchdog-enabled" to "false",
                        "storage.circuit-breaker-watchdog-buffer-seconds" to "10",
                        "storage.circuit-breaker-watchdog-check-interval-seconds" to "1",
                    ),
                ),
            )
            ctx.environment = env
            val placeholder = PropertySourcesPlaceholderConfigurer()
            placeholder.setEnvironment(env)
            ctx.addBeanFactoryPostProcessor(placeholder)
            ctx.register(StorageCircuitBreakerConfig::class.java)
            ctx.refresh()

            val local = ctx.getBean("localStorageCircuitBreaker", StorageCircuitBreaker::class.java)
            val remote = ctx.getBean("remoteStorageCircuitBreaker", StorageCircuitBreaker::class.java)
            assertNotNull(local)
            assertNotNull(remote)
            assertNotSame(local, remote, "local and remote must be separate instances")
            assertEquals("local", local.storageType)
            assertEquals("remote", remote.storageType)
            // Состояние независимо.
            local.recordFailure(RuntimeException("x"))
            assertEquals(0L, remote.metrics().totalNetworkFailures)
        } finally {
            ctx.close()
        }
    }
}
