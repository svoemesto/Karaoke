package com.svoemesto.karaokeapp.tunnel

import com.svoemesto.karaokeapp.AppLog
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Супервизор туннеля: держит туннель поднятым и восстанавливает его.
 *
 * Туннелем владеет приложение (FR-027). Супервизор:
 *  1. на старте поднимает туннель (FR-002) — и только после этого приложение
 *     работает с прод-каналами;
 *  2. раз в [ProdTunnelConfig.checkIntervalSeconds] проверяет состояние;
 *  3. если рукопожатие устарело (UDP-путь молча сломался) или интерфейс пропал —
 *     пересоздаёт туннель (крайний случай спеки 733);
 *  4. пишет события с кодами `TUNNEL-001…007` (FR-028, SC-010).
 *
 * @see WireGuardProdTunnel
 * @see knowledge/guidelines/runbooks/log-correlation.md — реестр кодов.
 */
class ProdTunnelSupervisor(
    private val tunnel: ProdTunnel,
    private val config: ProdTunnelConfig = tunnel.config,
) {
    private var executor: ScheduledExecutorService? = null
    private val started = AtomicBoolean(false)

    /** Последнее событие «туннель не поднят» — чтобы не повторять его каждый тик. */
    @Volatile
    private var lastDownReason: String? = null

    /** Текущее состояние туннеля (для проверки мониторинга). */
    @Volatile
    var state: TunnelState = TunnelState(up = false, reason = WireGuardProdTunnel.REASON_DISABLED)
        internal set

    /**
     * Поднимает туннель и запускает цикл наблюдения.
     *
     * @return `true`, если туннель поднят.
     */
    fun start(): Boolean {
        if (!started.compareAndSet(false, true)) return state.up
        if (!config.enabled) {
            state = TunnelState(up = false, reason = WireGuardProdTunnel.REASON_DISABLED)
            logEvent(
                level = Level.WARN,
                code = "TUNNEL-005",
                event = "tunnel.disabled",
                fields = mapOf("mode" to "public_path", "fallback" to "public"),
            )
            return false
        }

        val problems = ProdTunnelConfig.validate(config)
        if (problems.isNotEmpty()) {
            state = TunnelState(up = false, reason = WireGuardProdTunnel.REASON_CONFIG_INVALID)
            problems.forEach { problem ->
                logEvent(
                    level = Level.ERROR,
                    code = "TUNNEL-006",
                    event = "tunnel.config.invalid",
                    fields = mapOf("rule" to problem.substringBefore("=")),
                )
            }
            return false
        }

        val up = tunnel.ensureUp()
        state = up
        if (up.up) {
            logEvent(
                level = Level.WARN,
                code = "TUNNEL-001",
                event = "tunnel.up",
                fields =
                    mapOf(
                        "interface" to config.interfaceName,
                        "peer" to config.endpoint,
                        "handshakeAgeSec" to (up.handshakeAgeSeconds ?: 0L),
                    ),
            )
        } else {
            logDown(up.reason)
        }

        val service =
            Executors.newSingleThreadScheduledExecutor { runnable ->
                Thread(runnable, "prod-tunnel-supervisor").apply { isDaemon = true }
            }
        executor = service
        service.scheduleWithFixedDelay(
            { tick() },
            config.checkIntervalSeconds.toLong(),
            config.checkIntervalSeconds.toLong(),
            TimeUnit.SECONDS,
        )
        return up.up
    }

    /** Останавливает цикл наблюдения (при остановке приложения). */
    fun stop() {
        executor?.shutdownNow()
        executor = null
        started.set(false)
        runCatching { tunnel.shutdown() }
    }

    /**
     * Один тик наблюдения: проверка состояния и восстановление при необходимости.
     *
     * Открыт для тестирования (вызывается напрямую), поэтому не private.
     */
    fun tick() {
        val current = tunnel.status()
        state = current
        when {
            !current.up -> {
                logDown(current.reason)
                val restored = tunnel.restore()
                state = restored
                if (restored.up) {
                    logEvent(
                        level = Level.WARN,
                        code = "TUNNEL-004",
                        event = "tunnel.restored",
                        fields =
                            mapOf(
                                "interface" to config.interfaceName,
                                "restarts" to restored.restarts,
                                "handshakeAgeSec" to (restored.handshakeAgeSeconds ?: 0L),
                            ),
                    )
                }
            }

            current.isStale(config.handshakeTimeoutSeconds.toLong()) -> {
                logEvent(
                    level = Level.WARN,
                    code = "TUNNEL-003",
                    event = "tunnel.stale",
                    fields =
                        mapOf(
                            "interface" to config.interfaceName,
                            "handshakeAgeSec" to (current.handshakeAgeSeconds ?: -1L),
                            "timeoutSec" to config.handshakeTimeoutSeconds,
                        ),
                )
                val restored = tunnel.restore()
                state = restored
                if (restored.up) {
                    logEvent(
                        level = Level.WARN,
                        code = "TUNNEL-004",
                        event = "tunnel.restored",
                        fields =
                            mapOf(
                                "interface" to config.interfaceName,
                                "restarts" to restored.restarts,
                                "handshakeAgeSec" to (restored.handshakeAgeSeconds ?: 0L),
                            ),
                    )
                }
            }
        }
    }

    private fun logDown(reason: String?) {
        val effective = reason ?: WireGuardProdTunnel.REASON_COMMAND_FAILED
        if (lastDownReason == effective) return
        lastDownReason = effective
        logEvent(
            level = Level.WARN,
            code = "TUNNEL-002",
            event = "tunnel.down",
            fields = mapOf("interface" to config.interfaceName, "reason" to effective),
        )
        logEvent(
            level = Level.WARN,
            code = "TUNNEL-007",
            event = "tunnel.fallback",
            fields =
                mapOf(
                    "interface" to config.interfaceName,
                    "reason" to effective,
                    "fallback" to if (config.fallbackEnabled) "public" else "disabled",
                ),
        )
    }

    private fun logEvent(
        level: Level,
        code: String,
        event: String,
        fields: Map<String, Any?>,
    ) {
        val payload = fields + ("at" to Instant.now().toString())
        when (level) {
            Level.WARN -> AppLog.warn(CATEGORY, code, event, payload)
            Level.ERROR -> AppLog.error(CATEGORY, code, event, payload)
        }
    }

    private enum class Level {
        WARN,
        ERROR,
    }

    companion object {
        /** Категория логгера событий туннеля. */
        const val CATEGORY = "infra.prod.tunnel"

        /** Таймаут ожидания подъёма туннеля на старте приложения. */
        val STARTUP_TIMEOUT: Duration = Duration.ofSeconds(60)
    }
}
