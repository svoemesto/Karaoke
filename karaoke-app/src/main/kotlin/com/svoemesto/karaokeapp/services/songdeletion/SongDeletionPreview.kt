package com.svoemesto.karaokeapp.services.songdeletion

import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.WORKING_DATABASE
import com.svoemesto.karaokeapp.model.Song
import com.svoemesto.karaokeapp.model.SongNameHistory
import com.svoemesto.karaokeapp.services.KSS_APP
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.SAC_APP
import com.svoemesto.karaokeapp.services.StorageApiClient
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Предпросмотр удаления песни (KARAOKE-429, спека 739, FR-023…FR-026, SC-004).
 *
 * **Точность обязательна:** расхождение между показанными и реально удалёнными
 * файлами должно быть НУЛЬ (SC-004). Поэтому предпросмотр считает файлы и объекты
 * ТЕМ ЖЕ кодом, что и уборка: [SongArtifactOwnership] — общий модуль правил
 * владения. Оценка «по БД» не годится: файлы на диске и объекты в MinIO не
 * обязаны отражаться в строках каталога.
 *
 * @see specs/739-song-delete-cascade/contracts/song-delete-api.md § 2
 */
class SongDeletionPreview(
    private val database: KaraokeConnection = WORKING_DATABASE,
    private val storageService: KaraokeStorageService = KSS_APP,
    private val storageApiClient: StorageApiClient = SAC_APP,
) {
    companion object {
        /** Имя бакета с объектами песен. */
        const val BUCKET = "karaoke"

        /**
         * TTL кеша листингов бакета, мс (владелец, 2026-11-13).
         *
         * Предпросмотр ходит в оба хранилища полным листингом бакета: бакет
         * большой (десятки тысяч объектов), а MinIO без префикса листинга не
         * умеет — один только листинг занимал ~6 с на хранилище, и админ видел
         * «ничего не происходит» десяток секунд после клика. Кеш на полминуты
         * делает повторный предпросмотр (обычно подряд идут несколько песен)
         * мгновенным.
         *
         * Кеш живёт ТОЛЬКО в предпросмотре: уборка всегда смотрит в хранилище
         * своими глазами — по кешу удалять объекты нельзя.
         */
        const val LISTING_TTL_MS = 30_000L

        /** Предел ожидания листинга одного хранилища, с. */
        private const val LISTING_TIMEOUT_SEC = 120L

        /** Пул для параллельных листингов (локальный и удалённый — одновременно). */
        private val listingExecutor =
            Executors.newFixedThreadPool(2) { runnable ->
                Thread(runnable, "song-delete-preview-listing").apply { isDaemon = true }
            }

        /** Снимок листингов обоих хранилищ с меткой времени. */
        private data class BucketListing(
            val at: Long,
            val local: Map<String, Long>,
            val remote: Map<String, Long>,
        )

        private val listingLock = Any()
        private var cachedListing: BucketListing? = null
    }

    /** Снимок предпросмотра — ровно поля контракта § 2. */
    data class Preview(
        val songId: Long,
        val songName: String,
        val author: String,
        val year: Int,
        val album: String,
        val rootFolder: String,
        val filesCount: Int,
        val filesBytes: Long,
        val filesMb: Double,
        val localStorageObjects: Int,
        val remoteStorageObjects: Int,
        val storageBytes: Long,
        val albumWillBeDeleted: Boolean,
        val albumPictureWillBeDeleted: Boolean,
        val albumFolderWillBeDeleted: Boolean,
        val albumFolderReason: String?,
        val albumFolderLeftoverFiles: Int,
        val descendantsCount: Int,
        val newsCount: Int,
        val linksCount: Int,
        val playlistItemsCount: Int,
        val assignmentsCount: Int,
        val draftsCount: Int,
        val historyCount: Int,
        val blocked: Boolean,
        val blockReason: String?,
    )

    /**
     * Построить предпросмотр для песни.
     *
     * @param song песня (строка уже загружена).
     * @param blockedReason причина блокировки (активный процесс / чужая запись).
     */
    fun preview(
        song: Song,
        blockedReason: String? = null,
    ): Preview {
        val fileName = song.fileName
        val rootFolder = song.rootFolder

        // Файлы — тем же правилом владения, что и уборка (SC-004: расхождение 0).
        val filesToDelete = SongArtifactOwnership.collectFilesToDelete(rootFolder, fileName)
        val filesBytes = filesToDelete.sumOf { safeLength(it) }

        // Объекты хранилищ — по префиксу «$автор/$год - $альбом/» и правилу владения.
        val names = LinkedHashSet<String>()
        fileName.takeIf { it.isNotBlank() }?.let { names.add(it) }
        names.addAll(SongNameHistory.listForSong(song.id, database))
        val (localListing, remoteListing) = bucketListings()
        val localObjects = ownObjects(song.author, song.year.toString(), song.album, names, localListing)
        val remoteObjects = ownObjects(song.author, song.year.toString(), song.album, names, remoteListing)

        // Каскад на альбом.
        val albumId = song.albumId ?: 0L
        val liveSongsInAlbum = if (albumId > 0L) countSongsInAlbum(albumId, excludeSongId = song.id) else 0
        val albumWillBeDeleted = albumId > 0L && liveSongsInAlbum == 0
        val albumPictureWillBeDeleted = albumWillBeDeleted && hasPictureRecord(song)
        // Папка альбома — ТОТ ЖЕ код, что и уборка (FR-020, FR-021): папка уходит,
        // если в ней нет файлов ЖИВЫХ песен; неопознанные остатки (мусор
        // `[key].json` от прежних удалений) уходят вместе с альбомом.
        // Раньше предпросмотр считал «в папке не должно быть НИКАКИХ файлов» —
        // и врал: альбом был последним, папку уборка удаляла, а в окне
        // подтверждения значилось «папка не удаляется» (жалоба владельца
        // 2026-11-13).
        val liveSongsInFolder =
            if (rootFolder.isNotBlank()) {
                SongCleanupExecutor.liveSongsInAlbumFolder(
                    database = database,
                    rootFolder = rootFolder,
                    albumId = albumId,
                    excludeSongId = song.id,
                )
            } else {
                0
            }
        val albumFolderWillBeDeleted = rootFolder.isNotBlank() && liveSongsInFolder == 0
        val albumFolderReason =
            if (albumFolderWillBeDeleted) {
                null
            } else {
                "album_has_live_songs"
            }
        // Файлы, которые не относятся к песне, но лежат в папке альбома: они уйдут
        // вместе с альбомом (остатки прежних удалений).
        val albumFolderLeftoverFiles = (countFilesInFolder(rootFolder) - filesToDelete.size).coerceAtLeast(0)

        return Preview(
            songId = song.id,
            songName = song.songName,
            author = song.author,
            year = song.year.toInt(),
            album = song.album,
            rootFolder = rootFolder,
            filesCount = filesToDelete.size,
            filesBytes = filesBytes,
            filesMb = bytesToMb(filesBytes),
            localStorageObjects = localObjects.size,
            remoteStorageObjects = remoteObjects.size,
            storageBytes = localObjects.sumOf { it.second } + remoteObjects.sumOf { it.second },
            albumWillBeDeleted = albumWillBeDeleted,
            albumPictureWillBeDeleted = albumPictureWillBeDeleted,
            albumFolderWillBeDeleted = albumFolderWillBeDeleted,
            albumFolderReason = albumFolderReason,
            albumFolderLeftoverFiles = albumFolderLeftoverFiles,
            descendantsCount = countDescendants(song.id),
            newsCount = countBy("tbl_news", "song_id", song.id),
            linksCount = countBy("tbl_song_share_links", "song_id", song.id),
            playlistItemsCount = countBy("tbl_site_playlist_items", "song_id", song.id),
            assignmentsCount = countBy("tbl_song_assignments", "song_id", song.id),
            draftsCount = countDrafts(song.id),
            historyCount = countBy("tbl_listening_history", "song_id", song.id),
            blocked = blockedReason != null,
            blockReason = blockedReason,
        )
    }

    /** Свои объекты хранилища (ключ → размер). */
    private fun ownObjects(
        author: String,
        year: String,
        album: String,
        names: Set<String>,
        listing: Map<String, Long>,
    ): List<Pair<String, Long>> {
        val ownKeys =
            SongArtifactOwnership.ownStorageKeys(
                author = author,
                year = year,
                album = album,
                songFileNames = names,
                keys = listing.keys,
            )
        return ownKeys.map { it to (listing[it] ?: 0L) }
    }

    /**
     * Листинги обоих хранилищ: параллельно и с коротким кешем.
     *
     * Листинг бакета — тяжёлая операция (десятки тысяч объектов, без префикса),
     * поэтому оба хранилища читаются одновременно, а повторный предпросмотр в
     * течение [LISTING_TTL_MS] берёт кеш.
     *
     * @return пара (локальный листинг, удалённый листинг) — ключ → размер.
     */
    private fun bucketListings(): Pair<Map<String, Long>, Map<String, Long>> {
        synchronized(listingLock) {
            val now = System.currentTimeMillis()
            cachedListing
                ?.takeIf { now - it.at < LISTING_TTL_MS }
                ?.let { return it.local to it.remote }
        }
        val localFuture = CompletableFuture.supplyAsync({ listBucket(local = true) }, listingExecutor)
        val remoteFuture = CompletableFuture.supplyAsync({ listBucket(local = false) }, listingExecutor)
        val local = awaitListing(localFuture)
        val remote = awaitListing(remoteFuture)
        synchronized(listingLock) {
            cachedListing = BucketListing(System.currentTimeMillis(), local, remote)
        }
        return local to remote
    }

    /**
     * Результат листинга одного хранилища; сбой хранилища — пустой листинг
     * (как и раньше: предпросмотр не должен падать из-за одного хранилища).
     */
    private fun awaitListing(future: CompletableFuture<Map<String, Long>>): Map<String, Long> =
        try {
            future.get(LISTING_TIMEOUT_SEC, TimeUnit.SECONDS)
        } catch (e: Exception) {
            emptyMap()
        }

    /** Листинг бакета одного хранилища (ключ → размер). */
    private fun listBucket(local: Boolean): Map<String, Long> {
        val all =
            try {
                if (local) {
                    storageService.listFilesInfo(BUCKET)
                } else {
                    storageApiClient.listFilesInfo(BUCKET).block() ?: emptyList()
                }
            } catch (e: Exception) {
                emptyList()
            }
        return all.associate { it.fileName to it.size }
    }

    /** Живых песен в альбоме (кроме удаляемой). */
    private fun countSongsInAlbum(
        albumId: Long,
        excludeSongId: Long,
    ): Int {
        val connection = database.getConnection() ?: return 1
        return try {
            connection
                .prepareStatement("SELECT count(*) FROM tbl_songs WHERE album_id = ? AND id <> ?")
                .use { ps ->
                    ps.setLong(1, albumId)
                    ps.setLong(2, excludeSongId)
                    ps.executeQuery().use { rs ->
                        if (rs.next()) rs.getInt(1) else 1
                    }
                }
        } catch (e: Exception) {
            1
        }
    }

    /** Есть ли запись картинки альбома в справочнике изображений (FR-019). */
    private fun hasPictureRecord(song: Song): Boolean {
        val connection = database.getConnection() ?: return false
        val pictureName = "${song.author} - ${song.year} - ${song.album}"
        return try {
            connection
                .prepareStatement("SELECT count(*) FROM tbl_pictures WHERE picture_name = ?")
                .use { ps ->
                    ps.setString(1, pictureName)
                    ps.executeQuery().use { rs ->
                        if (rs.next()) rs.getInt(1) > 0 else false
                    }
                }
        } catch (e: Exception) {
            false
        }
    }

    /** Родственные песни (FR-026). */
    private fun countDescendants(songId: Long): Int {
        val connection = database.getConnection() ?: return 0
        return try {
            connection
                .prepareStatement("SELECT count(*) FROM tbl_songs WHERE audio_parent_id = ?")
                .use { ps ->
                    ps.setLong(1, songId)
                    ps.executeQuery().use { rs ->
                        if (rs.next()) rs.getInt(1) else 0
                    }
                }
        } catch (e: Exception) {
            0
        }
    }

    /** Счётчик записей в таблице по колонке `song_id`. */
    private fun countBy(
        table: String,
        column: String,
        songId: Long,
    ): Int {
        val connection = database.getConnection() ?: return 0
        return try {
            connection
                .prepareStatement("SELECT count(*) FROM $table WHERE $column = ?")
                .use { ps ->
                    ps.setLong(1, songId)
                    ps.executeQuery().use { rs ->
                        if (rs.next()) rs.getInt(1) else 0
                    }
                }
        } catch (e: Exception) {
            0
        }
    }

    /** Счётчик черновиков (связь по назначениям песни). */
    private fun countDrafts(songId: Long): Int {
        val connection = database.getConnection() ?: return 0
        return try {
            connection
                .prepareStatement(
                    "SELECT count(*) FROM tbl_song_assignment_drafts " +
                        "WHERE assignment_id IN (SELECT id FROM tbl_song_assignments WHERE song_id = ?)",
                ).use { ps ->
                    ps.setLong(1, songId)
                    ps.executeQuery().use { rs ->
                        if (rs.next()) rs.getInt(1) else 0
                    }
                }
        } catch (e: Exception) {
            0
        }
    }

    /** Файлов в дереве папки альбома (рекурсивно). */
    private fun countFilesInFolder(rootFolder: String): Int {
        if (rootFolder.isBlank()) return 0
        val root = File(rootFolder)
        if (!root.isDirectory) return 0
        return countFilesRecursively(root)
    }

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

    private fun safeLength(file: File): Long =
        try {
            file.length()
        } catch (e: Exception) {
            0L
        }

    /** Байты → мегабайты (округление до десятых, как в контракте). */
    private fun bytesToMb(bytes: Long): Double = Math.round(bytes / 1024.0 / 1024.0 * 10.0) / 10.0
}
