package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.tunnel.ProdTunnelConfig
import com.svoemesto.karaokeapp.tunnel.ProdTunnelSupervisor
import com.svoemesto.karaokeapp.tunnel.WireGuardProdTunnel
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Логгер подсистемы туннеля (канал к проду).
 */
private val prodTunnelBootLog = LoggerFactory.getLogger("infra.prod.tunnel")

/**
 * Служба постоянного туннеля к прод-хосту.
 *
 * Туннелем владеет приложение (FR-027): служба поднимает туннель на старте
 * (FR-002) и держит его супервизором. Прод-каналы (БД, MinIO, HTTP-каналы
 * админки) используют постоянный внутренний адрес `10.77.0.2` — он не зависит
 * от публичного адреса админской машины (FR-003, FR-031).
 *
 * Если туннель выключен (`PROD_TUNNEL_ENABLED=false`) или не поднялся —
 * приложение продолжает работать по прежнему (публичному) пути, если тот
 * доступен; факт отказа фиксируется событием `TUNNEL-002`/`TUNNEL-007`.
 *
 * @see com.svoemesto.karaokeapp.tunnel.WireGuardProdTunnel
 * @see knowledge/product/features/prod-tunnel.md
 */
@Service
class ProdTunnelService {
    /** Конфигурация туннеля (из окружения). */
    val config: ProdTunnelConfig = ProdTunnelConfig.fromEnv()

    /** Супервизор туннеля (null — туннель выключен). */
    val supervisor: ProdTunnelSupervisor? =
        if (config.enabled) {
            ProdTunnelSupervisor(WireGuardProdTunnel(config))
        } else {
            null
        }

    init {
        if (supervisor == null) {
            prodTunnelBootLog.info(
                "event=tunnel.disabled code=TUNNEL-005 mode=public_path — туннель к проду выключен конфигурацией",
            )
        } else {
            val active = supervisor
            val up = active?.start() ?: false
            prodTunnelBootLog.info(
                "event=tunnel.boot code=TUNNEL-001 up={} interface={} peer={}",
                up,
                config.interfaceName,
                config.endpoint,
            )
        }
        ProdTunnelHolder.supervisor = supervisor
        ProdTunnelHolder.config = config
    }

    /**
     * Останавливает туннель (при остановке приложения).
     */
    fun shutdown() {
        supervisor?.stop()
        ProdTunnelHolder.supervisor = null
    }
}

/**
 * Доступ к супервизору туннеля из кода, который не получает зависимости через
 * Spring (проверки мониторинга — объекты-синглтоны, см. `MonitorRegistry`).
 */
object ProdTunnelHolder {
    /** Супервизор туннеля (null — туннель выключен или приложение ещё не стартовало). */
    @Volatile
    var supervisor: ProdTunnelSupervisor? = null

    /** Конфигурация туннеля. */
    @Volatile
    var config: ProdTunnelConfig = ProdTunnelConfig()
}
