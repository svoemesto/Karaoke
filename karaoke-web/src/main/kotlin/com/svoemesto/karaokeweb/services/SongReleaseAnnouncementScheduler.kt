package com.svoemesto.karaokeweb.services

import com.svoemesto.karaokeweb.WORKING_DATABASE

import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.SongReleaseAnnouncementService
import com.svoemesto.karaokeapp.services.StorageApiClient
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Периодическая проверка «песня вышла в эфир по расписанию» (specs/092-fix-auto-news-triggers) —
 * независимая от синхронизации таблиц (`MainController.doChangeRecords`) точка входа в тот же
 * идемпотентный `SongReleaseAnnouncementService.checkAndAnnounce`, что уже вызывается оттуда
 * (specs/089-auto-news-song-release). Закрывает разрыв «дата/время эфира наступили, но никто не
 * запускал синхронизацию» — без этого джоба новость появлялась бы только при следующем
 * администраторском клике «Синхронизация в 1 клик», иногда с задержкой в часы/дни.
 *
 * Периодичность (~5 минут) — согласованное с пользователем допустимое отставание от фактического
 * времени эфира (см. spec.md, раздел Clarifications). По образцу `StatsCacheScheduler`/
 * `StemJobTempCleanupScheduler` из этого же пакета: ошибки логируются и не прерывают следующий тик.
 *
 * @see docs/features/dual-db-sync.md
 */
@Component
class SongReleaseAnnouncementScheduler(
    private val storageService: KaraokeStorageService,
    private val storageApiClient: StorageApiClient,
) {
    @Scheduled(fixedDelay = 5 * 60_000L, initialDelay = 60_000L)
    fun checkOnAir() {
        try {
            SongReleaseAnnouncementService.checkAndAnnounce(WORKING_DATABASE, storageService, storageApiClient)
        } catch (e: Exception) {
            println("[SongReleaseAnnouncementScheduler] checkOnAir error: ${e.message}")
        }
    }
}
