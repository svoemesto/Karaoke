package com.svoemesto.karaokeapp.tunnel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Спека 733 (FR-001, FR-003, FR-013, FR-030): конфигурация туннеля.
 *
 * Проверяются дефолты, чтение окружения и валидация — конфигурация, которая
 * прошла валидацию, обязана давать туннель с постоянными адресами и не
 * пересекаться с известными сетями машины.
 */
class ProdTunnelConfigTest {
    /** Публичный ключ WireGuard (base64, 32 байта) — тестовый, не рабочий. */
    private val validKey = "a2FyYW9rZS1wcm9kLXR1bm5lbC10ZXN0LWtleS0zMmI="

    @Test
    fun `дефолты соответствуют плану спеки 733`() {
        val config = ProdTunnelConfig.fromEnv { null }
        assertEquals(false, config.enabled)
        assertEquals("wg0", config.interfaceName)
        assertEquals("10.77.0.1/24", config.localAddress)
        assertEquals("10.77.0.2/24", config.peerAddress)
        assertEquals("10.77.0.2", config.prodHost)
        assertEquals("sm-karaoke.ru", config.dnsOverrideHost)
        assertEquals(25, config.keepaliveSeconds)
        assertEquals(180, config.handshakeTimeoutSeconds)
        assertEquals(30, config.checkIntervalSeconds)
        assertEquals(3, config.maxChannelRetries)
    }

    @Test
    fun `конфигурация читается из окружения`() {
        val env =
            mapOf(
                "PROD_TUNNEL_ENABLED" to "true",
                "PROD_TUNNEL_INTERFACE" to "wg1",
                "PROD_TUNNEL_PEER_PUBLIC_KEY" to validKey,
                "PROD_TUNNEL_ENDPOINT" to "188.127.240.124:51821",
                "PROD_TUNNEL_LOCAL_ADDRESS" to "10.78.0.1/24",
                "PROD_TUNNEL_PEER_ADDRESS" to "10.78.0.2/24",
                "PROD_TUNNEL_PROD_HOST" to "10.78.0.2",
                "PROD_TUNNEL_KEEPALIVE" to "30",
                "PROD_TUNNEL_MAX_CHANNEL_RETRIES" to "5",
            )
        val config = ProdTunnelConfig.fromEnv { env[it] }
        assertEquals(true, config.enabled)
        assertEquals("wg1", config.interfaceName)
        assertEquals("188.127.240.124:51821", config.endpoint)
        assertEquals("10.78.0.1/24", config.localAddress)
        assertEquals(30, config.keepaliveSeconds)
        assertEquals(5, config.maxChannelRetries)
    }

    @Test
    fun `выключенный туннель не имеет нарушений валидации`() {
        assertEquals(emptyList<String>(), ProdTunnelConfig.validate(ProdTunnelConfig.fromEnv { null }))
    }

    @Test
    fun `включённый туннель без ключа невалиден`() {
        val config = ProdTunnelConfig(enabled = true, peerPublicKey = "")
        val problems = ProdTunnelConfig.validate(config)
        assertTrue(problems.any { it.startsWith("rule=peer_public_key") }, "ожидалась проблема peer_public_key: $problems")
    }

    @Test
    fun `битый публичный ключ невалиден`() {
        val config = ProdTunnelConfig(enabled = true, peerPublicKey = "не-ключ")
        val problems = ProdTunnelConfig.validate(config)
        assertTrue(problems.any { it.startsWith("rule=peer_public_key") }, "ожидалась проблема peer_public_key: $problems")
    }

    @Test
    fun `неверный endpoint невалиден`() {
        val config = ProdTunnelConfig(enabled = true, peerPublicKey = validKey, endpoint = "188.127.240.124")
        val problems = ProdTunnelConfig.validate(config)
        assertTrue(problems.any { it.startsWith("rule=endpoint") }, "ожидалась проблема endpoint: $problems")
    }

    @Test
    fun `одинаковые адреса сторон невалидны`() {
        val config = ProdTunnelConfig(enabled = true, peerPublicKey = validKey, peerAddress = "10.77.0.1/24")
        val problems = ProdTunnelConfig.validate(config)
        assertTrue(problems.any { it.startsWith("rule=addresses_equal") }, "ожидалась проблема addresses_equal: $problems")
    }

    @Test
    fun `подсеть туннеля не должна пересекаться с известными сетями машины`() {
        // 172.18.0.0/16 — docker-сеть karaokenet; туннель не должен её пересекать.
        val config = ProdTunnelConfig(enabled = true, peerPublicKey = validKey, localAddress = "172.18.5.1/24")
        val problems = ProdTunnelConfig.validate(config)
        assertTrue(
            problems.any { it.startsWith("rule=subnet_overlap") },
            "ожидалась проблема subnet_overlap: $problems",
        )
    }

    @Test
    fun `таймаут рукопожатия должен быть не меньше трёх keepalive`() {
        val config =
            ProdTunnelConfig(
                enabled = true,
                peerPublicKey = validKey,
                keepaliveSeconds = 25,
                handshakeTimeoutSeconds = 60,
            )
        val problems = ProdTunnelConfig.validate(config)
        assertTrue(problems.any { it.startsWith("rule=handshake_timeout") }, "ожидалась проблема handshake_timeout: $problems")
    }

    @Test
    fun `корректная конфигурация проходит валидацию`() {
        val config = ProdTunnelConfig(enabled = true, peerPublicKey = validKey)
        assertEquals(emptyList<String>(), ProdTunnelConfig.validate(config))
    }
}
