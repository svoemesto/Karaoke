package com.svoemesto.karaokeapp.controllers

import com.svoemesto.karaokeapp.services.cleanup.ArtifactCleanupClass
import com.svoemesto.karaokeapp.services.cleanup.ArtifactCleanupService
import com.svoemesto.karaokeapp.services.cleanup.CleanupException
import com.svoemesto.karaokeapp.services.cleanup.CleanupPlan
import com.svoemesto.karaokeapp.services.cleanup.CleanupRunStatus
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * API разовой чистки артефактов песни (specs/544-song-artifact-cleanup, FR-013).
 *
 * Контракт: `specs/544-song-artifact-cleanup/contracts/cleanup-api.md`. Базовый префикс —
 * `/api/cleanup`; эндпоинты доступны напрямую на бэкенде `:8898` (через nginx `:7906` не
 * проксируются — AGENTS.md Pass 381). Публичный сайт и фронтенды этот контракт не используют.
 *
 * Правила: `plan` ничего не удаляет; `apply` без отпечатка отклоняется; защищённые классы
 * отклоняются всегда; удаление идёт вне HTTP-потока (`202` + опрос `status`).
 *
 * @see specs/544-song-artifact-cleanup/contracts/cleanup-api.md
 */
@RestController
@RequestMapping("/api/cleanup")
class CleanupController {
    private val service = ArtifactCleanupService()

    /**
     * Список классов из реестра с вердиктами и ожидаемыми объёмами.
     *
     * @see specs/544-song-artifact-cleanup/data-model.md
     */
    @GetMapping("/classes")
    fun classes(): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(mapOf("classes" to service.classes().map { it.toDto() }))

    /**
     * Сухой прогон по классу: отчёт «что будет удалено» + отпечаток утверждаемого набора.
     *
     * Тело: `{"classId": "DEMO_MP4"}`.
     *
     * @see specs/544-song-artifact-cleanup/contracts/cleanup-api.md
     */
    @PostMapping("/plan")
    fun plan(
        @RequestBody body: Map<String, String>,
    ): ResponseEntity<Map<String, Any>> =
        handle {
            val plan = service.plan(body["classId"].orEmpty())
            ResponseEntity.ok(plan.toDto())
        }

    /**
     * Полный список путей отчёта отдельной выгрузкой (отчёт на десятки тысяч строк не
     * кладётся в JSON целиком).
     *
     * @see specs/544-song-artifact-cleanup/contracts/cleanup-api.md
     */
    @GetMapping("/plan/{planId}/entries.csv", produces = [MediaType.TEXT_PLAIN_VALUE])
    fun entriesCsv(
        @PathVariable planId: String,
    ): ResponseEntity<String> =
        try {
            val plan = service.planById(planId)
            val csv =
                buildString {
                    append("path\tsizeBytes\treason\n")
                    plan.entries.forEach { append("${it.path}\t${it.sizeBytes}\t${it.reason}\n") }
                }
            ResponseEntity.ok(csv)
        } catch (e: CleanupException) {
            ResponseEntity.status(e.code).body("${e.errorCode}: ${e.message}")
        } catch (e: Exception) {
            val body = "internal_error: ${e.message ?: "внутренняя ошибка"}"
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body)
        }

    /**
     * Запуск удаления по утверждённому отчёту. Отвечает `202` и `runId`; прогресс — в [status].
     *
     * Тело: `{"planId", "fingerprint", "approvedBy", "confirm": true}`.
     *
     * @see specs/544-song-artifact-cleanup/data-model.md
     */
    @PostMapping("/apply")
    fun apply(
        @RequestBody body: Map<String, Any?>,
    ): ResponseEntity<Map<String, Any>> =
        handle {
            val run =
                service.apply(
                    planId = body["planId"]?.toString().orEmpty(),
                    fingerprint = body["fingerprint"]?.toString().orEmpty(),
                    approvedBy = body["approvedBy"]?.toString().orEmpty(),
                    confirm = body["confirm"] == true || body["confirm"]?.toString() == "true",
                )
            ResponseEntity.accepted().body(run.toDto())
        }

    /**
     * Статус прогона: состояние и счётчики.
     *
     * @see specs/544-song-artifact-cleanup/data-model.md
     */
    @GetMapping("/status")
    fun status(
        @RequestParam runId: String,
    ): ResponseEntity<Map<String, Any>> =
        handle { ResponseEntity.ok(service.status(runId).toDto()) }

    private fun handle(block: () -> ResponseEntity<Map<String, Any>>): ResponseEntity<Map<String, Any>> =
        try {
            block()
        } catch (e: CleanupException) {
            ResponseEntity
                .status(e.code)
                .body(mapOf("error" to e.errorCode, "message" to e.message))
        } catch (e: Exception) {
            ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("error" to "internal_error", "message" to (e.message ?: "внутренняя ошибка")))
        }

    private fun ArtifactCleanupClass.toDto(): Map<String, Any> =
        mapOf(
            "classId" to name,
            "title" to title,
            "verdict" to verdicts.first().name,
            "verdicts" to verdicts.map { it.name },
            "protected" to isProtected,
            "cleanupOrder" to cleanupOrder,
            "expectedFileCount" to expectedFileCount,
            "expectedBytes" to expectedBytes,
            "note" to note,
        )

    private fun CleanupPlan.toDto(): Map<String, Any> =
        mapOf(
            "planId" to planId,
            "classId" to classId,
            "generatedAt" to generatedAt,
            "fileCount" to fileCount,
            "totalBytes" to totalBytes,
            "fingerprint" to fingerprint,
            "warnings" to warnings,
            "sample" to
                entries.take(SAMPLE_SIZE).map {
                    mapOf("path" to it.path, "sizeBytes" to it.sizeBytes, "reason" to it.reason)
                },
        )

    private fun CleanupRunStatus.toDto(): Map<String, Any> =
        mapOf(
            "runId" to runId,
            "classId" to classId,
            "state" to state.name,
            "plannedCount" to plannedCount,
            "deletedCount" to deletedCount,
            "failedCount" to failedCount,
            "freedBytes" to freedBytes,
            "journalPath" to journalPath,
            "approvedBy" to approvedBy,
        )

    private companion object {
        /** Сколько записей отчёта кладём в JSON ответа (полный список — в entries.csv). */
        const val SAMPLE_SIZE = 50
    }
}
