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
 * - WARN `infra.prod.ping` при неуспешном HTTP-пинге сайта (с durationMs, error, exceptionClass — БЕЗ stacktrace).
 * - INFO `infra.prod.ping` при восстановлении после сбоя (`ping:recovered downForMin=N`).
 * - WARN `infra.prod.db` при неуспешном JDBC-пинге прод-БД (только host+port, НЕ JDBC URL — per FR-022 / Constitution § VIII.5).
 * - NO-OP в обычном режиме (когда пинги постоянно проходят) — минимум шума в логах.
 *
 * Каскад диагностики (specs/289-followup, после инцидентов 2026-09-01 20:47-20:57 и 22:46-22:57 MSK):
 * 1. Java HttpClient падает → WARN `ping:failed` + запускаем `runCurlDiagnostic()`.
 * 2. curl с admin-машины:
 *    - OK (exit 0) → INFO `ping:curlDiagnostic ok ... hint=javaClientIssue` (ложная тревога).
 *    - FAIL (exit != 0) → WARN `ping:curlDiagnostic failed ...` + запускаем `runSshCurlDiagnostic()`.
 * 3. ssh на прод + curl:
 *    - OK → WARN `... sshOk=true hint=adminMachineNetworkIssue` (проблема на маршруте admin → прод).
 *    - FAIL → WARN `... sshOk=false hint=realProdIssue` (реальный инцидент на проде).
 *    - SSH ERROR (например, нет ключей) → WARN `... sshError="..." hint=sshUnavailable`.
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

    // specs/289-followup: используем современный java.net.http.HttpClient вместо устаревшего
    // java.net.HttpURLConnection. Старый клиент имел проблемы с TLS handshake на длинных certificate
    // chains (MTU black-hole на маршруте admin-машина → прод) — браузер от той же машины работал, Java — нет.
    // java.net.http.HttpClient (Java 11+) — современный TLS stack, поддержка HTTP/2.
    private val httpClient: HttpClient =
        HttpClient
            .newBuilder()
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
     * Возвращает `Pair(up, durationMs)`.
     *
     * При ошибке НЕ логирует stacktrace (только error + exceptionClass) — чтобы не засорять логи.
     * Каскад диагностики — см. KDoc на `object ProdContainerCheck`.
     */
    private fun pingSite(): Pair<Boolean, Long> {
        val startMs = System.currentTimeMillis()
        return try {
            val request =
                HttpRequest
                    .newBuilder()
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
            // FR-011: WARN с durationMs + error + exceptionClass. БЕЗ Throwable (без stacktrace).
            pingLog.warn(
                "ping:failed url={} durationMs={} error=\"{}\" exceptionClass={}",
                PING_URL, durationMs, e.message, e::class.java.name,
            )
            // Каскадная диагностика (см. KDoc). Сначала curl с admin-машины.
            runCurlDiagnostic(durationMs)
            Pair(false, durationMs)
        }
    }

    /**
     * Диагностика 1-го уровня: curl с admin-машины.
     * Если OK → hint=javaClientIssue (ложная тревога).
     * Если FAIL → запускаем SSH-диагностику (runSshCurlDiagnostic).
     */
    private fun runCurlDiagnostic(javaDurationMs: Long) {
        try {
            val startMs = System.currentTimeMillis()
            val process =
                ProcessBuilder(
                    "curl",
                    "-sS",
                    "-o",
                    "/dev/null",
                    "-w",
                    "%{http_code}",
                    "--max-time",
                    "5",
                    PING_URL,
                ).redirectErrorStream(true).start()
            val finished = process.waitFor(8, java.util.concurrent.TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                // curl timeout — переходим к SSH-диагностике.
                runSshCurlDiagnostic(javaDurationMs, null, "timeout")
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
                // curl FAIL — переходим к SSH-диагностике.
                runSshCurlDiagnostic(javaDurationMs, exitCode, null)
            }
        } catch (e: Exception) {
            // launch error (curl не установлен и т.п.) — fallback на SSH.
            runSshCurlDiagnostic(javaDurationMs, null, "launchError=${e.message}")
        }
    }

    /**
     * Диагностика 2-го уровня: ssh на прод-сервер + curl.
     * sshCurlOk = true → hint=adminMachineNetworkIssue (проблема на маршруте admin → прод).
     * sshCurlOk = false → hint=realProdIssue (прод действительно лежит).
     * ssh error (нет ключей и т.п.) → hint=sshUnavailable (невозможно подтвердить).
     */
    private fun runSshCurlDiagnostic(javaDurationMs: Long, curlExitCode: Int?, curlError: String?) {
        try {
            val startMs = System.currentTimeMillis()
            // ssh на прод-сервер, оттуда curl на https://sm-karaoke.ru/.
            // --max-time 5 (curl timeout), без --hostkey校验 (чтобы не падать на strict host key).
            // Если SSH-ключей нет или нет доступа — ProcessBuilder бросит IOException (handled в catch).
            val process =
                ProcessBuilder(
                    "ssh",
                    "-o", "BatchMode=yes",
                    "-o", "StrictHostKeyChecking=no",
                    "-o", "ConnectTimeout=5",
                    "root@$DB_REMOTE_HOST",
                    "curl -sS -o /dev/null -w '%{http_code}' --max-time 5 $PING_URL",
                ).redirectErrorStream(true).start()
            val finished = process.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                pingLog.warn(
                    "ping:sshDiagnostic timeout javaDurationMs={} sshDurationMs={} curlExitCode={} curlError=\"{}\" hint=sshUnavailable",
                    javaDurationMs, System.currentTimeMillis() - startMs, curlExitCode, curlError,
                )
                return
            }
            val exitCode = process.exitValue()
            val sshDurationMs = System.currentTimeMillis() - startMs
            val sshOk = (exitCode == 0)
            val hint = if (sshOk) "adminMachineNetworkIssue" else "realProdIssue"
            pingLog.warn(
                "ping:sshDiagnostic sshOk={} javaDurationMs={} sshDurationMs={} curlExitCode={} hint={}",
                sshOk, javaDurationMs, sshDurationMs, hint,
            )
        } catch (e: Exception) {
            // launch error (ssh не установлен, IOException, etc.).
            pingLog.warn(
                "ping:sshDiagnostic error javaDurationMs={} error=\"{}\" curlExitCode={} hint=sshUnavailable",
                javaDurationMs, e.message, curlExitCode,
            )
        }
    }

    /**
     * JDBC-пинг прод-БД через `Connection.remote()`. Возвращает `Pair(up, durationMs)`.
     *
     * FR-015/FR-016: при ошибке пишет WARN `infra.prod.db - db:failed ...`. Логирует только `host` и `port`,
     * НЕ полный JDBC URL (который может содержать пароль — per FR-022 / Constitution § VIII.5).
     * БЕЗ stacktrace (только error + exceptionClass) — чтобы не засорять логи.
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
            // БЕЗ Throwable — только error message.
            dbLog.warn(
                "db:failed host={} port={} durationMs={} error=\"{}\" exceptionClass={}",
                DB_REMOTE_HOST, DB_REMOTE_PORT, durationMs, e.message, e::class.java.name,
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
