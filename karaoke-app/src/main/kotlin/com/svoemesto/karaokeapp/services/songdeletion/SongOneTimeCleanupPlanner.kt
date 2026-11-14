package com.svoemesto.karaokeapp.services.songdeletion

import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.KaraokeFileType
import com.svoemesto.karaokeapp.PROJECT_ROOT_FOLDERS
import com.svoemesto.karaokeapp.PUBLICATION_PICTURE_NAME_SUFFIXES
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

        /**
         * Альбомы, медиа которых списывается по решению владельца (KARAOKE-425):
         * автор и папка альбома на диске.
         */
        val MEDIA_WRITEOFF_ALBUMS: List<Pair<String, String>> =
            listOf(
                "АукцЫон" to "1996 - Жилец вершин",
                "Сектор Газа" to "2023 - Лирика (сингл)",
            )

        /** Таблица и критерий сироты (условие WHERE). */
        data class DbOrphanTable(
            val table: String,
            val where: String,
            val description: String,
        )

        /**
         * Таблицы с сиротами, которые владелец разрешил чистить (KARAOKE-426):
         * черновики заданий без задания и исторические ссылки на несуществующие песни.
         *
         * Порядок — из инвентаризации: черновики раньше истории, события раньше тяжёлых
         * таблиц поиска.
         */
        val DB_ORPHAN_TABLES: List<DbOrphanTable> =
            listOf(
                DbOrphanTable(
                    table = "tbl_song_assignment_drafts",
                    where = "assignment_id NOT IN (SELECT id FROM tbl_song_assignments)",
                    description = "черновики заданий без задания",
                ),
                DbOrphanTable(
                    table = "tbl_events",
                    where = "song_id IS NOT NULL AND song_id <> 0 AND song_id NOT IN (SELECT id FROM tbl_songs)",
                    description = "события несуществующих песен",
                ),
                DbOrphanTable(
                    table = "tbl_search_results",
                    where = "song_id IS NOT NULL AND song_id NOT IN (SELECT id FROM tbl_songs)",
                    description = "результаты поиска по несуществующим песням",
                ),
                DbOrphanTable(
                    table = "tbl_search_async",
                    where = "song_id IS NOT NULL AND song_id NOT IN (SELECT id FROM tbl_songs)",
                    description = "асинхронные снимки поиска по несуществующим песням",
                ),
                DbOrphanTable(
                    table = "tbl_listening_history",
                    where = "song_id NOT IN (SELECT id FROM tbl_songs)",
                    description = "история прослушиваний несуществующих песен",
                ),
            )

        /** Хвосты mp3-стемов в имени объекта песни. */
        private val storageStemTails =
            listOf(
                ".vocals.mp3",
                ".accompaniment.mp3",
                ".bass.mp3",
                ".drums.mp3",
                ".other.mp3",
            )

        /** Публикационная картинка песни: `&lt;имя песни&gt; [метка].png`. */
        private val PUBLICATION_PICTURE_REGEX = Regex("^(.*) \\[([^\\]]+)]\\.png$")

        /**
         * Ключи публикационных картинок песни: `$base [метка].png`.
         *
         * @param base ключ песни без артефакта (`$автор/$год - $альбом/$имя песни`).
         */
        internal fun publicationPictureKeys(base: String): List<String> =
            PUBLICATION_PICTURE_NAME_SUFFIXES.map { suffix -> "$base $suffix.png" }

        /**
         * Совпадает ли префикс пути объекта с автором и годом живой песни.
         *
         * Ключ объекта — `$автор/$год - $альбом/$имя песни`. Переименование **альбома**
         * меняет только часть пути после `$автор/$год - ` — такой объект действительно
         * устаревшая копия. Но у разных исполнителей бывают альбомы с одинаковым
         * названием («Призраки Завтра» у «Мумий Тролль», «Мумий Тролль, T-Fest» и
         * «Мумий Тролль feat. Скриптонит») — их объекты **разные записи**, а не копии.
         * Без проверки автора и года перечень разовой уборки предлагал удалить чужие
         * объекты (факт 2026-11-14, замер после импорта: 19 ложных кандидатов).
         *
         * @param author автор живой песни.
         * @param year год живой песни.
         * @param oldPrefix путь объекта-кандидата без имени файла.
         */
        internal fun isSameAuthorAndYear(
            author: String,
            year: String,
            oldPrefix: String,
        ): Boolean = oldPrefix.startsWith("$author/$year - ")

        /**
         * Хвост артефакта песни в имени объекта: mp3-стем или публикационная картинка.
         *
         * @param filePart имя объекта (после `$автор/$год - $альбом/`).
         * @return пара (имя песни, хвост артефакта) либо `null`, если это не артефакт песни.
         */
        internal fun storageTailOf(filePart: String): Pair<String, String>? {
            storageStemTails.forEach { tail ->
                if (filePart.endsWith(tail) && filePart.length > tail.length) {
                    return filePart.dropLast(tail.length) to tail
                }
            }
            val match = PUBLICATION_PICTURE_REGEX.matchEntire(filePart) ?: return null
            return match.groupValues[1] to " [${match.groupValues[2]}].png"
        }
    }

    /** Причина кандидата. */
    enum class Reason {
        /** Сирота: объект не принадлежит ни одной живой песне. */
        ORPHAN,

        /** Устаревшая копия: объект лежит под прежним именем живой песни. */
        STALE_COPY,

        /** Медиа альбома, списанного по решению владельца (KARAOKE-425). */
        WRITEOFF,

        /** Не опознан: подходящего правила не нашлось. */
        UNKNOWN,
    }

    /** Что за объект. */
    enum class CandidateKind {
        STORAGE_OBJECT,
        FILE,
        DB_ROWS,
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
                    digest.update('\t'.code.toByte())
                    // Пояснение входит в отпечаток: для кандидатов в БД там число строк —
                    // состав набора меняется при изменении числа строк, и такое изменение
                    // MUST требовать нового утверждения (FR-040).
                    digest.update(candidate.detail.toByteArray(Charsets.UTF_8))
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
        includeDatabase: Boolean = true,
    ): Plan {
        val liveSongs = loadLiveSongs()
        val liveNames = liveSongs.map { SongArtifactOwnership.normalizeForMatch(it.fileName) }.toSet()
        val ownKeys = ownStorageKeys(liveSongs)
        val historySongs = loadHistorySongs()

        val candidates = mutableListOf<Candidate>()
        if (includeStorage) {
            candidates.addAll(storageCandidates(liveSongs, ownKeys, historySongs, liveNames))
        }
        if (includeFiles) {
            candidates.addAll(diskCandidates(liveSongs))
        }
        if (includeDatabase) {
            candidates.addAll(databaseCandidates())
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

    /**
     * Прежние имена песен (tbl_song_name_history) → живая песня, которой они принадлежали.
     *
     * Объекты под прежним именем — устаревшие копии (FR-041): удаляются, только если у живой
     * песни на месте актуальный объект (иначе устаревшая копия — единственная).
     */
    private fun loadHistorySongs(): Map<String, LiveSong> {
        val connection = database.getConnection() ?: return emptyMap()
        return try {
            connection
                .prepareStatement(
                    "SELECT h.file_name, s.song_author, s.song_year, s.song_album, s.file_name " +
                        "FROM tbl_song_name_history h JOIN tbl_songs s ON s.id = h.song_id",
                ).use { ps ->
                    ps.executeQuery().use { rs ->
                        val result = mutableMapOf<String, LiveSong>()
                        while (rs.next()) {
                            val oldName = rs.getString("file_name") ?: continue
                            val currentName = rs.getString("file_name") ?: continue
                            result[oldName] =
                                LiveSong(
                                    author = rs.getString("song_author") ?: "",
                                    year = rs.getInt("song_year").toString(),
                                    album = rs.getString("song_album") ?: "",
                                    fileName = currentName,
                                )
                        }
                        result
                    }
                }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * Ключи хранилища, принадлежащие живым песням (текущие имена).
     *
     * Кроме типов файлов из [StorageCacheReset.storageFileNamesForSong] сюда входят
     **публикационные картинки** песни (`$storageFileName [метка].png`): их нет в
     перечислении типов файлов, метки живут отдельным списком
     * [PUBLICATION_PICTURE_NAME_SUFFIXES]. Без них «свои» картинки живых песен
     * принимались за устаревшие копии (инцидент 2026-11-13: в перечень попали
     * 43 678 объектов, из которых большинство — картинки живых песен).
     */
    private fun ownStorageKeys(liveSongs: List<LiveSong>): Set<String> {
        val result = HashSet<String>()
        liveSongs.forEach { song ->
            val keys =
                StorageCacheReset.storageFileNamesForSong(song.author, song.year, song.album, song.fileName)
            keys.forEach { result.add(it) }
            val base = "${song.author}/${song.year} - ${song.album}/${song.fileName}"
            result.addAll(publicationPictureKeys(base))
        }
        return result
    }

    /**
     * Медиа альбомов, списанных по решению владельца (KARAOKE-425, FR-041).
     *
     * Списываются альбомы медиа удалённых песен: «Жилец вершин» (АукцЫон, 1996) и
     * «Лирика (сингл)» (Сектор Газа, 2023). Их файлы не подпадают под правило «сирота»
     * (в папке нет живых песен → обход не заходит), поэтому список задан явно.
     *
     * Перечень — из решения владельца (KARAOKE-425, инвентаризация KARAOKE-417).
     * Исключения, которые не удаляются: mp3 песни «Где (2007)» (17282) — её копия
     * существует только в префиксе сироты, и обложки канонических альбомов.
     */
    private fun mediaWriteoffCandidates(): List<Candidate> {
        val result = mutableListOf<Candidate>()
        MEDIA_WRITEOFF_ALBUMS.forEach { (author, album) ->
            PROJECT_ROOT_FOLDERS.forEach { root ->
                val albumDir = File(root, "$author/$album")
                if (!albumDir.isDirectory) return@forEach
                albumDir.walkTopDown().forEach { file ->
                    if (file.isDirectory) return@forEach
                    result.add(
                        Candidate(
                            kind = CandidateKind.FILE,
                            location = CandidateLocation.LOCAL_FILESYSTEM,
                            pathOrKey = file.absolutePath,
                            sizeBytes = safeLength(file),
                            reason = Reason.WRITEOFF,
                            detail = "медиа альбома «$album», списанного по решению владельца",
                        ),
                    )
                }
            }
        }
        return result
    }

    /**
     * Кандидаты среди объектов хранилища (FR-041).
     *
     * Порядок проверок (каждый шаг — «не трогаем»):
     * 1. ключ принадлежит живой песне по текущему пути — не трогаем;
     * 2. обложка альбома, которого больше нет в каталоге, — кандидат (FR-041);
     * 3. общая обложка альбома/автора живого альбома — не трогаем (FR-014);
     * 4. устаревшая копия: объект живой песни под прежним путём (переименование) — кандидат,
     *    **но только если у живой песни на месте актуальный объект**; иначе устаревшая
     *    копия — единственная, и её удаление сломало бы песню (FR-042);
     * 5. near-match живой песни (совпадение после нормализации) — не сирота (FR-042);
     * 6. прочее — сирота (ORPHAN).
     */
    private fun storageCandidates(
        liveSongs: List<LiveSong>,
        ownKeys: Set<String>,
        historySongs: Map<String, LiveSong>,
        liveNames: Set<String>,
    ): List<Candidate> {
        val result = mutableListOf<Candidate>()
        // Живые песни по нормализованному имени файла — основа для поиска устаревших копий.
        val liveByName: Map<String, List<LiveSong>> =
            liveSongs.groupBy { SongArtifactOwnership.normalizeForMatch(it.fileName) }
        // Ключи обложек альбомов каталога: обложка альбома, которого больше нет, — сирота.
        val liveAlbumCovers: Set<String> =
            liveSongs
                .map { Triple(it.author, it.year, it.album) }
                .distinct()
                .flatMap { (author, year, album) ->
                    listOf(
                        "$author/$year - $album/$author - $year - $album.album.png",
                        "$author/$year - $album/$author - $year - $album.preview.album.png",
                    )
                }.toSet()
        listOf(
            CandidateLocation.LOCAL_STORAGE to listLocalObjects(),
            CandidateLocation.REMOTE_STORAGE to listRemoteObjects(),
        ).forEach { (location, objects) ->
            objects.forEach { (key, size) ->
                if (ownKeys.contains(key)) return@forEach
                val filePart = key.substringAfterLast('/')
                // Обложка альбома, которого больше нет в каталоге (FR-041).
                if (isAlbumCoverKey(key)) {
                    if (!liveAlbumCovers.contains(key)) {
                        result.add(
                            storageCandidate(
                                location,
                                key,
                                size,
                                Reason.ORPHAN,
                                "обложка альбома, которого больше нет в каталоге",
                            ),
                        )
                    }
                    return@forEach
                }
                // Общая обложка альбома/автора (FR-014) — не объект песни.
                if (isSharedCoverKey(key)) return@forEach
                // Устаревшая копия: объект живой песни под прежним путём.
                val oldPrefix = key.substringBeforeLast('/', "")
                val canonicalKeys = canonicalKeysOfStaleCopy(filePart, oldPrefix, liveByName, historySongs)
                // Свой объект живой песни (путь совпал) — не устаревшая копия.
                if (canonicalKeys.isNotEmpty() && canonicalKeys.none { canonical -> canonical == key }) {
                    // Удаляем, только если у живой песни ЕСТЬ актуальный объект: иначе
                    // устаревшая копия — единственная (FR-042).
                    if (canonicalKeys.all { canonical -> objectExists(location, canonical) }) {
                        result.add(
                            storageCandidate(
                                location,
                                key,
                                size,
                                Reason.STALE_COPY,
                                "устаревшая копия под прежним путём; актуальный объект живой песни на месте",
                            ),
                        )
                    }
                    return@forEach
                }
                // FR-042: near-match живой песни — не сирота.
                if (SongArtifactOwnership.isNearMatchOfLiveSong(liveNames, filePart)) return@forEach
                result.add(
                    storageCandidate(
                        location,
                        key,
                        size,
                        Reason.ORPHAN,
                        detailFor(key, Reason.ORPHAN),
                    ),
                )
            }
        }
        return result
    }

    /** Кандидат-объект хранилища. */
    private fun storageCandidate(
        location: CandidateLocation,
        key: String,
        size: Long,
        reason: Reason,
        detail: String,
    ): Candidate =
        Candidate(
            kind = CandidateKind.STORAGE_OBJECT,
            location = location,
            pathOrKey = key,
            sizeBytes = size,
            reason = reason,
            detail = detail,
        )

    /**
     * Канонические ключи живой песни, которой принадлежит объект под прежним путём.
     *
     * Путь объекта: `$автор/$год - $альбом/$имя песни[хвост артефакта]`. Если имя песни
     * совпадает с именем живой песни (после нормализации) или с её прежним именем
     * (`tbl_song_name_history`), объект — устаревшая копия этой песни: актуальный объект
     * лежит под другим путём (переименование не мигрирует ключи — KARAOKE-428).
     *
     * @return список актуальных ключей; пустой список — объект не устаревшая копия.
     */
    private fun canonicalKeysOfStaleCopy(
        filePart: String,
        oldPrefix: String,
        liveByName: Map<String, List<LiveSong>>,
        historySongs: Map<String, LiveSong>,
    ): List<String> {
        val tail = storageTailOf(filePart) ?: return emptyList()
        val (songName, artifactTail) = tail
        val normalized = SongArtifactOwnership.normalizeForMatch(songName)
        val matches = liveByName[normalized].orEmpty()
        if (matches.isNotEmpty()) {
            return matches
                .filter { song -> sameAuthorAndYear(song, oldPrefix) }
                .map { song -> canonicalKey(song, artifactTail) }
        }
        val historical = historySongs[songName] ?: return emptyList()
        if (!sameAuthorAndYear(historical, oldPrefix)) return emptyList()
        return listOf(canonicalKey(historical, artifactTail))
    }

    /**
     * Тот же автор и тот же год — иначе объект под старым путём не устаревшая копия.
     *
     * Ключ объекта — `$автор/$год - $альбом/$имя песни`. Переименование **альбома** меняет
     * только часть пути после `$автор/$год - ` — такие объекты действительно устаревшие
     * копии (актуальный объект лежит в канонической папке).
     *
     * Но у разных исполнителей бывают альбомы с одинаковым названием (например
     * «Призраки Завтра» у «Мумий Тролль», «Мумий Тролль, T-Fest» и
     * «Мумий Тролль feat. Скриптонит»): их объекты — **разные записи**, а не копии.
     * Без проверки автора и года перечень разовой уборки предлагал удалить чужие объекты
     * (факт 2026-11-14, замер после импорта: 19 ложных кандидатов).
     */
    private fun sameAuthorAndYear(
        song: LiveSong,
        oldPrefix: String,
    ): Boolean = isSameAuthorAndYear(song.author, song.year, oldPrefix)

    /** Актуальный ключ объекта песни: `$автор/$год - $альбом/$имя песни[хвост]`. */
    private fun canonicalKey(
        song: LiveSong,
        artifactTail: String,
    ): String = "${song.author}/${song.year} - ${song.album}/${song.fileName}$artifactTail"

    /** Есть ли объект в хранилище (проверка «актуальный объект живой песни на месте»). */
    private fun objectExists(
        location: CandidateLocation,
        key: String,
    ): Boolean =
        try {
            when (location) {
                CandidateLocation.LOCAL_STORAGE -> storageService.fileExists(BUCKET, key)
                CandidateLocation.REMOTE_STORAGE -> storageApiClient.fileExists(BUCKET, key)
                else -> false
            }
        } catch (e: Exception) {
            false
        }

    /** Ключ обложки альбома (не автора). */
    private fun isAlbumCoverKey(key: String): Boolean {
        val name = key.substringAfterLast('/')
        return name.endsWith(".album.png") || name.endsWith(".preview.album.png")
    }

    /**
     * Кандидаты в БД (FR-041): черновики заданий без задания и исторические ссылки
     * на несуществующие песни (KARAOKE-426).
     *
     * Критерии — утверждённые в инвентаризации (KARAOKE-420, артефакт
     * `T10-db-orphans.md` § «Готовый список к удалению»). Таблицы и условия **зашиты
     * в код**: SQL из перечня кандидатов не исполняется никогда — кандидат несёт только
     * имя таблицы, а удаление выполняет фиксированный запрос для этой таблицы.
     *
     * Важно: у `tbl_events` критерий содержит `song_id <> 0` — без него снесли бы живые
     * плейсхолдерные строки (11 253 строки «событие не про песню»).
     */
    private fun databaseCandidates(): List<Candidate> {
        val result = mutableListOf<Candidate>()
        DB_ORPHAN_TABLES.forEach { spec ->
            val count = countRows(spec) ?: return@forEach
            if (count <= 0) return@forEach
            val bytes = payloadBytes(spec) ?: 0L
            result.add(
                Candidate(
                    kind = CandidateKind.DB_ROWS,
                    location = CandidateLocation.DATABASE,
                    pathOrKey = spec.table,
                    sizeBytes = bytes,
                    reason = Reason.ORPHAN,
                    detail = "${spec.description}: $count строк",
                ),
            )
        }
        return result
    }

    /** Число строк по критерию сироты. */
    private fun countRows(spec: DbOrphanTable): Int? {
        val connection = database.getConnection() ?: return null
        return try {
            connection
                .prepareStatement("SELECT count(*) FROM ${spec.table} WHERE ${spec.where}")
                .use { ps ->
                    ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else null }
                }
        } catch (e: Exception) {
            null
        }
    }

    /** Примерный объём строк-сирот (payload, байт) — для оценки освобождаемого места. */
    private fun payloadBytes(spec: DbOrphanTable): Long? {
        val connection = database.getConnection() ?: return null
        return try {
            connection
                .prepareStatement(
                    "SELECT coalesce(sum(pg_column_size(t.*)), 0) FROM ${spec.table} t WHERE ${spec.where}",
                ).use { ps ->
                    ps.executeQuery().use { rs -> if (rs.next()) rs.getLong(1) else null }
                }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Кандидаты среди файлов на диске (FR-041, FR-042): настоящие сироты.
     *
     * Классификация — [SongOrphanFileClassifier], то есть утверждённое правило
     * инвентаризации (KARAOKE-424): кандидатом является только файл, чьё имя
     * похоже на артефакт песни и для которого песни с таким именем нет в БД
     * ни в каком виде. Обложки альбомов и авторов, `key_bpm.csv`, ручные файлы
     * и проекты рендера под правило не попадают — удалять их нельзя.
     */
    private fun diskCandidates(liveSongs: List<LiveSong>): List<Candidate> {
        val result = mutableListOf<Candidate>()
        result.addAll(mediaWriteoffCandidates())
        // Множество «своих» имён по папкам альбомов: файл принадлежит песне, если
        // его имя — артефакт одной из живых песен его альбома.
        val byFolder = liveSongs.groupBy { folderOf(it) }
        val classifier =
            SongOrphanFileClassifier(
                liveByFolder = byFolder.mapValues { (_, songs) -> songs.map { it.fileName } },
                allLiveNames = liveSongs.map { it.fileName },
            )
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
                            val verdict = classifier.classify(file.name, albumDir.path)
                            if (verdict.verdict != SongOrphanFileClassifier.Verdict.ORPHAN) return@forEach
                            result.add(
                                Candidate(
                                    kind = CandidateKind.FILE,
                                    location = CandidateLocation.LOCAL_FILESYSTEM,
                                    pathOrKey = file.absolutePath,
                                    sizeBytes = safeLength(file),
                                    reason = Reason.ORPHAN,
                                    detail = "песни с таким именем нет в БД (ни в каком виде)",
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

    /** Пояснение к кандидату. */
    private fun detailFor(
        key: String,
        reason: Reason,
    ): String =
        when (reason) {
            Reason.STALE_COPY -> "объект лежит под прежним именем живой песни"
            Reason.ORPHAN -> if (!key.contains('/')) "мусор в корне бакета" else "объект не принадлежит ни одной живой песне"
            Reason.WRITEOFF -> "медиа альбома, списанного по решению владельца"
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
