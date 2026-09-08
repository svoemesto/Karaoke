package com.svoemesto.karaokeapp.controllers

import com.svoemesto.karaokeapp.services.AdminTaskService
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * Контроллер polling'а admin-задач (specs/319-process-bulk-actions-v2, US4, EP-6).
 *
 * Frontend опрашивает `GET /api/admin/tasks/{taskId}` каждые 2 секунды (см.
 * `webvue3/.../Processes/store.js` action `pollBulkTask`).
 *
 * @see specs/319-process-bulk-actions-v2/contracts/admin-process-bulk-rest-api.md (EP-6)
 */
@Controller
@RequestMapping("/api/admin/tasks")
class AdminTaskController(
    private val service: AdminTaskService,
) {
    /** GET /api/admin/tasks/{taskId} — текущий статус. */
    @GetMapping("/{taskId}")
    @ResponseBody
    fun get(@PathVariable taskId: UUID): AdminTaskService.TaskStatus =
        service.getTask(taskId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found: $taskId")
}
