package com.svoemesto.karaokeapp.monitor.checks

import com.svoemesto.karaokeapp.Connection
import com.svoemesto.karaokeapp.KaraokeProperties
import com.svoemesto.karaokeapp.monitor.MonitorAlert
import com.svoemesto.karaokeapp.monitor.MonitorCheck
import com.svoemesto.karaokeapp.monitor.MonitorContext
import com.svoemesto.karaokeapp.monitor.MonitorSeverity
import com.svoemesto.karaokeapp.services.DB_REMOTE_HOST
import com.svoemesto.karaokeapp.services.DB_REMOTE_PORT
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant

/**
 * Проверяет доступность прод-сервера (karaoke-web/karaoke-public за nginx, хост из env `DB_REMOTE_HOST`)
 * HTTP-пингом (образец таймаутов - Utils.isVpnActive()) + доступность прод-БД. Серьёзность нарастает:
 * WARNING сразу после первого сбоя, CRITICAL - если недоступность длится
 * >= monitorProdDownCriticalMinutes минут.
 *
 * firstFailureAt хранится только в памяти (не персистится) - при перезапуске karaoke-app отсчёт
 * длительности сбоя начнётся заново (severity временно упадёт до WARNING); это осознанный компромисс
 * (см. план фичи), т.к. karaoke-app и так не 24/7 аптайм-монитор.
 */

/**
 * Singleton-объект Prod Container Check.
 *
 * Логирование (specs/288-prod-diagnostics-logging, FR-011..FR-016):
 * - WARN `infra.prod.ping` при неуспешном HTTP-пинге сайта (с durationMs, error, exceptionClass).
 * - INFO `infra.prod.ping` при восстановлении после сбоя (`ping:recovered downForMin=N`).
 * - WARN `infra.prod.db` при неуспешном JDBC-пинге прод-БД (только host+port, НЕ JDBC URL — per FR-022 / Constitution § VIII.5).
 * - NO-OP в обычном режиме (когда пинги постоянно проходят) — минимум шума в логах.
 *
 * Контракт формата WARN/INFO сообщений — см. `specs/288-prod-diagnostics-logging/contracts/log-format.md`.
 * Конвенция логирования (SLF4J + key=value + категория для grep) — см. local-0005.
 *
 * @see livedocs/architecture/decisions/local-0005-structured-logging-karaoke-app.md
 * @see specs/288-prod-diagnostics-logging/contracts/log-format.md
 */
object ProdContainerCheck : MonitorCheck {
    private const val PING_URL = "https://sm-karaoke.ru/"

    // specs/288-prod-diagnostics-logging FR-011: явные категории логгеров для grep-корреляции
    // с другими источниками логов (PostgreSQL pg_log, nginx access.log — по общему timestamp).
    private val pingLog = LoggerFactory.getLogger("infra.prod.ping")
    private val dbLog = LoggerFactory.getLogger("infra.prod.db")

    // specs/289-followup (после инцидента 2026-09-01 20:47-20:57 MSK): используем современный
    // java.net.http.HttpClient вместо устаревшего java.net.HttpURLConnection. Старый клиент
    // имел проблемы с TLS handshake на длинных certificate chains (MTU black-hole на маршруте
    // admin-машина → прод) — браузер от той же машины работал, Java — нет.
    // java.net.http.HttpClient (Java 11+) — современный TLS stack, поддержка HTTP/2.
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    @Volatile private var firstFailureAt: Instant? = null

    override fun run(ctx: MonitorContext): List<MonitorAlert> {
        // FR-012/FR-013/FR-016: pingSite() и pingRemoteDb() возвращают (up, durationMs) — для логирования.
        val (siteUp, siteDurationMs) = pingSite()
        val (dbUp, dbDurationMs) = pingRemoteDb()

        // FR-014: логируем восстановление ТОЛЬКО при смене состояния WARNING/CRITICAL → OK.
        // В обычном режиме (пинги постоянно OK) — НЕ пишем ничего (минимум шума).
        val wasFailing = firstFailureAt != null
        if (siteUp && dbUp) {
            if (wasFailing) {
                val downForMin = Duration.between(firstFailureAt, Instant.now()).toMinutes()
                pingLog.info(
                    "ping:recovered url={} downForMin={}",
                    PING_URL, downForMin,
                )
            }
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
                    "Проверьте сервер $DB_REMOTE_HOST по SSH: nginx (`nginx -t`, `systemctl status nginx`), " +
                        "`docker ps` (karaoke-web/karaoke-public/karaoke-db), логи контейнеров (`docker logs <container>`).",
            ),
        )
    }

    /**
     * HTTP-пинг прод-сайта через современный `java.net.http.HttpClient` (Java 11+).
     * Возвращает `Pair(up, durationMs)` — duration нужен для baseline
     * (если пинг начал занимать 3 сек вместо обычных 200 мс — это индикатор деградации).
     *
     * Почему `java.net.http.HttpClient`, а не `java.net.HttpURLConnection` (FR-011/FR-012):
     * - Современный TLS stack (Java 11+) — корректно работает с длинными certificate chains и
     *   HTTP/2 (nginx на проде поддерживает HTTP/2). `HttpURLConnection` известен проблемами с
     *   TLS handshake на MTU-чувствительных маршрутах (см. инцидент 2026-09-01 20:47-20:57 MSK —
     *   браузер от admin-машины работал, Java HttpURLConnection — нет).
     * - Поддержка HTTP/2 (server-side).
     * - Меньше проблем с renegotiation.
     *
     * FR-011/FR-012: при ошибке пишет WARN `infra.prod.ping - ping:failed ...`.
     */
    private fun pingSite(): Pair<Boolean, Long> {
        val startMs = System.currentTimeMillis()
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI(PING_URL))
                .timeout(Duration.ofSeconds(5))
                .header("User-Agent", "Mozilla/5.0 (ProdContainerCheck)")
                .GET()
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            val durationMs = System.currentTimeMillis() - startMs
            Pair(response.statusCode() in 200..399, durationMs)
        } catch (e: Exception) {
            val durationMs = System.currentTimeMillis() - startMs
            pingLog.warn(
                "ping:failed url={} durationMs={} error=\"{}\" exceptionClass={}",
                PING_URL, durationMs, e.message, e::class.java.name,
                e,
            )
            // Диагностический fallback через curl (shell). Полезно для отличения
            // "Java HttpClient проблема" от "реальный инцидент на проде":
            // - curl OK → Java проблема (TLS/MTU), прод работает, инцидент ложный.
            // - curl FAIL → реальный инцидент на проде, инцидент подтверждён.
            runCurlDiagnostic(durationMs)
            Pair(false, durationMs)
        }
    }

    /**
     * Диагностический fallback: если Java HttpClient упал, проверяем прод через `curl` (shell).
     * Это помогает различить проблему Java-клиента (MTU, TLS, сертификат) от реального инцидента
     * на проде. Если curl тоже не проходит — реальный инцидент (нужно проверять nginx,
     * cert и т.д.). Если curl OK — Java-проблема, инцидент ложный.
     */
    private fun runCurlDiagnostic(javaDurationMs: Long) {
        try {
            val startMs = System.currentTimeMillis()
            val process = ProcessBuilder("curl", "-sS", "-o", "/dev/null",
                "-w", "%{http_code}", "--max-time", "5", PING_URL)
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(8, java.util.concurrent.TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                pingLog.warn(
                    "ping:curlDiagnostic timeout javaDurationMs={} curlDurationMs={}",
                    javaDurationMs, System.currentTimeMillis() - startMs,
                )
                return
            }
            val exitCode = process.exitValue()
            val curlDurationMs = System.currentTimeMillis() - startMs
            if (exitCode == 0) {
                pingLog.info(
                    "ping:curlDiagnostic ok javaDurationMs={} curlDurationMs={} hint=javaClientIssue",
                    javaDurationMs, curlDurationMs,
                )
            } else {
                pingLog.warn(
                    "ping:curlDiagnostic failed javaDurationMs={} curlDurationMs={} exitCode={} hint=realProdIssue",
                    javaDurationMs, curlDurationMs, exitCode,
                )
            }
        } catch (e: Exception) {
            pingLog.warn("ping:curlDiagnostic error javaDurationMs={} error=\"{}\"", javaDurationMs, e.message)
        }
    }

    /**
     * JDBC-пинг прод-БД через `Connection.remote()`. Возвращает `Pair(up, durationMs)`.
     *
     * FR-015/FR-016: при ошибке пишет WARN `infra.prod.db - db:failed ...`. Логирует только `host` и `port`,
     * НЕ полный JDBC URL (который может содержать пароль — per FR-022 / Constitution § VIII.5).
     */
    private fun pingRemoteDb(): Pair<Boolean, Long> {
        val startMs = System.currentTimeMillis()
        // Connection.remote() открывает новое физическое JDBC-соединение - закрыть явно после
        // использования (тот же инвариант, что и в SponsrSyncScheduler).
        val db = Connection.remote()
        return try {
            val connection = db.getConnection()
            val up = connection != null && connection.isValid(3)
            Pair(up, System.currentTimeMillis() - startMs)
        } catch (e: Exception) {
            val durationMs = System.currentTimeMillis() - startMs
            dbLog.warn(
                "db:failed host={} port={} durationMs={} error=\"{}\" exceptionClass={}",
                DB_REMOTE_HOST, DB_REMOTE_PORT, durationMs, e.message, e::class.java.name,
                e,
            )
            Pair(false, durationMs)
        } finally {
            try {
                db.getConnection()?.close()
            } catch (_: Exception) {
            }
        }
    }
}