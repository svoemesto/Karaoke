package com.svoemesto.karaokeapp.tunnel

import java.time.Instant

/**
 * Состояние туннеля.
 *
 * @property up поднят ли туннель (интерфейс есть, рукопожатие было).
 * @property handshakeAgeSeconds возраст последнего рукопожатия в секундах;
 *   `null` — рукопожатий ещё не было.
 * @property reason причина состояния `down` (см. `contracts/event-codes.md`).
 * @property lastCheckedAt момент последней проверки.
 * @property restarts счётчик перезапусков туннеля за жизнь процесса.
 */
data class TunnelState(
    val up: Boolean,
    val handshakeAgeSeconds: Long? = null,
    val reason: String? = null,
    val lastCheckedAt: Instant = Instant.now(),
    val restarts: Int = 0,
) {
    /** Туннель поднят, но рукопожатие старше порога — «устаревший» туннель. */
    fun isStale(timeoutSeconds: Long): Boolean {
        val age = handshakeAgeSeconds ?: return up
        return age > timeoutSeconds
    }
}

/**
 * Жизненный цикл постоянного туннеля к прод-хосту.
 *
 * Туннелем владеет приложение (FR-027): оно само поднимает интерфейс, следит за
 * возрастом рукопожатия и восстанавливает туннель после обрыва (FR-002).
 *
 * @see WireGuardProdTunnel — реализация поверх WireGuard.
 * @see ProdTunnelSupervisor — цикл опроса и восстановления.
 */
interface ProdTunnel {
    /** Конфигурация туннеля. */
    val config: ProdTunnelConfig

    /**
     * Гарантирует поднятие туннеля (идемпотентно).
     *
     * @return состояние после попытки.
     */
    fun ensureUp(): TunnelState

    /** Текущее состояние без изменения туннеля. */
    fun status(): TunnelState

    /**
     * Восстанавливает туннель: снимает интерфейс (если есть) и поднимает заново.
     *
     * @return состояние после восстановления.
     */
    fun restore(): TunnelState

    /** Снимает туннель (используется при остановке приложения). */
    fun shutdown()
}
