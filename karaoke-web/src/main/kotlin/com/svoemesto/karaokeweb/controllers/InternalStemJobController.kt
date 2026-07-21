package com.svoemesto.karaokeweb.controllers

import com.svoemesto.karaokeweb.WORKING_DATABASE

import com.svoemesto.karaokeapp.model.StemJob
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.*
import java.io.File

/**
 * Server-to-server эндпоинты для karaoke-app (StemJobPollScheduler/StemJobProcessing) — НЕ проходят
 * через SiteAuthInterceptor (см. WebMvcConfig: addPathPatterns покрывает только пути account (вложенные)
 * и /api/public/auth/{me,logout}), защищены отдельным shared-secret заголовком X-Internal-Secret
 * (значение — Karaoke.stemJobsInternalSecret на стороне karaoke-app, stemjobs.internal-secret здесь;
 * задаётся админом одинаковым на обеих сторонах при деплое).
 *
 * /raw — отдаёт сырой файл, загруженный пользователем (см. PublicStemJobController.create), который
 * ещё лежит на диске karaoke-web (temp-dir), пока karaoke-app его не заберёт.
 * /ack — karaoke-app подтверждает, что файл забран и обработан (успешно или с ошибкой) — можно
 * удалить. Best-effort: если этот запрос не дойдёт, temp-файл всё равно зачистится по TTL
 * (см. StemJobTempCleanupScheduler).
 */
@RestController
@RequestMapping("/api/internal/stemjobs")
class InternalStemJobController(
    private val storageService: KaraokeStorageService,
    private val storageApiClient: StorageApiClient,
    @Value("\${stemjobs.temp-dir:/tmp/stemjobs}") private val tempDir: String,
    @Value("\${stemjobs.internal-secret:}") private val internalSecret: String,
) {
    private fun authorized(request: HttpServletRequest): Boolean {
        if (internalSecret.isBlank()) return false // не сконфигурировано — по умолчанию закрыто, не открыто
        return request.getHeader("X-Internal-Secret") == internalSecret
    }

    @GetMapping("/{id}/raw")
    fun raw(
        @PathVariable id: Long,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        if (!authorized(request)) {
            response.status = HttpServletResponse.SC_FORBIDDEN
            return
        }
        val job = StemJob.getById(id, WORKING_DATABASE, storageService, storageApiClient)
        if (job == null) {
            response.status = HttpServletResponse.SC_NOT_FOUND
            return
        }
        val file = File(tempDir, "${job.id}.${job.originalExt}")
        if (!file.exists()) {
            response.status = HttpServletResponse.SC_NOT_FOUND
            return
        }
        response.contentType = "application/octet-stream"
        response.setContentLengthLong(file.length())
        file.inputStream().use { input -> response.outputStream.use { output -> input.copyTo(output) } }
    }

    @PostMapping("/{id}/ack")
    fun ack(
        @PathVariable id: Long,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        if (!authorized(request)) {
            response.status = HttpServletResponse.SC_FORBIDDEN
            return
        }
        val job = StemJob.getById(id, WORKING_DATABASE, storageService, storageApiClient)
        val ext = job?.originalExt
        if (ext != null) {
            File(tempDir, "$id.$ext").delete()
        } else {
            // Запись могла уже быть удалена (delete_requested подхватила уборка раньше, чем пришёл
            // ack) — на всякий случай чистим по любому расширению из allow-list.
            StemJob.ALLOWED_EXTENSIONS.forEach { File(tempDir, "$id.$it").delete() }
        }
        response.status = HttpServletResponse.SC_OK
    }
}
