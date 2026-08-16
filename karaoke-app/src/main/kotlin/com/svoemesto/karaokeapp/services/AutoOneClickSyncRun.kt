package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.controllers.SyncOneClickResultDto
import java.time.Instant

/**
 * Запись о результатах одного тика автозапуска «Синхронизации в 1 клик» (spec 235).
 *
 * Хранится в in-memory `ConcurrentLinkedDeque<AutoOneClickSyncRun>` внутри
 * [AutoOneClickSyncScheduler] (последние ≤10 тиков). Не персистится в БД —
 * история начинается заново после каждого перезапуска `karaoke-app` (by design,
 * см. spec 235, A-007).
 *
 * Lifecycle: при создании `status = "RUNNING"`. В `finally`-блоке scheduler'а
 * переходит в `SUCCESS` (если per-target прошли без exceptions) или `FAILED`
 * (если бросилось исключение вне per-target; см. FR-016, SC-009).
 *
 * @property startedAt момент начала тика (`Instant.now()`)
 * @property finishedAt момент завершения тика (заполняется в `finally`)
 * @property status `"RUNNING" | "SUCCESS" | "FAILED"`
 * @property reason текст ошибки для `FAILED` (например, `"SQLException: connection refused"`); `null` для `SUCCESS` и `RUNNING`
 * @property totals суммарные `created/updated/deleted/moved` по всем `perTarget`
 * @property perTarget per-target результат, переиспользует [SyncOneClickResultDto] (без изменений)
 *
 * @see AutoOneClickSyncScheduler
 * @see AutoOneClickSyncStatusDto
 * @see livedocs/features/235-auto-sync-3h.md
 */
data class AutoOneClickSyncRun(
    val startedAt: Instant,
    val finishedAt: Instant? = null,
    val status: String = "RUNNING",
    val reason: String? = null,
    val totals: Totals = Totals(0, 0, 0, 0),
    val perTarget: List<SyncOneClickResultDto> = emptyList(),
)

/**
 * Суммарные счётчики `created/updated/deleted/moved` по всем `perTarget` одного тика.
 * Вложен в [AutoOneClickSyncRun] для удобства JSON-сериализации.
 *
 * @see AutoOneClickSyncRun
 * @see livedocs/features/235-auto-sync-3h.md
 */
data class Totals(
    val created: Int = 0,
    val updated: Int = 0,
    val deleted: Int = 0,
    val moved: Int = 0,
)
