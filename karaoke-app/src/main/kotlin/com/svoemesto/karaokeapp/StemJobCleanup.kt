package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.model.StemJob
import com.svoemesto.karaokeapp.model.StemJobMode
import com.svoemesto.karaokeapp.model.StemJobStatus
import com.svoemesto.karaokeapp.services.KSS_APP
import com.svoemesto.karaokeapp.services.SAC_APP
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.sql.Timestamp

/**
 * Общая логика остановки/удаления премиум-задания «Минусовки» (StemJob) — переиспользуется
 * периодической уборкой (StemJobPollScheduler.cleanup, протухшие DONE / delete_requested) и
 * админ-панелью webvue3 (StemJobsAdminController — немедленная остановка/удаление по клику).
 */
object StemJobCleanup {
    /**
     * Удаляет файлы задания (объекты MinIO, если уже загружены — status=DONE) и саму строку
     * tbl_stem_jobs. НЕ трогает текущий выполняющийся процесс — если задание ещё WORKING, сначала
     * вызвать stopRunningWork()/adminStop(), иначе поллер может продолжить писать в уже удалённую
     * строку (см. adminDeleteNow).
     *
     * Удаляются: объекты из ПРИВАТНОГО бакета `stemjobs` (спека 561), legacy-объекты из
     * публичного бакета `karaoke` (префикс `stemjobs/` — до правки приватные объекты лежали там
     * и были анонимно читаемы; без их удаления они «пережили» бы свою уборку) и локальные копии
     * на karaoke-web (`purge`).
     */
    fun cleanupJob(
        job: StemJob,
        database: KaraokeConnection,
    ) {
        if (job.status == StemJobStatus.DONE) {
            val bucket = StemJob.STORAGE_BUCKET
            runCatching {
                SAC_APP.deleteFile(bucket, StemJob.objectKey(job.id, StemJob.ORIGINAL_STEM, job.originalExt)).block()
            }
            StemJobMode.stemNames(job.mode).forEach { stem ->
                runCatching { SAC_APP.deleteFile(bucket, StemJob.objectKey(job.id, stem, "mp3")).block() }
            }
            // Legacy-объекты из публичного бакета — иначе они остались бы анонимно читаемыми.
            runCatching {
                SAC_APP
                    .deleteFile(
                        StemJob.PUBLIC_STORAGE_BUCKET,
                        StemJob.legacyObjectKey(job.id, StemJob.ORIGINAL_STEM, job.originalExt),
                    ).block()
            }
            StemJobMode.stemNames(job.mode).forEach { stem ->
                runCatching {
                    SAC_APP
                        .deleteFile(
                            StemJob.PUBLIC_STORAGE_BUCKET,
                            StemJob.legacyObjectKey(job.id, stem, "mp3"),
                        ).block()
                }
            }
            purgeWebCopies(job.id)
        }
        // На всякий случай подчищаем локальный temp (обычно уже удалён финализацией/провалом poll'а).
        File("$PATH_TO_TEMP_STEMJOB_FOLDER/${job.id}").deleteRecursively()
        StemJob.delete(job.id, database)
    }

    /**
     * Просит `karaoke-web` удалить свои локальные копии задания (приватный каталог результатов
     * и сырой файл загрузки) — `POST /api/internal/stemjobs/{id}/purge`. Best-effort: если запрос
     * не прошёл, копии удалит safety-net-очистка на стороне karaoke-web (StemJobTempCleanupScheduler).
     */
    private fun purgeWebCopies(jobId: Long) {
        val baseUrl = Karaoke.stemJobsWebInternalUrl.trim().trimEnd('/')
        if (baseUrl.isBlank()) return
        try {
            val connection = URL("$baseUrl/api/internal/stemjobs/$jobId/purge").openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("X-Internal-Secret", Karaoke.stemJobsInternalSecret)
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.doOutput = false
            connection.responseCode
            connection.disconnect()
        } catch (e: Exception) {
            println("[StemJobCleanup] purgeWebCopies jobId=$jobId: ${e.message}")
        }
    }

    /**
     * Останавливает выполняющийся demucs-пайплайн задания (если он сейчас в работе) — docker kill
     * контейнера stemjob-{id} (уникальное имя, см. StemJobProcessing.argsStemJobDemucs) + принудительное
     * убийство OS-подпроцесса потока THREAD_LANE_STEM_JOBS, если он сейчас обрабатывает именно это
     * задание (лейн выделенный, поэтому в один момент там максимум один активный шаг), + удаление ВСЕХ
     * оставшихся шагов этого задания из tbl_processes (LOCAL) — чтобы очередь не продолжила/не
     * переиграла задание. НЕ трогает никакой другой лейн/задание — в отличие от
     * KaraokeProcessWorker.forceStop(), который бы остановил вообще всё, включая обычный пайплайн
     * выпуска песен.
     *
     * Не пытается аккуратно отличить «докер ещё не стартовал»/«уже упал сам» — docker kill на
     * несуществующий контейнер и удаление уже отсутствующих строк безопасны (ignoreErrors/no-op).
     */
    fun stopRunningWork(jobId: Long) {
        runCommand(listOf("docker", "kill", "stemjob-$jobId"), ignoreErrors = true)

        // BR-259: сопоставление по ТОЧНОЙ строке описания не видело цепочку
        // CPU-повтора — у неё описание «Демукс премиум-задания #<id> (повтор без
        // GPU)». В итоге `destroyForcibly`/`interrupt` не вызывались, строки
        // `tbl_processes` повтора оставались, и повтор продолжал работать после
        // «Стоп»/«Удалить» (а его финализация перезаписывала статус задания).
        // Теперь — по ПРЕФИКСУ с id задания.
        val descriptionPrefix = stemJobDescriptionPrefix(jobId)
        val thread = KaraokeProcessWorker.threadsMap[KaraokeProcess.THREAD_LANE_STEM_JOBS]
        if (thread != null && thread.isAlive && isStemJobDescription(thread.karaokeProcess?.description, jobId)) {
            runCatching { thread.osProcess?.destroyForcibly() }
            runCatching { thread.interrupt() }
        }

        // Удаляем ВСЕ шаги этого задания (WAITING — ещё не начатые, и только что остановленный
        // WORKING/уже упавший) — что бы поток ни успел записать в свою строку перед смертью, значения
        // не имеют смысла: строка удаляется, а авторитетный статус задания правит executeFinalizeStemJob
        // проставлять уже некуда, т.к. вызывающая сторона (adminStop/adminDeleteNow) сама пишет
        // финальный статус в tbl_stem_jobs.
        //
        // BR-259: LIKE-фильтр по префиксу (ловит и основной шаг, и CPU-повтор),
        // а точная проверка — в Kotlin (чтобы не задеть задание с «другим» id,
        // например #42 vs #420).
        KaraokeProcess
            .loadList(
                mapOf(
                    "process_description" to descriptionPrefix,
                    "thread_id" to KaraokeProcess.THREAD_LANE_STEM_JOBS.toString(),
                ),
                Connection.local(),
            ).filter { isStemJobDescription(it.description, jobId) }
            .forEach { runCatching { KaraokeProcess.delete(it.id, Connection.local()) } }

        File("$PATH_TO_TEMP_STEMJOB_FOLDER/$jobId").deleteRecursively()
    }

    /**
     * BR-259: префикс описания шага премиум-задания по его id.
     *
     * Основной шаг: «Демукс премиум-задания #42»; CPU-повтор:
     * «Демукс премиум-задания #42 (повтор без GPU)». Префикс «Демукс премиум-задания #42»
     * покрывает оба — и не задевает чужие задания (у другого id другой префикс).
     */
    internal fun stemJobDescriptionPrefix(jobId: Long): String = "Демукс премиум-задания #$jobId"

    /**
     * BR-259: принадлежит ли описание шага именно этому заданию.
     *
     * Совпадение по префиксу + границе: описание должно либо РОВНО совпадать с
     * префиксом, либо начинаться с него и продолжаться пробелом (суффикс «(повтор
     * без GPU)»). Без границы задание #42 совпало бы с #420.
     */
    internal fun isStemJobDescription(
        description: String?,
        jobId: Long,
    ): Boolean {
        if (description == null) return false
        val prefix = stemJobDescriptionPrefix(jobId)
        if (description == prefix) return true
        return description.startsWith("$prefix ")
    }

    /** Останавливает задание (если ещё выполняется) и помечает его ERROR — файлы НЕ удаляются, пользователь/админ может решить удалить отдельно. */
    fun adminStop(
        jobId: Long,
        database: KaraokeConnection,
    ): Boolean {
        val job = StemJob.getById(jobId, database, KSS_APP, SAC_APP) ?: return false
        if (job.status != StemJobStatus.WAITING && job.status != StemJobStatus.WORKING) return false
        stopRunningWork(jobId)
        job.status = StemJobStatus.ERROR
        job.errorMessage = "Остановлено администратором"
        job.finishedAt = Timestamp(System.currentTimeMillis())
        job.save()
        return true
    }

    /** Останавливает (если выполняется) и немедленно удаляет — не дожидаясь периодической уборки. */
    fun adminDeleteNow(
        jobId: Long,
        database: KaraokeConnection,
    ): Boolean {
        val job = StemJob.getById(jobId, database, KSS_APP, SAC_APP) ?: return false
        if (job.status == StemJobStatus.WAITING || job.status == StemJobStatus.WORKING) {
            stopRunningWork(jobId)
        }
        cleanupJob(job, database)
        return true
    }
}
