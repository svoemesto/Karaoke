package com.svoemesto.karaokeweb.services

import com.svoemesto.karaokeweb.WORKING_DATABASE

import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.SongReleaseAnnouncementService
import com.svoemesto.karaokeapp.services.StorageApiClient
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Периодическая проверка «песня вышла в эфир по расписанию» (specs/101-song-news-flag, заменяет
 * специфику specs/092-fix-auto-news-triggers) — единственная точка кода, создающая новость «в
 * эфире» (FR-006/FR-007 spec.md 101): синхронизация и апрув задания редактора больше её не создают.
 * Вызывает `SongReleaseAnnouncementService.checkOnAirWindow`, которая рассматривает только песни,
 * чья дата/время эфира попали в последнее скользящее окно (~10 минут) — без отдельной таблицы учёта.
 * Закрывает разрыв «дата/время эфира наступили, но никто не запускал синхронизацию» — без этого
 * джоба новость никогда не появилась бы автоматически (см. spec.md 101, FR-009 — пропуск окна
 * недоступности сервиса допустим, задним числом новость не создаётся, администратор может создать
 * её вручную).
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
            SongReleaseAnnouncementService.checkOnAirWindow(WORKING_DATABASE, storageService, storageApiClient)
        } catch (e: Exception) {
            println("[SongReleaseAnnouncementScheduler] checkOnAir error: ${e.message}")
        }
    }
}
