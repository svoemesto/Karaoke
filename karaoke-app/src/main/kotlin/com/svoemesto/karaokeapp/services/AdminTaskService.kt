package com.svoemesto.karaokeapp.services

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Простой in-memory admin-task subsystem (specs/319-process-bulk-actions-v2, US4).
 *
 * Контракт: `POST /api/admin/.../bulk-*-async` → стартует фоновую задачу, возвращает
 * `{taskId, statusUrl}`. `GET /api/admin/tasks/{taskId}` → polling прогресса.
 *
 * **ВНИМАНИЕ**: задачи хранятся в памяти процесса karaoke-app. После рестарта —
 * теряются. Это нормально для v1 (US4 — расширение для bulk > 1000); для
 * персистентности в будущем — отдельный `tbl_admin_tasks` + cron cleanup.
 *
 * @see specs/319-process-bulk-actions-v2/spec.md (US4)
 * @see specs/319-process-bulk-actions-v2/contracts/admin-process-bulk-rest-api.md (EP-6)
 */
@Service
class AdminTaskService {
    private val mapper = ObjectMapper()

    /** Текущее состояние задачи. */
    data class TaskStatus(
        val taskId: UUID,
        val action: String,
        val totalCount: Int,
        val processedCount: Int = 0,
        val succeededCount: Int = 0,
        val failedCount: Int = 0,
        val status: String = RUNNING, // RUNNING | COMPLETED | PARTIAL | FAILED
        val startedAt: Long = System.currentTimeMillis(),
        val finishedAt: Long? = null,
        val errors: List<Map<String, Any?>> = emptyList(),
    )

    private val tasks = ConcurrentHashMap<UUID, TaskStatus>()
    private val executor = Executors.newFixedThreadPool(2)

    companion object {
        const val RUNNING = "RUNNING"
        const val COMPLETED = "COMPLETED"
        const val PARTIAL = "PARTIAL"
        const val FAILED = "FAILED"
    }

    /**
     * Стартует фоновую задачу, возвращает `taskId`.
     *
     * @param action имя операции (`bulk_update_field` / `bulk_delete`)
     * @param totalCount ожидаемое число id
     * @param block лямбда, выполняющая работу. Должна периодически обновлять
     *   прогресс через переданный `Progress` (см. ниже).
     */
    fun startTask(
        action: String,
        totalCount: Int,
        block: (progress: Progress) -> Unit,
    ): UUID {
        val taskId = UUID.randomUUID()
        val task = TaskStatus(taskId = taskId, action = action, totalCount = totalCount)
        tasks[taskId] = task

        executor.submit {
            val progress = Progress(taskId, totalCount)
            try {
                block(progress)
                // Финальный статус определяется по результату.
                val finalStatus =
                    when {
                        progress.failedCount.get() == 0 &&
                            progress.succeededCount.get() == totalCount -> COMPLETED
                        progress.succeededCount.get() > 0 -> PARTIAL
                        else -> FAILED
                    }
                tasks[taskId] =
                    task.copy(
                        processedCount = progress.processedCount.get(),
                        succeededCount = progress.succeededCount.get(),
                        failedCount = progress.failedCount.get(),
                        status = finalStatus,
                        finishedAt = System.currentTimeMillis(),
                        errors = progress.errors.toList(),
                    )
            } catch (e: Exception) {
                tasks[taskId] =
                    task.copy(
                        status = FAILED,
                        finishedAt = System.currentTimeMillis(),
                        errors = listOf(mapOf("reason" to (e.message ?: "Unknown error"))),
                    )
            }
        }
        return taskId
    }

    fun getTask(taskId: UUID): TaskStatus? = tasks[taskId]

    /**
     * Mutable progress callback, передаётся в фоновую задачу.
     *
     * Использует Atomic-примитивы для потокобезопасного обновления.
     */
    class Progress(
        val taskId: UUID,
        val totalCount: Int,
    ) {
        internal val processedCount = AtomicInteger(0)
        internal val succeededCount = AtomicInteger(0)
        internal val failedCount = AtomicInteger(0)
        internal val errors = mutableListOf<Map<String, Any?>>()

        fun reportSucceeded() {
            processedCount.incrementAndGet()
            succeededCount.incrementAndGet()
        }

        fun reportFailed(
            processId: Long,
            reason: String,
            code: String? = null,
        ) {
            processedCount.incrementAndGet()
            failedCount.incrementAndGet()
            errors += mapOf("processId" to processId, "reason" to reason, "errorCode" to (code ?: ""))
        }
    }
}
