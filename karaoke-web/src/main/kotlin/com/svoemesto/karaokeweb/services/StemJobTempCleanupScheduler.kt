package com.svoemesto.karaokeweb.services

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Safety-net очистка temp-директории загрузок «Создать минусовку» (см. PublicStemJobController,
 * InternalStemJobController) — независимо от нормального пути (ack от karaoke-app после
 * успешного/неудачного забора файла), удаляет файлы старше STALE_HOURS. Страховка на случай, если
 * karaoke-app недоступен (десктоп админа выключен) или ack-запрос не дошёл по сети — без неё диск
 * karaoke-web медленно заполнялся бы забытыми загрузками.
 *
 * Спека 561 (OpenProject #361): дополнительно чистится каталог РЕЗУЛЬТАТОВ задания
 * (`stemjobs.result-dir/<jobId>/`) — приватные объекты, переданные karaoke-app по внутреннему
 * каналу. Их срок жизни — TTL задания (StemJob.RETENTION_HOURS = 24 ч), поэтому у них свой
 * порог (RESULT_STALE_HOURS = TTL + запас), а не двухчасовой STALE_HOURS сырых загрузок.
 */
@Component
class StemJobTempCleanupScheduler(
    @Value("\${stemjobs.temp-dir:/tmp/stemjobs}") private val tempDir: String,
    private val resultFiles: StemJobResultFiles,
) {
    companion object {
        private const val STALE_HOURS = 2L

        // TTL результата задания (24 ч) + запас на «фантомные» каталоги и на часовой пояс.
        private const val RESULT_STALE_HOURS = 26L

        /**
         * BR-521 (аудит #336, OpenProject #401): своя категория журнала — по логам чистки
         * видно, что temp-директория переполняется.
         */
        private val log = LoggerFactory.getLogger("infra.stemjobs.cleanup")
    }

    /**
     * BR-521: счётчик неудачных удалений за время работы процесса.
     *
     * Раньше `runCatching { file.delete() }` проглатывал результат: файл, который не
     * удалился (занят другим процессом, нет прав, read-only FS), не давал никакого
     * сигнала — чистка молча переставала работать, а диск тем временем заполнялся.
     */
    private val failedDeletions = AtomicLong(0)

    /**
     * BR-521: метрика «неудачных удалений» (наружу — для мониторинга диска).
     *
     * @return число неудачных удалений с момента старта процесса.
     */
    fun failedDeletionCount(): Long = failedDeletions.get()

    @Scheduled(fixedDelay = 30 * 60_000L, initialDelay = 60_000L)
    fun cleanup() {
        val dir = File(tempDir)
        if (dir.exists()) {
            val staleBeforeMs = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(STALE_HOURS)
            dir.listFiles()?.forEach { file ->
                if (file.isFile && file.lastModified() < staleBeforeMs) {
                    // BR-021: результат удаления ПРОВЕРЯЕТСЯ — раньше runCatching проглатывал
                    // его, и «чистка не работает» было невозможно заметить.
                    val deleted = runCatching { file.delete() }.getOrDefault(false)
                    if (!deleted) {
                        failedDeletions.incrementAndGet()
                        log.warn("stemjobs:cleanup:delete-returned-false file={}", file.name)
                    }
                }
            }
        }
        val resultRoot = resultFiles.root
        if (!resultRoot.exists()) return
        val resultStaleBeforeMs = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(RESULT_STALE_HOURS)
        resultRoot.listFiles()?.forEach { jobDir ->
            if (jobDir.isDirectory && jobDir.lastModified() < resultStaleBeforeMs) {
                val deleted = runCatching { jobDir.deleteRecursively() }.getOrDefault(false)
                if (!deleted) {
                    failedDeletions.incrementAndGet()
                    log.warn("stemjobs:cleanup:delete-returned-false dir={}", jobDir.name)
                }
            }
        }
    }
}
