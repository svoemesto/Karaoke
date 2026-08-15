package com.svoemesto.karaokeweb.services

import com.svoemesto.karaokeweb.util.ClientIpResolver
import jakarta.servlet.http.HttpServletRequest

/**
 * Access guard для debug endpoint'а `/api/public/debug/db` (FR-013, US6).
 *
 * Решает, разрешён ли запрос исходя из двух условий:
 *  1. **Master flag**: `KARAOKE_WEB_DEBUG_DB_ENABLED=true` (default false). Если false —
 *     endpoint отключён (даже с правильным IP будет 404).
 *  2. **IP allowlist**: `KARAOKE_WEB_DEBUG_DB_ALLOWED_IPS` — comma-separated список
 *     IPv4-адресов (например, `127.0.0.1,192.168.1.5`). Если пусто — даже при enabled=true
 *     endpoint отключён (нет IP = нет доступа).
 *
 * На текущий момент поддерживаются только точные IPv4 (без CIDR). CIDR-mask при
 * необходимости добавляется в будущем — для прод-конфигурации с VPN-сетью
 * `KARAOKE_WEB_DEBUG_DB_ALLOWED_IPS=10.8.0.0/24` нужен CIDR-парсер.
 *
 * @see archive/archive/docs/features/site-traffic-resilience.md (FR-013)
 * @see KaraokeProperties
 * @see DebugDbController
 */
object DebugDbAccessGuard {
    /**
     * Возвращает `true` если запрос разрешён.
     *
     * @param request HTTP-запрос (используется для извлечения client IP).
     * @return `true` если [DebugDbController] должен вернуть JSON, `false` если 404/403.
     */
    fun isAllowed(properties: KaraokeProperties, request: HttpServletRequest): Boolean {
        if (!properties.debugDbEnabled) return false

        val allowedIps = properties.debugDbAllowedIps
        if (allowedIps.isBlank()) return false

        val clientIp = ClientIpResolver.resolve(request)
        val allowed = allowedIps.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        return clientIp in allowed
    }
}
