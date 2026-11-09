package com.svoemesto.karaokeapp.tunnel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Спека 733 (FR-002, FR-027): жизненный цикл туннеля.
 *
 * Команды `ip`/`wg` подменены — тест проверяет логику приложения (подъём,
 * идемпотентность, детектор устаревшего рукопожатия, классификацию причин
 * отказа) без root и без сети.
 */
class WireGuardProdTunnelTest {
    /** Подставной исполнитель команд: отвечает по заготовленным правилам. */
    private class FakeRunner(
        var interfaceExists: Boolean = false,
        var handshakeEpoch: Long = 0L,
        var failOn: String? = null,
        var failMessage: String = "",
    ) : TunnelCommandRunner {
        val calls = mutableListOf<String>()

        override fun run(vararg args: String): TunnelCommandResult {
            val command = args.joinToString(" ")
            calls += command
            val failMarker = failOn
            if (failMarker != null && command.contains(failMarker)) {
                return TunnelCommandResult(1, stderr = failMessage)
            }
            return when {
                command.startsWith("ip link show") -> TunnelCommandResult(if (interfaceExists) 0 else 1)
                command.startsWith("wg show") ->
                    if (handshakeEpoch > 0L) {
                        TunnelCommandResult(0, stdout = "pubkey\t${handshakeEpoch}\n")
                    } else {
                        TunnelCommandResult(0, stdout = "pubkey\t0\n")
                    }

                else -> TunnelCommandResult(0)
            }
        }
    }

    private fun config(
        enabled: Boolean = true,
        privateKeyExists: Boolean = true,
    ): ProdTunnelConfig {
        val keyFile = java.io.File.createTempFile("prod-tunnel-test", ".key")
        keyFile.deleteOnExit()
        if (!privateKeyExists) keyFile.delete()
        return ProdTunnelConfig(
            enabled = enabled,
            privateKeyPath = keyFile.absolutePath,
            peerPublicKey = "a2FyYW9rZS1wcm9kLXR1bm5lbC10ZXN0LWtleS0zMmI=",
        )
    }

    @Test
    fun `выключенный туннель не поднимается и сообщает причину disabled`() {
        val runner = FakeRunner()
        val tunnel = WireGuardProdTunnel(config(enabled = false), runner)
        val state = tunnel.ensureUp()
        assertFalse(state.up)
        assertEquals(WireGuardProdTunnel.REASON_DISABLED, state.reason)
        assertTrue(runner.calls.isEmpty(), "выключенный туннель не должен запускать команды: ${runner.calls}")
    }

    @Test
    fun `подъём туннеля создаёт интерфейс и настраивает пира`() {
        val runner = FakeRunner()
        val tunnel = WireGuardProdTunnel(config(), runner)
        tunnel.ensureUp()
        assertTrue(
            runner.calls.any { it == "ip link add wg0 type wireguard" },
            "интерфейс должен создаваться: ${runner.calls}",
        )
        assertTrue(
            runner.calls.any { it.startsWith("ip address add 10.77.0.1/24 dev wg0") },
            "адрес должен назначаться: ${runner.calls}",
        )
        assertTrue(
            runner.calls.any { it.startsWith("wg set wg0 private-key") && it.contains("allowed-ips 10.77.0.2/32") },
            "пир должен настраиваться: ${runner.calls}",
        )
        assertTrue(runner.calls.any { it == "ip link set wg0 up" }, "интерфейс должен подниматься: ${runner.calls}")
    }

    @Test
    fun `повторный вызов не пересоздаёт живой туннель`() {
        val runner = FakeRunner(interfaceExists = true, handshakeEpoch = Instant.now().epochSecond)
        val tunnel = WireGuardProdTunnel(config(), runner)
        val state = tunnel.ensureUp()
        assertTrue(state.up)
        assertFalse(
            runner.calls.any { it.startsWith("ip link add") },
            "живой туннель не пересоздаётся: ${runner.calls}",
        )
    }

    @Test
    fun `устаревшее рукопожатие приводит к пересозданию туннеля`() {
        val stale = Instant.now().epochSecond - 3600
        val runner = FakeRunner(interfaceExists = true, handshakeEpoch = stale)
        val tunnel = WireGuardProdTunnel(config(), runner)
        tunnel.ensureUp()
        assertTrue(
            runner.calls.any { it.startsWith("ip link delete") || it.startsWith("ip link del") },
            "устаревшее рукопожатие должно приводить к пересозданию: ${runner.calls}",
        )
        assertTrue(runner.calls.any { it.startsWith("ip link add") }, "интерфейс должен быть создан заново: ${runner.calls}")
    }

    @Test
    fun `отсутствие ключа распознаётся как no_key`() {
        val runner = FakeRunner()
        val tunnel = WireGuardProdTunnel(config(privateKeyExists = false), runner)
        val state = tunnel.ensureUp()
        assertFalse(state.up)
        assertEquals(WireGuardProdTunnel.REASON_NO_KEY, state.reason)
    }

    @Test
    fun `отказ из-за прав распознаётся как no_permission`() {
        val runner = FakeRunner(failOn = "ip link add", failMessage = "RTNETLINK answers: Operation not permitted")
        val tunnel = WireGuardProdTunnel(config(), runner)
        val state = tunnel.ensureUp()
        assertFalse(state.up)
        assertEquals(WireGuardProdTunnel.REASON_NO_PERMISSION, state.reason)
    }

    @Test
    fun `отсутствие рукопожатия означает, что туннель не поднят`() {
        val runner = FakeRunner(interfaceExists = true, handshakeEpoch = 0L)
        val tunnel = WireGuardProdTunnel(config(), runner)
        val state = tunnel.status()
        assertFalse(state.up)
        assertEquals(WireGuardProdTunnel.REASON_NO_HANDSHAKE, state.reason)
    }

    @Test
    fun `восстановление снимает интерфейс и поднимает заново`() {
        val runner = FakeRunner(interfaceExists = true, handshakeEpoch = 0L)
        val tunnel = WireGuardProdTunnel(config(), runner)
        val state = tunnel.restore()
        assertTrue(
            runner.calls.any { it.startsWith("ip link delete") || it.startsWith("ip link del") },
            "восстановление должно снимать интерфейс: ${runner.calls}",
        )
        assertEquals(1, state.restarts)
    }
}
