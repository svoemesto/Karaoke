package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.model.StemJob
import com.svoemesto.karaokeapp.model.StemJobMode
import com.svoemesto.karaokeapp.model.StemJobStatus
import com.svoemesto.karaokeapp.services.KSS_APP
import com.svoemesto.karaokeapp.services.SAC_APP
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.sql.Timestamp

/**
 * Премиум-фича «Создать минусовку из аудиофайла» (StemJob, tbl_stem_jobs) — периодический поллер +
 * уборка, по образцу SponsrSyncScheduler.kt: работает только пока запущен karaoke-app (admin-машина),
 * целится в Connection.remote() — реальные задания создают пользователи на прод-сайте (karaoke-web),
 * не в локальной dev-БД. Молчаливо no-op, если Karaoke.stemJobsWebInternalUrl не настроен (пока
 * админ не заполнит после деплоя — тот же инвариант, что и у sponsrSubscribersUrl).
 *
 * pollWaiting(): скачивает сырой файл WAITING-заданий с karaoke-web (внутренний HTTP, см.
 * InternalStemJobController) в свой temp, проверяет длительность (ffprobe) ДО постановки в очередь
 * demucs — чтобы не тратить GPU/слот thread-лейна на заведомо слишком длинный файл — и создаёт
 * KaraokeProcess (LOCAL БД, THREAD_LANE_STEM_JOBS). Сама строка tbl_processes остаётся на LOCAL — это
 * внутренняя бухгалтерия karaoke-app, основной цикл KaraokeProcessWorker (doStart, привязан к
 * Connection.local()) трогать не требуется.
 *
 * cleanup(): протухшие (DONE + expires_at < now) и/или помеченные пользователем на удаление задания —
 * единый проход удаляет объекты из MinIO (SAC_APP, тем же клиентом, каким они туда и попали) и саму
 * строку в tbl_stem_jobs.
 */
@Component
class StemJobPollScheduler {
    @Scheduled(fixedDelay = 45_000L, initialDelay = 30_000L)
    fun pollWaiting() {
        if (Karaoke.stemJobsWebInternalUrl.isBlank()) return
        val database = Connection.remote()
        try {
            val waiting = StemJob.loadWaiting(database, KSS_APP, SAC_APP)
            waiting.forEach { job ->
                runCatching { takeJob(job) }.onFailure {
                    println("[StemJobPollScheduler] pollWaiting jobId=${job.id}: ${it.message}")
                }
            }
        } finally {
            try {
                database.getConnection()?.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun takeJob(job: StemJob) {
        val tempFolder = File("$PATH_TO_TEMP_STEMJOB_FOLDER/${job.id}")
        tempFolder.deleteRecursively()
        tempFolder.mkdirs()
        val uploadFile = File(tempFolder, "upload.${job.originalExt}")

        val downloadError = downloadRawFile(job.id, uploadFile)
        if (downloadError != null) {
            println("[StemJobPollScheduler] downloadRawFile jobId=${job.id}: $downloadError")
            failJob(job, "Не удалось скачать исходный файл с karaoke-web ($downloadError)")
            tempFolder.deleteRecursively()
            return
        }

        val durationSeconds = probeDurationSeconds(uploadFile)
        if (durationSeconds == null || durationSeconds > StemJob.MAX_DURATION_SECONDS) {
            failJob(job, "Длительность файла превышает лимит (${StemJob.MAX_DURATION_SECONDS / 60} мин)")
            tempFolder.deleteRecursively()
            ackRawFileConsumed(job.id)
            return
        }

        job.status = StemJobStatus.WORKING
        job.startedAt = Timestamp(System.currentTimeMillis())
        job.save()

        val (args, envs) = argsStemJobDemucs(job)
        val karaokeProcess = KaraokeProcess(Connection.local())
        karaokeProcess.name = "StemJob #${job.id} (${job.mode})"
        karaokeProcess.status = KaraokeProcessStatuses.WAITING.name
        karaokeProcess.priority = 1
        karaokeProcess.command = ""
        karaokeProcess.type =
            if (job.mode == StemJobMode.DEMUCS5) KaraokeProcessTypes.STEM_JOB_DEMUCS5.name else KaraokeProcessTypes.STEM_JOB_DEMUCS2.name
        karaokeProcess.settingsId = 0
        karaokeProcess.threadId = KaraokeProcess.THREAD_LANE_STEM_JOBS
        karaokeProcess.description = "Демукс премиум-задания #${job.id}"
        karaokeProcess.args = args
        karaokeProcess.envs = envs

        val separated = KaraokeProcess.separate(karaokeProcess)
        KaraokeProcess.createDbInstance(separated)
    }

    private fun failJob(
        job: StemJob,
        message: String,
    ) {
        job.status = StemJobStatus.ERROR
        job.errorMessage = message
        job.finishedAt = Timestamp(System.currentTimeMillis())
        job.save()
    }

    // Возвращает null при успехе, иначе краткое человекочитаемое описание причины (пишется и в
    // консоль karaoke-app, и в StemJob.errorMessage — админ должен видеть причину без доступа к
    // логам karaoke-app, только по панели «Минусовки»/личному кабинету пользователя).
    private fun downloadRawFile(
        jobId: Long,
        dest: File,
    ): String? {
        val baseUrl = Karaoke.stemJobsWebInternalUrl.trim().trimEnd('/')
        return try {
            val connection = URL("$baseUrl/api/internal/stemjobs/$jobId/raw").openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("X-Internal-Secret", Karaoke.stemJobsInternalSecret)
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            val code = connection.responseCode
            if (code != 200) {
                val bodySnippet =
                    runCatching {
                        (connection.errorStream ?: connection.inputStream)?.bufferedReader()?.readText()?.take(200)
                    }.getOrNull()
                connection.disconnect()
                return "HTTP $code" + (if (!bodySnippet.isNullOrBlank()) ": $bodySnippet" else "") +
                    (
                        if (code ==
                            403
                        ) {
                            " — проверьте, что stemJobsInternalSecret (Properties) совпадает с STEMJOBS_INTERNAL_SECRET на karaoke-web"
                        } else {
                            ""
                        }
                    )
            }
            connection.inputStream.use { input -> dest.outputStream().use { output -> input.copyTo(output) } }
            connection.disconnect()
            if (dest.exists() && dest.length() > 0) null else "файл скачан пустым"
        } catch (e: Exception) {
            "${e.javaClass.simpleName}: ${e.message}"
        }
    }

    private fun probeDurationSeconds(file: File): Double? {
        val output =
            runCommand(
                listOf(
                    "ffprobe",
                    "-v",
                    "error",
                    "-show_entries",
                    "format=duration",
                    "-of",
                    "default=noprint_wrappers=1:nokey=1",
                    file.absolutePath,
                ),
            )
        return output.trim().toDoubleOrNull()
    }

    // Best-effort уведомление karaoke-web об уже принятом решении по заданию (отказ по длительности —
    // никакой KaraokeProcess не создавался, поэтому обычный путь ack (executeFinalizeStemJob) не
    // сработает). Если запрос не прошёл — временный файл всё равно зачистится safety-net TTL-очисткой
    // на стороне karaoke-web.
    private fun ackRawFileConsumed(jobId: Long) {
        val baseUrl = Karaoke.stemJobsWebInternalUrl.trim().trimEnd('/')
        if (baseUrl.isBlank()) return
        try {
            val connection = URL("$baseUrl/api/internal/stemjobs/$jobId/ack").openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("X-Internal-Secret", Karaoke.stemJobsInternalSecret)
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.doOutput = false
            connection.responseCode
            connection.disconnect()
        } catch (e: Exception) {
            println("[StemJobPollScheduler] ackRawFileConsumed jobId=$jobId: ${e.message}")
        }
    }

    @Scheduled(fixedDelay = 5 * 60_000L, initialDelay = 60_000L)
    fun cleanup() {
        val database = Connection.remote()
        try {
            val pending = StemJob.loadPendingCleanup(database, KSS_APP, SAC_APP)
            pending.forEach { job ->
                runCatching { StemJobCleanup.cleanupJob(job, database) }.onFailure {
                    println("[StemJobPollScheduler] cleanup jobId=${job.id}: ${it.message}")
                }
            }
        } finally {
            try {
                database.getConnection()?.close()
            } catch (_: Exception) {
            }
        }
    }
}
