package com.svoemesto.karaokeweb.audit

import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeweb.services.LocalConnectionReaper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Уборка простаивающих соединений в `karaoke-web` (инцидент 2026-11-15).
 *
 * Соединения кешируются в `ThreadLocal`; у недолговечных потоков (Tomcat,
 * `Dispatchers.IO`) их никто не закрывает, и они копятся до `max_connections` —
 * БД отвечала `FATAL: sorry, too many clients already`, а эндпоинты, ходящие
 * в БД, отдавали 503. В `karaoke-app` такой рейпер есть с 2026-11-12
 * (KARAOKE-407); в `karaoke-web` его не было — `@SpringBootApplication` web'а
 * сканирует только свой пакет, поэтому компонент app'а туда не попадает.
 */
class LocalConnectionReaperTest {
    /** Подмена соединения: считает вызовы уборки и возвращает заданный результат. */
    private class FakeConnection(
        private val toClose: Int,
        private val fail: Boolean = false,
    ) : KaraokeConnection(url = "jdbc:postgresql://fake:5432/karaoke", username = "u", password = "p", name = "FAKE") {
        var calls = 0
        var lastIdleMs = -1L

        override fun reapIdleConnections(maxIdleMs: Long): Int {
            calls++
            lastIdleMs = maxIdleMs
            if (fail) throw IllegalStateException("уборка не удалась")
            return toClose
        }

        override fun openConnectionCount(): Int = 7
    }

    @Test
    fun `уборка вызывается для LOCAL и SERVER с заданным порогом простоя`() {
        val local = FakeConnection(toClose = 0)
        val remote = FakeConnection(toClose = 0)
        val reaper = LocalConnectionReaper(local = local, remote = remote, intervalMs = 300_000, idleMs = 900_000)

        reaper.reap()

        assertEquals(1, local.calls, "LOCAL должен быть убран один раз (${local.calls})")
        assertEquals(1, remote.calls, "SERVER должен быть убран один раз (${remote.calls})")
        assertEquals(900_000, local.lastIdleMs, "порог простоя должен передаваться в соединение")
        assertEquals(900_000, remote.lastIdleMs)
    }

    @Test
    fun `уборка закрывает именно простаивающие соединения`() {
        val local = FakeConnection(toClose = 240)
        val remote = FakeConnection(toClose = 2)
        val reaper = LocalConnectionReaper(local = local, remote = remote, intervalMs = 300_000, idleMs = 900_000)

        reaper.reap()

        assertEquals(1, local.calls)
        assertEquals(1, remote.calls)
    }

    @Test
    fun `сбой уборки LOCAL не отменяет уборку SERVER`() {
        val local = FakeConnection(toClose = 0, fail = true)
        val remote = FakeConnection(toClose = 3)
        val reaper = LocalConnectionReaper(local = local, remote = remote, intervalMs = 300_000, idleMs = 900_000)

        reaper.reap()

        assertEquals(1, local.calls, "LOCAL убран (и упал)")
        assertEquals(1, remote.calls, "SERVER всё равно убран")
    }

    @Test
    fun `на пустом реестре уборка ничего не закрывает и не падает`() {
        val empty =
            object : KaraokeConnection(
                url = "jdbc:postgresql://fake:5432/karaoke",
                username = "u",
                password = "p",
                name = "EMPTY",
            ) {}

        assertEquals(0, empty.reapIdleConnections(900_000), "на пустом реестре закрывать нечего")
        assertEquals(0, empty.openConnectionCount(), "реестр пуст")
    }

    @Test
    fun `порог простоя меньше периода проверки не задаётся по умолчанию`() {
        val reaper =
            LocalConnectionReaper(
                local = FakeConnection(toClose = 0),
                remote = FakeConnection(toClose = 0),
                intervalMs = 300_000,
                idleMs = 900_000,
            )
        val idleField = LocalConnectionReaper::class.java.getDeclaredField("idleMs")
        idleField.isAccessible = true
        val idleMs = idleField.get(reaper) as Long

        assertTrue(idleMs > 300_000, "порог простоя ($idleMs) должен быть заметно больше периода проверки")
    }
}
