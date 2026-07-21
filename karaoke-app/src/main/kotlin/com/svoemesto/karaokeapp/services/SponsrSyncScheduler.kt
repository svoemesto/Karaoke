package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.Connection
import com.svoemesto.karaokeapp.Karaoke
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Периодический запуск [SponsrSyncService.syncViaScraping] — работает только пока запущен
 * karaoke-app (admin-машина; тот же инвариант, что и у checkLastAlbum/Yandex-музыки — это
 * desktop-приложение, а не всегда работающий сервис). Целится в Connection.remote() — реальные
 * подписчики регистрируются на прод-сайте, не в локальной dev-БД.
 *
 * Молчаливо no-op, если Karaoke.sponsrSubscribersUrl не настроен (см. SponsrSyncService) — не
 * спамит ошибками, пока Sponsr-скрейпинг не откалиброван под реальный кабинет.
 */

/**
 * Класс Sponsr Sync Scheduler.
 *
 * @see docs/features/async-process-queue.md
 */
@Component
class SponsrSyncScheduler {
    @Scheduled(fixedRate = 12 * 3600_000L, initialDelay = 5 * 60_000L)
    fun run() {
        if (Karaoke.sponsrSubscribersUrl.isBlank()) return
        // Connection.remote() открывает новое физическое JDBC-соединение — закрыть явно после
        // использования (тот же инвариант, что withDb в TariffsController/SiteUsersController).
        val db = Connection.remote()
        try {
            val result = SponsrSyncService.syncViaScraping(db, KSS_APP, SAC_APP)
            result.messages.forEach { println("[SponsrSyncScheduler] $it") }
        } finally {
            try {
                db.getConnection()?.close()
            } catch (_: Exception) {
            }
        }
    }
}
