package com.svoemesto.karaokeapp.controllers

import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity

/**
 * Формирует стандартный ответ 503 + Retry-After + errorCode stats.unavailable
 * для всех stats endpoint-ов. Используется в StatsController и
 * com.svoemesto.karaokeapp.services.StatsDebugController.
 *
 * Паттерн заимствован из спеки 167 (share.internal, share.notFound) —
 * единый формат ошибки для всего модуля.
 *
 * @see specs/174-fix-stats-connection-leak/contracts/stats-unavailable.md
 * @see specs/174-fix-stats-connection-leak/spec.md FR-003
 */
fun statsUnavailableResponse(
    endpoint: String,
    cause: Throwable? = null,
): ResponseEntity<Map<String, Any>> {
    val log = LoggerFactory.getLogger("StatsController")
    log.warn(
        "stats.unavailable endpoint={} cause={}",
        endpoint,
        cause?.javaClass?.simpleName ?: "null",
    )
    val headers = HttpHeaders()
    headers.add("Retry-After", "10")
    val body: Map<String, Any> =
        mapOf(
            "errorCode" to "stats.unavailable",
            "retryAfterSeconds" to 10,
            "endpoint" to endpoint,
        )
    val response: ResponseEntity<Map<String, Any>> =
        ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).headers(headers).body(body)
    return response
}
