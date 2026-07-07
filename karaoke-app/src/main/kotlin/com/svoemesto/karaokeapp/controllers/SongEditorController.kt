package com.svoemesto.karaokeapp.controllers

import com.svoemesto.karaokeapp.Connection
import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.KaraokeFileType
import com.svoemesto.karaokeapp.model.KaraokeDbTable
import com.svoemesto.karaokeapp.model.Settings
import com.svoemesto.karaokeapp.model.SettingField
import com.svoemesto.karaokeapp.model.SiteUser
import com.svoemesto.karaokeapp.model.SongAssignment
import com.svoemesto.karaokeapp.model.SongAssignmentDraft
import com.svoemesto.karaokeapp.model.SongAssignmentStatus
import com.svoemesto.karaokeapp.model.SourceMarker
import com.svoemesto.karaokeapp.runCommand
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import java.io.File
import java.sql.Timestamp

// Админская сторона онлайн-редактора караоке-разметки (webvue3). Живёт в karaoke-app (admin-машина):
// назначение песни пользователю с автозаливкой стемов в MinIO, просмотр черновиков, апрув (применение
// разметки в tbl_settings через Settings.setSourceMarkers — только здесь есть локальный диск +
// WORKING_DATABASE) и реджект с комментарием.
//
// Пары эндпоинтов по назначению БД:
//  - digest/byId — просмотр, уважают target=local|remote (админ смотрит любую БД), withDb закрывает
//    per-request соединение (иначе "too many clients", см. resolveDb connection leak в CLAUDE.md).
//  - assign/approve/reject — ВСЕГДА local: assignment authority = LOCAL (oneClickDirection
//    LOCAL_TO_SERVER); создание на remote привело бы к зеркальному удалению следующим sync-push.
//    Применение разметки/стемов тоже требует локального диска.
@Controller
@RequestMapping("/api/songeditor")
class SongEditorController(
    private val storageService: KaraokeStorageService,
    private val storageApiClient: StorageApiClient,
) {

    // Терпимый к неизвестным ключам декодер — маркеры черновика несут поля admin-формата (locklad и т.п.),
    // которых нет в SourceMarker; строгий Json.Default бросил бы на них.
    private val json = Json { ignoreUnknownKeys = true }

    private fun resolveDb(target: String?): KaraokeConnection =
        if (target == "remote") Connection.remote() else Connection.local()

    private fun <T> withDb(target: String?, block: (KaraokeConnection) -> T): T {
        val db = resolveDb(target)
        return try {
            block(db)
        } finally {
            try { db.getConnection()?.close() } catch (_: Exception) {}
        }
    }

    // --- Автозаливка стемов (та же логика, что ApiController.getSongFileVocalMp3/getSongFileMusicMp3:
    // convertFlacToMp3 + pushMp3ToStorage). Best-effort: если FLAC недоступен локально — тихо пропускаем,
    // публичный редактор просто не покажет соответствующую дорожку.

    private fun convertFlacToMp3(flacPath: String): File? {
        val flacFile = File(flacPath)
        if (!flacFile.exists()) return null
        val mp3File = File(flacPath.removeSuffix(".flac") + ".mp3")
        if (!mp3File.exists()) {
            val process = ProcessBuilder("ffmpeg", "-i", flacPath, "-codec:a", "libmp3lame", "-qscale:a", "2", "-y", mp3File.absolutePath)
                .redirectErrorStream(true)
                .start()
            process.waitFor()
            if (!mp3File.exists()) return null
        }
        return mp3File
    }

    private fun pushMp3ToStorage(mp3File: File, settings: Settings, fileType: KaraokeFileType) {
        val bucket = "karaoke"
        val storageKey = "${settings.storageFileName}${fileType.suffix}.${fileType.extention}"
        if (!storageService.fileExists(bucket, storageKey)) {
            storageService.uploadFile(bucket, storageKey, mp3File.absolutePath)
        }
    }

    private fun ensureStemsInStorage(settings: Settings) {
        convertFlacToMp3(settings.vocalsNameFlac)?.let { pushMp3ToStorage(it, settings, KaraokeFileType.MP3_VOCAL) }
        convertFlacToMp3(settings.accompanimentNameFlac)?.let { pushMp3ToStorage(it, settings, KaraokeFileType.MP3_ACCOMPANIMENT) }
    }

    // --- Эндпоинты ---

    // Назначить песню (голос) пользователю сайта. Заодно гарантирует наличие вокала/минуса в MinIO.
    @PostMapping("/assign")
    @ResponseBody
    fun assign(
        @RequestParam songId: Long,
        @RequestParam assigneeId: Long,
        @RequestParam(required = false, defaultValue = "0") voice: Long,
        @RequestParam(required = false, defaultValue = "0") assignedBy: Long,
    ): Map<String, Any?> = withDb("local") { db ->
        val settings = Settings.loadFromDbById(songId, db, storageService = storageService, storageApiClient = storageApiClient)
            ?: return@withDb mapOf("ok" to false, "error" to "song_not_found")
        SiteUser.getSiteUserById(assigneeId, db, storageService, storageApiClient)
            ?: return@withDb mapOf("ok" to false, "error" to "user_not_found")
        SongAssignment.findExisting(songId, assigneeId, db, storageService, storageApiClient)?.let {
            return@withDb mapOf("ok" to false, "error" to "already_assigned", "id" to it.id)
        }
        // Best-effort автозаливка стемов (не роняем назначение, если конвертация не удалась).
        try { ensureStemsInStorage(settings) } catch (_: Exception) {}
        val a = SongAssignment(database = db, storageService = storageService, storageApiClient = storageApiClient)
        a.assigneeId = assigneeId
        a.songId = songId
        a.voice = voice
        a.assignedBy = assignedBy
        a.adminStatus = SongAssignmentStatus.ADMIN_OPEN
        val created = KaraokeDbTable.createDbInstance(entity = a, database = db) as? SongAssignment
        mapOf("ok" to (created != null), "id" to (created?.id ?: 0L))
    }

    // Список заданий (webvue3): композитный статус + метаданные песни/пользователя. Черновики тянем
    // батчем (без N+1), из них — user_status для композитного статуса и submitted_at.
    @PostMapping("/digest")
    @ResponseBody
    fun digest(
        @RequestParam(required = false) target: String?,
        @RequestParam(required = false) filterAssigneeId: Long?,
        @RequestParam(required = false) filterStatus: String?,
    ): Map<String, Any> = withDb(target) { db ->
        var assignments = SongAssignment.loadAll(db, storageService, storageApiClient)
        filterAssigneeId?.let { a -> assignments = assignments.filter { it.assigneeId == a } }

        val drafts = SongAssignmentDraft.loadByAssignments(assignments.map { it.id }, db, storageService, storageApiClient)
        val users = assignments.map { it.assigneeId }.distinct()
            .associateWith { SiteUser.getSiteUserById(it, db, storageService, storageApiClient) }
        val songs = if (assignments.isEmpty()) emptyMap()
        else Settings.loadListFromDbByIds(assignments.map { it.songId }.distinct(), db, storageService, storageApiClient)

        var list = assignments.map { a ->
            val draft = drafts[a.id]
            val status = SongAssignmentStatus.resolve(a.adminStatus, draft?.userStatus, a.reviewedAt, draft?.submittedAt)
            val user = users[a.assigneeId]
            val s = songs[a.songId]
            mapOf(
                "id" to a.id,
                "assigneeId" to a.assigneeId,
                "assigneeEmail" to (user?.email ?: ""),
                "assigneeName" to (user?.displayName ?: ""),
                "songId" to a.songId,
                "songName" to (s?.songName ?: ""),
                "author" to (s?.author ?: ""),
                "album" to (s?.album ?: ""),
                "year" to (s?.year ?: 0),
                "voice" to a.voice,
                "status" to status.dbValue,
                "adminStatus" to a.adminStatus,
                "reviewComment" to a.reviewComment,
                "assignedAt" to a.assignedAt,
                "reviewedAt" to a.reviewedAt,
                "submittedAt" to draft?.submittedAt,
            )
        }
        filterStatus?.takeIf { it.isNotBlank() }?.let { st -> list = list.filter { it["status"] == st } }
        mapOf("songAssignmentsDigest" to list)
    }

    // Одно задание + черновик (просмотр submitted в webvue3): текст/маркеры пользователя для ревью.
    @PostMapping("/byId")
    @ResponseBody
    fun byId(@RequestParam id: Long, @RequestParam(required = false) target: String?): Any? = withDb(target) { db ->
        val a = SongAssignment.getById(id, db, storageService, storageApiClient) ?: return@withDb null
        val draft = SongAssignmentDraft.getByAssignment(id, db, storageService, storageApiClient)
        val user = SiteUser.getSiteUserById(a.assigneeId, db, storageService, storageApiClient)
        val s = Settings.loadFromDbById(a.songId, db, storageService = storageService, storageApiClient = storageApiClient)
        val status = SongAssignmentStatus.resolve(a.adminStatus, draft?.userStatus, a.reviewedAt, draft?.submittedAt)
        mapOf(
            "id" to a.id,
            "assigneeId" to a.assigneeId,
            "assigneeEmail" to (user?.email ?: ""),
            "assigneeName" to (user?.displayName ?: ""),
            "songId" to a.songId,
            "songName" to (s?.songName ?: ""),
            "author" to (s?.author ?: ""),
            "album" to (s?.album ?: ""),
            "year" to (s?.year ?: 0),
            "voice" to a.voice,
            "status" to status.dbValue,
            "adminStatus" to a.adminStatus,
            "reviewComment" to a.reviewComment,
            "assignedAt" to a.assignedAt,
            "reviewedAt" to a.reviewedAt,
            "submittedAt" to draft?.submittedAt,
            "draftSourceText" to (draft?.editedSourceText ?: ""),
            "draftMarkers" to (draft?.editedMarkers ?: "[]"),
        )
    }

    // Одобрить: применить черновик в tbl_settings (setSourceMarkers пересчитывает resultText/
    // formattedText*/srt + saveToDb) и поднять id_status до 3 (порог доступности в онлайн-плеере —
    // PublicPlayerController.stemsReady). Только LOCAL (нужен локальный диск для .srt + WORKING_DATABASE).
    @PostMapping("/approve")
    @ResponseBody
    fun approve(@RequestParam id: Long): Map<String, Any?> = withDb("local") { db ->
        val a = SongAssignment.getById(id, db, storageService, storageApiClient)
            ?: return@withDb mapOf("ok" to false, "error" to "assignment_not_found")
        val draft = SongAssignmentDraft.getByAssignment(id, db, storageService, storageApiClient)
            ?: return@withDb mapOf("ok" to false, "error" to "draft_not_found")
        val settings = Settings.loadFromDbById(a.songId, db, storageService = storageService, storageApiClient = storageApiClient)
            ?: return@withDb mapOf("ok" to false, "error" to "song_not_found")
        val voice = a.voice.toInt()

        val markers = try {
            json.decodeFromString(ListSerializer(SourceMarker.serializer()), draft.editedMarkers)
        } catch (_: Exception) {
            return@withDb mapOf("ok" to false, "error" to "bad_markers")
        }

        // Тот же порядок, что рабочий эндпоинт ApiController.saveSourceTextAndMarkers:
        settings.setSourceMarkers(voice, markers)
        val srt = settings.convertMarkersToSrt(voice)
        try {
            val pathToFile = "${settings.rootFolder}/${settings.fileName}.voice${voice + 1}.srt"
            File(pathToFile).writeText(srt)
            runCommand(listOf("chmod", "666", pathToFile))
        } catch (_: Exception) {
            println("Ошибка при создании файла субтитров при апруве задания $id.")
        }
        settings.setSourceText(voice, draft.editedSourceText)

        // Сделать песню доступной в онлайн-плеере (idStatus>=3).
        if (settings.idStatus < 3) {
            settings.fields[SettingField.ID_STATUS] = "3"
            settings.saveToDb()
        }

        a.adminStatus = SongAssignmentStatus.ADMIN_APPROVED
        a.reviewedAt = Timestamp(System.currentTimeMillis())
        a.reviewComment = ""
        a.save()
        mapOf("ok" to true, "idStatus" to settings.idStatus)
    }

    // Отклонить с комментарием — правки НЕ применяются, задание возвращается пользователю на доработку.
    @PostMapping("/reject")
    @ResponseBody
    fun reject(@RequestParam id: Long, @RequestParam(required = false, defaultValue = "") comment: String): Map<String, Any?> =
        withDb("local") { db ->
            val a = SongAssignment.getById(id, db, storageService, storageApiClient)
                ?: return@withDb mapOf("ok" to false, "error" to "assignment_not_found")
            a.adminStatus = SongAssignmentStatus.ADMIN_REJECTED
            a.reviewComment = comment
            a.reviewedAt = Timestamp(System.currentTimeMillis())
            a.save()
            mapOf("ok" to true)
        }

    // Удалить назначение (снять задание). Черновик уйдёт каскадом sync (или отдельным удалением на PROD).
    @PostMapping("/delete")
    @ResponseBody
    fun delete(@RequestParam id: Long): Map<String, Any?> = withDb("local") { db ->
        val ok = SongAssignment.delete(id, db)
        mapOf("ok" to ok)
    }
}
