package com.svoemesto.karaokeapp.controllers

import com.svoemesto.karaokeapp.services.ArtifactIntegrityScheduler
import com.svoemesto.karaokeapp.services.DurationBackfillService
import com.svoemesto.karaokeapp.services.SidecarBackfillService
import com.svoemesto.karaokeapp.services.SongArtifactRepairService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 674 (FR-013/FR-014): админский API ремонта и отчёта целостности.
 *
 * @see specs/674-kortege-execution/contracts/admin-api.md §4–5
 */
@RestController
@RequestMapping("/api/admin/artifacts")
class ArtifactAdminController(
    private val repairService: SongArtifactRepairService,
    private val integrityScheduler: ArtifactIntegrityScheduler,
    private val sidecarBackfill: SidecarBackfillService,
    private val durationBackfill: DurationBackfillService,
) {
    data class RepairRequest(
        val songIds: List<Long> = emptyList(),
        val dryRun: Boolean = true,
        val includeKeyBpm: Boolean = true,
    )

    @PostMapping("/repair")
    fun repair(
        @RequestBody request: RepairRequest,
    ): ResponseEntity<Any> {
        if (request.songIds.isEmpty() || request.songIds.size > 100) {
            return ResponseEntity.badRequest().body(mapOf("error" to "songIds: 1..100"))
        }
        return if (request.dryRun) {
            ResponseEntity.ok(
                mapOf(
                    "dryRun" to true,
                    "findings" to repairService.findings(request.songIds),
                ),
            )
        } else {
            ResponseEntity.ok(
                mapOf(
                    "dryRun" to false,
                    "tuples" to repairService.enqueue(request.songIds, request.includeKeyBpm),
                ),
            )
        }
    }

    data class BackfillRequest(
        val songIds: List<Long> = emptyList(),
        val dryRun: Boolean = true,
    )

    @PostMapping("/backfill-sidecar")
    fun backfillSidecar(
        @RequestBody request: BackfillRequest,
    ): ResponseEntity<Any> {
        if (request.songIds.isEmpty() || request.songIds.size > 100) {
            return ResponseEntity.badRequest().body(mapOf("error" to "songIds: 1..100"))
        }
        return ResponseEntity.ok(
            mapOf(
                "dryRun" to request.dryRun,
                "results" to sidecarBackfill.run(request.songIds, request.dryRun),
            ),
        )
    }

    @GetMapping("/integrity")
    fun integrity(): ResponseEntity<Any> {
        val report =
            integrityScheduler.lastReport
                ?: return ResponseEntity.noContent().build()
        return ResponseEntity.ok(report)
    }

    data class DurationBackfillRequest(
        val songIds: List<Long> = emptyList(),
        val dryRun: Boolean = true,
    )

    /**
     * Разовая починка длительности песен (`song_ms = 0`) — инцидент 2026-11-13.
     *
     * Пересчитываются только песни с нулевой длительностью; у остальных значение не трогается.
     */
    @PostMapping("/backfill-duration")
    fun backfillDuration(
        @RequestBody request: DurationBackfillRequest,
    ): ResponseEntity<Any> {
        if (request.songIds.isEmpty() || request.songIds.size > 100) {
            return ResponseEntity.badRequest().body(mapOf("error" to "songIds: 1..100"))
        }
        return ResponseEntity.ok(
            mapOf(
                "dryRun" to request.dryRun,
                "results" to durationBackfill.run(request.songIds, request.dryRun),
            ),
        )
    }
}
