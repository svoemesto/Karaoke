package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.Connection
import com.svoemesto.karaokeapp.controllers.statsUnavailableResponse
import com.svoemesto.karaokeapp.model.CacheKeyInfo
import com.svoemesto.karaokeapp.model.StatsDebugDto
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import java.sql.SQLException
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Контроллер для ручной диагностики stats-инфраструктуры.
 *
 * Endpoint `POST /api/stats/debug` (без auth — admin-зона, permitAll через
 * SecurityConfig для всех /api, см. AGENTS.md Principle V).
 *
 * Возвращает:
 * - cacheSize — количество ключей в StatsCache (включая expired);
 * - cacheKeys — каждая запись с endpoint, params, age (в секундах) и
 *   expired = true если age > TTL_SECONDS;
 * - pgActiveConnections — счётчик pg_stat_activity;
 * - pgMaxConnections — из pg_settings WHERE name=max_connections;
 * - timestamp — ISO-8601 UTC.
 *
 * При невозможности подключиться к Postgres (например, в момент инцидента
 * «too many clients already») возвращается тот же 503 stats.unavailable формат,
 * что и в основных stats endpoint-ах — см. statsUnavailableResponse в
 * com.svoemesto.karaokeapp.controllers.StatsResponseUtils.
 *
 * @see specs/174-fix-stats-connection-leak/contracts/stats-debug.md
 * @see specs/174-fix-stats-connection-leak/spec.md FR-010
 */
@RestController
class StatsDebugController {
    /**
     * Возвращает состояние кеша + счётчик Postgres-соединений.
     *
     * Использует тот же try-finally с db.getConnection() close() паттерн, что
     * и приватный withDb в StatsController (не дублируем helper — он private).
     */
    @PostMapping("/api/stats/debug")
    fun debug(): ResponseEntity<Map<String, Any>> {
        val timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now().atOffset(ZoneOffset.UTC))
        val db = Connection.local()
        return try {
            var activeConnections = -1
            var maxConnections = -1
            val conn = db.getConnection()
            if (conn == null) {
                return statsUnavailableResponse("/api/stats/debug")
            }
            try {
                conn.prepareStatement("SELECT count(*) FROM pg_stat_activity").use { ps ->
                    ps.executeQuery().use { rs ->
                        if (rs.next()) activeConnections = rs.getInt(1)
                    }
                }
                conn.prepareStatement("SELECT setting FROM pg_settings WHERE name = 'max_connections'").use { ps ->
                    ps.executeQuery().use { rs ->
                        if (rs.next()) maxConnections = rs.getInt(1)
                    }
                }
            } finally {
                try {
                    db.getConnection()?.close()
                } catch (_: Exception) {
                }
            }
            val now = Instant.now()
            val ttl = StatsCache.TTL_SECONDS
            val keys: List<CacheKeyInfo> =
                StatsCache.snapshot().map { (key, entry) ->
                    val remainingSec = Duration.between(now, entry.expiresAt).seconds
                    val age = ttl - remainingSec
                    CacheKeyInfo(
                        endpoint = key.endpoint,
                        params = key.params,
                        ageSeconds = age,
                        expired = age > ttl,
                    )
                }
            val dto =
                StatsDebugDto(
                    cacheSize = keys.size,
                    cacheKeys = keys,
                    pgActiveConnections = activeConnections,
                    pgMaxConnections = maxConnections,
                    timestamp = timestamp,
                )

            @Suppress("UNCHECKED_CAST")
            val response: ResponseEntity<Map<String, Any>> = ResponseEntity.ok(dto as Map<String, Any>)
            response
        } catch (e: SQLException) {
            statsUnavailableResponse("/api/stats/debug", e)
        }
    }
}
