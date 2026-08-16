package com.svoemesto.karaokeapp.controllers.dto

import com.svoemesto.karaokeapp.controllers.SyncOneClickResultDto
import com.svoemesto.karaokeapp.services.AutoOneClickSyncRun
import com.svoemesto.karaokeapp.services.Totals

/**
 * DTO для `GET /api/sync/auto-status` (spec 235, FR-009).
 *
 * Возвращается `AutoOneClickSyncStatusController` для UI-блока «Автозапуск»
 * в `webvue3/src/components/Sync/SyncTable.vue`. Все поля всегда непустые
 * (`enabled`/`intervalMs`/`initialDelayMs` берутся из `KaraokeProperties`,
 * `lastRun`/`nextRunEstimate` могут быть `null` если история пуста или
 * автозапуск выключен).
 *
 * @property enabled текущее значение `KaraokeProperties.autoOneClickSyncEnabled`
 * @property intervalMs текущее значение `KaraokeProperties.autoOneClickSyncIntervalMs`
 * @property initialDelayMs текущее значение `KaraokeProperties.autoOneClickSyncInitialDelayMs`
 * @property lastRun самый последний тик или `null` если история пуста
 * @property last10 до 10 последних тиков, **newest first** (для UI-списка)
 * @property nextRunEstimate ISO-8601 момент следующего тика (вычисляется как `lastRun.finishedAt + intervalMs` или `appStartTime + initialDelayMs`); `null` если `enabled=false`
 *
 * @see AutoOneClickSyncRun
 * @see AutoOneClickSyncRunDto
 * @see livedocs/features/235-auto-sync-3h.md
 */
data class AutoOneClickSyncStatusDto(
    val enabled: Boolean,
    val intervalMs: Long,
    val initialDelayMs: Long,
    val lastRun: AutoOneClickSyncRunDto?,
    val last10: List<AutoOneClickSyncRunDto>,
    val nextRunEstimate: String?,
)

/**
 * DTO одного тика (используется внутри [AutoOneClickSyncStatusDto.lastRun] и [AutoOneClickSyncStatusDto.last10]).
 *
 * Структура идентична [AutoOneClickSyncRun], но `Instant` сериализуется как ISO-8601 string
 * (для совместимости с `Instant.toString()` и JS `new Date(...)`).
 *
 * @property startedAt ISO-8601
 * @property finishedAt ISO-8601 или `null` для `RUNNING` (на практике не отдаётся — `RUNNING` transient)
 * @property status `"RUNNING" | "SUCCESS" | "FAILED"`
 * @property reason текст ошибки для `FAILED`; `null` для `SUCCESS` и `RUNNING`
 * @property totals суммарные `created/updated/deleted/moved` по всем `perTarget`
 * @property perTarget переиспользует [SyncOneClickResultDto] (без изменений)
 *
 * @see AutoOneClickSyncRun
 * @see TotalsDto
 * @see livedocs/features/235-auto-sync-3h.md
 */
data class AutoOneClickSyncRunDto(
    val startedAt: String,
    val finishedAt: String?,
    val status: String,
    val reason: String?,
    val totals: TotalsDto,
    val perTarget: List<SyncOneClickResultDto>,
)

/**
 * DTO для [Totals] (вложен в [AutoOneClickSyncRunDto]). Поля — `Int` (а не `List<String>` как в [SyncOneClickResultDto]),
 * потому что это счётчики, а не ID записей.
 *
 * @see AutoOneClickSyncRunDto
 * @see Totals
 * @see livedocs/features/235-auto-sync-3h.md
 */
data class TotalsDto(
    val created: Int,
    val updated: Int,
    val deleted: Int,
    val moved: Int,
)

/**
 * Конвертеры из runtime value-классов в JSON DTO.
 * Используются в [com.svoemesto.karaokeapp.controllers.AutoOneClickSyncStatusController].
 *
 * @see livedocs/features/235-auto-sync-3h.md
 */
object AutoOneClickSyncDtos {
    /**
     * Конвертирует [AutoOneClickSyncRun] в [AutoOneClickSyncRunDto] для JSON-сериализации.
     * `Instant` → ISO-8601 string (`Instant.toString()`).
     */
    fun toDto(run: AutoOneClickSyncRun): AutoOneClickSyncRunDto =
        AutoOneClickSyncRunDto(
            startedAt = run.startedAt.toString(),
            finishedAt = run.finishedAt?.toString(),
            status = run.status,
            reason = run.reason,
            totals =
                TotalsDto(
                    created = run.totals.created,
                    updated = run.totals.updated,
                    deleted = run.totals.deleted,
                    moved = run.totals.moved,
                ),
            perTarget = run.perTarget,
        )

    /**
     * Конвертирует `Iterable<AutoOneClickSyncRun>` в `List<AutoOneClickSyncRunDto>`,
     * сохраняя порядок. Используется для `last10` — `ConcurrentLinkedDeque` обходится
     * через `descendingIterator()` для newest-first.
     */
    fun toDtos(runs: Iterable<AutoOneClickSyncRun>): List<AutoOneClickSyncRunDto> = runs.map { toDto(it) }
}
