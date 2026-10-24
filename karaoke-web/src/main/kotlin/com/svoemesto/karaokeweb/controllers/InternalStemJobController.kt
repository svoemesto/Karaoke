package com.svoemesto.karaokeweb.controllers

import com.svoemesto.karaokeweb.WORKING_DATABASE

import com.svoemesto.karaokeapp.model.StemJob
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import com.svoemesto.karaokeweb.services.StemJobResultFiles
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
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
 *
 * /files/{fileName} — ПРИВАТНЫЕ объекты задания (стемы и загруженный исходник). Спека 561
 * (OpenProject #361, аудит BR-258): объекты лежат в отдельном бакете `stemjobs` без публичной
 * политики, читать который karaoke-web не умеет (нет учётных данных, presigned через
 * nginx path-proxy ломает SigV4). Поэтому приложение с доступом (karaoke-app) читает объект из
 * бакета и передаёт его сюда по этому же защищённому каналу; владелец задания получает файл
 * из приватного каталога результатов (PublicStemJobController.download).
 * /purge — приложение просит удалить локальные копии задания (уборка по TTL/удалению).
 */

/**
 * Контроллер (HTTP/WebSocket endpoints) для internal stem job .
 *
 * @see AGENTS.md
 */
@RestController
@RequestMapping("/api/internal/stemjobs")
class InternalStemJobController(
    private val storageService: KaraokeStorageService,
    private val storageApiClient: StorageApiClient,
    private val resultFiles: StemJobResultFiles,
    @Value("\${stemjobs.temp-dir:/tmp/stemjobs}") private val tempDir: String,
    @Value("\${stemjobs.internal-secret:}") private val internalSecret: String,
) {
    private val securityLog = LoggerFactory.getLogger("infra.stemjobs.security")

    private fun authorized(request: HttpServletRequest): Boolean {
        if (internalSecret.isBlank()) return false // не сконфигурировано — по умолчанию закрыто, не открыто
        return request.getHeader("X-Internal-Secret") == internalSecret
    }

    /** Причина отказа для журнала: что именно не совпало (значение секрета в журнал НЕ пишется). */
    private fun denialReason(request: HttpServletRequest): String =
        when {
            internalSecret.isBlank() -> "secret_not_configured"
            request.getHeader("X-Internal-Secret") == null -> "missing_secret"
            else -> "invalid_secret"
        }

    private fun sourceOf(request: HttpServletRequest): String =
        request.getHeader("X-Real-IP") ?: request.remoteAddr ?: "unknown"

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

    /**
     * Приём приватного объекта задания от karaoke-app (тело — сырые байты объекта).
     *
     * Имя файла проверяется по фиксированному allow-list (`StemJob.isValidResultFileName`):
     * «<stem>.<ext>», без разделителей пути. Проверки задания по БД здесь нет — канал
     * доверенный (доступ есть только у приложения со знанием секрета), «фантомные» каталоги
     * удаляются уборкой по TTL.
     */
    @PutMapping("/{id}/files/{fileName}")
    fun putFile(
        @PathVariable id: Long,
        @PathVariable fileName: String,
        @RequestBody(required = false) content: ByteArray?,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        if (!authorized(request)) {
            securityLog.warn(
                "stemjobs:security:denied reason={} source={} jobId={} file={}",
                denialReason(request),
                sourceOf(request),
                id,
                fileName,
            )
            response.status = HttpServletResponse.SC_FORBIDDEN
            return
        }
        if (!StemJob.isValidResultFileName(fileName)) {
            securityLog.warn(
                "stemjobs:security:rejected source={} jobId={} file={}",
                sourceOf(request),
                id,
                fileName,
            )
            response.status = HttpServletResponse.SC_BAD_REQUEST
            return
        }
        val bytes = content ?: ByteArray(0)
        try {
            val target = resultFiles.fileByName(id, fileName)
            target.parentFile?.mkdirs()
            target.writeBytes(bytes)
            securityLog.info(
                "stemjobs:security:stored jobId={} file={} bytes={}",
                id,
                fileName,
                bytes.size,
            )
            response.status = HttpServletResponse.SC_OK
        } catch (e: Exception) {
            securityLog.warn(
                "stemjobs:security:store-failed jobId={} file={} reason={}",
                id,
                fileName,
                e.javaClass.simpleName,
            )
            response.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
        }
    }

    /** Удаление локальных копий задания (уборка по TTL/удалению задания). */
    @PostMapping("/{id}/purge")
    fun purge(
        @PathVariable id: Long,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        if (!authorized(request)) {
            securityLog.warn(
                "stemjobs:security:denied reason={} source={} jobId={}",
                denialReason(request),
                sourceOf(request),
                id,
            )
            response.status = HttpServletResponse.SC_FORBIDDEN
            return
        }
        val removed = resultFiles.purge(id)
        securityLog.info("stemjobs:security:purged jobId={} files={}", id, removed)
        response.status = HttpServletResponse.SC_OK
    }
}
