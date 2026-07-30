package com.svoemesto.karaokeapp.controllers

import com.svoemesto.karaokeapp.Connection
import com.svoemesto.karaokeapp.Karaoke
import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.KaraokeFileType
import com.svoemesto.karaokeapp.KaraokeProperties
import com.svoemesto.karaokeapp.WORKING_DATABASE
import com.svoemesto.karaokeapp.llm.TextCorrectorAgent
import com.svoemesto.karaokeapp.model.KaraokeDbTable
import com.svoemesto.karaokeapp.model.SongField
import com.svoemesto.karaokeapp.model.Song
import com.svoemesto.karaokeapp.model.SiteUser
import com.svoemesto.karaokeapp.model.SongAssignment
import com.svoemesto.karaokeapp.model.SongAssignmentDraft
import com.svoemesto.karaokeapp.model.SongAssignmentStatus
import com.svoemesto.karaokeapp.model.SourceMarker
import com.svoemesto.karaokeapp.model.WhisperMarkerAligner
import com.svoemesto.karaokeapp.rightFileName
import com.svoemesto.karaokeapp.runCommand
import com.svoemesto.karaokeapp.services.AlignmentServiceClient
import com.svoemesto.karaokeapp.services.KSS_APP
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.SAC_APP
import com.svoemesto.karaokeapp.services.SongReleaseAnnouncementService
import com.svoemesto.karaokeapp.services.StorageApiClient
import com.svoemesto.karaokeapp.services.WhisperAsrService
import com.svoemesto.karaokeapp.updateRemoteSongFromLocalDatabase
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import java.io.File
import java.sql.Timestamp
import java.util.concurrent.atomic.AtomicBoolean

// Админская сторона онлайн-редактора караоке-разметки (webvue3). Живёт в karaoke-app (admin-машина):
// назначение песни пользователю с автозаливкой стемов в MinIO, просмотр черновиков, апрув (применение
// разметки в tbl_songs через Song.setSourceMarkers — только здесь есть локальный диск +
// WORKING_DATABASE) и реджект с комментарием.
//
// Пары эндпоинтов по назначению БД (songassignments синкается SERVER_TO_LOCAL — remote первичен для
// реального рабочего цикла, который часто идёт целиком на PROD):
//  - digest/byId — просмотр, уважают target=local|remote (админ смотрит любую БД), withDb закрывает
//    per-request соединение (иначе "too many clients", см. resolveDb connection leak).
//  - assign/reject/delete — target-aware (withDb(target), по умолчанию local): пишут ИМЕННО в ту БД,
//    где реально идёт работа пользователя — иначе статус/комментарий уйдут в БД, которую karaoke-web не
//    читает, и правка останется невидимой (был баг: reject() был жёстко local, из-за чего отклонённое
//    задание, живущее на remote, не показывало пользователю отказ и блокировало дальнейшее редактирование).
//  - approve — читает черновик из target (обычно remote, если работа шла там), но ПРИМЕНЯЕТ разметку в
//    tbl_songs и статус задания ВСЕГДА в LOCAL — только здесь есть локальный диск для .srt/рендера.

/**
 * Контроллер (HTTP/WebSocket endpoints) для song editor .
 *
 * @see AGENTS.md
 */
@Controller
@RequestMapping("/api/songeditor")
class SongEditorController(
    private val storageService: KaraokeStorageService,
    private val storageApiClient: StorageApiClient,
) {
    // Терпимый к неизвестным ключам декодер — маркеры черновика несут поля admin-формата (locklad и т.п.),
    // которых нет в SourceMarker; строгий Json.Default бросил бы на них.
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        // Защита от параллельных фоновых сканирований checkAndAnnounce (specs/098-fix-approve-
        // blocking-checkandannounce) — если админ апрувит несколько заданий подряд, не плодим
        // одновременно несколько 4000+-кандидатных сканов поверх одного и того же прод-соединения.
        // Пропущенный запуск не теряет анонсы навсегда — следующий вызов (approve/scheduler/sync)
        // всё равно найдёт тех же кандидатов (идемпотентно, см. checkAndAnnounce KDoc).
        private val checkAndAnnounceRunning = AtomicBoolean(false)
    }

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

    // --- Автозаливка стемов (та же логика, что ApiController.getSongFileVocalMp3/getSongFileMusicMp3:
    // convertFlacToMp3 + pushMp3ToStorage). Best-effort: если FLAC недоступен локально — тихо пропускаем,
    // публичный редактор просто не покажет соответствующую дорожку.

    private fun convertFlacToMp3(flacPath: String): File? {
        val flacFile = File(flacPath)
        if (!flacFile.exists()) return null
        val mp3File = File(flacPath.removeSuffix(".flac") + ".mp3")
        if (!mp3File.exists()) {
            val process =
                ProcessBuilder("ffmpeg", "-i", flacPath, "-codec:a", "libmp3lame", "-qscale:a", "2", "-y", mp3File.absolutePath)
                    .redirectErrorStream(true)
                    .start()
            process.waitFor()
            if (!mp3File.exists()) return null
        }
        return mp3File
    }

    private fun pushMp3ToStorage(
        mp3File: File,
        settings: Song,
        fileType: KaraokeFileType,
    ) {
        val bucket = "karaoke"
        val storageKey = "${settings.storageFileName}${fileType.suffix}.${fileType.extention}"
        if (!storageService.fileExists(bucket, storageKey)) {
            storageService.uploadFile(bucket, storageKey, mp3File.absolutePath)
        }
        // Персистентный флаг готовности плеера (см. deploy/karaoke-db/26_player_readiness_flags.sql) —
        // см. аналогичный хук в ApiController.pushMp3ToStorage.
        when (fileType) {
            KaraokeFileType.MP3_ACCOMPANIMENT ->
                if (!settings.stemAccompanimentReady) {
                    settings.stemAccompanimentReady = true
                    settings.saveToDb()
                }
            KaraokeFileType.MP3_VOCAL ->
                if (!settings.stemVocalReady) {
                    settings.stemVocalReady = true
                    settings.saveToDb()
                }
            else -> {}
        }
    }

    private fun ensureStemsInStorage(settings: Song) {
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
    // clearMarkers — участвует только когда у песни УЖЕ есть непустые маркеры (переиздание/повторная
    // обработка): null → эндпоинт ничего не создаёт и просит фронт переспросить пользователя (ошибка
    // "markers_exist"); true/false — фронт уже получил ответ, назначение создаётся, а при true черновик
    // задания сразу заводится с ПУСТЫМИ маркерами по каждому голосу (settings.sourceMarkersList в БД не
    // трогаем — очистка касается только рабочей копии пользователя, не самой песни).
    @PostMapping("/assign")
    @ResponseBody
    fun assign(
        @RequestParam songId: Long,
        @RequestParam assigneeId: Long,
        @RequestParam(required = false, defaultValue = "0") assignedBy: Long,
        @RequestParam(required = false) target: String?,
        @RequestParam(required = false) clearMarkers: Boolean?,
    ): Map<String, Any?> =
        withDb(target) { db ->
            val settings =
                Song.loadFromDbById(songId, db, storageService = storageService, storageApiClient = storageApiClient)
                    ?: return@withDb mapOf("ok" to false, "error" to "song_not_found")
            SiteUser.getSiteUserById(assigneeId, db, storageService, storageApiClient)
                ?: return@withDb mapOf("ok" to false, "error" to "user_not_found")
            SongAssignment.findExisting(songId, assigneeId, db, storageService, storageApiClient)?.let {
                return@withDb mapOf("ok" to false, "error" to "already_assigned", "id" to it.id)
            }
            val hasMarkers = settings.sourceMarkersList.any { it.isNotEmpty() }
            if (hasMarkers && clearMarkers == null) {
                return@withDb mapOf("ok" to false, "error" to "markers_exist")
            }
            // Best-effort автозаливка стемов (не роняем назначение, если конвертация не удалась). karaoke-app
            // работает только на машине админа — локальный диск с FLAC доступен независимо от того, в какую
            // БД (local/remote) пишем сам SongAssignment.
            try {
                ensureStemsInStorage(settings)
            } catch (_: Exception) {
            }
            val a = SongAssignment(database = db, storageService = storageService, storageApiClient = storageApiClient)
            a.assigneeId = assigneeId
            a.songId = songId
            a.assignedBy = assignedBy
            a.adminStatus = SongAssignmentStatus.ADMIN_OPEN
            val created =
                KaraokeDbTable.createDbInstance(entity = a, database = db) as? SongAssignment
                    ?: return@withDb mapOf("ok" to false, "error" to "create_failed")
            if (hasMarkers && clearMarkers == true) {
                val draft = SongAssignmentDraft(database = db, storageService = storageService, storageApiClient = storageApiClient)
                draft.assignmentId = created.id
                draft.assigneeId = assigneeId
                draft.editedSourceText = SongAssignmentDraft.encodeTextsPerVoice(settings.sourceTextList)
                draft.editedMarkers = SongAssignmentDraft.encodeMarkersPerVoice(List(settings.countVoices) { emptyList<SourceMarker>() })
                draft.userStatus = SongAssignmentStatus.USER_IN_PROGRESS
                KaraokeDbTable.createDbInstance(entity = draft, database = db)
            }
            mapOf("ok" to true, "id" to created.id)
        }

    // Список заданий (webvue3): композитный статус + метаданные песни/пользователя. Черновики тянем
    // батчем (без N+1), из них — user_status для композитного статуса и submitted_at.
    @PostMapping("/digest")
    @ResponseBody
    fun digest(
        @RequestParam(required = false) target: String?,
        @RequestParam(required = false) filterAssigneeId: Long?,
        @RequestParam(required = false) filterStatus: String?,
        @RequestParam(required = false) filterAuthor: String?,
    ): Map<String, Any> =
        withDb(target) { db ->
            var assignments = SongAssignment.loadAll(db, storageService, storageApiClient)
            filterAssigneeId?.let { a -> assignments = assignments.filter { it.assigneeId == a } }

            val drafts = SongAssignmentDraft.loadByAssignments(assignments.map { it.id }, db, storageService, storageApiClient)
            val users =
                assignments
                    .map { it.assigneeId }
                    .distinct()
                    .associateWith { SiteUser.getSiteUserById(it, db, storageService, storageApiClient) }
            val songs =
                if (assignments.isEmpty()) {
                    emptyMap()
                } else {
                    Song.loadListFromDbByIds(assignments.map { it.songId }.distinct(), db, storageService, storageApiClient)
                }

            var list =
                assignments.map { a ->
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
            filterAuthor?.takeIf { it.isNotBlank() }?.let { author ->
                list = list.filter { (it["author"] as? String)?.contains(author, ignoreCase = true) == true }
            }
            mapOf("songAssignmentsDigest" to list)
        }

    // Одно задание + черновик (просмотр submitted в webvue3): текст/маркеры пользователя для ревью,
    // ПО ВСЕМ ГОЛОСАМ (draftSourceTexts/draftMarkersPerVoice — массивы, индекс = номер голоса).
    @PostMapping("/byId")
    @ResponseBody
    fun byId(
        @RequestParam id: Long,
        @RequestParam(required = false) target: String?,
    ): Any? =
        withDb(target) { db ->
            val a = SongAssignment.getById(id, db, storageService, storageApiClient) ?: return@withDb null
            val draft = SongAssignmentDraft.getByAssignment(id, db, storageService, storageApiClient)
            val user = SiteUser.getSiteUserById(a.assigneeId, db, storageService, storageApiClient)
            val s = Song.loadFromDbById(a.songId, db, storageService = storageService, storageApiClient = storageApiClient)
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

    // Одобрить: применить черновик в tbl_songs для КАЖДОГО голоса черновика (setSourceMarkers
    // пересчитывает resultText/formattedText*/srt + saveToDb) и поднять id_status до 3 (порог
    // доступности в онлайн-плеере — PublicPlayerController.stemsReady). Если голосов в черновике
    // МЕНЬШЕ, чем сейчас в Song — пользователь удалил хвостовые голоса, обрезаем их и в Song
    // (truncateVoicesTo).
    //
    // Задание/черновик читаются И апрувятся (статус, reviewComment, reviewedAt) в ОДНОЙ И ТОЙ ЖЕ БД —
    // той, что выбрана в target (по умолчанию local). Пока сайт-юзер работает на проде (karaoke-web),
    // его черновик и само задание реально лежат НА СЕРВЕРЕ и могут быть ещё не подтянуты синхронизацией
    // (songassignments/songassignmentdrafts, SERVER_TO_LOCAL) — читать/писать local в этом случае значило
    // бы применить УСТАРЕВШУЮ разметку и апрувить "чужую" (несинкнутую) копию задания, которую следующий
    // pull с сервера перезатрёт обратно статусом "open" (remote первичен для sync).
    //
    // ИСКЛЮЧЕНИЕ — сама песня (`Song`): применение разметки (setSourceMarkers/.srt-файлы) и подъём
    // id_status ВСЕГДА идёт в LOCAL, независимо от target — karaoke-app умеет писать .srt и резолвить
    // rootFolder только на локальном диске админ-машины.
    @PostMapping("/approve")
    @ResponseBody
    fun approve(
        @RequestParam id: Long,
        @RequestParam(required = false) target: String?,
    ): Map<String, Any?> {
        val isRemoteRead = target == "remote"
        val readDb = if (isRemoteRead) Connection.remote() else null
        try {
            return withDb("local") { localDb ->
                val assignmentDb = readDb ?: localDb
                val aRead =
                    SongAssignment.getById(id, assignmentDb, storageService, storageApiClient)
                        ?: return@withDb mapOf("ok" to false, "status" to "error", "error" to "assignment_not_found")

                // Повторный/двойной клик по уже одобренному заданию — короткое замыкание
                // (specs/094-fix-approve-news-failure, FR-002/FR-006): не переприменяем разметку/push/
                // анонс повторно, просто сообщаем администратору, что задание уже одобрено.
                if (aRead.adminStatus == SongAssignmentStatus.ADMIN_APPROVED) {
                    return@withDb mapOf("ok" to true, "status" to "already_approved")
                }

                val draft =
                    SongAssignmentDraft.getByAssignment(id, assignmentDb, storageService, storageApiClient)
                        ?: return@withDb mapOf("ok" to false, "status" to "error", "error" to "draft_not_found")
                val settings =
                    Song.loadFromDbById(aRead.songId, localDb, storageService = storageService, storageApiClient = storageApiClient)
                        ?: return@withDb mapOf("ok" to false, "status" to "error", "error" to "song_not_found")

                val markersPerVoice = draft.editedMarkersPerVoice(json)
                val textsPerVoice = draft.editedTextsPerVoice(json)
                if (markersPerVoice.isEmpty()) {
                    return@withDb mapOf("ok" to false, "status" to "error", "error" to "bad_markers")
                }

                // Применение разметки к Song (маркеры/текст/файлы .srt + подъём idStatus) не должно
                // остаться необработанным исключением (specs/095-fix-approve-song-save-exception, тот
                // же класс риска, что чинил specs/094-fix-approve-news-failure для aRead.save()): при
                // сбое (например, SQLException на JDBC-соединении внутри settings.saveToDb()) задание
                // НЕ помечается одобренным (return ниже — до блока push/checkAndAnnounce/aRead.save()),
                // администратор получает типизированную ошибку вместо необработанного HTTP 500.
                try {
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
                    // Хвостовые голоса, удалённые пользователем в черновике (были в Song, но их больше
                    // нет в присланном списке) — обрезаем.
                    if (markersPerVoice.size < prevVoiceCount) {
                        settings.truncateVoicesTo(markersPerVoice.size)
                    }

                    // Сделать песню доступной в онлайн-плеере (idStatus>=6). Апрув админом присланной
                    // разметки — явное ручное подтверждение (не автоматика, FR-011 не применяется),
                    // поэтому статус выставляется сразу в терминальное значение 6 (READY), а не на 1 шаг
                    // вперёд (specs/022-song-status-lifecycle).
                    if (settings.idStatus < 6) {
                        settings.fields[SongField.ID_STATUS] = "6"
                        settings.saveToDb()
                    }
                } catch (e: Exception) {
                    println("[SongEditorController.approve] применение разметки к песне ${settings.id} не удалось: ${e.message}")
                    return@withDb mapOf("ok" to false, "status" to "error", "error" to "song_save_failed")
                }

                // Пушим изменённую песню на сервер — тот же механизм, что кнопка "Обновить на сервере"
                // в SongEdit.vue (doUpdateRemoteSettingFromLocalDatabase). Applied markers/idStatus
                // живут пока только в LOCAL; без явного push remote их не увидит (обычная запись
                // Song НЕ синкается по diff'у автоматически). Тот же предохранитель, что у самой
                // кнопки (allowUpdateRemote, :disabled="!allowUpdateRemote") — best-effort, ошибка
                // пуша не должна откатывать уже совершённый апрув.
                if (Karaoke.allowUpdateRemote) {
                    // Тайминги (specs/096-approve-news-timing-diagnostics) — временная диагностика:
                    // раньше сбой здесь тонул в пустом catch (_: Exception) без единого сообщения в
                    // логе, что делало невозможным отличить "быстро упало" от "долго висело".
                    val pushStart = System.currentTimeMillis()
                    try {
                        val pushResult = updateRemoteSongFromLocalDatabase(settings.id)
                        val pushDone = System.currentTimeMillis()
                        println(
                            "[approve/timing] push на SERVER: ${pushDone - pushStart} ms, " +
                                "created=${pushResult.created.size} updated=${pushResult.updated.size}",
                        )
                        // Апрув должен мгновенно (без ожидания отдельной синхронизации) отразиться
                        // новостью на сайте (specs/092-fix-auto-news-triggers) — но только если push
                        // реально применился (непустой SyncResult), иначе checkAndAnnounce сверился бы
                        // с устаревшей копией песни на сервере (см. research.md фичи 092, п.2-3: путь
                        // через updateDatabases → /changerecords best-effort и зависит от orthogonal
                        // sync-тумблеров, полагаться на него нельзя).
                        //
                        // checkAndAnnounce ЗАПУСКАЕТСЯ В ФОНОВОМ ПОТОКЕ, НЕ дожидаясь его завершения
                        // (specs/098-fix-approve-blocking-checkandannounce) — живая проверка на проде
                        // (specs/096) показала, что при накопленном «хвосте» ранее не анонсированных
                        // песен (в наблюдавшемся случае — 4126 кандидатов, ни один не прошёл
                        // isPubliclyWatchable) этот вызов синхронно занимает 50+ секунд, что превышает
                        // любой разумный тайм-аут браузера/прокси и превращает успешный апрув в «Ошибка
                        // запроса» для администратора. HTTP-ответ (и статус задания) не должны зависеть
                        // от длительности этого сканирования — оно и так best-effort и не гейтит success
                        // (FR-004 specs/094-fix-approve-news-failure). AtomicBoolean-гвард не даёт
                        // параллельным апрувам плодить несколько одновременных сканов поверх одного и
                        // того же прод-соединения.
                        if (pushResult.created.isNotEmpty() || pushResult.updated.isNotEmpty()) {
                            if (checkAndAnnounceRunning.compareAndSet(false, true)) {
                                Thread {
                                    val announceStart = System.currentTimeMillis()
                                    try {
                                        SongReleaseAnnouncementService.checkAndAnnounce(Connection.remote(), KSS_APP, SAC_APP)
                                        println(
                                            "[approve/timing] checkAndAnnounce (фоновый поток): " +
                                                "${System.currentTimeMillis() - announceStart} ms",
                                        )
                                    } catch (e: Exception) {
                                        println(
                                            "[SongEditorController.approve] checkAndAnnounce error (фоновый поток, " +
                                                "${System.currentTimeMillis() - announceStart} ms): ${e.message}",
                                        )
                                    } finally {
                                        checkAndAnnounceRunning.set(false)
                                    }
                                }.apply {
                                    isDaemon = true
                                    name = "approve-check-and-announce"
                                }.start()
                            } else {
                                println(
                                    "[SongEditorController.approve] checkAndAnnounce уже выполняется в фоне — " +
                                        "пропускаем повторный запуск для задания $id (идемпотентно, найдётся следующим вызовом)",
                                )
                            }
                        }
                    } catch (e: Exception) {
                        println(
                            "[SongEditorController.approve] push на SERVER не удался " +
                                "(${System.currentTimeMillis() - pushStart} ms): ${e.message}",
                        )
                    }
                }

                // Апрув пишется В ТУ ЖЕ БД, откуда прочитали задание (assignmentDb) — не всегда local.
                // Локальное применение к Song выше уже удалось — эта запись не должна остаться
                // необработанным исключением (specs/094-fix-approve-news-failure, FR-003/FR-005): при
                // сбое задание НЕ помечается одобренным, администратор получает типизированную ошибку
                // вместо необработанного HTTP 500 (см. research.md, п.2 — ранее незащищённое место).
                val saveStatusStart = System.currentTimeMillis()
                try {
                    aRead.adminStatus = SongAssignmentStatus.ADMIN_APPROVED
                    aRead.reviewedAt = Timestamp(System.currentTimeMillis())
                    aRead.reviewComment = ""
                    aRead.save()
                    println("[approve/timing] aRead.save(): ${System.currentTimeMillis() - saveStatusStart} ms")
                } catch (e: Exception) {
                    println(
                        "[SongEditorController.approve] сохранение статуса задания $id не удалось " +
                            "(${System.currentTimeMillis() - saveStatusStart} ms): ${e.message}",
                    )
                    return@withDb mapOf("ok" to false, "status" to "error", "error" to "save_failed")
                }
                mapOf("ok" to true, "status" to "success", "idStatus" to settings.idStatus)
            }
        } finally {
            if (readDb != null) {
                try {
                    readDb.getConnection()?.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    // Отклонить с комментарием — правки НЕ применяются, задание возвращается пользователю на доработку.
    // target ОБЯЗАН указывать на ту же БД, где реально работает пользователь (обычно remote, если весь
    // цикл идёт на PROD) — иначе статус/комментарий уйдут в БД, которую karaoke-web не читает, и
    // пользователь так и останется заблокирован в статусе "submitted" (не увидит отказ и не сможет
    // продолжить редактировать). Не "безопасно из любого вида", как для чистого чтения по id — это запись.
    @PostMapping("/reject")
    @ResponseBody
    fun reject(
        @RequestParam id: Long,
        @RequestParam(required = false, defaultValue = "") comment: String,
        @RequestParam(required = false) target: String?,
    ): Map<String, Any?> =
        withDb(target) { db ->
            val a =
                SongAssignment.getById(id, db, storageService, storageApiClient)
                    ?: return@withDb mapOf("ok" to false, "error" to "assignment_not_found")
            a.adminStatus = SongAssignmentStatus.ADMIN_REJECTED
            a.reviewComment = comment
            a.reviewedAt = Timestamp(System.currentTimeMillis())
            a.save()
            mapOf("ok" to true)
        }

    // Удалить назначение (снять задание). target — та же оговорка, что у reject() выше: запись должна
    // идти в реальную БД задания, иначе на ней задание останется висеть нетронутым.
    @PostMapping("/delete")
    @ResponseBody
    fun delete(
        @RequestParam id: Long,
        @RequestParam(required = false) target: String?,
    ): Map<String, Any?> =
        withDb(target) { db ->
            val ok = SongAssignment.delete(id, db)
            mapOf("ok" to ok)
        }

    // Отозвать назначение у редактора (забрать задание, чтобы передать другому). Семантически тот же
    // эффект, что у delete(), но с обязательной очисткой черновика — иначе в tbl_song_assignment_drafts
    // остаётся «висящая» строка на уже не существующее назначение, которая сбивает с толку при
    // аудите/отладке. После revoke() песня снова «не назначена» — другой редактор открывает её через
    // обычный селектор «Назначить…» в таблице песен, без необходимости сначала нажимать Delete.
    //
    // target — та же оговорка, что у reject()/delete(): пишем в реальную БД задания, иначе на ней
    // задание останется висеть нетронутым (на стороне пользователя будет виден старый draft).
    @PostMapping("/revoke")
    @ResponseBody
    fun revoke(
        @RequestParam id: Long,
        @RequestParam(required = false) target: String?,
    ): Map<String, Any?> =
        withDb(target) { db ->
            val exists =
                SongAssignment.getById(id, db, storageService, storageApiClient)
                    ?: return@withDb mapOf("ok" to false, "error" to "assignment_not_found")
            // Чистим черновик ДО удаления задания — на случай, если БД ловит FK наоборот
            // (на нашей схеме FK нет, но порядок не повредит, и черновик точно не «осиротеет»).
            SongAssignmentDraft.deleteByAssignment(id, db)
            val ok = SongAssignment.delete(id, db)
            mapOf("ok" to ok)
        }

    // Количество заданий "на проверке" — бейдж пункта меню «Задания редактора» в webvue3 (по образцу
    // /api/chat/unreadcount).
    @PostMapping("/submittedcount")
    @ResponseBody
    fun submittedCount(
        @RequestParam(required = false) target: String?,
    ): Int =
        withDb(target) { db ->
            SongAssignment.countSubmitted(db, storageService, storageApiClient)
        }

    // Батч-статус назначений для таблицы/карточки песни (кнопка «Назначить»/«Назначено») — без N+1:
    // одним запросом узнаём для целой страницы/одной песни, есть ли задание и в каком оно статусе.
    // songIds — CSV. Имя исполнителя — точечные getSiteUserById по уникальным assigneeId (их на
    // страницу мало — не N+1 в существенном смысле).
    @PostMapping("/statusbysongids")
    @ResponseBody
    fun statusBySongIds(
        @RequestParam songIds: String,
        @RequestParam(required = false) target: String?,
    ): Map<String, Any?> =
        withDb(target) { db ->
            val ids = songIds.split(",").mapNotNull { it.trim().toLongOrNull() }
            val composed = SongAssignment.composeStatusesForSongIds(ids, db, storageService, storageApiClient)
            val users =
                composed.values
                    .map { it.first.assigneeId }
                    .distinct()
                    .associateWith { SiteUser.getSiteUserById(it, db, storageService, storageApiClient) }
            val statuses =
                composed.mapValues { (_, pair) ->
                    val (a, status) = pair
                    val user = users[a.assigneeId]
                    mapOf(
                        "assignmentId" to a.id,
                        "status" to status.dbValue,
                        "assigneeName" to (user?.displayName?.takeIf { it.isNotBlank() } ?: user?.email ?: ""),
                    )
                }
            mapOf("statuses" to statuses)
        }

    // ---- Админский онлайн-редактор (webvue3) -----------------------------------------------
    //
    // Зеркало PublicSongEditorController для админской стороны: тот же UX редактора, что и в
    // karaoke-public, но canEdit=true всегда (админ — не конечный редактор сайта, проверки ему не
    // мешают) и без кнопок submit/recall. Поддерживает два режима, по параметру mode:
    //   - "song"        — id это songId; читаем/пишем Song (tbl_songs) для ВСЕХ голосов.
    //   - "assignment"  — id это assignmentId; читаем/пишем черновик задания (tbl_song_assignment_drafts).
    // target (local|remote) — куда писать и откуда читать (по умолчанию local). Для режима "song"
    // target определяет, ГДЕ будут жить правки; в "assignment" — где лежит само задание (status
    // и draft). Идентично по духу остальным target-aware эндпоинтам контроллера.

    // Открыть задание/песню в редакторе. Возвращает sourceTexts[]/markersPerVoice[] ВСЕХ голосов,
    // URLs стемов (используются для waveform и превью-плеера) и метаданные для шапки редактора.
    // canEdit=true жёстко (из режима редактор никогда не блокируется; submit/recall в этой версии нет).
    @PostMapping("/edit/byId")
    @ResponseBody
    fun editById(
        @RequestParam id: Long,
        @RequestParam(required = false, defaultValue = "song") mode: String,
        @RequestParam(required = false) target: String?,
    ): Any? {
        if (mode != "song" && mode != "assignment") {
            return mapOf("ok" to false, "error" to "bad_mode")
        }
        return withDb(target) { db ->
            // Резолвим songId в зависимости от режима.
            val songId: Long =
                if (mode == "song") {
                    id
                } else {
                    SongAssignment.getById(id, db, storageService, storageApiClient)?.songId
                        ?: return@withDb mapOf("found" to false, "id" to id)
                }

            // Song читаем ВСЕГДА из WORKING_DATABASE: только там есть локальный диск с FLAC и .srt
            // (см. комментарий getSongPlayerData в ApiController). target не влияет на выбор Song.
            val settings =
                Song.loadFromDbById(
                    songId,
                    WORKING_DATABASE,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                ) ?: return@withDb mapOf("found" to false, "id" to id, "songId" to songId)

            val sourceTexts: List<String>
            val markersPerVoice: List<List<SourceMarker>>
            var assignmentId: Long? = null
            var statusForResponse: String = "song"
            var reviewCommentForResponse: String = ""

            if (mode == "song") {
                sourceTexts = settings.sourceTextList.toMutableList()
                markersPerVoice = settings.sourceMarkersList.toMutableList()
            } else {
                val a =
                    SongAssignment.getById(id, db, storageService, storageApiClient)
                        ?: return@withDb mapOf("found" to false, "id" to id, "songId" to songId)
                if (a.songId != settings.id) return@withDb mapOf("found" to false, "id" to id, "songId" to songId)
                assignmentId = a.id
                val draft = SongAssignmentDraft.getByAssignment(a.id, db, storageService, storageApiClient)
                if (draft != null) {
                    sourceTexts = draft.editedTextsPerVoice(json).toMutableList()
                    markersPerVoice = draft.editedMarkersPerVoice(json).toMutableList()
                } else {
                    sourceTexts = settings.sourceTextList.toMutableList()
                    markersPerVoice = settings.sourceMarkersList.toMutableList()
                }
                statusForResponse =
                    SongAssignmentStatus
                        .resolve(
                            a.adminStatus,
                            draft?.userStatus,
                            a.reviewedAt,
                            draft?.submittedAt,
                        ).dbValue
                reviewCommentForResponse = if (statusForResponse == SongAssignmentStatus.REJECTED.dbValue) a.reviewComment else ""
            }

            mapOf(
                "found" to true,
                "mode" to mode,
                "id" to id,
                "songId" to settings.id,
                "songName" to settings.songName,
                "author" to settings.author,
                "album" to settings.album,
                "year" to settings.year.takeIf { it > 0 },
                "track" to settings.track.takeIf { it > 0 },
                "key" to settings.key.takeIf { it.isNotBlank() },
                "bpm" to settings.bpm,
                "voiceCount" to markersPerVoice.size,
                "sourceTexts" to sourceTexts,
                "markersPerVoice" to markersPerVoice,
                "audioVocalsUrl" to "/api/song/${settings.id}/filevoice.mp3",
                "audioAccompanimentUrl" to "/api/song/${settings.id}/fileminus.mp3",
                "audioBassUrl" to if (File(settings.bassNameFlac).exists()) "/api/song/${settings.id}/filebass.mp3" else null,
                "audioDrumsUrl" to if (File(settings.drumsNameFlac).exists()) "/api/song/${settings.id}/filedrums.mp3" else null,
                "albumImageUrl" to
                    settings.pictureAlbum?.storageFileName?.let {
                        "/api/picture/file?file=${java.net.URLEncoder.encode(it, java.nio.charset.StandardCharsets.UTF_8)}"
                    },
                "artistImageUrl" to
                    settings.pictureAuthor?.storageFileName?.let {
                        "/api/picture/file?file=${java.net.URLEncoder.encode(it, java.nio.charset.StandardCharsets.UTF_8)}"
                    },
                "exportBaseName" to "${settings.fileName} [id-${settings.id}]".rightFileName(),
                "canEdit" to true,
                "assignmentId" to assignmentId,
                "reviewComment" to reviewCommentForResponse,
                "status" to statusForResponse,
            )
        }
    }

    // Сохранить правки (ВСЕ голоса разом). sourceTexts/markersPerVoice — JSON-массивы.
    // В режиме "song" пишет напрямую в Song в ту же БД, что и assignmentsTarget (setSourceMarkers/
    // setSourceText тригерят saveToDb внутри). В режиме "assignment" — создаёт/обновляет черновик
    // задания (аналогично PublicSongEditorController.save, но без проверки canEdit — для админа
    // редактирование открыто в любом статусе).
    @PostMapping("/edit/save")
    @ResponseBody
    fun editSave(
        @RequestParam id: Long,
        @RequestParam(required = false, defaultValue = "song") mode: String,
        @RequestParam(required = false) target: String?,
        @RequestParam sourceTexts: String,
        @RequestParam markersPerVoice: String,
    ): Map<String, Any?> {
        if (mode != "song" && mode != "assignment") {
            return mapOf("ok" to false, "error" to "bad_mode")
        }
        // Парсим payload один раз (терпимый Json — ignoreUnknownKeys уже настроен в `json`).
        val parsedTexts: List<String>
        val parsedMarkers: List<List<SourceMarker>>
        try {
            parsedTexts = json.decodeFromString(ListSerializer(String.serializer()), sourceTexts)
            parsedMarkers = json.decodeFromString(ListSerializer(ListSerializer(SourceMarker.serializer())), markersPerVoice)
        } catch (_: Exception) {
            return mapOf("ok" to false, "error" to "bad_payload")
        }

        if (mode == "song") {
            // Пишем в Song в ту же БД, что и assignmentsTarget — единообразно с логикой
            // остальных target-aware методов. Song.setSourceMarkers/setSourceText делают saveToDb()
            // внутри (пересчитывают resultText/formattedTextSong/formattedTextTabs/formattedTextChords).
            return withDb(target) { db ->
                val settings =
                    Song.loadFromDbById(id, db, storageService = storageService, storageApiClient = storageApiClient)
                        ?: return@withDb mapOf("ok" to false, "error" to "song_not_found")
                val voiceCount = maxOf(settings.countVoices, parsedMarkers.size)
                for (v in 0 until voiceCount) {
                    val markers = parsedMarkers.getOrNull(v) ?: emptyList()
                    settings.setSourceMarkers(v, markers)
                    val text = parsedTexts.getOrNull(v) ?: ""
                    settings.setSourceText(v, text)
                }
                if (parsedMarkers.size < settings.countVoices) {
                    settings.truncateVoicesTo(parsedMarkers.size)
                }
                mapOf("ok" to true, "voiceCount" to settings.countVoices, "idStatus" to settings.idStatus)
            }
        } else {
            return withDb(target) { db ->
                val a =
                    SongAssignment.getById(id, db, storageService, storageApiClient)
                        ?: return@withDb mapOf("ok" to false, "error" to "assignment_not_found")
                var draft = SongAssignmentDraft.getByAssignment(a.id, db, storageService, storageApiClient)
                if (draft == null) {
                    draft = SongAssignmentDraft(database = db, storageService = storageService, storageApiClient = storageApiClient)
                    draft.assignmentId = a.id
                    draft.assigneeId = a.assigneeId
                    draft.userStatus = SongAssignmentStatus.USER_IN_PROGRESS
                    KaraokeDbTable.createDbInstance(entity = draft, database = db)
                }
                draft.editedSourceText = SongAssignmentDraft.encodeTextsPerVoice(parsedTexts)
                draft.editedMarkers = SongAssignmentDraft.encodeMarkersPerVoice(parsedMarkers)
                draft.save()
                mapOf(
                    "ok" to true,
                    "status" to SongAssignmentStatus.resolve(a.adminStatus, draft.userStatus, a.reviewedAt, draft.submittedAt).dbValue,
                )
            }
        }
    }

    // Бутстрап текста песни через Whisper-транскрипцию (см. WhisperAsrService) - ТОЛЬКО для
    // голоса, у которого текста ещё нет вообще: "сырой" распознанный текст (whisperText) можно
    // скопировать в WhisperDebugModal и использовать как отправную точку. Раньше этот эндпоинт
    // ещё и расставлял маркеры (word-level интерполяция через WhisperMarkerAligner.alignToMarkers)
    // для уже введённого текста - убрано: forced-alignment ("Точные маркеры",
    // editForcedAlignMarkers) даёт точность на слог вместо интерполяции по слову и дублировал
    // этот путь при непустом тексте, поэтому кнопка "Авто-маркеры" в SubsEdit.vue теперь
    // задизейблена, если текст уже есть - используйте "Точные маркеры" вместо неё.
    @PostMapping("/edit/autoMarkers")
    @ResponseBody
    fun editAutoMarkers(
        @RequestParam id: Long,
        @RequestParam sourceText: String,
    ): Map<String, Any?> {
        if (sourceText.isNotBlank()) return mapOf("ok" to false, "error" to "text_already_exists")

        val settings =
            Song.loadFromDbById(id, WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
                ?: return mapOf("ok" to false, "error" to "song_not_found")

        val vocalsFile = File(settings.vocalsNameFlac)
        if (!vocalsFile.exists()) return mapOf("ok" to false, "error" to "vocals_not_found")

        val transcription = WhisperAsrService.transcribe(vocalsFile) ?: return mapOf("ok" to false, "error" to "whisper_unavailable")
        val words = WhisperAsrService.flatWords(transcription)
        if (words.isEmpty()) return mapOf("ok" to false, "error" to "no_speech_recognized")

        // "Сырой" ответ Whisper - SubsEdit.vue показывает его в отдельном отладочном окне,
        // markers всегда пуст (см. комментарий выше) - WhisperDebugModal умеет копировать один
        // текст без применения маркеров (кнопка "Применить маркеры" задизейблена без них).
        val whisperText = transcription.text.ifBlank { transcription.segments.joinToString(" ") { it.text }.trim() }

        return mapOf(
            "ok" to true,
            "whisperText" to whisperText,
            "whisperWords" to words,
            "markers" to emptyList<Any>(),
        )
    }

    // Согласование официального текста с Whisper для ещё НЕ размеченной песни (см. план фичи
    // "Согласование официального текста с Whisper"): находит вставки (что-то реально спето, но
    // отсутствует в тексте) и возвращает дополненный текст. Ничего не сохраняет - только
    // предоставляет исправленный текст, дальше он идёт как вход для forced-alignment (align.py/
    // serve.py, взамен нынешнего alignToMarkers на этом эндпоинте) - интеграция с SubsEdit/serve.py
    // отдельная, следующая итерация (см. план фичи, "Вне рамок").
    @PostMapping("/edit/reconcileText")
    @ResponseBody
    fun editReconcileText(
        @RequestParam id: Long,
        @RequestParam sourceText: String,
    ): Map<String, Any?> {
        if (sourceText.isBlank()) return mapOf("ok" to false, "error" to "empty_source_text")

        val settings =
            Song.loadFromDbById(id, WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
                ?: return mapOf("ok" to false, "error" to "song_not_found")

        val vocalsFile = File(settings.vocalsNameFlac)
        if (!vocalsFile.exists()) return mapOf("ok" to false, "error" to "vocals_not_found")

        val transcription = WhisperAsrService.transcribe(vocalsFile) ?: return mapOf("ok" to false, "error" to "whisper_unavailable")
        val words = WhisperAsrService.flatWords(transcription)
        if (words.isEmpty()) return mapOf("ok" to false, "error" to "no_speech_recognized")

        val reconciledText = WhisperMarkerAligner.reconcileText(sourceText, words)
        return mapOf(
            "ok" to true,
            "text" to reconciledText,
            "changed" to (reconciledText != sourceText),
            // Для отладочного окна на фронте (переиспользует WhisperDebugModal) - видно, что именно
            // услышал Whisper, раз текст в итоге поменялся.
            "whisperWords" to words,
        )
    }

    // Расстановка маркеров через forced-alignment (alignment-ml/serve.py, см. AlignmentServiceClient) -
    // взамен Whisper ASR (editAutoMarkers): текст УЖЕ известен (обычно после /edit/reconcileText),
    // модель просто выравнивает его по аудио - точность на слог, без интерполяции. Ничего не
    // сохраняет, как и editAutoMarkers - фронт показывает результат на подтверждение, сохраняет
    // обычным Save.
    @PostMapping("/edit/forcedAlignMarkers")
    @ResponseBody
    fun editForcedAlignMarkers(
        @RequestParam id: Long,
        @RequestParam sourceText: String,
        @RequestParam(required = false) useFinetunedModel: Boolean?,
    ): Map<String, Any?> {
        if (sourceText.isBlank()) return mapOf("ok" to false, "error" to "empty_source_text")

        val settings =
            Song.loadFromDbById(id, WORKING_DATABASE, storageService = storageService, storageApiClient = storageApiClient)
                ?: return mapOf("ok" to false, "error" to "song_not_found")

        val vocalsFile = File(settings.vocalsNameFlac)
        if (!vocalsFile.exists()) return mapOf("ok" to false, "error" to "vocals_not_found")

        // useFinetunedModel не передан фронтом (старый кэш и т.п.) - берём дефолт из настроек
        // (alignmentUseFinetunedModel), а не жёстко false, чтобы свойство реально на что-то влияло.
        val effectiveUseFinetuned = useFinetunedModel ?: KaraokeProperties.getBoolean("alignmentUseFinetunedModel")
        val response =
            AlignmentServiceClient.align(vocalsFile, sourceText, effectiveUseFinetuned)
                ?: return mapOf("ok" to false, "error" to "alignment_service_unavailable")
        if (!response.ok || response.syllables.isEmpty()) return mapOf("ok" to false, "error" to "no_alignment_result")

        val syllableTimes = response.syllables.map { (it.startMs / 1000.0) to (it.endMs / 1000.0) }
        val markers =
            WhisperMarkerAligner.buildMarkersFromSyllableTimes(sourceText, syllableTimes)
                ?: return mapOf("ok" to false, "error" to "syllable_count_mismatch")

        return mapOf("ok" to true, "markers" to markers)
    }

    // AI-редактор текста (кнопка в SubsEdit.vue): исправление орфографии/пунктуации через LLM -
    // LM Studio, тот же клиент (LmStudioService), что и ScraperAgent для поиска текстов песен
    // (см. docs/features/llm-lyrics-search.md). Ничего не сохраняет - фронт показывает результат
    // с подсветкой правок, применяет обычным Apply, сохраняет обычным Save.
    @PostMapping("/edit/correctText")
    @ResponseBody
    fun editCorrectText(
        @RequestParam mode: String,
        @RequestParam sourceText: String,
    ): Map<String, Any?> {
        if (sourceText.isBlank()) return mapOf("ok" to false, "error" to "empty_source_text")
        if (mode != "spelling" && mode != "punctuation") return mapOf("ok" to false, "error" to "bad_mode")

        val corrected =
            try {
                if (mode == "spelling") TextCorrectorAgent.fixSpelling(sourceText) else TextCorrectorAgent.fixPunctuation(sourceText)
            } catch (e: Exception) {
                null
            } ?: return mapOf("ok" to false, "error" to "lm_studio_unavailable")

        return mapOf("ok" to true, "text" to corrected)
    }
}
