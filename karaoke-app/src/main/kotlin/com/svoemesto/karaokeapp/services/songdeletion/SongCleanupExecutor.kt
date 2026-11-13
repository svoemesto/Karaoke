package com.svoemesto.karaokeapp.services.songdeletion

import com.svoemesto.karaokeapp.AppLog
import com.svoemesto.karaokeapp.HealthReport
import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.KaraokeFileType
import com.svoemesto.karaokeapp.StorageCacheReset
import com.svoemesto.karaokeapp.WORKING_DATABASE
import com.svoemesto.karaokeapp.model.Message
import com.svoemesto.karaokeapp.model.SongNameHistory
import com.svoemesto.karaokeapp.model.SseNotification
import com.svoemesto.karaokeapp.services.KSS_APP
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.SAC_APP
import com.svoemesto.karaokeapp.services.SNS
import com.svoemesto.karaokeapp.services.StorageApiClient
import com.svoemesto.karaokeapp.services.StorageMetadataCache
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

/**
 * Фоновая уборка артефактов удалённой песни (KARAOKE-429, спека 739, US3).
 *
 * **Порядок шагов — FR-031:** диск → хранилища → альбом и его обложка → папка
 * альбома. Папка альбома последняя: её содержимое известно только после удаления
 * файлов песни.
 *
 * **Идемпотентность.** Каждый шаг устойчив к повтору: уже удалённый файл/объект
 * даёт `SKIPPED`, а не `FAILED` (FR-030: задание переживает рестарт приложения и
 * продолжается с незавершённых шагов).
 *
 * **Честный остаток.** Каждое действие пишется в журнал
 * ([SongCleanupJournal]); прогон получает терминальный статус `DONE` только когда
 * отказов нет, иначе `PARTIAL` (SC-003).
 *
 * @see specs/739-song-delete-cascade/spec.md FR-010…FR-022, FR-029…FR-037
 */
class SongCleanupExecutor(
    private val database: KaraokeConnection = WORKING_DATABASE,
    private val storageService: KaraokeStorageService = KSS_APP,
    private val storageApiClient: StorageApiClient = SAC_APP,
    /**
     * Паузы между попытками удаления объекта хранилища (FR-032).
     *
     * Параметр — чтобы тест повторов не ждал реальные 2s→60s. По умолчанию —
     * боевые значения из [BACKOFF_MS].
     */
    private val backoffMs: List<Long> = BACKOFF_MS,
) {
    companion object {
        /** Категория структурированных логов фичи (ADR local-0005). */
        const val CATEGORY = "infra.song.delete"

        /** Имя бакета с объектами песен. */
        const val BUCKET = "karaoke"

        /** Число попыток при недоступности хранилища (FR-032). */
        const val MAX_ATTEMPTS = 5

        /** Паузы между попытками, миллисекунды (FR-032): 2s → 60s. */
        val BACKOFF_MS: List<Long> = listOf(2_000L, 5_000L, 15_000L, 30_000L, 60_000L)

        /**
         * Остались ли в папке альбома файлы ПОСЛЕ уборки песни (FR-020, FR-021).
         *
         * Папка альбома удаляется, только если в ней не осталось ни одного файла:
         * удалять папку с файлами нельзя — среди них могут быть файлы живых песен
         * (альбом-«близнец») или неопознанные файлы, которые по FR-016 остаются
         * на месте.
         *
         * @param rootFolder папка альбома.
         * @param filesToDelete файлы, которые уборка удалит (считаются вычтенными).
         * @return число файлов, остающихся в папке.
         */
        fun remainingFilesCount(
            rootFolder: String,
            filesToDelete: Collection<java.io.File> = emptyList(),
        ): Int {
            if (rootFolder.isBlank()) return 0
            val root = java.io.File(rootFolder)
            if (!root.isDirectory) return 0
            val deletedPaths = filesToDelete.map { it.absolutePath }.toSet()
            var count = 0
            root.walkTopDown().forEach { file ->
                if (file.isDirectory) return@forEach
                if (file.absolutePath in deletedPaths) return@forEach
                count++
            }
            return count
        }

        /**
         * Живых песен, чьи файлы лежат в папке альбома (FR-021).
         *
         * Проверяются оба признака: папка (`tbl_songs.root_folder`) и альбом
         * (`tbl_songs.album_id`) — альбом-«близнец» может иметь другую запись
         * альбома, но те же файлы на диске.
         *
         * При недоступной БД возвращается 1 (папку НЕ удаляем): сбой проверки не
         * должен приводить к удалению папки с живыми песнями.
         */
        fun liveSongsInAlbumFolder(
            database: KaraokeConnection,
            rootFolder: String,
            albumId: Long,
        ): Int {
            val connection = database.getConnection() ?: return 1
            return try {
                val sql =
                    if (albumId > 0L) {
                        "SELECT count(*) FROM tbl_songs " +
                            "WHERE rtrim(root_folder, '/') = rtrim(?, '/') OR album_id = ?"
                    } else {
                        "SELECT count(*) FROM tbl_songs WHERE rtrim(root_folder, '/') = rtrim(?, '/')"
                    }
                connection.prepareStatement(sql).use { ps ->
                    ps.setString(1, rootFolder)
                    if (albumId > 0L) ps.setLong(2, albumId)
                    ps.executeQuery().use { rs ->
                        if (rs.next()) rs.getInt(1) else 1
                    }
                }
            } catch (e: Exception) {
                log.warn(
                    "song.cleanup.album_folder.count_failed: rootFolder={} albumId={} — {}",
                    rootFolder,
                    albumId,
                    e.toString(),
                )
                1
            }
        }

        /**
         * Размер в человекочитаемых единицах — МБ/ГБ (FR-036).
         *
         * Сырые байты в уведомлении администратору неинформативны: песня —
         * это десятки-сотни мегабайт, а альбом — гигабайты.
         *
         * @param bytes количество освобождённых байт.
         * @return строка вида «12,3 МБ» или «1,05 ГБ».
         */
        fun formatBytes(bytes: Long): String {
            if (bytes <= 0L) return "0 МБ"
            val megabytes = bytes.toDouble() / (1024.0 * 1024.0)
            return if (megabytes >= 1024.0) {
                String.format(Locale.ROOT, "%.2f ГБ", megabytes / 1024.0)
            } else {
                String.format(Locale.ROOT, "%.1f МБ", megabytes)
            }
        }

        private val log = LoggerFactory.getLogger(CATEGORY)
    }

    /** Накопительные счётчики прогона. */
    private data class Counters(
        var filesTotal: Int = 0,
        var filesDeleted: Int = 0,
        var filesKept: Int = 0,
        var filesFailed: Int = 0,
        var objectsTotal: Int = 0,
        var objectsDeleted: Int = 0,
        var objectsKept: Int = 0,
        var objectsFailed: Int = 0,
        var bytesDeleted: Long = 0,
    )

    /**
     * Выполнить уборку прогона.
     *
     * @param runId идентификатор прогона (`tbl_song_cleanup_runs.id`).
     * @param songId идентификатор удалённой песни.
     * @param initiator кто инициировал уборку.
     * @return `true`, если уборка выполнена (даже частично); `false`, если прогон
     *   не найден или шаги не выполнены.
     */
    fun execute(
        runId: Long,
        songId: Long,
        initiator: String,
    ): Boolean {
        val run =
            SongCleanupJournal.runSummary(runId, database)
                ?: run {
                    log.warn("song.cleanup.run.started: runId={} прогон не найден", runId)
                    return false
                }
        log.info(
            "event=song.cleanup.run.started code=SONG-CLEANUP-001 runId={} songId={}",
            runId,
            songId,
        )
        markRunning(runId)

        val counters = Counters()
        try {
            cleanupDisk(run, songId, initiator, counters)
            cleanupStorages(run, songId, initiator, counters)
            cleanupAlbum(run, initiator, counters)
            cleanupAlbumFolder(run, initiator, counters)
        } catch (e: Exception) {
            AppLog.error(
                category = CATEGORY,
                code = "song.cleanup.run.failed",
                event = "song.cleanup.run.failed",
                fields = mapOf("runId" to runId),
                error = e,
            )
            SongCleanupJournal.updateRunState(
                runId,
                CleanupRunState.FAILED,
                database,
                error = e.toString(),
            )
            return false
        }

        SongCleanupJournal.updateCounters(
            runId = runId,
            filesTotal = counters.filesTotal,
            filesDeleted = counters.filesDeleted,
            filesKept = counters.filesKept,
            filesFailed = counters.filesFailed,
            objectsTotal = counters.objectsTotal,
            objectsDeleted = counters.objectsDeleted,
            objectsKept = counters.objectsKept,
            objectsFailed = counters.objectsFailed,
            bytesDeleted = counters.bytesDeleted,
            database = database,
        )

        // SC-003: «ложного успеха» не бывает — при отказах прогон PARTIAL, а не DONE.
        val hasFailures = counters.filesFailed > 0 || counters.objectsFailed > 0
        val finalState = CleanupRunStateMachine.terminalState(hasFailures)
        SongCleanupJournal.updateRunState(runId, finalState, database)
        log.info(
            "event=song.cleanup.run.finished code=SONG-CLEANUP-002 runId={} state={} filesDeleted={} " +
                "objectsDeleted={} bytesDeleted={}",
            runId,
            finalState.name,
            counters.filesDeleted,
            counters.objectsDeleted,
            counters.bytesDeleted,
        )
        notifyTerminalState(run, finalState, counters)
        return true
    }

    /**
     * Перевести прогон в состояние `RUNNING`.
     *
     * Цепочка состояний — `PLANNED → APPROVED → RUNNING → …`. Если прогон ещё в
     * `PLANNED` (например, уборку запустили вручную, минуя сервис удаления),
     * сначала фиксируется `APPROVED` — иначе переход в `RUNNING` был бы отклонён
     * машиной состояний, и прогон остался бы в `PLANNED`.
     */
    private fun markRunning(runId: Long) {
        val current = SongCleanupJournal.currentState(runId, database)
        if (current == CleanupRunState.PLANNED) {
            SongCleanupJournal.updateRunState(runId, CleanupRunState.APPROVED, database)
        }
        SongCleanupJournal.updateRunState(runId, CleanupRunState.RUNNING, database)
    }

    // ---------------------------------------------------------------- диск

    /**
     * Шаг «диск» (FR-010, FR-012, FR-015, FR-016).
     *
     * Удаляются ВСЕ файлы песни — включая исходники и файлы проектов рендера
     * (FR-012: вердикты `PROTECTED`/`KEEP` реестра уборки здесь НЕ применяются).
     * Файлы, которые не удалось опознать как артефакты песни, остаются на месте и
     * попадают в журнал как «оставлено» (FR-016).
     */
    private fun cleanupDisk(
        run: SongCleanupJournal.RunSummary,
        songId: Long,
        initiator: String,
        counters: Counters,
    ) {
        val rootFolder = run.rootFolder
        val fileName = run.fileName
        if (rootFolder.isBlank() || fileName.isBlank()) {
            log.warn("song.cleanup.step.done: runId={} нет контекста песни — шаг «диск» пропущен", run.runId)
            return
        }
        val root = File(rootFolder)
        if (!root.isDirectory) return

        SongArtifactOwnership.artifactDirs(root).forEach { dir ->
            val files = dir.listFiles()?.sortedBy { it.name }.orEmpty()
            files.forEach { file ->
                if (file.isDirectory) return@forEach
                when {
                    SongArtifactOwnership.isOwnArtifact(fileName, file.name) -> deleteDiskFile(run, file, initiator, counters)
                    // Похож на файл песни, но правилом не опознан (например, ручная
                    // правка имени) — оставляем на месте и фиксируем в журнале.
                    file.name.startsWith(fileName) -> keepDiskFile(run, file, "not_recognized", counters)
                    else -> Unit
                }
            }
        }
    }

    /** Удаление файла песни (или битого симлинка). */
    private fun deleteDiskFile(
        run: SongCleanupJournal.RunSummary,
        file: File,
        initiator: String,
        counters: Counters,
    ) {
        val isSymlink = Files.isSymbolicLink(file.toPath())
        if (isSymlink) {
            // FR-015: битые симлинки удаляются, живые — остаются (чужой/общий).
            if (symbolicLinkTargetExists(file)) {
                counters.filesKept++
                SongCleanupJournal.addEntry(
                    runId = run.runId,
                    kind = SongCleanupJournal.EntryKind.FILE,
                    location = SongCleanupJournal.EntryLocation.LOCAL_FILESYSTEM,
                    pathOrKey = file.absolutePath,
                    sizeBytes = 0,
                    result = SongCleanupJournal.EntryResult.SKIPPED,
                    reason = "alive_symlink",
                    initiator = initiator,
                    database = database,
                )
                return
            }
        }
        counters.filesTotal++
        val size =
            try {
                if (isSymlink) 0L else file.length()
            } catch (e: Exception) {
                0L
            }
        val deleted =
            try {
                Files.deleteIfExists(file.toPath())
            } catch (e: Exception) {
                AppLog.warn(
                    category = CATEGORY,
                    code = "song.cleanup.step.failed",
                    event = "song.cleanup.step.failed",
                    fields = mapOf("path" to file.absolutePath),
                    error = e,
                )
                false
            }
        if (deleted) {
            counters.filesDeleted++
            counters.bytesDeleted += size
            SongCleanupJournal.addEntry(
                runId = run.runId,
                kind = SongCleanupJournal.EntryKind.FILE,
                location = SongCleanupJournal.EntryLocation.LOCAL_FILESYSTEM,
                pathOrKey = file.absolutePath,
                sizeBytes = size,
                result = SongCleanupJournal.EntryResult.DELETED,
                initiator = initiator,
                database = database,
            )
        } else {
            counters.filesFailed++
            SongCleanupJournal.addEntry(
                runId = run.runId,
                kind = SongCleanupJournal.EntryKind.FILE,
                location = SongCleanupJournal.EntryLocation.LOCAL_FILESYSTEM,
                pathOrKey = file.absolutePath,
                sizeBytes = size,
                result = SongCleanupJournal.EntryResult.FAILED,
                reason = "file_busy",
                initiator = initiator,
                database = database,
            )
        }
    }

    /** Файл оставлен на месте с причиной (FR-016). */
    private fun keepDiskFile(
        run: SongCleanupJournal.RunSummary,
        file: File,
        reason: String,
        counters: Counters,
    ) {
        counters.filesTotal++
        counters.filesKept++
        SongCleanupJournal.addEntry(
            runId = run.runId,
            kind = SongCleanupJournal.EntryKind.FILE,
            location = SongCleanupJournal.EntryLocation.LOCAL_FILESYSTEM,
            pathOrKey = file.absolutePath,
            sizeBytes = safeLength(file),
            result = SongCleanupJournal.EntryResult.KEPT,
            reason = reason,
            initiator = currentInitiator(run),
            database = database,
        )
    }

    // ------------------------------------------------------------ хранилища

    /**
     * Шаг «хранилища» (FR-013, FR-014, FR-032, FR-037).
     *
     * Удаляются объекты песни по текущему имени и по всем именам из истории
     * переименований. Обложка альбома и картинка автора не удаляются (FR-014) —
     * они защищены структурно: другой префикс ключа.
     */
    private fun cleanupStorages(
        run: SongCleanupJournal.RunSummary,
        songId: Long,
        initiator: String,
        counters: Counters,
    ) {
        val author = run.author
        val year = run.year
        val album = run.album
        if (author.isBlank() || album.isBlank()) return

        val names = LinkedHashSet<String>()
        run.fileName.takeIf { it.isNotBlank() }?.let { names.add(it) }
        names.addAll(SongNameHistory.listForSong(songId, database))

        val prefix = "$author/$year - $album/"
        val localObjects = listObjects(source = StorageMetadataCache.SOURCE_LOCAL)
        val remoteObjects = listObjects(source = StorageMetadataCache.SOURCE_REMOTE)

        listOf(
            Triple(StorageMetadataCache.SOURCE_LOCAL, SongCleanupJournal.EntryLocation.LOCAL_STORAGE, localObjects),
            Triple(StorageMetadataCache.SOURCE_REMOTE, SongCleanupJournal.EntryLocation.REMOTE_STORAGE, remoteObjects),
        ).forEach { (source, location, objects) ->
            objects.forEach { info ->
                val key = info.fileName
                if (!key.startsWith(prefix)) return@forEach
                val own =
                    names.any { name ->
                        SongArtifactOwnership.isOwnStorageKey(author, year.toString(), album, name, key)
                    }
                if (!own) return@forEach
                deleteStorageObject(run, source, location, key, info.size, initiator, counters)
            }
        }
        log.info(
            "event=song.cleanup.step.done code=SONG-CLEANUP-003 runId={} step=storage objectsDeleted={}",
            run.runId,
            counters.objectsDeleted,
        )
    }

    /** Список объектов бакета по источнику (пустой список при недоступности). */
    private fun listObjects(source: String): List<com.svoemesto.karaokeapp.services.StorageFileInfo> =
        try {
            if (source == StorageMetadataCache.SOURCE_REMOTE) {
                storageApiClient.listFilesInfo(BUCKET).block() ?: emptyList()
            } else {
                storageService.listFilesInfo(BUCKET)
            }
        } catch (e: Exception) {
            AppLog.warn(
                category = CATEGORY,
                code = "song.cleanup.step.failed",
                event = "song.cleanup.step.failed",
                fields = mapOf("source" to source, "step" to "storage.list"),
                error = e,
            )
            emptyList()
        }

    /**
     * Удаление объекта хранилища с повторами (FR-032): до 5 попыток с паузами
     * 2s → 60s, каждая попытка — отдельная запись журнала.
     */
    private fun deleteStorageObject(
        run: SongCleanupJournal.RunSummary,
        source: String,
        location: SongCleanupJournal.EntryLocation,
        key: String,
        size: Long,
        initiator: String,
        counters: Counters,
    ) {
        counters.objectsTotal++
        var deleted = false
        var lastError: String? = null
        for (attempt in 1..MAX_ATTEMPTS) {
            try {
                if (source == StorageMetadataCache.SOURCE_REMOTE) {
                    storageApiClient.deleteFile(BUCKET, key).block()
                } else {
                    storageService.deleteFile(BUCKET, key)
                }
                deleted = true
                SongCleanupJournal.addEntry(
                    runId = run.runId,
                    kind = SongCleanupJournal.EntryKind.STORAGE_OBJECT,
                    location = location,
                    pathOrKey = key,
                    sizeBytes = size,
                    result = SongCleanupJournal.EntryResult.DELETED,
                    attempt = attempt,
                    initiator = initiator,
                    database = database,
                )
                break
            } catch (e: Exception) {
                lastError = e.toString()
                SongCleanupJournal.addEntry(
                    runId = run.runId,
                    kind = SongCleanupJournal.EntryKind.STORAGE_OBJECT,
                    location = location,
                    pathOrKey = key,
                    sizeBytes = size,
                    result = SongCleanupJournal.EntryResult.FAILED,
                    reason = "storage_unavailable",
                    attempt = attempt,
                    initiator = initiator,
                    database = database,
                )
                if (attempt < MAX_ATTEMPTS) {
                    val pause = backoffMs.getOrElse(attempt - 1) { backoffMs.lastOrNull() ?: 0L }
                    try {
                        Thread.sleep(pause)
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                }
            }
        }
        if (deleted) {
            counters.objectsDeleted++
            counters.bytesDeleted += size
            // FR-037: кеш сведений о хранилище приводится в соответствие с фактом.
            invalidateStorageCache(source, key)
        } else {
            counters.objectsFailed++
            AppLog.warn(
                category = CATEGORY,
                code = "song.cleanup.step.failed",
                event = "song.cleanup.step.failed",
                fields = mapOf("source" to source, "key" to key, "reason" to (lastError ?: "unknown")),
            )
        }
    }

    /**
     * Инвалидация строки кеша метаданных хранилища по удалённому ключу (FR-037).
     *
     * Тот же механизм, что у `POST /api/song/resetStorageCache`
     * ([StorageMetadataCache.refreshKeys]): после удаления объекта его строка в
     * `tbl_storage_metadata_cache` не должна оставаться — иначе проверка здоровья
     * продолжит считать удалённый объект существующим.
     */
    private fun invalidateStorageCache(
        source: String,
        key: String,
    ) {
        try {
            HealthReport.storageMetadataCache?.refreshKeys(listOf(Triple(source, BUCKET, key)))
        } catch (e: Exception) {
            log.warn("song.cleanup.cache.invalidate_failed: source={} key='{}' — {}", source, key, e.toString())
        }
    }

    // --------------------------------------------------------------- альбом

    /**
     * Шаг «альбом» (FR-018, FR-019): альбом удаляется, только если песня была
     * последней в нём. Удаляются запись `tbl_albums`, запись `tbl_pictures`
     * картинки альбома, обложка и превью обложки в обоих хранилищах.
     */
    private fun cleanupAlbum(
        run: SongCleanupJournal.RunSummary,
        initiator: String,
        counters: Counters,
    ) {
        val albumId = run.albumId
        if (albumId <= 0L) return
        val liveSongs = countLiveSongsInAlbum(albumId)
        if (liveSongs > 0) {
            SongCleanupJournal.addEntry(
                runId = run.runId,
                kind = SongCleanupJournal.EntryKind.ALBUM,
                location = SongCleanupJournal.EntryLocation.DATABASE,
                pathOrKey = "album:$albumId",
                sizeBytes = 0,
                result = SongCleanupJournal.EntryResult.KEPT,
                reason = "album_has_live_songs",
                initiator = initiator,
                database = database,
            )
            return
        }
        // Запись альбома.
        val albumDeleted = deleteAlbumRecord(albumId)
        SongCleanupJournal.addEntry(
            runId = run.runId,
            kind = SongCleanupJournal.EntryKind.ALBUM,
            location = SongCleanupJournal.EntryLocation.DATABASE,
            pathOrKey = "album:$albumId",
            sizeBytes = 0,
            result =
                if (albumDeleted) {
                    SongCleanupJournal.EntryResult.DELETED
                } else {
                    SongCleanupJournal.EntryResult.FAILED
                },
            reason = if (albumDeleted) null else "db_error",
            initiator = initiator,
            database = database,
        )
        // Запись картинки альбома в справочнике изображений.
        val pictureName = "${authorOf(run)} - ${run.year} - ${run.album}"
        val pictureDeleted = deletePictureRecord(pictureName)
        SongCleanupJournal.addEntry(
            runId = run.runId,
            kind = SongCleanupJournal.EntryKind.PICTURE,
            location = SongCleanupJournal.EntryLocation.DATABASE,
            pathOrKey = pictureName,
            sizeBytes = 0,
            result =
                if (pictureDeleted) {
                    SongCleanupJournal.EntryResult.DELETED
                } else {
                    SongCleanupJournal.EntryResult.FAILED
                },
            reason = if (pictureDeleted) null else "db_error",
            initiator = initiator,
            database = database,
        )
        // Обложка и превью обложки в обоих хранилищах.
        listOf(KaraokeFileType.PICTURE_ALBUM, KaraokeFileType.PICTURE_ALBUM_PREVIEW).forEach { type ->
            val key =
                StorageCacheReset.storageFileNameFor(
                    author = authorOf(run),
                    year = run.year.toString(),
                    album = run.album,
                    songFileName = "",
                    karaokeFileType = type,
                )
            deleteAlbumCoverObject(run, key, initiator, counters)
        }
    }

    /** Удаление обложки альбома в обоих хранилищах. */
    private fun deleteAlbumCoverObject(
        run: SongCleanupJournal.RunSummary,
        key: String,
        initiator: String,
        counters: Counters,
    ) {
        listOf(
            StorageMetadataCache.SOURCE_LOCAL to SongCleanupJournal.EntryLocation.LOCAL_STORAGE,
            StorageMetadataCache.SOURCE_REMOTE to SongCleanupJournal.EntryLocation.REMOTE_STORAGE,
        ).forEach { (source, location) ->
            counters.objectsTotal++
            try {
                val exists =
                    if (source == StorageMetadataCache.SOURCE_REMOTE) {
                        storageApiClient.fileExists(BUCKET, key)
                    } else {
                        storageService.fileExists(BUCKET, key)
                    }
                if (!exists) {
                    counters.objectsKept++
                    SongCleanupJournal.addEntry(
                        runId = run.runId,
                        kind = SongCleanupJournal.EntryKind.PICTURE,
                        location = location,
                        pathOrKey = key,
                        sizeBytes = 0,
                        result = SongCleanupJournal.EntryResult.SKIPPED,
                        reason = "not_found",
                        initiator = initiator,
                        database = database,
                    )
                    return@forEach
                }
                if (source == StorageMetadataCache.SOURCE_REMOTE) {
                    storageApiClient.deleteFile(BUCKET, key).block()
                } else {
                    storageService.deleteFile(BUCKET, key)
                }
                counters.objectsDeleted++
                SongCleanupJournal.addEntry(
                    runId = run.runId,
                    kind = SongCleanupJournal.EntryKind.PICTURE,
                    location = location,
                    pathOrKey = key,
                    sizeBytes = 0,
                    result = SongCleanupJournal.EntryResult.DELETED,
                    initiator = initiator,
                    database = database,
                )
                invalidateStorageCache(source, key)
            } catch (e: Exception) {
                counters.objectsFailed++
                SongCleanupJournal.addEntry(
                    runId = run.runId,
                    kind = SongCleanupJournal.EntryKind.PICTURE,
                    location = location,
                    pathOrKey = key,
                    sizeBytes = 0,
                    result = SongCleanupJournal.EntryResult.FAILED,
                    reason = "storage_unavailable",
                    initiator = initiator,
                    database = database,
                )
            }
        }
    }

    // --------------------------------------------------------- папка альбома

    /**
     * Шаг «папка альбома» (FR-020, FR-021, FR-022, FR-017).
     *
     * Папка удаляется, если в ней нет файлов ЖИВЫХ песен (FR-020); если файлы
     * песен, заведённых в базе, лежат в папке — папка остаётся (FR-021).
     *
     * Проверка идёт по БД, а не по наличию файлов: неопознанные файлы (мусор
     * `[key].json` от прежних удалений) папку не удерживают — иначе папка
     * альбома удалённой песни оставалась бы навсегда. Каждый удалённый файл
     * попадает в журнал отдельно (FR-016: что осталось / что ушло — видно).
     */
    private fun cleanupAlbumFolder(
        run: SongCleanupJournal.RunSummary,
        initiator: String,
        counters: Counters,
    ) {
        val rootFolder = run.rootFolder
        if (rootFolder.isBlank()) return
        val root = File(rootFolder)
        if (!root.isDirectory) return

        // FR-020/FR-021: папка удаляется, если в ней НЕТ файлов живых песен.
        // Проверка — по БД: песни, заведённые в базе, живут в папке альбома
        // (tbl_songs.root_folder) и держат альбом (tbl_songs.album_id).
        //
        // Важно: файлы, которые не удалось опознать как артефакты песни (мусор
        // `[key].json` от прежних удалений), папку НЕ удерживают. Иначе папка
        // альбома удалённой песни оставалась бы навсегда (дефект, найден на
        // приёмке 2026-11-12: альбом был последним, а папка осталась).
        val liveSongs = countLiveSongsInAlbumFolder(rootFolder, run.albumId)
        if (liveSongs > 0) {
            SongCleanupJournal.addEntry(
                runId = run.runId,
                kind = SongCleanupJournal.EntryKind.ALBUM_FOLDER,
                location = SongCleanupJournal.EntryLocation.LOCAL_FILESYSTEM,
                pathOrKey = rootFolder,
                sizeBytes = 0,
                result = SongCleanupJournal.EntryResult.KEPT,
                reason = "album_has_live_songs",
                initiator = initiator,
                database = database,
            )
            return
        }
        // Снимок содержимого папки ДО удаления: после удаления размеры файлов
        // недоступны, а в журнале должны лежать реальные пути и объём.
        val removedFiles = collectFilesWithSizes(root)
        val removedBytes = removedFiles.sumOf { it.second }
        val deleted = deleteRecursively(root)
        if (deleted) {
            counters.filesTotal += removedFiles.size
            counters.filesDeleted += removedFiles.size
            counters.bytesDeleted += removedBytes
            removedFiles.forEach { (path, size) ->
                SongCleanupJournal.addEntry(
                    runId = run.runId,
                    kind = SongCleanupJournal.EntryKind.FILE,
                    location = SongCleanupJournal.EntryLocation.LOCAL_FILESYSTEM,
                    pathOrKey = path,
                    sizeBytes = size,
                    result = SongCleanupJournal.EntryResult.DELETED,
                    reason = "removed_with_album",
                    initiator = initiator,
                    database = database,
                )
            }
            SongCleanupJournal.addEntry(
                runId = run.runId,
                kind = SongCleanupJournal.EntryKind.ALBUM_FOLDER,
                location = SongCleanupJournal.EntryLocation.LOCAL_FILESYSTEM,
                pathOrKey = rootFolder,
                sizeBytes = removedBytes,
                result = SongCleanupJournal.EntryResult.DELETED,
                initiator = initiator,
                database = database,
            )
        } else {
            counters.filesFailed++
            SongCleanupJournal.addEntry(
                runId = run.runId,
                kind = SongCleanupJournal.EntryKind.ALBUM_FOLDER,
                location = SongCleanupJournal.EntryLocation.LOCAL_FILESYSTEM,
                pathOrKey = rootFolder,
                sizeBytes = 0,
                result = SongCleanupJournal.EntryResult.FAILED,
                reason = "file_busy",
                initiator = initiator,
                database = database,
            )
        }
    }

    /**
     * SSE-уведомление о терминальном статусе прогона (FR-036).
     *
     * Администратор должен видеть итог уборки без ручного опроса журнала.
     */
    private fun notifyTerminalState(
        run: SongCleanupJournal.RunSummary,
        state: CleanupRunState,
        counters: Counters,
    ) {
        try {
            SNS.send(
                SseNotification.message(
                    Message(
                        type = "info",
                        head = "Уборка песни завершена: ${run.songName}",
                        body =
                            "Прогон ${run.runId}, состояние ${state.name}. " +
                                "Удалено файлов: ${counters.filesDeleted}, объектов: ${counters.objectsDeleted}, " +
                                "освобождено: ${formatBytes(counters.bytesDeleted)}.",
                    ),
                ),
            )
        } catch (e: Exception) {
            log.warn("song.cleanup.notify_failed: runId={} — {}", run.runId, e.toString())
        }
    }

    // ------------------------------------------------------------- утилиты

    /**
     * Живых песен, чьи файлы лежат в папке альбома (FR-021).
     *
     * Проверка — по БД: песни, заведённые в базе, живут в папке альбома
     * (`tbl_songs.root_folder`) и держат альбом (`tbl_songs.album_id`).
     */
    private fun countLiveSongsInAlbumFolder(rootFolder: String, albumId: Long): Int =
        SongCleanupExecutor.liveSongsInAlbumFolder(database, rootFolder, albumId)

    /**
     * Файлы каталога (рекурсивно) с размерами — снимаются ДО удаления.
     *
     * После удаления размер недоступен, а в журнал должны лечь реальные пути
     * и объём (FR-016: администратор видит, что ушло вместе с папкой).
     */
    private fun collectFilesWithSizes(root: File): List<Pair<String, Long>> {
        val result = mutableListOf<Pair<String, Long>>()

        fun walk(dir: File) {
            dir.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    walk(file)
                } else {
                    result.add(file.path to file.length())
                }
            }
        }
        walk(root)
        return result
    }

    /** Живых песен в альбоме (после удаления песни). */
    private fun countLiveSongsInAlbum(albumId: Long): Int {
        val connection = database.getConnection() ?: return 1
        return try {
            connection
                .prepareStatement("SELECT count(*) FROM tbl_songs WHERE album_id = ?")
                .use { ps ->
                    ps.setLong(1, albumId)
                    ps.executeQuery().use { rs ->
                        if (rs.next()) rs.getInt(1) else 1
                    }
                }
        } catch (e: Exception) {
            log.warn("song.cleanup.album.count_failed: albumId={} — {}", albumId, e.toString())
            // Не знаем — считаем, что живые песни есть: альбом не трогаем (fail-closed).
            1
        }
    }

    /** Удаление записи альбома. */
    private fun deleteAlbumRecord(albumId: Long): Boolean {
        val connection = database.getConnection() ?: return false
        return try {
            connection.prepareStatement("DELETE FROM tbl_albums WHERE id = ?").use { ps ->
                ps.setLong(1, albumId)
                ps.executeUpdate()
            }
            true
        } catch (e: Exception) {
            log.warn("song.cleanup.album.delete_failed: albumId={} — {}", albumId, e.toString())
            false
        }
    }

    /** Удаление записи картинки альбома из справочника изображений. */
    private fun deletePictureRecord(pictureName: String): Boolean {
        val connection = database.getConnection() ?: return false
        return try {
            connection.prepareStatement("DELETE FROM tbl_pictures WHERE picture_name = ?").use { ps ->
                ps.setString(1, pictureName)
                ps.executeUpdate()
            }
            true
        } catch (e: Exception) {
            log.warn("song.cleanup.picture.delete_failed: '{}' — {}", pictureName, e.toString())
            false
        }
    }

    /** Считает файлы в дереве каталога (для решения «осталась ли папка»). */
    private fun countFilesRecursively(dir: File): Int {
        var count = 0
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                count += countFilesRecursively(file)
            } else {
                count++
            }
        }
        return count
    }

    /** Рекурсивное удаление каталога (только пустого содержимого — вызывающий проверил). */
    private fun deleteRecursively(dir: File): Boolean =
        try {
            Files.walk(dir.toPath()).use { stream ->
                stream
                    .sorted(Comparator.reverseOrder())
                    .forEach { path: Path ->
                        Files.deleteIfExists(path)
                    }
            }
            true
        } catch (e: Exception) {
            log.warn("song.cleanup.folder.delete_failed: '{}' — {}", dir.path, e.toString())
            false
        }

    /** Существует ли цель симлинка. */
    private fun symbolicLinkTargetExists(file: File): Boolean =
        try {
            Files.exists(file.toPath().toRealPath())
        } catch (e: Exception) {
            false
        }

    /** Размер файла без исключений. */
    private fun safeLength(file: File): Long =
        try {
            file.length()
        } catch (e: Exception) {
            0L
        }

    /** Автор прогона (для записей журнала). */
    private fun authorOf(run: SongCleanupJournal.RunSummary): String = run.author

    /** Инициатор прогона (для записей журнала). */
    private fun currentInitiator(run: SongCleanupJournal.RunSummary): String = run.initiator
}
