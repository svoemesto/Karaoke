package com.svoemesto.karaokeweb.util

import jakarta.servlet.http.HttpServletRequest

// karaoke-web слушает порт, опубликованный Docker (docker-compose-web.yml) — голый TCP-peer
// (request.remoteHost) всегда равен адресу docker-шлюза (типично 172.18.0.1), а не реальному
// IP клиента, даже когда прод-nginx (80to8897) уже правильно проставляет X-Real-IP/
// X-Forwarded-For. Фолбэк на remoteHost остаётся только для случая отсутствия обоих
// заголовков (например, локальный dev без nginx перед приложением).

/**
 * Singleton-объект Client Ip Resolver.
 *
 * @see AGENTS.md
 */
object ClientIpResolver {
    fun resolve(request: HttpServletRequest): String {
        request
            .getHeader("X-Forwarded-For")
            ?.takeIf { it.isNotBlank() }
            ?.let { return it.split(",").first().trim() }
        request
            .getHeader("X-Real-IP")
            ?.takeIf { it.isNotBlank() }
            ?.let { return it.trim() }
        return request.remoteHost
    }
}
