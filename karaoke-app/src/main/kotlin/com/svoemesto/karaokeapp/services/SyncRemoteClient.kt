package com.svoemesto.karaokeapp.services

import org.slf4j.LoggerFactory
import java.net.ConnectException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.time.Duration
import javax.net.ssl.SSLException

/**
 * Устойчивый HTTP-клиент для синхронизации с прод-сайтом (Pass 431, OpenProject #155).
 *
 * **Зачем**: раньше каждый вызов `POST https://sm-karaoke.ru/changerecords` в
 * `Utils.kt` строился одноразовым `HttpClient.newBuilder().build()` **без таймаутов**
 * и **без `try/catch`**. Любая транзиентная сетевая ошибка (`SSLHandshakeException:
 * Remote host terminated the handshake` / `Connection timed out`) летела наверх,
 * проходила через `postSyncOneClick` (без `catch`) и валила весь запрос в HTTP 500
 * со стектрейсом.
 *
 * **Теперь**: единый helper с конечными таймаутами, одной повторной попыткой на
 * транзиентные ошибки и внутренним `try/catch` (наружу НЕ бросает — возвращает
 * `false`). Логи — категория `infra.sync.remote`.
 *
 * @see specs/431-sync-resilience/spec.md
 * @see knowledge/domains/processing/components/two-db-sync.md
 */
object SyncRemoteClient {
    const val URL = "https://sm-karaoke.ru/changerecords"

    private val log = LoggerFactory.getLogger("infra.sync.remote")

    /** Pass 431 (#155): конечные таймауты (решение владельца). */
    private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(10)
    private val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(60)

    /** Задержка перед единственной повторной попыткой. */
    private const val RETRY_DELAY_MS = 2_000L

    private val httpClient: HttpClient =
        HttpClient
            .newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build()

    /**
     * Отправляет тело на [URL] с повтором. Возвращает `true` при 2xx,
     * `false` при неуспехе/исчерпании попыток (исключение НЕ бросается).
     *
     * Транзиентные ошибки (`SSLException`, `ConnectException`, `HttpTimeoutException`,
     * `IOException` о таймауте/сбросе) — 1 повтор через [RETRY_DELAY_MS]. Прочие
     * ошибки — без повтора.
     */
    fun postChangeRecords(body: String): Boolean = postChangeRecords(body) { httpClient.send(it, HttpResponse.BodyHandlers.ofString()) }

    /**
     * Test-seam: позволяет подменить реальный `send` (и задержку retry) в unit-тестах.
     */
    internal fun postChangeRecords(
        body: String,
        retryDelayMs: Long = RETRY_DELAY_MS,
        send: (HttpRequest) -> HttpResponse<String>,
    ): Boolean {
        var lastError: Exception? = null
        var attempt = 1
        while (attempt <= 2) {
            try {
                val request =
                    HttpRequest
                        .newBuilder()
                        .uri(URI.create(URL))
                        .timeout(REQUEST_TIMEOUT)
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .header("Content-Type", "application/json")
                        .build()
                val response = send(request)
                val ok = response.statusCode() in 200..299
                if (ok) {
                    log.info("sync:remote:ok status={} attempt={}", response.statusCode(), attempt)
                } else {
                    log.warn("sync:remote:failure attempt={} status={}", attempt, response.statusCode())
                }
                return ok
            } catch (e: Exception) {
                lastError = e
                val transient = isTransient(e)
                if (!transient || attempt == 2) {
                    log.warn(
                        "sync:remote:failure attempt={} error={}",
                        attempt, "${e::class.simpleName}: ${e.message ?: "(no message)"}",
                    )
                    return false
                }
                log.warn(
                    "sync:remote:retry attempt={} error={}",
                    attempt, "${e::class.simpleName}: ${e.message ?: "(no message)"}",
                )
                try {
                    Thread.sleep(retryDelayMs)
                } catch (ie: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return false
                }
            }
            attempt++
        }
        // Достижимо только если цикл завершился без return (не ожидается).
        lastError?.let { log.warn("sync:remote:failure exhausted error={}", it::class.simpleName) }
        return false
    }

    /**
     * Транзиентные (сетевые) ошибки, для которых оправдан повтор:
     * TLS-сбой, отказ в соединении, таймаут, сброс соединения.
     */
    internal fun isTransient(e: Exception): Boolean =
        when (e) {
            is SSLException, is ConnectException, is HttpTimeoutException -> true
            is java.io.InterruptedIOException -> true
            is java.io.IOException -> {
                val msg = (e.message ?: "").lowercase()
                msg.contains("timed out") || msg.contains("timeout") || msg.contains("connection reset")
            }
            else -> false
        }
}
