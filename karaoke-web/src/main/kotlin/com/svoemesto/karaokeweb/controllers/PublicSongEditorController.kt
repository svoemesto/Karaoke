package com.svoemesto.karaokeweb.controllers

import com.svoemesto.karaokeweb.WORKING_DATABASE

import com.svoemesto.karaokeapp.model.KaraokeDbTable
import com.svoemesto.karaokeapp.model.Song
import com.svoemesto.karaokeapp.model.SiteUser
import com.svoemesto.karaokeapp.model.SongAssignment
import com.svoemesto.karaokeapp.model.SongAssignmentDraft
import com.svoemesto.karaokeapp.model.SongAssignmentStatus
import com.svoemesto.karaokeapp.model.SourceMarker
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import com.svoemesto.karaokeweb.config.SiteAuthInterceptor
import com.svoemesto.karaokeweb.services.PlayerGestureUnlockService
import jakarta.servlet.http.HttpServletRequest
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
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
 * не трогает Song.setSourceMarkers / rootFolder / *NameFlac-геттеры — это уронило бы процесс karaoke-web
 * (Song trap: APP_WORK_ON_SERVER не инициализирован, см. PublicPlayerController). Применение
 * разметки в tbl_songs делает karaoke-app при апруве. Чтение метаданных песни (songName/author/…)
 * и seed текста/маркеров (getSourceText/getSourceMarkers — парсинг JSON-колонок) безопасно — ровно
 * это уже делает PublicPlayerController.playerData.
 *
 * Стемы для waveform отдаёт существующий PublicPlayerController (/api/public/player/{id}/filevoice.mp3
 * и fileminus.mp3) под токеном PlayerGestureUnlockService — тем же, что и плеер. Токен выдаётся при
 * открытии задания (владение уже проверено), вне зависимости от onAir/премиума — редактор работает
 * до публикации.
 */

/**
 * Контроллер (HTTP/WebSocket endpoints) для public song editor .
 *
 * @see AGENTS.md
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

    private fun currentUser(request: HttpServletRequest): SiteUser = request.getAttribute(SiteAuthInterceptor.SITE_USER_ATTR) as SiteUser

    // Своё задание (иначе null → 404, факт чужого задания наружу не утекает).
    private fun loadOwnedAssignment(
        id: Long,
        ownerId: Long,
    ): SongAssignment? = SongAssignment.getById(id, db, storageService, storageApiClient)?.takeIf { it.assigneeId == ownerId }

    // Композитный статус (единая точка, та же логика, что у админа).
    private fun statusOf(
        assignment: SongAssignment,
        draft: SongAssignmentDraft?,
    ): SongAssignmentStatus =
        SongAssignmentStatus.resolve(assignment.adminStatus, draft?.userStatus, assignment.reviewedAt, draft?.submittedAt)

    // Редактируем во всех состояниях, кроме «на проверке» и «одобрено».
    private fun canEdit(
        assignment: SongAssignment,
        draft: SongAssignmentDraft?,
    ): Boolean {
        val st = statusOf(assignment, draft)
        return st != SongAssignmentStatus.SUBMITTED && st != SongAssignmentStatus.APPROVED
    }

    private fun notFound(): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to "not_found"))

    // ---- Список моих заданий -----------------------------------------------------------------

    @GetMapping("/tasks")
    fun tasks(request: HttpServletRequest): List<Map<String, Any?>> {
        val user = currentUser(request)
        // Роль "редактор" — снятие роли должно закрывать доступ даже при наличии старых назначений.
        if (!user.isEditor) return emptyList()
        val assignments = SongAssignment.loadByAssignee(user.id, db, storageService, storageApiClient)
        val drafts = SongAssignmentDraft.loadByAssignments(assignments.map { it.id }, db, storageService, storageApiClient)
        val songs =
            if (assignments.isEmpty()) {
                emptyMap()
            } else {
                Song.loadListFromDbByIds(assignments.map { it.songId }.distinct(), db, storageService, storageApiClient)
            }
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
                "status" to status.dbValue,
                // Комментарий показываем только когда есть смысл (отклонено) — иначе пустой.
                "reviewComment" to (if (status == SongAssignmentStatus.REJECTED) a.reviewComment else ""),
            )
        }
    }

    // ---- Одно задание (открытие в редакторе) -------------------------------------------------

    // Задание покрывает ВСЮ песню (все голоса) — sourceTexts/markersPerVoice - массивы, индекс = номер
    // голоса. Реальную позицию плеера/подсветку слогов ведёт фронт (переключение голосов — целиком
    // клиентское состояние до сохранения).
    @GetMapping("/tasks/{id}")
    fun task(
        @PathVariable id: Long,
        request: HttpServletRequest,
    ): ResponseEntity<Map<String, Any?>> {
        val user = currentUser(request)
        if (!user.isEditor) return notFound()
        val a = loadOwnedAssignment(id, user.id) ?: return notFound()
        val draft = SongAssignmentDraft.getByAssignment(id, db, storageService, storageApiClient)
        val song =
            Song.loadFromDbById(a.songId, WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
                ?: return notFound()
        val status = statusOf(a, draft)

        // Текст/маркеры ПО ГОЛОСАМ: из черновика, если есть; иначе seed из Song (текущее
        // состояние песни, целиком — все уже существующие голоса).
        val sourceTexts: List<String>
        val markersPerVoice: List<List<SourceMarker>>
        if (draft != null) {
            sourceTexts = draft.editedTextsPerVoice(json)
            markersPerVoice = draft.editedMarkersPerVoice(json)
        } else {
            sourceTexts = song.sourceTextList
            markersPerVoice = song.sourceMarkersList
        }

        // Токен доступа к стемам (тот же механизм, что публичный плеер) — привязан к заданию, поэтому
        // playerdata сможет подставить ИМЕННО черновик этого задания (см. PublicPlayerController).
        // Владение уже проверено выше.
        val token = gestureUnlockService.issueDirectAccessTokenForAssignment(a.songId, a.id)
        val tokenSuffix = "?token=$token"

        return ResponseEntity.ok(
            mapOf(
                "id" to a.id,
                "songId" to a.songId,
                "songName" to song.songName,
                "author" to song.author,
                "album" to song.album,
                "year" to song.year.takeIf { it > 0 },
                "bpm" to song.bpm,
                "status" to status.dbValue,
                "canEdit" to canEdit(a, draft),
                "reviewComment" to (if (status == SongAssignmentStatus.REJECTED) a.reviewComment else ""),
                "sourceTexts" to sourceTexts,
                "markersPerVoice" to markersPerVoice,
                "audioVocalsUrl" to "/api/public/player/${a.songId}/filevoice.mp3$tokenSuffix",
                "audioAccompanimentUrl" to "/api/public/player/${a.songId}/fileminus.mp3$tokenSuffix",
                // Тот же токен, что и выше, но отдельным полем — чтобы фронт мог собрать полноценный
                // KaraokePlayer (playerdata) для превью, не выковыривая его из query-строки URL стемов.
                "playerToken" to token,
            ),
        )
    }

    // ---- Сохранить черновик (ВСЕ голоса разом) -----------------------------------------------

    @PostMapping("/tasks/{id}/save")
    fun save(
        @PathVariable id: Long,
        @RequestParam sourceTexts: String, // JSON-массив строк, индекс = номер голоса
        @RequestParam markersPerVoice: String, // JSON-массив массивов SourceMarker, индекс = номер голоса
        request: HttpServletRequest,
    ): ResponseEntity<Map<String, Any?>> {
        val user = currentUser(request)
        if (!user.isEditor) return notFound()
        val a = loadOwnedAssignment(id, user.id) ?: return notFound()
        var draft = SongAssignmentDraft.getByAssignment(id, db, storageService, storageApiClient)
        if (!canEdit(a, draft)) return ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("error" to "not_editable"))

        // Валидируем формат (но храним как есть строкой — единый формат с фронтом).
        try {
            json.decodeFromString(ListSerializer(String.serializer()), sourceTexts)
            json.decodeFromString(ListSerializer(ListSerializer(SourceMarker.serializer())), markersPerVoice)
        } catch (_: Exception) {
            return ResponseEntity.badRequest().body(mapOf("error" to "bad_payload"))
        }

        if (draft == null) {
            draft = SongAssignmentDraft(database = db, storageService = storageService, storageApiClient = storageApiClient)
            draft.assignmentId = a.id
            draft.assigneeId = user.id
            draft.editedSourceText = sourceTexts
            draft.editedMarkers = markersPerVoice
            draft.userStatus = SongAssignmentStatus.USER_IN_PROGRESS
            KaraokeDbTable.createDbInstance(entity = draft, database = db)
        } else {
            draft.editedSourceText = sourceTexts
            draft.editedMarkers = markersPerVoice
            // Правка после reject/submit возвращает в работу.
            draft.userStatus = SongAssignmentStatus.USER_IN_PROGRESS
            draft.save()
        }
        return ResponseEntity.ok(mapOf("ok" to true, "status" to SongAssignmentStatus.IN_PROGRESS.dbValue))
    }

    // ---- Отправить на проверку ---------------------------------------------------------------

    @PostMapping("/tasks/{id}/submit")
    fun submit(
        @PathVariable id: Long,
        request: HttpServletRequest,
    ): ResponseEntity<Map<String, Any?>> {
        val user = currentUser(request)
        if (!user.isEditor) return notFound()
        val a = loadOwnedAssignment(id, user.id) ?: return notFound()
        val draft =
            SongAssignmentDraft.getByAssignment(id, db, storageService, storageApiClient)
                ?: return ResponseEntity.badRequest().body(mapOf("error" to "no_draft"))
        if (!canEdit(a, draft)) return ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("error" to "not_editable"))
        draft.userStatus = SongAssignmentStatus.USER_SUBMITTED
        draft.submittedAt = Timestamp(System.currentTimeMillis())
        draft.save()
        return ResponseEntity.ok(mapOf("ok" to true, "status" to SongAssignmentStatus.SUBMITTED.dbValue))
    }

    // ---- Отозвать с проверки (вернуть в работу) ----------------------------------------------

    // Пока задание "на проверке", а админ ЕЩЁ не вынес вердикт, пользователь может передумать и
    // забрать его обратно в работу (например, сам заметил ошибку). Разрешено СТРОГО из статуса
    // SUBMITTED — проверка на сервере, не по клиентскому состоянию (админ мог уже успеть
    // одобрить/отклонить между открытием страницы и кликом). canEdit() после этого сама даёт true
    // (IN_PROGRESS не входит в список запрещённых статусов), отдельного флага не нужно.
    @PostMapping("/tasks/{id}/recall")
    fun recall(
        @PathVariable id: Long,
        request: HttpServletRequest,
    ): ResponseEntity<Map<String, Any?>> {
        val user = currentUser(request)
        if (!user.isEditor) return notFound()
        val a = loadOwnedAssignment(id, user.id) ?: return notFound()
        val draft =
            SongAssignmentDraft.getByAssignment(id, db, storageService, storageApiClient)
                ?: return ResponseEntity.badRequest().body(mapOf("error" to "no_draft"))
        if (statusOf(a, draft) != SongAssignmentStatus.SUBMITTED) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("error" to "not_submitted"))
        }
        draft.userStatus = SongAssignmentStatus.USER_IN_PROGRESS
        draft.save()
        return ResponseEntity.ok(mapOf("ok" to true, "status" to SongAssignmentStatus.IN_PROGRESS.dbValue))
    }

    // ---- Отказаться от активного задания (US-2) ---------------------------------------------

    /**
     * Отказаться от задания в активном статусе (`assigned`/`in_progress`/`submitted`/`rejected`).
     * Удаляет запись задания И связанный черновик (orphan-cleanup, как `SongEditorController.revoke()`).
     * Песня (`tbl_songs`) и разметка не трогаются. Идемпотентно — повторный клик возвращает
     * `assignment_not_found`. Для одобренных используется `/tasks/{id}/delete`.
     *
     * @see archive/archive/docs/features/editor-tasks.md
     */
    @PostMapping("/tasks/{id}/refuse")
    fun refuse(
        @PathVariable id: Long,
        request: HttpServletRequest,
    ): ResponseEntity<Map<String, Any?>> {
        val user = currentUser(request)
        if (!user.isEditor) return notFound()
        val a = loadOwnedAssignment(id, user.id) ?: return notFound()
        // Чистим черновик ДО удаления задания (как в admin revoke()).
        SongAssignmentDraft.deleteByAssignment(id, db)
        val ok = SongAssignment.delete(id, db)
        println("[editor-tasks/refuse] user=${user.id} id=$id deleted=$ok")
        return ResponseEntity.ok(mapOf("ok" to ok, "deleted" to if (ok) 1 else 0))
    }

    // ---- Удалить одобренное задание (US-3) ---------------------------------------------------

    /**
     * Удалить одобренное задание (`approved`) из списка редактора. Удаляется ТОЛЬКО запись задания —
     * `tbl_songs` не затрагивается (разметка, id_status, публикация остаются как есть). Идемпотентно.
     *
     * @see archive/archive/docs/features/editor-tasks.md
     */
    @PostMapping("/tasks/{id}/delete")
    fun delete(
        @PathVariable id: Long,
        request: HttpServletRequest,
    ): ResponseEntity<Map<String, Any?>> {
        val user = currentUser(request)
        if (!user.isEditor) return notFound()
        val a = loadOwnedAssignment(id, user.id) ?: return notFound()
        val draft = SongAssignmentDraft.getByAssignment(id, db, storageService, storageApiClient)
        // Жёсткая серверная проверка: «Удалить» разрешено ТОЛЬКО для одобренных. Для активных —
        // отдельный `/refuse`. Иначе фронт-баг или прямой POST могут удалить активное задание.
        if (statusOf(a, draft) != SongAssignmentStatus.APPROVED) {
            return ResponseEntity.ok(mapOf("ok" to false, "error" to "not_approved"))
        }
        // Черновик НЕ удаляем — он архив после апрува (FR-012, research.md п.5).
        val ok = SongAssignment.delete(id, db)
        println("[editor-tasks/delete] user=${user.id} id=$id deleted=$ok")
        return ResponseEntity.ok(mapOf("ok" to ok, "deleted" to if (ok) 1 else 0))
    }

    // ---- Удалить все мои одобренные (US-4, батч) --------------------------------------------

    /**
     * Удалить все одобренные задания текущего редактора одним SQL (`KaraokeDbTable.deleteIn`).
     * Активные (`assigned`/`in_progress`/`submitted`/`rejected`) не трогаются.
     * Идемпотентно — повторный клик возвращает `ok: true, deleted: 0`. 0 одобренных — то же самое.
     *
     * @see archive/archive/docs/features/editor-tasks.md
     */
    @PostMapping("/tasks/delete-approved")
    fun deleteApproved(request: HttpServletRequest): ResponseEntity<Map<String, Any?>> {
        val user = currentUser(request)
        if (!user.isEditor) return notFound()
        val assignments = SongAssignment.loadByAssignee(user.id, db, storageService, storageApiClient)
        if (assignments.isEmpty()) return ResponseEntity.ok(mapOf("ok" to true, "deleted" to 0))
        val drafts = SongAssignmentDraft.loadByAssignments(assignments.map { it.id }, db, storageService, storageApiClient)
        val approvedIds = assignments.filter { statusOf(it, drafts[it.id]) == SongAssignmentStatus.APPROVED }.map { it.id }
        if (approvedIds.isEmpty()) return ResponseEntity.ok(mapOf("ok" to true, "deleted" to 0))
        val deleted = KaraokeDbTable.deleteIn(SongAssignment.TABLE_NAME, approvedIds, db)
        println("[editor-tasks/delete-approved] user=${user.id} requested=${approvedIds.size} deleted=$deleted")
        return ResponseEntity.ok(mapOf("ok" to true, "deleted" to deleted))
    }
}
