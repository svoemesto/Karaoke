package com.svoemesto.karaokeapp.monitor.checks

import com.svoemesto.karaokeapp.monitor.MonitorAlert
import com.svoemesto.karaokeapp.monitor.MonitorCheck
import com.svoemesto.karaokeapp.monitor.MonitorContext
import com.svoemesto.karaokeapp.monitor.MonitorSeverity
import com.svoemesto.karaokeapp.services.ProdTunnelHolder

/**
 * Проверка состояния постоянного туннеля к прод-хосту.
 *
 * Спека 733 (FR-008, FR-028, SC-010): состояние туннеля MUST быть видно в
 * мониторинге **отдельным признаком**, а не сливаться с общим «прод недоступен».
 * Именно этот признак позволяет отличить «упал туннель» от «упал прод» и от
 * «оборвался WAN админской машины» (runbook § 1d).
 *
 * Проверка ничего не чинит и не ходит в сеть — она читает состояние,
 * поддерживаемое приложением (владельцем туннеля).
 *
 * @see com.svoemesto.karaokeapp.tunnel.ProdTunnelSupervisor
 * @see knowledge/guidelines/runbooks/log-correlation.md — реестр кодов `TUNNEL-*`.
 */
object ProdTunnelCheck : MonitorCheck {
    /** Ключ алерта — стабильный между прогонами (по нему работает «прочитано»). */
    const val KEY = "prod.tunnel"

    override fun run(ctx: MonitorContext): List<MonitorAlert> {
        val supervisor = ProdTunnelHolder.supervisor
        val config = ProdTunnelHolder.config

        // Туннель выключен конфигурацией — это штатный режим (публичный путь),
        // а не авария: алерт не поднимаем, факт остаётся в логе (TUNNEL-005).
        if (supervisor == null) return emptyList()

        val state = supervisor.state
        if (state.up && !state.isStale(config.handshakeTimeoutSeconds.toLong())) {
            return emptyList()
        }

        val stale = state.up
        val fallbackPublic = config.fallbackEnabled
        return listOf(
            MonitorAlert(
                key = KEY,
                severity = if (stale || !fallbackPublic) MonitorSeverity.WARNING else MonitorSeverity.CRITICAL,
                title =
                    if (stale) {
                        "Туннель к проду не отвечает (устаревшее рукопожатие)"
                    } else {
                        "Туннель к проду не поднят"
                    },
                body =
                    if (stale) {
                        "Последнее рукопожатие туннеля старше ${config.handshakeTimeoutSeconds} с — " +
                            "канал к проду (БД, MinIO, синхронизация) может не работать. " +
                            "Обычно это обрыв WAN на админской машине (шлюз 10.0.1.1) — см. runbook § 1d."
                    } else {
                        "Туннель к проду не поднят (причина: ${state.reason ?: "неизвестна"}). " +
                            if (fallbackPublic) {
                                "Каналы переключены на публичный путь — при смене IP провайдера они перестанут работать."
                            } else {
                                "Каналы отключены: работа приложения ограничена локальной БД."
                            }
                    },
                category = "Прод-канал",
                detail =
                    "interface=${config.interfaceName} reason=${state.reason ?: "-"} " +
                        "handshakeAgeSec=${state.handshakeAgeSeconds ?: -1} restarts=${state.restarts}",
                recommendations =
                    "Проверить туннель: docker exec karaoke-app wg show ${config.interfaceName}; " +
                        "если рукопожатия нет — проверить WAN (ping 10.0.1.1, ping 1.1.1.1).",
            ),
        )
    }
}
