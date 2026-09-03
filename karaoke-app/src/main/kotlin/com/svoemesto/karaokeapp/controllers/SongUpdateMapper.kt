package com.svoemesto.karaokeapp.controllers

import com.svoemesto.karaokeapp.censored
import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.KaraokeProcess
import com.svoemesto.karaokeapp.model.Album
import com.svoemesto.karaokeapp.model.Author
import com.svoemesto.karaokeapp.model.Song
import com.svoemesto.karaokeapp.model.SongField
import com.svoemesto.karaokeapp.model.SongType
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import com.svoemesto.karaokeapp.sanitizeSongFileName
import org.slf4j.LoggerFactory

/**
 * Результат применения параметров [POST /api/song/update] к [Song].
 *
 * Содержит информацию, которая нужна вызывающему коду (контроллеру) после
 * применения параметров, но не может быть вычислена внутри маппера
 * (например, нужно ли вызывать [ApiController.notifyStatsDirty]).
 *
 * @property albumLinkValid true если альбом успешно привязан (или альбом не менялся);
 *   false — попытка привязать чужой/несуществующий альбом (cross-author check не прошёл).
 * @property fileNameRenameError null если переименование прошло успешно (или не запрашивалось),
 *   иначе — понятное для пользователя сообщение об ошибке.
 * @property freeChanged true если поле [Song.free] реально изменилось (для notifyStatsDirty).
 * @property idStatusChanged true если поле [Song.idStatus] реально изменилось (для notifyStatsDirty).
 * @property baselineAutoFilled true если baseline-логика автозаполнила [SongField.NAME_CENSORED]
 *   из [Song.songName] (для логирования/observability).
 *
 * @see docs.features.song-edit-and-censored.md
 */
data class SongUpdateApplyResult(
    val albumLinkValid: Boolean = true,
    val fileNameRenameError: String? = null,
    val freeChanged: Boolean = false,
    val idStatusChanged: Boolean = false,
    val baselineAutoFilled: Boolean = false,
)

/**
 * Централизованный маппер параметров [POST /api/song/update] → [Song].
 *
 * Заменяет явное перечисление ~95 `@RequestParam` в [ApiController.songs2Update]
 * на единый `@RequestParam Map<String, String> all` + этот маппер.
 * Это устраняет корневую причину класса багов «фронт шлёт поле X,
 * бэкенд его не принимает» — Spring Web молча отбрасывает неизвестные
 * query-параметры (см. spec 302 Root cause analysis).
 *
 * Маппер выполняет 5 фаз (A-E):
 * 1. **Phase A — Special-case fields** с бизнес-логикой (fileName sanitize +
 *    collision + active-process check, albumId cross-author check,
 *    songType enum mapping). Обрабатываются первыми, чтобы их результат
 *    (например, [SongUpdateApplyResult.fileNameRenameError]) был доступен
 *    для финального return.
 * 2. **Phase B — Standard string fields** через [SongField] lookup-table
 *    (camelCase param → SONG_FIELD_NAME). Все параметры, у которых есть
 *    прямое соответствие `SongField.X`, маппятся в `song.fields[X]`.
 * 3. **Phase C — Direct setters** для полей, которые НЕ в [SongField]:
 *    tags, rootFolder, description, shortDescription, warning.
 * 4. **Phase D — Baseline** автозаполнение [SongField.NAME_CENSORED] из
 *    [Song.songName] если пустое (политика «доверие редактору»,
 *    см. specs/277-song-name-censored Clarification Q1/A).
 * 5. **Phase E — Result** собирает [SongUpdateApplyResult] (albumLinkValid,
 *    fileNameRenameError, freeChanged, idStatusChanged, baselineAutoFilled).
 *
 * Неизвестные параметры (не в [SongField] и не в special-case) — WARN-лог
 * + ignore (обратная совместимость с потенциальными будущими клиентами,
 * см. spec 302 FR-014 edge case «Unknown param»).
 *
 * Семантика 1:1 с текущим телом [ApiController.songs2Update] (см. spec 302 FR-014).
 *
 * @see docs.features.song-edit-and-censored.md
 */
object SongUpdateMapper {
    private val log = LoggerFactory.getLogger(SongUpdateMapper::class.java)

    /**
     * Lookup-table стандартных полей: camelCase-имя параметра → [SongField].
     *
     * Строится явно (без reflection), чтобы:
     * (а) избежать ошибок рефлексии в runtime;
     * (б) явно перечислить поддерживаемые поля (легко читается);
     * (в) легко добавить новое поле — одна строка.
     */
    private val fieldLookup: Map<String, SongField> =
        mapOf(
            "id" to SongField.ID,
            "songName" to SongField.NAME,
            "songNameCensored" to SongField.NAME_CENSORED,
            "author" to SongField.AUTHOR,
            "album" to SongField.ALBUM,
            "year" to SongField.YEAR,
            "track" to SongField.TRACK,
            "key" to SongField.KEY,
            "bpm" to SongField.BPM,
            "ms" to SongField.MS,
            "format" to SongField.FORMAT,
            "date" to SongField.DATE,
            "time" to SongField.TIME,
            "boostyOnly" to SongField.BOOSTY_ONLY,
            "idBoosty" to SongField.ID_BOOSTY,
            "versionBoosty" to SongField.VERSION_BOOSTY,
            "idBoostyFiles" to SongField.ID_BOOSTY_FILES,
            "versionBoostyFiles" to SongField.VERSION_BOOSTY_FILES,
            "idVk" to SongField.ID_VK,
            "idDzenLyrics" to SongField.ID_DZEN_LYRICS,
            "versionDzenLyrics" to SongField.VERSION_DZEN_LYRICS,
            "idDzenKaraoke" to SongField.ID_DZEN_KARAOKE,
            "versionDzenKaraoke" to SongField.VERSION_DZEN_KARAOKE,
            "idDzenChords" to SongField.ID_DZEN_CHORDS,
            "versionDzenChords" to SongField.VERSION_DZEN_CHORDS,
            "idDzenMelody" to SongField.ID_DZEN_MELODY,
            "versionDzenMelody" to SongField.VERSION_DZEN_MELODY,
            "idVkLyrics" to SongField.ID_VK_LYRICS,
            "versionVkLyrics" to SongField.VERSION_VK_LYRICS,
            "idVkKaraoke" to SongField.ID_VK_KARAOKE,
            "versionVkKaraoke" to SongField.VERSION_VK_KARAOKE,
            "idVkChords" to SongField.ID_VK_CHORDS,
            "versionVkChords" to SongField.VERSION_VK_CHORDS,
            "idVkMelody" to SongField.ID_VK_MELODY,
            "versionVkMelody" to SongField.VERSION_VK_MELODY,
            "idStatus" to SongField.ID_STATUS,
            "color" to SongField.COLOR,
            "idTelegramLyrics" to SongField.ID_TELEGRAM_LYRICS,
            "versionTelegramLyrics" to SongField.VERSION_TELEGRAM_LYRICS,
            "idTelegramKaraoke" to SongField.ID_TELEGRAM_KARAOKE,
            "versionTelegramKaraoke" to SongField.VERSION_TELEGRAM_KARAOKE,
            "idTelegramChords" to SongField.ID_TELEGRAM_CHORDS,
            "versionTelegramChords" to SongField.VERSION_TELEGRAM_CHORDS,
            "idTelegramMelody" to SongField.ID_TELEGRAM_MELODY,
            "versionTelegramMelody" to SongField.VERSION_TELEGRAM_MELODY,
            "idPlLyrics" to SongField.ID_PL_LYRICS,
            "versionPlLyrics" to SongField.VERSION_PL_LYRICS,
            "idPlKaraoke" to SongField.ID_PL_KARAOKE,
            "versionPlKaraoke" to SongField.VERSION_PL_KARAOKE,
            "idPlChords" to SongField.ID_PL_CHORDS,
            "versionPlChords" to SongField.VERSION_PL_CHORDS,
            "idPlMelody" to SongField.ID_PL_MELODY,
            "versionPlMelody" to SongField.VERSION_PL_MELODY,
            "idMaxLyrics" to SongField.ID_MAX_LYRICS,
            "versionMaxLyrics" to SongField.VERSION_MAX_LYRICS,
            "idMaxKaraoke" to SongField.ID_MAX_KARAOKE,
            "versionMaxKaraoke" to SongField.VERSION_MAX_KARAOKE,
            "idMaxChords" to SongField.ID_MAX_CHORDS,
            "versionMaxChords" to SongField.VERSION_MAX_CHORDS,
            "idMaxMelody" to SongField.ID_MAX_MELODY,
            "versionMaxMelody" to SongField.VERSION_MAX_MELODY,
            "idDzenDemo" to SongField.ID_DZEN_DEMO,
            "idVkDemo" to SongField.ID_VK_DEMO,
            "idTelegramDemo" to SongField.ID_TELEGRAM_DEMO,
            "idMaxDemo" to SongField.ID_MAX_DEMO,
            "versionDzenDemo" to SongField.VERSION_DZEN_DEMO,
            "versionVkDemo" to SongField.VERSION_VK_DEMO,
            "versionTelegramDemo" to SongField.VERSION_TELEGRAM_DEMO,
            "versionMaxDemo" to SongField.VERSION_MAX_DEMO,
            "resultVersion" to SongField.RESULT_VERSION,
            "diffBeats" to SongField.DIFFBEATS,
            "rate" to SongField.RATE,
            "rootId" to SongField.ROOT_ID,
            "audioParentId" to SongField.AUDIO_PARENT_ID,
            "audioSimilarityPercent" to SongField.AUDIO_SIMILARITY_PERCENT,
            "audioDeltaMs" to SongField.AUDIO_DELTA_MS,
            "free" to SongField.FREE,
            "idTariff" to SongField.ID_TARIFF,
            "indexTabsVariant" to SongField.INDEX_TABS_VARIANT,
            "idSponsr" to SongField.ID_SPONSR,
            "versionSponsr" to SongField.VERSION_SPONSR,
        )

    /**
     * Множество direct-setter ключей (поля, которые НЕ в [SongField]).
     * Обрабатываются в Phase C — пишутся напрямую в свойства [Song].
     */
    private val directSetters: Set<String> =
        setOf(
            "tags",
            "rootFolder",
            "description",
            "shortDescription",
            "warning",
        )

    /**
     * Множество special-case ключей (поля с бизнес-логикой).
     * Обрабатываются в Phase A — до Phase B/C, чтобы их результат
     * был доступен для финального return.
     */
    private val specialCaseKeys: Set<String> =
        setOf(
            "fileName",
            "albumId",
            "songType",
        )

    /**
     * Применяет параметры из [params] к [song].
     *
     * Семантика 1:1 с текущим телом [ApiController.songs2Update] (см. spec 302 FR-014).
     *
     * @param song загруженная песня (мутируется in-place).
     * @param params query-параметры из [POST /api/song/update] (ключи — camelCase, значения — String).
     * @param database рабочая БД (для fileName collision check, albumId lookup, baseline censor).
     * @param storageService storage service (для albumId lookup).
     * @param storageApiClient storage API client (для albumId lookup).
     * @return [SongUpdateApplyResult] с информацией о результате применения.
     */
    fun apply(
        song: Song,
        params: Map<String, String>,
        database: KaraokeConnection,
        storageService: KaraokeStorageService,
        storageApiClient: StorageApiClient,
    ): SongUpdateApplyResult {
        // Снимки ДО применения правок (для diff в Phase E).
        val freeBefore = song.free
        val idStatusBefore = song.idStatus

        var albumLinkValid = true
        var fileNameRenameError: String? = null
        var baselineAutoFilled = false

        // ===== Phase A: Special-case fields =====
        // fileName — sanitize + collision + active-process check (FR-014, behavior 1:1).
        params["fileName"]?.let { requestedFileName ->
            val sanitized = requestedFileName.sanitizeSongFileName()
            val effectiveRootFolder = params["rootFolder"] ?: song.rootFolder
            if (sanitized.isEmpty()) {
                fileNameRenameError =
                    "Имя файла после удаления недопустимых символов оказалось пустым — введите другое значение."
            } else if (sanitized != song.fileName) {
                val collision =
                    Song
                        .loadListFromDb(
                            args =
                                mapOf(
                                    Pair("file_name", sanitized),
                                    Pair("root_folder", effectiveRootFolder),
                                ),
                            database = database,
                            storageService = storageService,
                            storageApiClient = storageApiClient,
                            withoutMarkersAndText = true,
                        ).any { it.id != song.id }
                if (collision) {
                    fileNameRenameError = "Песня с именем файла «$sanitized» уже существует в этой папке."
                } else if (KaraokeProcess.hasActiveProcess(songId = song.id, database = database)) {
                    fileNameRenameError = "Над песней сейчас выполняется фоновая обработка — дождитесь её завершения и повторите переименование."
                } else {
                    val oldFileName = song.fileName
                    song.fileName = sanitized
                    song.renameCascadeExtraArtifacts(oldFileName)
                }
            }
        }

        // albumId — cross-author check (FR-014, behavior 1:1).
        params["albumId"]?.let { rawAlbumId ->
            val newAlbumId = rawAlbumId.toLongOrNull()
            if (newAlbumId == null || newAlbumId <= 0L) {
                song.albumId = null
            } else {
                val album = Album.getAlbumById(newAlbumId, database, storageService, storageApiClient)
                val albumAuthor = album?.let { Author.getAuthorById(it.authorId, database, storageService, storageApiClient) }
                if (album != null && albumAuthor != null && albumAuthor.author.equals(song.author, ignoreCase = true)) {
                    song.albumId = newAlbumId
                } else {
                    albumLinkValid = false
                }
            }
        }

        // songType — enum mapping (FR-014, behavior 1:1).
        params["songType"]?.let { rawSongType ->
            song.songType = SongType.entries.firstOrNull { st -> st.dbValue == rawSongType.lowercase() } ?: SongType.SONG
        }

        // ===== Phase B + C: Standard + Direct fields =====
        for ((key, value) in params) {
            // Skip already-processed special-case keys.
            if (key in specialCaseKeys) continue

            when {
                // Direct setters (Phase C)
                key in directSetters -> {
                    when (key) {
                        "tags" -> song.tags = value
                        "rootFolder" -> song.rootFolder = value
                        "description" -> song.description = value
                        "shortDescription" -> song.shortDescription = value
                        "warning" -> song.warning = value
                    }
                }
                // Standard fields via SongField lookup (Phase B)
                else -> {
                    val field = fieldLookup[key]
                    if (field != null) {
                        song.fields[field] = value
                    } else {
                        // Неизвестный параметр — WARN-лог + ignore (обратная совместимость).
                        log.warn("[SongUpdateMapper] Unknown param '$key' ignored for song id=${song.id}")
                    }
                }
            }
        }

        // ===== Phase D: Baseline =====
        // Если song_name_censored пустое И song_name непустое — автозаполнить через censor()
        // (политика «доверие редактору» — см. specs/277-song-name-censored Clarification Q1/A).
        if (song.songNameCensored.isEmpty() && song.songName.isNotEmpty()) {
            song.songNameCensored = song.songName.censored(database)
            baselineAutoFilled = true
        }

        // ===== Phase E: Result =====
        return SongUpdateApplyResult(
            albumLinkValid = albumLinkValid,
            fileNameRenameError = fileNameRenameError,
            freeChanged = song.free != freeBefore,
            idStatusChanged = song.idStatus != idStatusBefore,
            baselineAutoFilled = baselineAutoFilled,
        )
    }
}
