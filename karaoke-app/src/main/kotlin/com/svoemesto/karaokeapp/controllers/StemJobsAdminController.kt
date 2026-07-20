package com.svoemesto.karaokeapp.controllers

import com.svoemesto.karaokeapp.Connection
import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.StemJobCleanup
import com.svoemesto.karaokeapp.model.StemJob
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody

// Админ-панель webvue3 «Минусовки» — управление заданиями пользователей на разделение аудио
// (StemJob, премиум-фича личного кабинета). Target-aware, как SiteUsersController/ChatController —
// реальные задания живут на прод-БД (target=remote), target=local — для локальной отладки.
// Остановка/удаление (docker kill, чистка tbl_processes/temp/MinIO) физически возможны только на
// этой машине (администратора) — независимо от target, который выбирает лишь БД для tbl_stem_jobs.
@Controller
@RequestMapping("/api/stemjobs")
class StemJobsAdminController {
    private fun resolveDb(target: String?): KaraokeConnection = if (target == "remote") Connection.remote() else Connection.local()

    private fun <T> withDb(
        target: String?,
        block: (KaraokeConnection) -> T,
    ): T {
        val db = resolveDb(target)
        return try {
            block(db)
        } finally {
            try {
                db.getConnection()?.close()
            } catch (_: Exception) {
            }
        }
    }

    @PostMapping("/list")
    @ResponseBody
    fun list(
        @RequestParam(required = false) target: String?,
    ): Map<String, Any> =
        withDb(target) { db ->
            mapOf("stemJobs" to StemJob.loadAllWithUserInfo(db))
        }

    // Останавливает выполняющийся demucs-пайплайн (если WAITING/WORKING) и помечает задание ERROR.
    // Файлы (если частично загружены) не трогает — отдельная кнопка «Удалить».
    @PostMapping("/{id}/stop")
    @ResponseBody
    fun stop(
        @PathVariable id: Long,
        @RequestParam(required = false) target: String?,
    ): Map<String, Any> =
        withDb(target) { db ->
            mapOf("ok" to StemJobCleanup.adminStop(id, db))
        }

    // Останавливает (если ещё выполняется) и немедленно удаляет — файлы из MinIO + строку в БД, не
    // дожидаясь периодической уборки (StemJobPollScheduler.cleanup, раз в 5 минут).
    @PostMapping("/{id}/delete")
    @ResponseBody
    fun delete(
        @PathVariable id: Long,
        @RequestParam(required = false) target: String?,
    ): Map<String, Any> =
        withDb(target) { db ->
            mapOf("ok" to StemJobCleanup.adminDeleteNow(id, db))
        }
}
