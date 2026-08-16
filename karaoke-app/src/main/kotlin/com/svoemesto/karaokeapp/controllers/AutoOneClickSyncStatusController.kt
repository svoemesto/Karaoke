package com.svoemesto.karaokeapp.controllers

import com.svoemesto.karaokeapp.controllers.dto.AutoOneClickSyncStatusDto
import com.svoemesto.karaokeapp.services.AutoOneClickSyncScheduler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Контроллер для чтения статуса автозапуска «Синхронизации в 1 клик» (spec 235, FR-009).
 *
 * Единственный endpoint — GET /api/sync/auto-status, возвращает
 * [AutoOneClickSyncStatusDto] для UI-блока «Автозапуск» в
 * webvue3/src/components/Sync/SyncTable.vue. permitAll — /api/sync/auto-status
 * не входит в /api/private (см. com.svoemesto.karaokeapp.config.SecurityConfig).
 *
 * Не делает: не отдаёт SSE-push (Q2 в Clarifications — REST-only by design).
 * UI обновляет блок при монтировании SyncTable.vue через loadSyncAutoStatusPromise
 * и по F5.
 *
 * @see AutoOneClickSyncScheduler.getStatus
 * @see AutoOneClickSyncStatusDto
 * @see livedocs/features/235-auto-sync-3h.md
 */
@RestController
@RequestMapping("/api/sync")
class AutoOneClickSyncStatusController(
    private val scheduler: AutoOneClickSyncScheduler,
) {
    /**
     * Возвращает текущий статус автозапуска.
     *
     * @return 200 OK + JSON [AutoOneClickSyncStatusDto]. Всегда 200,
     *   даже если `enabled=false` или history пуста (поля `lastRun` /
     *   `nextRunEstimate` могут быть `null`).
     */
    @GetMapping("/auto-status")
    fun getAutoStatus(): AutoOneClickSyncStatusDto = scheduler.getStatus()
}
