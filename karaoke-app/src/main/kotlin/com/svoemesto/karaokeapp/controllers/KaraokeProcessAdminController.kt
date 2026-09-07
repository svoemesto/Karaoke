package com.svoemesto.karaokeapp.controllers

import com.svoemesto.karaokeapp.WORKING_DATABASE
import com.svoemesto.karaokeapp.services.KaraokeProcessAdminService
import org.springframework.context.annotation.DependsOn
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody

/**
 * Админ-контроллер процессов (specs/315-admin-ui-karaoke-process-v5).
 *
 * 6 endpoints под `/api/admin/processes/`. Auth — `permitAll()` (как весь webvue3,
 * SecurityConfig.kt:43-45). Паттерн — `StemJobsAdminController` (Lesson #3 iter #1).
 *
 * @DependsOn("karaokeAppService") — Lesson #4 iter #1: на service И controller.
 *
 * @see specs/315-admin-ui-karaoke-process-v5/spec.md
 */
@Controller
@RequestMapping("/api/admin/processes")
@DependsOn("karaokeAppService")
class KaraokeProcessAdminController(
    private val service: KaraokeProcessAdminService,
) {
    private val db = WORKING_DATABASE

    /** GET /api/admin/processes — список с фильтрацией. */
    @GetMapping
    @ResponseBody
    fun list(
        @RequestParam(required = false) status: List<String>?,
        @RequestParam(required = false) type: List<String>?,
        @RequestParam(required = false) threadId: Int?,
        @RequestParam(required = false) chainId: Long?,
        @RequestParam(required = false, defaultValue = "false") includeDeleted: Boolean,
        @RequestParam(required = false) name: String?,
        @RequestParam(required = false, defaultValue = "true") topLevelOnly: Boolean,
        @RequestParam(required = false) parentId: Long?,
        @RequestParam(required = false, defaultValue = "100") limit: Int,
        @RequestParam(required = false, defaultValue = "0") offset: Int,
    ): ProcessListResultBody {
        val filters =
            KaraokeProcessAdminService.ProcessFilters(
                status = status ?: emptyList(),
                type = type ?: emptyList(),
                threadId = threadId,
                chainId = chainId,
                includeDeleted = includeDeleted,
                name = name,
                topLevelOnly = topLevelOnly,
                parentId = parentId,
                limit = limit,
                offset = offset,
            )
        val result = service.loadProcesses(db, filters)
        return ProcessListResultBody(total = result.total, items = result.items)
    }

    /** GET /api/admin/processes/{id} — один процесс. */
    @GetMapping("/{id}")
    @ResponseBody
    fun get(
        @PathVariable id: Int,
    ): Any =
        service.loadProcess(id, db) ?: throw KaraokeProcessAdminService.ProcessNotFoundException(id)

    /** POST /api/admin/processes/{id}/edit — редактирование. */
    @PostMapping("/{id}/edit")
    @ResponseBody
    fun edit(
        @PathVariable id: Int,
        @RequestBody changes: Map<String, Any?>,
        @RequestHeader(value = "X-Admin-Username", required = false) username: String?,
    ): Any = service.editProcess(id, changes, actor(username), db)

    /** POST /api/admin/processes/{id}/delete — soft-delete + cancel/stop. */
    @PostMapping("/{id}/delete")
    @ResponseBody
    fun delete(
        @PathVariable id: Int,
        @RequestHeader(value = "X-Admin-Username", required = false) username: String?,
    ): Map<String, Any> {
        service.deleteProcess(id, actor(username), db)
        return mapOf("ok" to true)
    }

    /** POST /api/admin/processes/{id}/retry — retry ERROR-процесса. */
    @PostMapping("/{id}/retry")
    @ResponseBody
    fun retry(
        @PathVariable id: Int,
        @RequestHeader(value = "X-Admin-Username", required = false) username: String?,
    ): Any = service.retryProcess(id, actor(username), db)

    /** GET /api/admin/processes/{id}/audit?days=30 — audit-лог. */
    @GetMapping("/{id}/audit")
    @ResponseBody
    fun audit(
        @PathVariable id: Int,
        @RequestParam(required = false, defaultValue = "30") days: Int,
    ): Map<String, Any> = mapOf("items" to service.loadAudit(id, days, db))

    /** 404 — процесс не найден. */
    @ExceptionHandler(KaraokeProcessAdminService.ProcessNotFoundException::class)
    @ResponseBody
    fun handleNotFound(e: KaraokeProcessAdminService.ProcessNotFoundException): ResponseEntity<Map<String, Any>> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to "NOT_FOUND", "message" to (e.message ?: "Not found")))

    /** 400 — невалидный переход статуса (Error Format contracts/admin-process-rest-api.md). */
    @ExceptionHandler(KaraokeProcessAdminService.InvalidStatusTransitionException::class)
    @ResponseBody
    fun handleInvalidTransition(e: KaraokeProcessAdminService.InvalidStatusTransitionException): ResponseEntity<Map<String, Any>> =
        ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(
                mapOf(
                    "error" to "INVALID_STATUS_TRANSITION",
                    "message" to (e.message ?: "Invalid status transition"),
                    "details" to
                        mapOf(
                            "currentStatus" to e.currentStatus,
                            "requestedStatus" to e.requestedStatus,
                        ),
                ),
            )

    private fun actor(username: String?): String = username?.takeIf { it.isNotBlank() } ?: "admin:unknown"
}

/** Тело ответа списка процессов (total + items). */
data class ProcessListResultBody(
    val total: Int,
    val items: List<com.svoemesto.karaokeapp.dto.admin.KaraokeProcessAdminDTO>,
)
