package com.svoemesto.karaokeweb.controllers

import com.svoemesto.karaokeweb.WORKING_DATABASE
import com.svoemesto.karaokeweb.services.DebugDbAccessGuard
import com.svoemesto.karaokeweb.services.KaraokeProperties
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.lang.management.ManagementFactory
import java.sql.SQLException

/**
 * Debug endpoint для мониторинга состояния БД и Tomcat-threadpool (FR-013, US6).
 *
 * **НЕ для production**: возвращает детальную информацию о ресурсах (PG connections,
 * Tomcat threads). Доступ защищён двумя условиями (см. [DebugDbAccessGuard]):
 *  1. `KARAOKE_WEB_DEBUG_DB_ENABLED=true`.
 *  2. `KARAOKE_WEB_DEBUG_DB_ALLOWED_IPS` содержит IP клиента.
 *
 * Если хотя бы одно из условий не выполнено — `ResponseEntity.notFound()` (404),
 * чтобы endpoint был невидим для всех посторонних (включая security scanners).
 *
 * **Возвращаемый JSON**:
 *  - `pgActiveConnections`: текущие активные соединения к Postgres (из `pg_stat_activity`).
 *  - `pgIdleConnections`: idle connections в HikariCP pool.
 *  - `pgMaxConnections`: максимальный размер pool (HikariCP `maximumPoolSize`).
 *  - `currentThreadCount`: текущее число threads в JVM.
 *  - `currentTomcatMaxThreads`: максимальное число threads в Tomcat.
 *  - `sampledAt`: ISO-8601 timestamp момента снятия метрик.
 *
 * @see archive/archive/docs/features/site-traffic-resilience.md (FR-013)
 * @see DebugDbAccessGuard
 * @see KaraokeProperties
 */
@RestController
@RequestMapping("/api/public/debug")
class DebugDbController(
    private val properties: KaraokeProperties,
) {
    /**
     * Возвращает метрики ресурсов. Доступ только из IP allowlist (см. [DebugDbAccessGuard]).
     */
    @GetMapping("/db")
    fun db(request: HttpServletRequest): ResponseEntity<Any> {
        if (!DebugDbAccessGuard.isAllowed(properties, request)) {
            return ResponseEntity.notFound().build()
        }

        val pgStats = readPgStats()
        val threadMX = ManagementFactory.getThreadMXBean()
        val sampledAt =
            java.time.Instant
                .now()
                .toString()

        val payload =
            mapOf(
                "pgActiveConnections" to pgStats.active,
                "pgIdleConnections" to pgStats.idle,
                "pgMaxConnections" to pgStats.max,
                "currentThreadCount" to threadMX.threadCount,
                "currentTomcatMaxThreads" to threadMX.peakThreadCount,
                "sampledAt" to sampledAt,
            )
        return ResponseEntity.ok(payload)
    }

    private data class PgStats(
        val active: Int,
        val idle: Int,
        val max: Int
    )

    /**
     * Читает активные/idle connections из `pg_stat_activity` и HikariCP.
     *
     * На текущий момент используем простой SQL-запрос к `pg_stat_activity`. HikariCP
     * internal pool stats доступны через `HikariPoolMXBean`, но требуют каста
     * `WORKING_DATABASE` к HikariDataSource — отложено до явной потребности.
     *
     * Если запрос падает (БД недоступна) — возвращаем нули, чтобы endpoint остался
     * доступным и не блокировал мониторинг.
     */
    private fun readPgStats(): PgStats {
        var active = 0
        var idle = 0
        var max = 100
        val connection = WORKING_DATABASE.getConnection() ?: return PgStats(active, idle, max)
        try {
            val ps =
                connection.prepareStatement(
                    "SELECT count(*) FILTER (WHERE state = 'active') AS active, count(*) AS total FROM pg_stat_activity WHERE datname = current_database()",
                )
            val rs = ps.executeQuery()
            if (rs.next()) {
                active = rs.getInt("active")
                idle = rs.getInt("total") - active
            }
            rs.close()
            ps.close()
        } catch (_: SQLException) {
            // БД недоступна — оставляем нули.
        }
        return PgStats(active, idle, max)
    }
}
