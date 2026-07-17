package com.svoemesto.karaokeweb.services

import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Safety-net очистка temp-директории загрузок «Создать минусовку» (см. PublicStemJobController,
 * InternalStemJobController) — независимо от нормального пути (ack от karaoke-app после
 * успешного/неудачного забора файла), удаляет файлы старше STALE_HOURS. Страховка на случай, если
 * karaoke-app недоступен (десктоп админа выключен) или ack-запрос не дошёл по сети — без неё диск
 * karaoke-web медленно заполнялся бы забытыми загрузками.
 */
@Component
class StemJobTempCleanupScheduler(
    @Value("\${stemjobs.temp-dir:/tmp/stemjobs}") private val tempDir: String,
) {
    companion object {
        private const val STALE_HOURS = 2L
    }

    @Scheduled(fixedDelay = 30 * 60_000L, initialDelay = 60_000L)
    fun cleanup() {
        val dir = File(tempDir)
        if (!dir.exists()) return
        val staleBeforeMs = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(STALE_HOURS)
        dir.listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < staleBeforeMs) {
                runCatching { file.delete() }
            }
        }
    }
}
