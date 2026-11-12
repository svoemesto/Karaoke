package com.svoemesto.karaokeapp.services.songdeletion

import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.KaraokeFileType
import com.svoemesto.karaokeapp.PROJECT_ROOT_FOLDERS
import com.svoemesto.karaokeapp.StorageCacheReset
import com.svoemesto.karaokeapp.WORKING_DATABASE
import com.svoemesto.karaokeapp.services.KSS_APP
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.SAC_APP
import com.svoemesto.karaokeapp.services.StorageApiClient
import java.io.File
import java.security.MessageDigest
import java.time.LocalDateTime

/**
 * Планировщик разовой уборки осиротевших артефактов (KARAOKE-429, спека 739, US4).
 *
 * **Протокол утверждённого набора (FR-039, FR-040).** Планировщик только ЧИТАЕТ и
 * строит перечень кандидатов с размерами и отпечатком набора. Ничего не удаляет:
 * удаляет [SongOneTimeCleanupService] и только по утверждённому отпечатку —
 * если состав набора изменился, уборка отказывается выполняться.
 *
 * **Живые объекты не трогаются (FR-042, SC-008).** Объект, имя которого совпадает
 * с именем живой песни после нормализации написания (регистр, `ё`/`е`, кавычки,
 * `01`/`1`), в перечень НЕ попадает: near-match — это не сирота.
 *
 * @see specs/739-song-delete-cascade/spec.md FR-039…FR-043
 */
class SongOneTimeCleanupPlanner(
    private val database: KaraokeConnection = WORKING_DATABASE,
    private val storageService: KaraokeStorageService = KSS_APP,
    private val storageApiClient: StorageApiClient = SAC_APP,
) {
    companion object {
        /** Имя бакета с объектами песен. */
        const val BUCKET = "karaoke"
    }

    /** Причина кандидата. */
    enum class Reason {
        /** Сирота: объект не принадлежит ни одной живой песне. */
        ORPHAN,

        /** Устаревшая копия: объект лежит под прежним именем живой песни. */
        STALE_COPY,

        /** Не опознан: подходящего правила не нашлось. */
        UNKNOWN,
    }

    /** Что за объект. */
    enum class CandidateKind {
        STORAGE_OBJECT,
        FILE,
    }

    /** Где лежит объект. */
    enum class CandidateLocation {
        LOCAL_STORAGE,
        REMOTE_STORAGE,
        LOCAL_FILESYSTEM,
        DATABASE,
    }

    /** Кандидат разовой уборки (FR-039). */
    data class Candidate(
        val kind: CandidateKind,
        val location: CandidateLocation,
        val pathOrKey: String,
        val sizeBytes: Long,
        val reason: Reason,
        val detail: String,
    )

    /** Перечень кандидатов с отпечатком набора (FR-039). */
    data class Plan(
        val candidates: List<Candidate>,
        val generatedAt: String,
        val liveSongsCount: Int,
    ) {
        /** Суммарный объём кандидатов, байт. */
        val totalBytes: Long get() = candidates.sumOf { it.sizeBytes }

        /**
         * Отпечаток набора (FR-040): sha256 по строкам «путь + размер», отсортированным
         * по пути. Устойчив к порядку обхода, чувствителен к составу и размерам.
         */
        val fingerprint: String by lazy {
            val digest = MessageDigest.getInstance("SHA-256")
            candidates
                .sortedBy { it.pathOrKey }
                .forEach { candidate ->
                    digest.update(candidate.pathOrKey.toByteArray(Charsets.UTF_8))
                    digest.update('\t'.code.toByte())
                    digest.update(candidate.sizeBytes.toString().toByteArray(Charsets.UTF_8))
                    digest.update('\n'.code.toByte())
                }
            "sha256:" + digest.digest().joinToString("") { "%02x".format(it) }
        }
    }

    /** Живая песня: имя файла + контекст для ключей хранилища. */
    private data class LiveSong(
        val author: String,
        val year: String,
        val album: String,
        val fileName: String,
    )

    /**
     * Сформировать перечень кандидатов (FR-039).
     *
     * @param includeStorage включить ли объекты хранилищ.
     * @param includeFiles включить ли файлы на диске.
     */
    fun plan(
        includeStorage: Boolean = true,
        includeFiles: Boolean = true,
    ): Plan {
        val liveSongs = loadLiveSongs()
        val liveNames = liveSongs.map { SongArtifactOwnership.normalizeForMatch(it.fileName) }.toSet()
        val ownKeys = ownStorageKeys(liveSongs)
        val historyNames = loadHistoryNames()

        val candidates = mutableListOf<Candidate>()
        if (includeStorage) {
            candidates.addAll(storageCandidates(liveSongs, ownKeys, historyNames, liveNames))
        }
        if (includeFiles) {
            candidates.addAll(diskCandidates(liveSongs, liveNames))
        }
        return Plan(
            candidates = candidates.distinctBy { candidate -> candidate.location to candidate.pathOrKey },
            generatedAt = generatedAt(),
            liveSongsCount = liveSongs.size,
        )
    }

    /** Момент формирования перечня (ISO-8601, локальное время). */
    private fun generatedAt(): String = LocalDateTime.now().toString()

    /** Живые песни каталога. */
    private fun loadLiveSongs(): List<LiveSong> {
        val connection = database.getConnection() ?: return emptyList()
        return try {
            connection
                .prepareStatement("SELECT song_author, song_year, song_album, file_name FROM tbl_songs")
                .use { ps ->
                    ps.executeQuery().use { rs ->
                        val result = mutableListOf<LiveSong>()
                        while (rs.next()) {
                            val fileName = rs.getString("file_name") ?: continue
                            if (fileName.isBlank()) continue
                            result.add(
                                LiveSong(
                                    author = rs.getString("song_author") ?: "",
                                    year = (rs.getInt("song_year")).toString(),
                                    album = rs.getString("song_album") ?: "",
                                    fileName = fileName,
                                ),
                            )
                        }
                        result
                    }
                }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Прежние имена песен (tbl_song_name_history) → объекты под ними считаются STALE_COPY. */
    private fun loadHistoryNames(): Set<String> {
        val connection = database.getConnection() ?: return emptySet()
        return try {
            connection
                .prepareStatement("SELECT DISTINCT file_name FROM tbl_song_name_history")
                .use { ps ->
                    ps.executeQuery().use { rs ->
                        val result = mutableSetOf<String>()
                        while (rs.next()) {
                            rs.getString("file_name")?.let { result.add(it) }
                        }
                        result
                    }
                }
        } catch (e: Exception) {
            emptySet()
        }
    }

    /** Ключи хранилища, принадлежащие живым песням (текущие имена). */
    private fun ownStorageKeys(liveSongs: List<LiveSong>): Set<String> {
        val result = HashSet<String>()
        liveSongs.forEach { song ->
            val keys =
                StorageCacheReset.storageFileNamesForSong(song.author, song.year, song.album, song.fileName)
            keys.forEach { result.add(it) }
        }
        return result
    }

    /** Кандидаты среди объектов хранилища (FR-041). */
    private fun storageCandidates(
        liveSongs: List<LiveSong>,
        ownKeys: Set<String>,
        historyNames: Set<String>,
        liveNames: Set<String>,
    ): List<Candidate> {
        val result = mutableListOf<Candidate>()
        listOf(
            CandidateLocation.LOCAL_STORAGE to listLocalObjects(),
            CandidateLocation.REMOTE_STORAGE to listRemoteObjects(),
        ).forEach { (location, objects) ->
            objects.forEach { (key, size) ->
                if (ownKeys.contains(key)) return@forEach
                // FR-042: near-match живой песни — не сирота.
                val baseName = key.substringAfterLast('/')
                if (SongArtifactOwnership.isNearMatchOfLiveSong(liveNames, baseName)) return@forEach
                // Общие обложки альбомов/авторов — не объекты песни.
                if (isSharedCoverKey(key)) return@forEach
                val reason =
                    if (isStaleCopy(key, liveSongs, historyNames)) {
                        Reason.STALE_COPY
                    } else {
                        Reason.ORPHAN
                    }
                result.add(
                    Candidate(
                        kind = CandidateKind.STORAGE_OBJECT,
                        location = location,
                        pathOrKey = key,
                        sizeBytes = size,
                        reason = reason,
                        detail = detailFor(key, reason),
                    ),
                )
            }
        }
        return result
    }

    /** Кандидаты среди файлов на диске (FR-041): файлы, не принадлежащие живым песням. */
    private fun diskCandidates(
        liveSongs: List<LiveSong>,
        liveNames: Set<String>,
    ): List<Candidate> {
        val result = mutableListOf<Candidate>()
        // Множество «своих» имён по папкам альбомов: файл принадлежит песне, если
        // его имя — артефакт одной из живых песен его альбома.
        val byFolder = liveSongs.groupBy { folderOf(it) }
        PROJECT_ROOT_FOLDERS.forEach { root ->
            val rootDir = File(root)
            if (!rootDir.isDirectory) return@forEach
            rootDir.listFiles()?.forEach { authorDir ->
                if (!authorDir.isDirectory) return@forEach
                authorDir.listFiles()?.forEach { albumDir ->
                    if (!albumDir.isDirectory) return@forEach
                    val live = byFolder[albumDir.path].orEmpty()
                    if (live.isEmpty()) return@forEach
                    SongArtifactOwnership.artifactDirs(albumDir).forEach { dir ->
                        dir.listFiles()?.forEach { file ->
                            if (file.isDirectory) return@forEach
                            val own =
                                live.any { song ->
                                    SongArtifactOwnership.isOwnArtifact(song.fileName, file.name)
                                }
                            if (own) return@forEach
                            if (SongArtifactOwnership.isNearMatchOfLiveSong(liveNames, file.name)) return@forEach
                            result.add(
                                Candidate(
                                    kind = CandidateKind.FILE,
                                    location = CandidateLocation.LOCAL_FILESYSTEM,
                                    pathOrKey = file.absolutePath,
                                    sizeBytes = safeLength(file),
                                    reason = Reason.ORPHAN,
                                    detail = "файл не принадлежит ни одной живой песне альбома",
                                ),
                            )
                        }
                    }
                }
            }
        }
        return result
    }

    /** Папка альбома песни (по автору/году/альбому). */
    private fun folderOf(song: LiveSong): String =
        PROJECT_ROOT_FOLDERS
            .map { root -> File(root, "${song.author}/${song.year} - ${song.album}") }
            .firstOrNull { it.isDirectory }
            ?.path
            ?: ""

    /** Устаревшая копия: ключ соответствует прежнему имени живой песни. */
    private fun isStaleCopy(
        key: String,
        liveSongs: List<LiveSong>,
        historyNames: Set<String>,
    ): Boolean {
        val baseName = key.substringAfterLast('/')
        if (historyNames.isEmpty()) return false
        return liveSongs.any { song ->
            historyNames.any { oldName ->
                SongArtifactOwnership.isOwnArtifact(oldName, baseName)
            }
        }
    }

    /** Пояснение к кандидату. */
    private fun detailFor(
        key: String,
        reason: Reason,
    ): String =
        when (reason) {
            Reason.STALE_COPY -> "объект лежит под прежним именем живой песни"
            Reason.ORPHAN -> if (!key.contains('/')) "мусор в корне бакета" else "объект не принадлежит ни одной живой песне"
            Reason.UNKNOWN -> "не опознан"
        }

    /** Ключ общей обложки альбома/автора — не объект песни (FR-014). */
    private fun isSharedCoverKey(key: String): Boolean {
        val name = key.substringAfterLast('/')
        return sharedCoverSuffixes.any { suffix -> name.endsWith(suffix) }
    }

    /** Суффиксы общих обложек. */
    private val sharedCoverSuffixes =
        listOf(".album.png", ".preview.album.png", ".author.png", ".preview.author.png")

    /** Объекты локального хранилища (ключ → размер). */
    private fun listLocalObjects(): List<Pair<String, Long>> =
        try {
            storageService.listFilesInfo(BUCKET).map { it.fileName to it.size }
        } catch (e: Exception) {
            emptyList()
        }

    /** Объекты удалённого хранилища (ключ → размер). */
    private fun listRemoteObjects(): List<Pair<String, Long>> =
        try {
            storageApiClient.listFilesInfo(BUCKET).block()?.map { it.fileName to it.size } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

    private fun safeLength(file: File): Long =
        try {
            file.length()
        } catch (e: Exception) {
            0L
        }

    /** Типы файлов, лежащих в хранилище (для документации и проверок). */
    val storageFileTypes: List<KaraokeFileType> =
        KaraokeFileType.entries.filter { it.karaokeFileTypeFor.name == "SONG" }
}
