package com.svoemesto.karaokeapp.tunnel

import java.util.Base64

/**
 * Конфигурация постоянного туннеля админ-машина→прод (WireGuard).
 *
 * Спека 733 (FR-001, FR-003, FR-027, FR-030, FR-031). Источник значений —
 * окружение контейнера `karaoke-app`; секреты (приватный ключ, сертификаты) —
 * в смонтированном каталоге, в репозитории их нет (FR-013).
 *
 * **Адресация** (см. `specs/733-prod-tunnel-mtls/plan.md`, DD-1):
 *  - [localAddress] — адрес админской стороны туннеля (`10.77.0.1/24`). Именно его
 *    прод видит как адрес источника — в allow-list прод-хоста ровно одна строка
 *    `allow 10.77.0.1;` (FR-005).
 *  - [prodHost] — постоянный внутренний адрес прод-хоста (`10.77.0.2`), без префикса:
 *    его используют и приложение, и оператор с хоста (DBeaver) — один и тот же
 *    адрес (FR-031, FR-032).
 *  - [peerAddress] — адрес пира «prod» в формате CIDR (идёт в `wg set … allowed-ips`).
 *
 * @see ProdTunnel — жизненный цикл туннеля.
 * @see knowledge/product/features/prod-tunnel.md
 */
data class ProdTunnelConfig(
    val enabled: Boolean = false,
    val interfaceName: String = "wg0",
    val privateKeyPath: String = "/run/karaoke/tunnel/tunnel.key",
    val secretDir: String = "/run/karaoke/tunnel",
    val localAddress: String = "10.77.0.1/24",
    val peerAddress: String = "10.77.0.2/24",
    val peerPublicKey: String = "",
    val endpoint: String = "188.127.240.124:51820",
    val keepaliveSeconds: Int = 25,
    val handshakeTimeoutSeconds: Int = 180,
    val checkIntervalSeconds: Int = 30,
    val prodHost: String = "10.77.0.2",
    val dnsOverrideHost: String = "sm-karaoke.ru",
    val hostsFile: String = "/etc/hosts",
    val fallbackEnabled: Boolean = true,
    val maxChannelRetries: Int = 3,
) {
    /**
     * Подсети, с которыми подсеть туннеля MUST NOT пересекаться (FR-007).
     *
     * Значения — из измерений 2026-11-09: `tun0` (OpenVPN к pfSense) `172.28.3.0/24`,
     * Docker-сети `172.18.0.0/16` (karaokenet), `172.21.0.0/16`, `172.23.0.0/16`,
     * VPN-маршруты `10.128.0.0/16`, LAN админской машины `10.0.1.0/24`.
     */
    val forbiddenSubnets: List<String> =
        listOf(
            "172.28.3.0/24",
            "172.18.0.0/16",
            "172.21.0.0/16",
            "172.23.0.0/16",
            "10.128.0.0/16",
            "10.0.1.0/24",
        )

    companion object {
        /**
         * Читает конфигурацию из окружения.
         *
         * @param env функция доступа к переменной (в тестах — подставная).
         */
        fun fromEnv(env: (String) -> String? = System::getenv): ProdTunnelConfig {
            fun str(name: String, default: String): String = env(name)?.trim()?.takeIf { it.isNotEmpty() } ?: default

            fun bool(name: String, default: Boolean): Boolean =
                env(name)?.trim()?.lowercase()?.let {
                    when (it) {
                        "1", "true", "yes", "on" -> true
                        "0", "false", "no", "off" -> false
                        else -> default
                    }
                } ?: default

            fun int(name: String, default: Int): Int = env(name)?.trim()?.toIntOrNull() ?: default

            val secretDir = str("PROD_TUNNEL_SECRET_DIR", "/run/karaoke/tunnel")
            return ProdTunnelConfig(
                enabled = bool("PROD_TUNNEL_ENABLED", false),
                interfaceName = str("PROD_TUNNEL_INTERFACE", "wg0"),
                privateKeyPath = str("PROD_TUNNEL_PRIVATE_KEY_PATH", "$secretDir/tunnel.key"),
                secretDir = secretDir,
                localAddress = str("PROD_TUNNEL_LOCAL_ADDRESS", "10.77.0.1/24"),
                peerAddress = str("PROD_TUNNEL_PEER_ADDRESS", "10.77.0.2/24"),
                peerPublicKey = str("PROD_TUNNEL_PEER_PUBLIC_KEY", ""),
                endpoint = str("PROD_TUNNEL_ENDPOINT", "188.127.240.124:51820"),
                keepaliveSeconds = int("PROD_TUNNEL_KEEPALIVE", 25),
                handshakeTimeoutSeconds = int("PROD_TUNNEL_HANDSHAKE_TIMEOUT", 180),
                checkIntervalSeconds = int("PROD_TUNNEL_CHECK_INTERVAL", 30),
                prodHost = str("PROD_TUNNEL_PROD_HOST", "10.77.0.2"),
                dnsOverrideHost = str("PROD_TUNNEL_DNS_OVERRIDE_HOST", "sm-karaoke.ru"),
                hostsFile = str("PROD_TUNNEL_HOSTS_FILE", "/etc/hosts"),
                fallbackEnabled = bool("PROD_TUNNEL_FALLBACK_ENABLED", true),
                maxChannelRetries = int("PROD_TUNNEL_MAX_CHANNEL_RETRIES", 3),
            )
        }

        /**
         * Проверяет конфигурацию.
         *
         * @return список нарушений (пустой — конфигурация корректна). Каждое
         *   нарушение попадает в лог как `TUNNEL-006` (`tunnel.config.invalid`).
         */
        fun validate(config: ProdTunnelConfig): List<String> {
            val problems = mutableListOf<String>()
            if (!config.enabled) return problems

            if (config.interfaceName.isBlank() || !config.interfaceName.matches(Regex("[a-zA-Z0-9]{1,15}"))) {
                problems += "rule=interface_name value=${config.interfaceName}"
            }
            if (config.privateKeyPath.isBlank()) {
                problems += "rule=private_key_path value="
            }
            if (!isBase64Key(config.peerPublicKey)) {
                problems += "rule=peer_public_key value=${config.peerPublicKey.take(8)}"
            }
            if (!isHostPort(config.endpoint)) {
                problems += "rule=endpoint value=${config.endpoint}"
            }
            val local = parseCidr(config.localAddress)
            val peer = parseCidr(config.peerAddress)
            if (local == null) {
                problems += "rule=local_address value=${config.localAddress}"
            }
            if (peer == null) {
                problems += "rule=peer_address value=${config.peerAddress}"
            }
            if (local != null && peer != null) {
                if (local.address == peer.address) {
                    problems += "rule=addresses_equal value=${config.localAddress}"
                }
                if (local.prefix != peer.prefix) {
                    problems += "rule=prefix_mismatch local=${config.localAddress} peer=${config.peerAddress}"
                }
            }
            if (local != null) {
                val tunnelCidr = "${local.address}/${local.prefix}"
                config.forbiddenSubnets.forEach { forbidden ->
                    val other = parseCidr(forbidden)
                    if (other != null && overlaps(local, other)) {
                        problems += "rule=subnet_overlap tunnel=$tunnelCidr forbidden=$forbidden"
                    }
                }
            }
            if (config.handshakeTimeoutSeconds < config.keepaliveSeconds * 3) {
                problems +=
                    "rule=handshake_timeout timeout=${config.handshakeTimeoutSeconds} keepalive=${config.keepaliveSeconds}"
            }
            if (config.checkIntervalSeconds < 5) {
                problems += "rule=check_interval value=${config.checkIntervalSeconds}"
            }
            if (config.keepaliveSeconds !in 5..300) {
                problems += "rule=keepalive value=${config.keepaliveSeconds}"
            }
            if (config.maxChannelRetries < 0) {
                problems += "rule=max_channel_retries value=${config.maxChannelRetries}"
            }
            return problems
        }

        /** Публичный ключ WireGuard — base64, 32 байта (44 символа с `=`). */
        fun isBase64Key(value: String): Boolean {
            val trimmed = value.trim()
            if (trimmed.length != 44 || !trimmed.endsWith("=")) return false
            return runCatching {
                Base64
                    .getDecoder()
                    .decode(trimmed)
            }.map {
                it.size == 32
            }.getOrDefault(false)
        }

        /** `host:port`, порт 1..65535. */
        fun isHostPort(value: String): Boolean {
            val parts = value.trim().split(":")
            if (parts.size != 2) return false
            val port = parts[1].toIntOrNull() ?: return false
            return parts[0].isNotBlank() && port in 1..65535
        }

        /** Разбор `a.b.c.d/prefix`; `null` — не адрес. */
        fun parseCidr(value: String): Cidr? {
            val parts = value.trim().split("/")
            if (parts.size != 2) return null
            val octets = parts[0].split(".")
            if (octets.size != 4) return null
            val numbers = octets.map { it.toIntOrNull() ?: return null }
            if (numbers.any { it !in 0..255 }) return null
            val prefix = parts[1].toIntOrNull() ?: return null
            if (prefix !in 0..32) return null
            val address = numbers.fold(0L) { acc, n -> (acc shl 8) or n.toLong() }
            return Cidr(address, prefix, parts[0])
        }

        /**
         * Пересекаются ли две подсети.
         *
         * Сети пересекаются, если адрес сети одной из них входит в другую:
         * сеть `/16` и адрес `172.18.5.1/24` пересекаются, хотя их
         * «сетевые адреса» вычисленные по своим маскам различаются.
         */
        fun overlaps(
            a: Cidr,
            b: Cidr,
        ): Boolean {
            val maskA = mask(a.prefix)
            val maskB = mask(b.prefix)
            val netA = a.address and maskA
            val netB = b.address and maskB
            return (netA and maskB) == netB || (netB and maskA) == netA
        }

        /** Маска подсети по префиксу. */
        private fun mask(prefix: Int): Long = if (prefix == 0) 0L else (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
    }
}

/** Разобранная подсеть IPv4. */
data class Cidr(
    val address: Long,
    val prefix: Int,
    val literal: String,
)
