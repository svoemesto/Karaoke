package com.svoemesto.karaokeapp.db

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Тест параметров пула (спека 778, KARAOKE-430).
 *
 * Без живой БД: проверяется только разбор и валидация параметров.
 */
class PoolConfigTest {
    @Test
    fun `корректные параметры принимаются`() {
        val config =
            PoolConfig(
                maxPoolSize = 128,
                minIdle = 2,
                acquireTimeoutMs = 5_000,
                maxLifetimeMs = 1_800_000,
                keepaliveMs = 60_000,
                validationTimeoutMs = 3_000,
            )
        assertEquals(128, config.maxPoolSize)
        assertEquals(2, config.minIdle)
    }

    @Test
    fun `нулевой и отрицательный размер пула отклоняются`() {
        assertThrows(IllegalArgumentException::class.java) { PoolConfig(maxPoolSize = 0) }
        assertThrows(IllegalArgumentException::class.java) { PoolConfig(maxPoolSize = -1) }
    }

    @Test
    fun `minIdle больше maxPoolSize отклоняется`() {
        val e =
            assertThrows(IllegalArgumentException::class.java) {
                PoolConfig(maxPoolSize = 8, minIdle = 9)
            }
        assertTrue(e.message!!.contains("minIdle"), "сообщение должно называть параметр: ${e.message}")
    }

    @Test
    fun `неположительные таймауты отклоняются`() {
        assertThrows(IllegalArgumentException::class.java) {
            PoolConfig(maxPoolSize = 8, acquireTimeoutMs = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PoolConfig(maxPoolSize = 8, validationTimeoutMs = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PoolConfig(maxPoolSize = 8, maxLifetimeMs = 0)
        }
    }

    @Test
    fun `keepalive не меньше maxLifetime отклоняется`() {
        assertThrows(IllegalArgumentException::class.java) {
            PoolConfig(maxPoolSize = 8, maxLifetimeMs = 60_000, keepaliveMs = 60_000)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PoolConfig(maxPoolSize = 8, maxLifetimeMs = 60_000, keepaliveMs = 120_000)
        }
    }

    @Test
    fun `сумма пулов меньше лимита БД принимается`() {
        // При sizing от конфигурации: app 128 + 20 = 148, web 96 + 10 = 106.
        PoolConfig.requireWithinDbLimit(listOf(128, 20))
        PoolConfig.requireWithinDbLimit(listOf(96, 10))
    }

    @Test
    fun `сумма пулов больше лимита БД отклоняется`() {
        val e =
            assertThrows(IllegalArgumentException::class.java) {
                PoolConfig.requireWithinDbLimit(listOf(200, 150))
            }
        assertTrue(e.message!!.contains("350"), "сообщение должно называть сумму: ${e.message}")
        assertTrue(e.message!!.contains("300"), "сообщение должно называть лимит: ${e.message}")
    }
}
