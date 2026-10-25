package com.svoemesto.karaokeweb.controllers

import com.svoemesto.karaokeweb.WORKING_DATABASE
import com.svoemesto.karaokeweb.services.DebugDbAccessGuard
import com.svoemesto.karaokeweb.services.KaraokeProperties
import jakarta.servlet.http.HttpServletRequest
import org.apache.coyote.AbstractProtocol
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext
import org.springframework.boot.web.embedded.tomcat.TomcatWebServer
import org.springframework.context.ApplicationContext
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.lang.management.ManagementFactory
import java.sql.SQLException
import java.util.concurrent.ThreadPoolExecutor

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
 * **Возвращаемый JSON** (BR-017 — все значения соответствуют своему названию):
 *  - `pgActiveConnections`: активные соединения к Postgres (из `pg_stat_activity`).
 *  - `pgIdleConnections`: простаивающие соединения к Postgres (`total - active`).
 *  - `pgMaxConnections`: реальный лимит PostgreSQL (`SHOW max_connections`), `-1` —
 *    значение не удалось прочитать (БД недоступна).
 *  - `currentThreadCount`: текущее число живых threads в JVM.
 *  - `currentTomcatMaxThreads`: максимальное число threads в пуле Tomcat
 *    (`ThreadPoolExecutor.maximumPoolSize` коннектора).
 *  - `sampledAt`: ISO-8601 timestamp момента снятия метрик.
 *
 * BR-017 (аудит #336, OpenProject #401) — до правки три поля врали:
 *  - `pgMaxConnections` был **захардкожен в 100** (никак не related к настройке БД);
 *  - `currentTomcatMaxThreads` содержал `ThreadMXBean.peakThreadCount` — пик потоков
 *    ВСЕЙ JVM, а не максимум потоков Tomcat;
 *  - KDoc обещал «HikariCP pool», но в проекте его нет: соединения — собственный
 *    thread-local пул [com.svoemesto.karaokeapp.KaraokeConnection] (ADR-0001, raw JDBC).
 *
 * @see archive/docs/features/site-traffic-resilience.md (FR-013)
 * @see DebugDbAccessGuard
 * @see KaraokeProperties
 */
@RestController
@RequestMapping("/api/public/debug")
class DebugDbController(
    private val properties: KaraokeProperties,
    // BR-017: реальный `maxThreads` Tomcat-коннектора читается из запущенного web-сервера.
    private val applicationContext: ApplicationContext,
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
                "currentTomcatMaxThreads" to tomcatMaxThreads(),
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
        // BR-017: -1 = «значение не удалось прочитать» (БД недоступна). Прежде стояла
        // константа 100 — она была ни при каком max_connections.
        var max = -1
        val connection = WORKING_DATABASE.getConnection() ?: return PgStats(active, idle, max)
        try {
            connection.prepareStatement("SHOW max_connections").use { ps ->
                val rs = ps.executeQuery()
                if (rs.next()) {
                    max = rs.getInt(1)
                }
                rs.close()
            }
            connection
                .prepareStatement(
                    "SELECT count(*) FILTER (WHERE state = 'active') AS active, count(*) AS total FROM pg_stat_activity WHERE datname = current_database()",
                ).use { ps ->
                    val rs = ps.executeQuery()
                    if (rs.next()) {
                        active = rs.getInt("active")
                        idle = rs.getInt("total") - active
                    }
                    rs.close()
                }
        } catch (_: SQLException) {
            // БД недоступна — оставляем нули (max остаётся -1 = «неизвестно»).
        }
        return PgStats(active, idle, max)
    }

    /**
     * BR-017: реальный максимум потоков Tomcat.
     *
     * Берётся `maximumPoolSize` пула коннектора (тот же `ThreadPoolExecutor`, на котором
     * Tomcat обслуживает запросы). Если web-сервер не Tomcat или коннектор ещё не
     * инициализирован — 0 («неизвестно»), чтобы поле не врало.
     */
    private fun tomcatMaxThreads(): Int {
        val webServer =
            (applicationContext as? ServletWebServerApplicationContext)?.webServer ?: return 0
        val tomcat = (webServer as? TomcatWebServer)?.tomcat ?: return 0
        val connector = tomcat.getConnector() ?: return 0
        val executor = (connector.protocolHandler as? AbstractProtocol<*>)?.executor as? ThreadPoolExecutor
        executor?.let { return it.maximumPoolSize }
        // Запасной путь: свойство коннектора `maxThreads` (то же значение, что и у пула).
        return connector.getProperty("maxThreads") as? Int ?: 0
    }
}
