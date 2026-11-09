package com.svoemesto.karaokeapp.monitor

import com.svoemesto.karaokeapp.monitor.checks.ProdTunnelCheck
import com.svoemesto.karaokeapp.services.ProdTunnelHolder
import com.svoemesto.karaokeapp.tunnel.ProdTunnelConfig
import com.svoemesto.karaokeapp.tunnel.ProdTunnelSupervisor
import com.svoemesto.karaokeapp.tunnel.TunnelState
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Спека 733 (FR-008, FR-028, SC-010): состояние туннеля видно в мониторинге
 * отдельным признаком и НЕ сливается с общим «прод недоступен».
 */
class ProdTunnelCheckTest {
    private val config = ProdTunnelConfig(enabled = true, handshakeTimeoutSeconds = 180, fallbackEnabled = true)

    @AfterEach
    fun cleanup() {
        ProdTunnelHolder.supervisor = null
        ProdTunnelHolder.config = ProdTunnelConfig()
    }

    private fun supervisorWith(state: TunnelState): ProdTunnelSupervisor {
        val tunnel =
            object : com.svoemesto.karaokeapp.tunnel.ProdTunnel {
                override val config: ProdTunnelConfig = this@ProdTunnelCheckTest.config

                override fun ensureUp(): TunnelState = state

                override fun status(): TunnelState = state

                override fun restore(): TunnelState = state

                override fun shutdown() = Unit
            }
        return ProdTunnelSupervisor(tunnel, config).also {
            it.state = state
        }
    }

    @Test
    fun `выключенный туннель не поднимает алерт`() {
        ProdTunnelHolder.supervisor = null
        ProdTunnelHolder.config = ProdTunnelConfig()
        assertTrue(ProdTunnelCheck.run(mockContext()).isEmpty())
    }

    @Test
    fun `поднятый туннель не поднимает алерт`() {
        ProdTunnelHolder.supervisor =
            supervisorWith(TunnelState(up = true, handshakeAgeSeconds = 5, lastCheckedAt = Instant.now()))
        ProdTunnelHolder.config = config
        assertTrue(ProdTunnelCheck.run(mockContext()).isEmpty())
    }

    @Test
    fun `неподнятый туннель с откатом на публичный путь — критический алерт`() {
        ProdTunnelHolder.supervisor =
            supervisorWith(
                TunnelState(up = false, reason = "no_handshake", lastCheckedAt = Instant.now()),
            )
        ProdTunnelHolder.config = config
        val alerts = ProdTunnelCheck.run(mockContext())
        assertEquals(1, alerts.size)
        assertEquals("prod.tunnel", alerts[0].key)
        assertEquals(MonitorSeverity.CRITICAL, alerts[0].severity)
        assertTrue(alerts[0].body.contains("публичный путь"), "алерт должен говорить про откат: ${alerts[0].body}")
    }

    @Test
    fun `устаревшее рукопожатие — предупреждение`() {
        val stale = Instant.now().epochSecond - 3600
        ProdTunnelHolder.supervisor =
            supervisorWith(TunnelState(up = true, handshakeAgeSeconds = stale, lastCheckedAt = Instant.now()))
        ProdTunnelHolder.config = config
        val alerts = ProdTunnelCheck.run(mockContext())
        assertEquals(1, alerts.size)
        assertEquals(MonitorSeverity.WARNING, alerts[0].severity)
        assertTrue(alerts[0].title.contains("устаревшее"), "ожидался признак устаревшего рукопожатия: ${alerts[0].title}")
    }

    /** Проверка не использует БД — контекст подставной (Mockito, как в HeapUsageCheckTest). */
    private fun mockContext(): MonitorContext =
        MonitorContext(
            localDb = org.mockito.Mockito.mock(com.svoemesto.karaokeapp.KaraokeConnection::class.java),
            storageService = org.mockito.Mockito.mock(com.svoemesto.karaokeapp.services.KaraokeStorageService::class.java),
            storageApiClient = org.mockito.Mockito.mock(com.svoemesto.karaokeapp.services.StorageApiClient::class.java),
        )
}
