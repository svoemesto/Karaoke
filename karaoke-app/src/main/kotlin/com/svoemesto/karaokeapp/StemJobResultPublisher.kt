package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.model.StemJob
import com.svoemesto.karaokeapp.services.SAC_APP
import com.svoemesto.karaokeapp.services.StorageApiClient
import org.slf4j.LoggerFactory
import java.net.HttpURLConnection
import java.net.URL

/**
 * Передача приватных объектов премиум-задания из `karaoke-app` в `karaoke-web` по внутреннему
 * каналу `X-Internal-Secret` (спека 561, OpenProject #361, аудит BR-258).
 *
 * Почему так: `karaoke-web` **не умеет** читать MinIO с учётными данными
 * (`WebKaraokeStorageServiceImpl` — заглушка, все методы бросают
 * `UnsupportedOperationException`), а presigned-URL через nginx path-proxy нереализуем (SigV4
 * ломается переписыванием пути). Поэтому приватные объекты лежат в отдельном бакете `stemjobs`
 * без публичной bucket policy, а приложение с доступом (`karaoke-app`, единственный клиент с
 * учётными данными) **читает объект из бакета** и передаёт его в `karaoke-web` по защищённому
 * каналу — там объект кладётся в приватный каталог результатов, и его отдаёт владельцу
 * `PublicStemJobController.download`.
 *
 * Направление канала — admin → prod: прод-`karaoke-web` не достигает admin-машины (firewall),
 * поэтому server-to-server канал для приватных объектов может идти только так — как уже
 * работают `/api/internal/stemjobs/{id}/raw` и `/ack`.
 *
 * Контракт канала — `specs/561-impl-stem-bucket/contracts/stemjob-private-files-contract.md`.
 *
 * @see specs/561-impl-stem-bucket/research.md
 */
object StemJobResultPublisher {
    private val log = LoggerFactory.getLogger("infra.stemjobs.transfer")

    /** Сколько попыток на сетевую ошибку (403 — отказ по правам, повтор не поможет). */
    private const val MAX_ATTEMPTS = 3

    /** Пауза между попытками. */
    private const val RETRY_DELAY_MS = 2_000L

    /**
     * Передаёт один приватный объект задания (`stem`) в `karaoke-web`.
     *
     * @return `null` — объект передан; иначе — короткое описание причины (для `StemJob.errorMessage`).
     */
    fun publish(
        jobId: Long,
        stem: String,
        ext: String,
        storageApiClient: StorageApiClient = SAC_APP,
    ): String? {
        val baseUrl = Karaoke.stemJobsWebInternalUrl.trim().trimEnd('/')
        if (baseUrl.isBlank()) return "не настроен stemJobsWebInternalUrl (Properties) — нет канала передачи результатов"
        val fileName = StemJob.resultFileName(stem, ext)
        val key = StemJob.objectKey(jobId, stem, ext)
        val bytes =
            try {
                storageApiClient.downloadFile(StemJob.STORAGE_BUCKET, key).block()
            } catch (e: Exception) {
                return "ошибка чтения объекта $key из бакета ${StemJob.STORAGE_BUCKET}: ${e.message}"
            } ?: return "объект $key не найден в бакете ${StemJob.STORAGE_BUCKET}"

        var lastError: String? = null
        for (attempt in 1..MAX_ATTEMPTS) {
            val error = send(baseUrl, jobId, fileName, bytes, Karaoke.stemJobsInternalSecret)
            if (error == null) {
                log.info("stemjobs:transfer:ok jobId={} file={} bytes={}", jobId, fileName, bytes.size)
                return null
            }
            lastError = error
            if (error.startsWith("HTTP 403")) {
                // Отказ по правам = секреты не совпадают (или не настроены) — повтор не поможет.
                log.warn("stemjobs:transfer:denied jobId={} file={} reason={}", jobId, fileName, error)
                return error
            }
            log.warn("stemjobs:transfer:retry jobId={} file={} attempt={} reason={}", jobId, fileName, attempt, error)
            if (attempt < MAX_ATTEMPTS) Thread.sleep(RETRY_DELAY_MS)
        }
        return lastError
    }

    /**
     * Один HTTP-запрос передачи объекта. Возвращает `null` при успехе, иначе — описание ошибки.
     * Значение секрета в журнал не попадает.
     */
    internal fun send(
        baseUrl: String,
        jobId: Long,
        fileName: String,
        bytes: ByteArray,
        secret: String,
    ): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = URL("$baseUrl/api/internal/stemjobs/$jobId/files/$fileName").openConnection() as HttpURLConnection
            connection.requestMethod = "PUT"
            connection.doOutput = true
            connection.setRequestProperty("X-Internal-Secret", secret)
            connection.setRequestProperty("Content-Type", "application/octet-stream")
            connection.setFixedLengthStreamingMode(bytes.size)
            connection.connectTimeout = 15_000
            connection.readTimeout = 120_000
            connection.outputStream.use { it.write(bytes) }
            val code = connection.responseCode
            if (code == 200) null else "HTTP $code"
        } catch (e: Exception) {
            "${e.javaClass.simpleName}: ${e.message}"
        } finally {
            connection?.disconnect()
        }
    }
}
