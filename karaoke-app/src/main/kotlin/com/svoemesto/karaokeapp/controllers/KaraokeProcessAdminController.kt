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
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

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

    /** Cap для sync bulk-endpoint'ов (> = async endpoint). См. spec D-5. */
    private val bulkSyncLimit = 1000

    /** Cap для snapshot endpoint /bulk/snapshot (защита от over-fetch). */
    private val idsByFilterLimit = 10000

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

    /**
     * GET /api/admin/processes/bulk/snapshot — snapshot id по текущему фильтру (FR-002, FR-004).
     *
     * Под `/bulk/` prefix чтобы избежать конфликта маршрутизации с `/{id}` —
     * Spring Boot 3.x иногда выбирает `{id}` для любой literal-path на той же
     * глубине, пытается сконвертировать "ids-by-filter" в Int → 500.
     * Доп. сегмент `/bulk/` гарантирует, что `{id}`-matcher не сработает.
     *
     * Тот же набор query-параметров, что у `list`, но возвращает ТОЛЬКО массив id
     * (≤ `idsByFilterLimit` элементов) + total. Используется UI для подсчёта
     * «отобрано N» в bulk-actions bar; `loadProcesses` остаётся для отрисовки таблицы
     * (limit 1000 на страницу).
     *
     * @see specs/319-process-bulk-actions-v2/spec.md (FR-002, FR-004)
     * @see specs/319-process-bulk-actions-v2/contracts/admin-process-bulk-rest-api.md (EP-5)
     */
    @GetMapping("/bulk/snapshot")
    @ResponseBody
    fun idsByFilter(
        @RequestParam(required = false) status: List<String>?,
        @RequestParam(required = false) type: List<String>?,
        @RequestParam(required = false) threadId: Int?,
        @RequestParam(required = false) chainId: Long?,
        @RequestParam(required = false, defaultValue = "false") includeDeleted: Boolean,
        @RequestParam(required = false) name: String?,
        @RequestParam(required = false, defaultValue = "true") topLevelOnly: Boolean,
        @RequestParam(required = false) parentId: Long?,
    ): com.svoemesto.karaokeapp.dto.admin.IdsByFilterResponse {
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
                limit = idsByFilterLimit,
                offset = 0,
            )
        return service.loadProcessIds(db, filters)
    }

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

    /**
     * POST /api/admin/processes/bulk-update — sync bulk-edit одного поля (EP-1 контракта).
     *
     * Sync endpoint: ≤ `bulkSyncLimit` (1000) процессов. > 1000 → async endpoint (US4).
     *
     * @see specs/319-process-bulk-actions-v2/contracts/admin-process-bulk-rest-api.md (EP-1)
     */
    @PostMapping("/bulk-update")
    @ResponseBody
    fun bulkUpdate(
        @RequestParam ids: String,
        @RequestParam field: String,
        @RequestParam value: String,
        @RequestParam(required = false) batchId: String?,
        @RequestHeader(value = "X-Admin-Username", required = false) username: String?,
    ): com.svoemesto.karaokeapp.dto.admin.BulkOperationReport {
        // По аналогии с ApiController.getSmartCopyAll: ids приходит как semicolon-separated
        // строка (`?ids=1;2;3` из webvue3/src/components/Processes/store.js — `ids.join(';')`).
        // value приходит как String, coerce в сервисе на основе field (priority/threadId → Int,
        // status → String).
        val idList =
            ids.split(";").mapNotNull { it.trim().toIntOrNull() }
        if (idList.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "ids must be non-empty")
        }
        if (idList.size > bulkSyncLimit) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Use /bulk-update-async for > $bulkSyncLimit ids (received ${idList.size})",
            )
        }
        val parsedBatchId =
            batchId?.takeIf { it.isNotBlank() }?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        return service.bulkUpdateProcesses(
            ids = idList,
            field = field,
            value = value,
            actor = actor(username),
            batchId = parsedBatchId,
            database = db,
        )
    }

    /**
     * POST /api/admin/processes/bulk-delete — sync hard-delete (EP-2 контракта).
     *
     * Sync endpoint: ≤ `bulkSyncLimit` (1000) процессов. > 1000 → async endpoint (US4).
     *
     * @see specs/319-process-bulk-actions-v2/contracts/admin-process-bulk-rest-api.md (EP-2)
     */
    @PostMapping("/bulk-delete")
    @ResponseBody
    fun bulkDelete(
        @RequestParam ids: String,
        @RequestParam(required = false) batchId: String?,
        @RequestHeader(value = "X-Admin-Username", required = false) username: String?,
    ): com.svoemesto.karaokeapp.dto.admin.BulkOperationReport {
        val idList =
            ids.split(";").mapNotNull { it.trim().toIntOrNull() }
        if (idList.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "ids must be non-empty")
        }
        if (idList.size > bulkSyncLimit) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Use /bulk-delete-async for > $bulkSyncLimit ids (received ${idList.size})",
            )
        }
        val parsedBatchId =
            batchId?.takeIf { it.isNotBlank() }?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        return service.bulkDeleteProcesses(
            ids = idList,
            actor = actor(username),
            batchId = parsedBatchId,
            database = db,
        )
    }

    /**
     * POST /api/admin/processes/bulk-update-async — стартует async bulk-edit (EP-3 контракта).
     *
     * Возвращает 202 Accepted с `taskId` и `statusUrl` для polling прогресса.
     *
     * @see specs/319-process-bulk-actions-v2/contracts/admin-process-bulk-rest-api.md (EP-3)
     */
    @PostMapping("/bulk-update-async")
    @ResponseBody
    fun bulkUpdateAsync(
        @RequestParam ids: String,
        @RequestParam field: String,
        @RequestParam value: String,
        @RequestParam(required = false) batchId: String?,
        @RequestHeader(value = "X-Admin-Username", required = false) username: String?,
    ): Map<String, Any> {
        val idList =
            ids.split(";").mapNotNull { it.trim().toIntOrNull() }
        if (idList.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "ids must be non-empty")
        }
        val parsedBatchId =
            batchId?.takeIf { it.isNotBlank() }?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val taskId =
            service.bulkUpdateProcessesAsync(
                ids = idList,
                field = field,
                value = value,
                actor = actor(username),
                database = db,
            )
        return mapOf(
            "taskId" to taskId.toString(),
            "statusUrl" to "/api/admin/tasks/$taskId",
            "estimatedDurationSec" to ((idList.size / 100) * 6),
        )
    }

    /**
     * POST /api/admin/processes/bulk-delete-async — стартует async bulk-delete (EP-4 контракта).
     *
     * @see specs/319-process-bulk-actions-v2/contracts/admin-process-bulk-rest-api.md (EP-4)
     */
    @PostMapping("/bulk-delete-async")
    @ResponseBody
    fun bulkDeleteAsync(
        @RequestParam ids: String,
        @RequestParam(required = false) batchId: String?,
        @RequestHeader(value = "X-Admin-Username", required = false) username: String?,
    ): Map<String, Any> {
        val idList =
            ids.split(";").mapNotNull { it.trim().toIntOrNull() }
        if (idList.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "ids must be non-empty")
        }
        val parsedBatchId =
            batchId?.takeIf { it.isNotBlank() }?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val taskId =
            service.bulkDeleteProcessesAsync(
                ids = idList,
                actor = actor(username),
                database = db,
            )
        return mapOf(
            "taskId" to taskId.toString(),
            "statusUrl" to "/api/admin/tasks/$taskId",
            "estimatedDurationSec" to ((idList.size / 100) * 5),
        )
    }

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
