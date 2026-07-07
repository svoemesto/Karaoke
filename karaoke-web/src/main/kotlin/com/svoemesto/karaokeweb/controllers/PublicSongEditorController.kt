package com.svoemesto.karaokeweb.controllers

import com.svoemesto.karaokeapp.model.KaraokeDbTable
import com.svoemesto.karaokeapp.model.Settings
import com.svoemesto.karaokeapp.model.SiteUser
import com.svoemesto.karaokeapp.model.SongAssignment
import com.svoemesto.karaokeapp.model.SongAssignmentDraft
import com.svoemesto.karaokeapp.model.SongAssignmentStatus
import com.svoemesto.karaokeapp.model.SourceMarker
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import com.svoemesto.karaokeweb.WORKING_DATABASE
import com.svoemesto.karaokeweb.config.SiteAuthInterceptor
import com.svoemesto.karaokeweb.services.PlayerGestureUnlockService
import jakarta.servlet.http.HttpServletRequest
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.sql.Timestamp

/**
 * Публичная сторона онлайн-редактора караоке-разметки (karaoke-public). Весь класс под путём
 * account (см. WebMvcConfig) → авто-защита SiteAuthInterceptor: "siteUser" в request всегда
 * установлен и не забанен. Пользователь видит только СВОИ задания (проверка assignee_id == user.id).
 *
 * КРИТИЧНО: этот контроллер ПИШЕТ только в tbl_song_assignment_drafts (черновик пользователя), НИКОГДА
 * не трогает Settings.setSourceMarkers / rootFolder / *NameFlac-геттеры — это уронило бы процесс karaoke-web
 * (Settings trap: APP_WORK_ON_SERVER не инициализирован, см. PublicPlayerController). Применение
 * разметки в tbl_settings делает karaoke-app при апруве. Чтение метаданных песни (songName/author/…)
 * и seed текста/маркеров (getSourceText/getSourceMarkers — парсинг JSON-колонок) безопасно — ровно
 * это уже делает PublicPlayerController.playerData.
 *
 * Стемы для waveform отдаёт существующий PublicPlayerController (/api/public/player/{id}/filevoice.mp3
 * и fileminus.mp3) под токеном PlayerGestureUnlockService — тем же, что и плеер. Токен выдаётся при
 * открытии задания (владение уже проверено), вне зависимости от onAir/премиума — редактор работает
 * до публикации.
 */
@RestController
@RequestMapping("/api/public/account/editor")
class PublicSongEditorController(
    private val storageService: KaraokeStorageService,
    private val storageApiClient: StorageApiClient,
    private val gestureUnlockService: PlayerGestureUnlockService,
) {
    private val db get() = WORKING_DATABASE

    // Терпимый к неизвестным ключам декодер — маркеры с фронта несут поля admin-формата (locklad и т.п.),
    // которых нет в SourceMarker; строгий Json.Default бросил бы на них (см. AIAssistant.kt).
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    private fun currentUser(request: HttpServletRequest): SiteUser =
        request.getAttribute(SiteAuthInterceptor.SITE_USER_ATTR) as SiteUser

    // Своё задание (иначе null → 404, факт чужого задания наружу не утекает).
    private fun loadOwnedAssignment(id: Long, ownerId: Long): SongAssignment? =
        SongAssignment.getById(id, db, storageService, storageApiClient)?.takeIf { it.assigneeId == ownerId }

    // Композитный статус (единая точка, та же логика, что у админа).
    private fun statusOf(assignment: SongAssignment, draft: SongAssignmentDraft?): SongAssignmentStatus =
        SongAssignmentStatus.resolve(assignment.adminStatus, draft?.userStatus, assignment.reviewedAt, draft?.submittedAt)

    // Редактируем во всех состояниях, кроме «на проверке» и «одобрено».
    private fun canEdit(assignment: SongAssignment, draft: SongAssignmentDraft?): Boolean {
        val st = statusOf(assignment, draft)
        return st != SongAssignmentStatus.SUBMITTED && st != SongAssignmentStatus.APPROVED
    }

    private fun notFound(): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to "not_found"))

    // ---- Список моих заданий -----------------------------------------------------------------

    @GetMapping("/tasks")
    fun tasks(request: HttpServletRequest): List<Map<String, Any?>> {
        val user = currentUser(request)
        val assignments = SongAssignment.loadByAssignee(user.id, db, storageService, storageApiClient)
        val drafts = SongAssignmentDraft.loadByAssignments(assignments.map { it.id }, db, storageService, storageApiClient)
        val songs = if (assignments.isEmpty()) emptyMap()
        else Settings.loadListFromDbByIds(assignments.map { it.songId }.distinct(), db, storageService, storageApiClient)
        return assignments.map { a ->
            val draft = drafts[a.id]
            val status = statusOf(a, draft)
            val s = songs[a.songId]
            mapOf(
                "id" to a.id,
                "songId" to a.songId,
                "songName" to (s?.songName ?: ""),
                "author" to (s?.author ?: ""),
                "album" to (s?.album ?: ""),
                "year" to (s?.year ?: 0),
                "voice" to a.voice,
                "status" to status.dbValue,
                // Комментарий показываем только когда есть смысл (отклонено) — иначе пустой.
                "reviewComment" to (if (status == SongAssignmentStatus.REJECTED) a.reviewComment else ""),
            )
        }
    }

    // ---- Одно задание (открытие в редакторе) -------------------------------------------------

    @GetMapping("/tasks/{id}")
    fun task(@PathVariable id: Long, request: HttpServletRequest): ResponseEntity<Map<String, Any?>> {
        val user = currentUser(request)
        val a = loadOwnedAssignment(id, user.id) ?: return notFound()
        val draft = SongAssignmentDraft.getByAssignment(id, db, storageService, storageApiClient)
        val settings = Settings.loadFromDbById(a.songId, WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
            ?: return notFound()
        val voice = a.voice.toInt()
        val status = statusOf(a, draft)

        // Текст/маркеры: из черновика, если есть; иначе seed из Settings (текущее состояние песни).
        val sourceText: String
        val markers: List<SourceMarker>
        if (draft != null) {
            sourceText = draft.editedSourceText
            markers = try {
                json.decodeFromString(ListSerializer(SourceMarker.serializer()), draft.editedMarkers)
            } catch (_: Exception) { emptyList() }
        } else {
            sourceText = settings.getSourceText(voice)
            markers = settings.getSourceMarkers(voice)
        }

        // Токен доступа к стемам (тот же механизм, что публичный плеер). Владение уже проверено выше.
        val token = gestureUnlockService.issueDirectAccessToken(a.songId)
        val tokenSuffix = "?token=$token"

        return ResponseEntity.ok(mapOf(
            "id" to a.id,
            "songId" to a.songId,
            "songName" to settings.songName,
            "author" to settings.author,
            "album" to settings.album,
            "year" to settings.year.takeIf { it > 0 },
            "bpm" to settings.bpm,
            "voice" to a.voice,
            "status" to status.dbValue,
            "canEdit" to canEdit(a, draft),
            "reviewComment" to (if (status == SongAssignmentStatus.REJECTED) a.reviewComment else ""),
            "sourceText" to sourceText,
            "markers" to markers,
            "audioVocalsUrl" to "/api/public/player/${a.songId}/filevoice.mp3$tokenSuffix",
            "audioAccompanimentUrl" to "/api/public/player/${a.songId}/fileminus.mp3$tokenSuffix",
        ))
    }

    // ---- Сохранить черновик ------------------------------------------------------------------

    @PostMapping("/tasks/{id}/save")
    fun save(
        @PathVariable id: Long,
        @RequestParam sourceText: String,
        @RequestParam markers: String,
        request: HttpServletRequest,
    ): ResponseEntity<Map<String, Any?>> {
        val user = currentUser(request)
        val a = loadOwnedAssignment(id, user.id) ?: return notFound()
        var draft = SongAssignmentDraft.getByAssignment(id, db, storageService, storageApiClient)
        if (!canEdit(a, draft)) return ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("error" to "not_editable"))

        // Валидируем маркеры (но храним как есть строкой — единый формат с фронтом).
        try {
            json.decodeFromString(ListSerializer(SourceMarker.serializer()), markers)
        } catch (_: Exception) {
            return ResponseEntity.badRequest().body(mapOf("error" to "bad_markers"))
        }

        if (draft == null) {
            draft = SongAssignmentDraft(database = db, storageService = storageService, storageApiClient = storageApiClient)
            draft.assignmentId = a.id
            draft.assigneeId = user.id
            draft.editedSourceText = sourceText
            draft.editedMarkers = markers
            draft.userStatus = SongAssignmentStatus.USER_IN_PROGRESS
            KaraokeDbTable.createDbInstance(entity = draft, database = db)
        } else {
            draft.editedSourceText = sourceText
            draft.editedMarkers = markers
            // Правка после reject/submit возвращает в работу.
            draft.userStatus = SongAssignmentStatus.USER_IN_PROGRESS
            draft.save()
        }
        return ResponseEntity.ok(mapOf("ok" to true, "status" to SongAssignmentStatus.IN_PROGRESS.dbValue))
    }

    // ---- Отправить на проверку ---------------------------------------------------------------

    @PostMapping("/tasks/{id}/submit")
    fun submit(@PathVariable id: Long, request: HttpServletRequest): ResponseEntity<Map<String, Any?>> {
        val user = currentUser(request)
        val a = loadOwnedAssignment(id, user.id) ?: return notFound()
        val draft = SongAssignmentDraft.getByAssignment(id, db, storageService, storageApiClient)
            ?: return ResponseEntity.badRequest().body(mapOf("error" to "no_draft"))
        if (!canEdit(a, draft)) return ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("error" to "not_editable"))
        draft.userStatus = SongAssignmentStatus.USER_SUBMITTED
        draft.submittedAt = Timestamp(System.currentTimeMillis())
        draft.save()
        return ResponseEntity.ok(mapOf("ok" to true, "status" to SongAssignmentStatus.SUBMITTED.dbValue))
    }
}
