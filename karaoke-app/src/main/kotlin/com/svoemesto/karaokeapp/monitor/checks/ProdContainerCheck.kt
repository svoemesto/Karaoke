package com.svoemesto.karaokeapp.monitor.checks

import com.svoemesto.karaokeapp.Connection
import com.svoemesto.karaokeapp.KaraokeProperties
import com.svoemesto.karaokeapp.monitor.MonitorAlert
import com.svoemesto.karaokeapp.monitor.MonitorCheck
import com.svoemesto.karaokeapp.monitor.MonitorContext
import com.svoemesto.karaokeapp.monitor.MonitorSeverity
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration
import java.time.Instant

/**
 * Проверяет доступность прод-сервера (karaoke-web/karaoke-public за nginx на 79.174.95.69) HTTP-
 * пингом (образец таймаутов - Utils.isVpnActive()) + доступность прод-БД. Серьёзность нарастает:
 * WARNING сразу после первого сбоя, CRITICAL - если недоступность длится
 * >= monitorProdDownCriticalMinutes минут.
 *
 * firstFailureAt хранится только в памяти (не персистится) - при перезапуске karaoke-app отсчёт
 * длительности сбоя начнётся заново (severity временно упадёт до WARNING); это осознанный компромисс
 * (см. план фичи), т.к. karaoke-app и так не 24/7 аптайм-монитор.
 */
object ProdContainerCheck : MonitorCheck {
    private const val PING_URL = "https://sm-karaoke.ru/"

    @Volatile private var firstFailureAt: Instant? = null

    override fun run(ctx: MonitorContext): List<MonitorAlert> {
        val siteUp = pingSite()
        val dbUp = pingRemoteDb()

        if (siteUp && dbUp) {
            firstFailureAt = null
            return emptyList()
        }

        val since = firstFailureAt ?: Instant.now().also { firstFailureAt = it }
        val downForMinutes = Duration.between(since, Instant.now()).toMinutes()
        val criticalMinutes = KaraokeProperties.getLong("monitorProdDownCriticalMinutes").takeIf { it > 0 } ?: 5L
        val severity = if (downForMinutes >= criticalMinutes) MonitorSeverity.CRITICAL else MonitorSeverity.WARNING

        val whatIsDown =
            listOfNotNull(
                if (!siteUp) "сайт ($PING_URL)" else null,
                if (!dbUp) "БД прод-сервера" else null,
            ).joinToString(" и ")

        return listOf(
            MonitorAlert(
                key = "infra.prod.down",
                severity = severity,
                title = "Прод-сервер недоступен",
                body = "Недоступен(ы): $whatIsDown.",
                category = "Инфраструктура",
                detail = "недоступен уже $downForMinutes мин.",
                recommendations =
                    "Проверьте сервер 79.174.95.69 по SSH: nginx (`nginx -t`, `systemctl status nginx`), " +
                        "`docker ps` (karaoke-web/karaoke-public/karaoke-db), логи контейнеров (`docker logs <container>`).",
            ),
        )
    }

    private fun pingSite(): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(PING_URL).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.instanceFollowRedirects = true
            conn.responseCode in 200..399
        } catch (e: Exception) {
            println("ProdContainerCheck: ping $PING_URL не удался: ${e.message}")
            false
        } finally {
            conn?.disconnect()
        }
    }

    private fun pingRemoteDb(): Boolean {
        // Connection.remote() открывает новое физическое JDBC-соединение - закрыть явно после
        // использования (тот же инвариант, что и в SponsrSyncScheduler).
        val db = Connection.remote()
        return try {
            val connection = db.getConnection()
            connection != null && connection.isValid(3)
        } catch (e: Exception) {
            false
        } finally {
            try {
                db.getConnection()?.close()
            } catch (_: Exception) {
            }
        }
    }
}
