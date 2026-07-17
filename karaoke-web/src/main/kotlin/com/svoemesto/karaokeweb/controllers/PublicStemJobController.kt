package com.svoemesto.karaokeweb.controllers

import com.svoemesto.karaokeapp.model.SiteUser
import com.svoemesto.karaokeapp.model.StemJob
import com.svoemesto.karaokeapp.model.StemJobMode
import com.svoemesto.karaokeapp.model.StemJobStatus
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import com.svoemesto.karaokeweb.WORKING_DATABASE
import com.svoemesto.karaokeweb.config.SiteAuthInterceptor
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.sql.Timestamp

/**
 * Премиум-фича «Создать минусовку из аудиофайла» (см. план фичи) — публичная сторона (karaoke-public,
 * личный кабинет). Весь класс под путём account (см. WebMvcConfig) → авто-защита SiteAuthInterceptor:
 * "siteUser" в request всегда установлен. Пользователь видит и управляет только СВОИМИ заданиями.
 *
 * karaoke-web НЕ имеет доступа на запись в MinIO (см. KaraokeStorageServiceImplWeb) и не может
 * запускать demucs/docker — здесь только: приём загрузки (сырой файл на СВОЙ диск, temp-dir) +
 * создание строки tbl_stem_jobs (WAITING). Обработку забирает karaoke-app (см.
 * StemJobPollScheduler в karaoke-app — тянет файл через InternalStemJobController). Скачивание
 * готовых стемов — не редирект на публичный MinIO URL (как /api/public/picture), а стрим ЧЕРЕЗ
 * приложение: содержимое приватное (чужой аудиофайл пользователя), должно оставаться за авторизацией.
 */
@RestController
@RequestMapping("/api/public/account/stemjobs")
class PublicStemJobController(
    private val storageService: KaraokeStorageService,
    private val storageApiClient: StorageApiClient,
    @Value("\${stemjobs.temp-dir:/tmp/stemjobs}") private val tempDir: String,
    @Value("\${storage.proxy-url}") private val minioProxyUrl: String,
) {
    private val db get() = WORKING_DATABASE

    private fun currentUser(request: HttpServletRequest): SiteUser =
        request.getAttribute(SiteAuthInterceptor.SITE_USER_ATTR) as SiteUser

    // Своё задание (иначе null → 404, факт чужого задания наружу не утекает) — по образцу
    // PublicSongEditorController.loadOwnedAssignment.
    private fun loadOwnedJob(id: Long, ownerId: Long): StemJob? =
        StemJob.getById(id, db, storageService, storageApiClient)?.takeIf { it.siteUserId == ownerId }

    @GetMapping("/list")
    fun list(request: HttpServletRequest): List<com.svoemesto.karaokeapp.model.StemJobDto> {
        val user = currentUser(request)
        return StemJob.loadByUser(user.id, db, storageService, storageApiClient).map { it.toDTO() }
    }

    @PostMapping("/create")
    fun create(
        @RequestParam file: MultipartFile,
        @RequestParam mode: String,
        request: HttpServletRequest,
    ): ResponseEntity<Any> {
        val user = currentUser(request)
        if (!user.isEffectivePremium) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to "premium_required"))
        }
        if (mode != StemJobMode.DEMUCS2 && mode != StemJobMode.DEMUCS5) {
            return ResponseEntity.badRequest().body(mapOf("error" to "invalid_mode"))
        }
        if (file.isEmpty) {
            return ResponseEntity.badRequest().body(mapOf("error" to "file_required"))
        }
        if (file.size > StemJob.MAX_FILE_SIZE_BYTES) {
            return ResponseEntity.badRequest().body(mapOf("error" to "file_too_large", "maxBytes" to StemJob.MAX_FILE_SIZE_BYTES))
        }
        val ext = (file.originalFilename ?: "").substringAfterLast('.', "").lowercase().filter { it.isLetterOrDigit() }
        if (ext !in StemJob.ALLOWED_EXTENSIONS) {
            return ResponseEntity.badRequest().body(mapOf("error" to "unsupported_format", "allowed" to StemJob.ALLOWED_EXTENSIONS))
        }
        val activeCount = StemJob.countActiveByUser(user.id, db)
        if (activeCount >= StemJob.MAX_ACTIVE_JOBS_PER_USER) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(mapOf("error" to "queue_limit_reached", "limit" to StemJob.MAX_ACTIVE_JOBS_PER_USER))
        }

        val job = StemJob.createNew(
            siteUserId = user.id,
            mode = mode,
            originalFileName = file.originalFilename ?: "upload.$ext",
            originalExt = ext,
            fileSizeBytes = file.size,
            database = db,
            storageService = storageService,
            storageApiClient = storageApiClient,
        ) ?: return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf("error" to "create_failed"))

        val dir = File(tempDir)
        if (!dir.exists()) dir.mkdirs()
        val dest = File(dir, "${job.id}.$ext")
        try {
            file.transferTo(dest)
        } catch (e: Exception) {
            StemJob.delete(job.id, db)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf("error" to "upload_failed"))
        }

        return ResponseEntity.ok(job.toDTO())
    }

    @PostMapping("/{id}/delete")
    fun delete(@PathVariable id: Long, request: HttpServletRequest): ResponseEntity<Any> {
        val user = currentUser(request)
        val job = loadOwnedJob(id, user.id) ?: return ResponseEntity.notFound().build()
        // Фактическое удаление файлов из MinIO (если уже загружены) делает уборка на karaoke-app —
        // только она умеет писать в хранилище (см. StemJobPollScheduler.cleanup). Тот же флаг закрывает
        // и WAITING (ещё не забранное — просто отменяется), и DONE (готовое).
        job.deleteRequested = true
        job.save()
        return ResponseEntity.ok(mapOf("ok" to true))
    }

    @GetMapping("/{id}/download")
    fun download(
        @PathVariable id: Long,
        @RequestParam stem: String,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        val user = currentUser(request)
        val job = loadOwnedJob(id, user.id)
        val now = Timestamp(System.currentTimeMillis())
        if (job == null || job.status != StemJobStatus.DONE || job.expiresAt == null || job.expiresAt!!.before(now)) {
            response.status = HttpServletResponse.SC_NOT_FOUND
            return
        }
        val validStems = StemJobMode.stemNames(job.mode).toSet() + "original"
        if (stem !in validStems) {
            response.status = HttpServletResponse.SC_BAD_REQUEST
            return
        }
        val ext = if (stem == "original") job.originalExt else "mp3"
        val key = "stemjobs/${job.id}/$stem.$ext"
        val encodedPath = key.split("/").joinToString("/") { URLEncoder.encode(it, StandardCharsets.UTF_8).replace("+", "%20") }

        try {
            val connection = URL("$minioProxyUrl/minio/karaoke/$encodedPath").openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 30_000
            if (connection.responseCode != 200) {
                response.status = HttpServletResponse.SC_NOT_FOUND
                connection.disconnect()
                return
            }
            val baseName = job.originalFileName.substringBeforeLast('.').ifBlank { "stem" }.replace("\"", "")
            response.contentType = if (ext == "mp3") "audio/mpeg" else "application/octet-stream"
            response.setHeader("Content-Disposition", "attachment; filename=\"$baseName - $stem.$ext\"")
            connection.inputStream.use { input -> response.outputStream.use { output -> input.copyTo(output) } }
            connection.disconnect()
        } catch (e: Exception) {
            response.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
        }
    }
}
