package com.svoemesto.karaokeapp.tunnel

import java.io.File
import java.time.Instant

/**
 * Реализация туннеля поверх WireGuard.
 *
 * Интерфейс `wg0` живёт **в сетевом пространстве контейнера `karaoke-app`**:
 * приложение — владелец жизненного цикла (FR-027). Команды (`ip`, `wg`)
 * выполняются внутри контейнера, поэтому приложению нужны `NET_ADMIN` и
 * `/dev/net/tun` (см. `deploy/docker-compose-app.yml`).
 *
 * Команды (без `wg-quick` — меньше зависимостей и предсказуемее):
 * ```
 * ip link add wg0 type wireguard
 * ip address add 10.77.0.1/24 dev wg0
 * wg set wg0 private-key <file> peer <pubkey> endpoint <host:port> \
 *        allowed-ips 10.77.0.2/32 persistent-keepalive 25
 * ip link set wg0 up
 * ```
 *
 * Приватный ключ **не читается приложением**: его передаёт `wg` по пути файла
 * (FR-030), в лог не попадает ни содержимое ключа, ни конфигурация.
 *
 * @see ProdTunnel
 * @see knowledge/product/features/prod-tunnel.md
 */
class WireGuardProdTunnel(
    override val config: ProdTunnelConfig,
    private val runner: TunnelCommandRunner = ProcessTunnelCommandRunner(),
    private val hostsOverride: HostsOverride = HostsOverride(),
) : ProdTunnel {
    private var restarts = 0

    override fun ensureUp(): TunnelState {
        if (!config.enabled) {
            return TunnelState(up = false, reason = REASON_DISABLED, restarts = restarts)
        }

        val problems = ProdTunnelConfig.validate(config)
        if (problems.isNotEmpty()) {
            return TunnelState(up = false, reason = REASON_CONFIG_INVALID, restarts = restarts)
        }

        if (!File(config.privateKeyPath).isFile) {
            return TunnelState(up = false, reason = REASON_NO_KEY, restarts = restarts)
        }

        if (interfaceExists()) {
            val current = readState()
            if (current.up && !current.isStale(config.handshakeTimeoutSeconds.toLong())) {
                applyHostsOverride(true)
                return current
            }
            // Интерфейс есть, но рукопожатия нет (или оно устарело) — пересоздаём.
            return bringUp(force = true)
        }

        return bringUp(force = false)
    }

    override fun status(): TunnelState = readState()

    override fun restore(): TunnelState {
        restarts++
        deleteInterface()
        applyHostsOverride(false)
        return ensureUp()
    }

    override fun shutdown() {
        deleteInterface()
        applyHostsOverride(false)
    }

    private fun bringUp(force: Boolean): TunnelState {
        if (force) {
            deleteInterface()
        }
        val add =
            runner.run(
                "ip",
                "link",
                "add",
                config.interfaceName,
                "type",
                "wireguard",
            )
        if (!add.ok) {
            // `ip link add` может вернуть «File exists» — тогда считаем, что интерфейс уже есть.
            if (!interfaceExists()) {
                return TunnelState(
                    up = false,
                    reason = classifyFailure(add.diagnostic()),
                    lastCheckedAt = Instant.now(),
                    restarts = restarts,
                )
            }
        }

        val address =
            runner.run(
                "ip",
                "address",
                "add",
                config.localAddress,
                "dev",
                config.interfaceName,
            )
        if (!address.ok && !address.diagnostic().contains("File exists")) {
            deleteInterface()
            return TunnelState(
                up = false,
                reason = classifyFailure(address.diagnostic()),
                lastCheckedAt = Instant.now(),
                restarts = restarts,
            )
        }

        val peer =
            runner.run(
                "wg",
                "set",
                config.interfaceName,
                "private-key",
                config.privateKeyPath,
                "peer",
                config.peerPublicKey,
                "endpoint",
                config.endpoint,
                "allowed-ips",
                peerAllowedIps(),
                "persistent-keepalive",
                config.keepaliveSeconds.toString(),
            )
        if (!peer.ok) {
            deleteInterface()
            return TunnelState(
                up = false,
                reason = classifyFailure(peer.diagnostic()),
                lastCheckedAt = Instant.now(),
                restarts = restarts,
            )
        }

        val up = runner.run("ip", "link", "set", config.interfaceName, "up")
        if (!up.ok) {
            deleteInterface()
            return TunnelState(
                up = false,
                reason = classifyFailure(up.diagnostic()),
                lastCheckedAt = Instant.now(),
                restarts = restarts,
            )
        }

        applyHostsOverride(true)
        return readState()
    }

    /**
     * `AllowedIPs` для пира «prod».
     *
     * Всегда `/32`: туннель принимает от пира ровно один адрес — постоянный
     * внутренний адрес прод-стороны (FR-005: в allow-list прода одна строка).
     */
    private fun peerAllowedIps(): String {
        val parsed = ProdTunnelConfig.parseCidr(config.peerAddress)
        val address = parsed?.literal ?: config.peerAddress.substringBefore("/")
        return "$address/32"
    }

    private fun interfaceExists(): Boolean =
        runner.run("ip", "link", "show", config.interfaceName).ok

    private fun deleteInterface() {
        runner.run("ip", "link", "delete", config.interfaceName)
    }

    /**
     * Читает состояние интерфейса: наличие и возраст рукопожатия.
     *
     * `wg show <iface> latest-handshakes` печатает строки
     * `<publicKey>\t<unix-epoch>`; нулевая эпоха — рукопожатия не было.
     */
    private fun readState(): TunnelState {
        val now = Instant.now()
        if (!interfaceExists()) {
            return TunnelState(up = false, reason = REASON_INTERFACE_MISSING, lastCheckedAt = now, restarts = restarts)
        }
        val handshakes = runner.run("wg", "show", config.interfaceName, "latest-handshakes")
        if (!handshakes.ok) {
            return TunnelState(
                up = false,
                reason = classifyFailure(handshakes.diagnostic()),
                lastCheckedAt = now,
                restarts = restarts,
            )
        }
        val epoch =
            handshakes.stdout
                .lineSequence()
                .mapNotNull { line ->
                    line
                        .trim()
                        .split("\t")
                        .lastOrNull()
                        ?.trim()
                        ?.toLongOrNull()
                }.filter { it > 0L }
                .maxOrNull()
        if (epoch == null) {
            // Интерфейс есть, рукопожатия ещё не было — туннель не поднят.
            return TunnelState(up = false, reason = REASON_NO_HANDSHAKE, lastCheckedAt = now, restarts = restarts)
        }
        val age = (now.epochSecond - epoch).coerceAtLeast(0L)
        return TunnelState(up = true, handshakeAgeSeconds = age, lastCheckedAt = now, restarts = restarts)
    }

    /**
     * Включает/выключает переопределение резолвинга имени прод-сайта.
     *
     * Пока туннель поднят — имя прод-сайта резолвится в постоянный внутренний
     * адрес (FR-021/FR-022: имя хоста и сертификат сервера остаются прежними,
     * меняется только транспорт). Туннель не поднят — переопределение снимается,
     * приложение возвращается на публичный путь (откат, FR-017).
     */
    private fun applyHostsOverride(enabled: Boolean) {
        runCatching {
            if (enabled) {
                hostsOverride.enable(config.prodHost, config.dnsOverrideHost, config.hostsFile)
            } else {
                hostsOverride.disable(config.dnsOverrideHost, config.hostsFile)
            }
        }
    }

    /**
     * Переводит текст ошибки команды в причину из реестра событий.
     *
     * Тексты ошибок `ip`/`wg` различаются между версиями, поэтому ищем
     * устойчивые маркеры.
     */
    private fun classifyFailure(diagnostic: String): String {
        val text = diagnostic.lowercase()
        return when {
            text.contains("operation not permitted") || text.contains("permission denied") -> REASON_NO_PERMISSION
            text.contains("no such file") || text.contains("cannot open") -> REASON_NO_KEY
            text.contains("network is unreachable") ||
                text.contains("no route to host") ||
                text.contains("timed out") ||
                text.contains("timeout") -> REASON_UDP_BLOCKED
            text.contains("address already in use") || text.contains("file exists") -> REASON_INTERFACE_EXISTS
            else -> REASON_COMMAND_FAILED
        }
    }

    companion object {
        const val REASON_DISABLED = "disabled"
        const val REASON_CONFIG_INVALID = "config_invalid"
        const val REASON_NO_KEY = "no_key"
        const val REASON_NO_PERMISSION = "no_permission"
        const val REASON_NO_HANDSHAKE = "no_handshake"
        const val REASON_INTERFACE_MISSING = "interface_missing"
        const val REASON_UDP_BLOCKED = "udp_blocked"
        const val REASON_COMMAND_FAILED = "command_failed"
        const val REASON_INTERFACE_EXISTS = "interface_exists"
    }
}
