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
import com.svoemesto.karaokeapp.runCommand
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
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

    // Назначить песню (ВСЮ, все голоса) пользователю сайта. Заодно гарантирует наличие вокала/минуса
    // в MinIO. Поле SongAssignment.voice сохранено в схеме (не удалено ради избежания SQL-миграции),
    // но больше ничего не определяет — задание всегда покрывает все голоса песни (см. approve/byId).
    //
    // target — где создавать (по умолчанию local): реальный цикл работы (назначить → пользователь
    // делает → админ апрувит) часто идёт ЦЕЛИКОМ на PROD — тогда назначение должно появиться сразу
    // на сервере, а не в LOCAL с последующим ожиданием push'а (songassignments теперь синкается
    // SERVER_TO_LOCAL, как pull пользователей/статистики — см. SyncTarget.kt).
    @PostMapping("/assign")
    @ResponseBody
    fun assign(
        @RequestParam songId: Long,
        @RequestParam assigneeId: Long,
        @RequestParam(required = false, defaultValue = "0") assignedBy: Long,
        @RequestParam(required = false) target: String?,
    ): Map<String, Any?> = withDb(target) { db ->
        val settings = Settings.loadFromDbById(songId, db, storageService = storageService, storageApiClient = storageApiClient)
            ?: return@withDb mapOf("ok" to false, "error" to "song_not_found")
        SiteUser.getSiteUserById(assigneeId, db, storageService, storageApiClient)
            ?: return@withDb mapOf("ok" to false, "error" to "user_not_found")
        SongAssignment.findExisting(songId, assigneeId, db, storageService, storageApiClient)?.let {
            return@withDb mapOf("ok" to false, "error" to "already_assigned", "id" to it.id)
        }
        // Best-effort автозаливка стемов (не роняем назначение, если конвертация не удалась). karaoke-app
        // работает только на машине админа — локальный диск с FLAC доступен независимо от того, в какую
        // БД (local/remote) пишем сам SongAssignment.
        try { ensureStemsInStorage(settings) } catch (_: Exception) {}
        val a = SongAssignment(database = db, storageService = storageService, storageApiClient = storageApiClient)
        a.assigneeId = assigneeId
        a.songId = songId
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

    // Одно задание + черновик (просмотр submitted в webvue3): текст/маркеры пользователя для ревью,
    // ПО ВСЕМ ГОЛОСАМ (draftSourceTexts/draftMarkersPerVoice — массивы, индекс = номер голоса).
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
            "status" to status.dbValue,
            "adminStatus" to a.adminStatus,
            "reviewComment" to a.reviewComment,
            "assignedAt" to a.assignedAt,
            "reviewedAt" to a.reviewedAt,
            "submittedAt" to draft?.submittedAt,
            "draftSourceTexts" to (draft?.editedTextsPerVoice(json) ?: emptyList()),
            "draftMarkersPerVoice" to (draft?.editedMarkersPerVoice(json) ?: emptyList()),
        )
    }

    // Одобрить: применить черновик в tbl_settings для КАЖДОГО голоса черновика (setSourceMarkers
    // пересчитывает resultText/formattedText*/srt + saveToDb) и поднять id_status до 3 (порог
    // доступности в онлайн-плеере — PublicPlayerController.stemsReady). Если голосов в черновике
    // МЕНЬШЕ, чем сейчас в Settings — пользователь удалил хвостовые голоса, обрезаем их и в Settings
    // (truncateVoicesTo).
    //
    // ЧТЕНИЕ задания/черновика уважает target (по умолчанию local): пока сайт-юзер работает на
    // проде (karaoke-web), его черновик реально лежит НА СЕРВЕРЕ и может не быть ещё подтянут
    // синхронизацией (songassignmentdrafts, SERVER_TO_LOCAL) — читать local в этом случае значило
    // бы применить УСТАРЕВШУЮ разметку. id песни/задания ОДИНАКОВ в обеих БД (сам механизм sync
    // требует стабильного общего ключа для диффа), поэтому SongAssignment/Settings по одному и тому
    // же id в local/remote — одна и та же логическая сущность, путаницы с "чужой" записью нет.
    //
    // ЗАПИСЬ — ВСЕГДА в LOCAL, в двух местах: (1) Settings — karaoke-app умеет писать .srt/резолвить
    // rootFolder только на локальном диске; (2) статус задания (adminStatus=APPROVED) — assignment
    // authority = LOCAL (oneClickDirection LOCAL_TO_SERVER), если писать статус туда же, откуда
    // читали (remote), следующий push перезатрёт апрув обратно старым статусом из local.
    @PostMapping("/approve")
    @ResponseBody
    fun approve(@RequestParam id: Long, @RequestParam(required = false) target: String?): Map<String, Any?> {
        val isRemoteRead = target == "remote"
        val readDb = if (isRemoteRead) Connection.remote() else null
        try {
            return withDb("local") { localDb ->
                val effectiveReadDb = readDb ?: localDb
                val aRead = SongAssignment.getById(id, effectiveReadDb, storageService, storageApiClient)
                    ?: return@withDb mapOf("ok" to false, "error" to "assignment_not_found")
                val draft = SongAssignmentDraft.getByAssignment(id, effectiveReadDb, storageService, storageApiClient)
                    ?: return@withDb mapOf("ok" to false, "error" to "draft_not_found")
                val settings = Settings.loadFromDbById(aRead.songId, localDb, storageService = storageService, storageApiClient = storageApiClient)
                    ?: return@withDb mapOf("ok" to false, "error" to "song_not_found")

                val markersPerVoice = draft.editedMarkersPerVoice(json)
                val textsPerVoice = draft.editedTextsPerVoice(json)
                if (markersPerVoice.isEmpty()) return@withDb mapOf("ok" to false, "error" to "bad_markers")

                val prevVoiceCount = settings.sourceMarkersList.size
                for (voice in markersPerVoice.indices) {
                    settings.setSourceMarkers(voice, markersPerVoice[voice])
                    val srt = settings.convertMarkersToSrt(voice)
                    try {
                        val pathToFile = "${settings.rootFolder}/${settings.fileName}.voice${voice + 1}.srt"
                        File(pathToFile).writeText(srt)
                        runCommand(listOf("chmod", "666", pathToFile))
                    } catch (_: Exception) {
                        println("Ошибка при создании файла субтитров при апруве задания $id (голос $voice).")
                    }
                    settings.setSourceText(voice, textsPerVoice.getOrElse(voice) { "" })
                }
                // Хвостовые голоса, удалённые пользователем в черновике (были в Settings, но их больше нет
                // в присланном списке) — обрезаем.
                if (markersPerVoice.size < prevVoiceCount) {
                    settings.truncateVoicesTo(markersPerVoice.size)
                }

                // Сделать песню доступной в онлайн-плеере (idStatus>=3).
                if (settings.idStatus < 3) {
                    settings.fields[SettingField.ID_STATUS] = "3"
                    settings.saveToDb()
                }

                val aLocal = if (isRemoteRead) {
                    SongAssignment.getById(id, localDb, storageService, storageApiClient) ?: aRead
                } else aRead
                aLocal.adminStatus = SongAssignmentStatus.ADMIN_APPROVED
                aLocal.reviewedAt = Timestamp(System.currentTimeMillis())
                aLocal.reviewComment = ""
                aLocal.save()
                mapOf("ok" to true, "idStatus" to settings.idStatus)
            }
        } finally {
            if (readDb != null) try { readDb.getConnection()?.close() } catch (_: Exception) {}
        }
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
