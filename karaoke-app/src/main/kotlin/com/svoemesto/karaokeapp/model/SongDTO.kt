package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.HealthReportDTO
import com.svoemesto.karaokeapp.KaraokeConnection
import java.io.Serializable
import java.util.*
import kotlin.String

/**
 * DTO для setting: сериализуемое представление для API/UI.
 *
 * @see docs/features/mlt-generator.md
 */
data class SongDTO(
    val id: Long,
    var idPrevious: Long,
    var idNext: Long,
    var idLeft: Long,
    var idRight: Long,
    val rootFolder: String,
    val fileName: String,
    val idStatus: Long,
    val status: String,
    val tags: String,
    val color: String,
    val songName: String,
    val songNameCensored: String,
    val author: String,
    val album: String,
    val date: String,
    val time: String,
    val timecode: String,
    val dateTimePublish: Date?,
    val onAir: Boolean,
    val year: Long,
    val track: Long,
    val key: String,
    val bpm: Long,
    val ms: Long,
    val countVoices: Int,
    val firstSongInAlbum: Boolean,
    val flagBoosty: String,
    val flagSponsr: String,
    val flagVk: String,
    val flagDzenLyrics: String,
    val flagDzenKaraoke: String,
    val flagDzenChords: String,
    val flagDzenMelody: String,
    val flagPlayerDemo: String,
    val flagVkLyrics: String,
    val flagVkKaraoke: String,
    val flagVkChords: String,
    val flagVkMelody: String,
    val flagTelegramLyrics: String,
    val flagTelegramKaraoke: String,
    val flagTelegramChords: String,
    val flagTelegramMelody: String,
    val flagPlLyrics: String,
    val flagPlKaraoke: String,
    val flagPlChords: String,
    val flagPlMelody: String,
    val flagMaxLyrics: String,
    val flagMaxKaraoke: String,
    val flagMaxChords: String,
    val flagMaxMelody: String,
    val flagExclusive: String,
    val flagFree: String,
    val processColorBoosty: String,
    val processColorSponsr: String,
    val processColorVk: String,
    val processColorMeltLyrics: String,
    val processColorMeltKaraoke: String,
    val processColorMeltChords: String,
    val processColorMeltMelody: String,
    val processColorPlayerDemo: String,
    val processColorDzenLyrics: String,
    val processColorDzenKaraoke: String,
    val processColorDzenChords: String,
    val processColorDzenMelody: String,
    val processColorVkLyrics: String,
    val processColorVkKaraoke: String,
    val processColorVkChords: String,
    val processColorVkMelody: String,
    val processColorTelegramLyrics: String,
    val processColorTelegramKaraoke: String,
    val processColorTelegramChords: String,
    val processColorTelegramMelody: String,
    val processColorPlLyrics: String,
    val processColorPlKaraoke: String,
    val processColorPlChords: String,
    val processColorPlMelody: String,
    val processColorMaxLyrics: String,
    val processColorMaxKaraoke: String,
    val processColorMaxChords: String,
    val processColorMaxMelody: String,
    val idBoosty: String,
    val idBoostyFiles: String,
    val idSponsr: String,
    val versionBoosty: Int,
    val versionBoostyFiles: Int,
    val versionSponsr: Int,
    val indexTabsVariant: Int,
    val idVk: String,
    val idDzenLyrics: String,
    val idDzenKaraoke: String,
    val idDzenChords: String,
    val idDzenMelody: String,
    val versionDzenLyrics: Int,
    val versionDzenKaraoke: Int,
    val versionDzenChords: Int,
    val versionDzenMelody: Int,
    val idVkLyrics: String,
    val idVkLyricsOID: String,
    val idVkLyricsID: String,
    val idVkKaraoke: String,
    val idVkKaraokeOID: String,
    val idVkKaraokeID: String,
    val idVkChords: String,
    val idVkChordsOID: String,
    val idVkChordsID: String,
    val idVkMelody: String,
    val idVkMelodyOID: String,
    val idVkMelodyID: String,
    val versionVkLyrics: Int,
    val versionVkKaraoke: Int,
    val versionVkChords: Int,
    val versionVkMelody: Int,
    val idTelegramLyrics: String,
    val idTelegramKaraoke: String,
    val idTelegramChords: String,
    val idTelegramMelody: String,
    val versionTelegramLyrics: Int,
    val versionTelegramKaraoke: Int,
    val versionTelegramChords: Int,
    val versionTelegramMelody: Int,
    val idPlLyrics: String,
    val idPlKaraoke: String,
    val idPlChords: String,
    val idPlMelody: String,
    val versionPlLyrics: Int,
    val versionPlKaraoke: Int,
    val versionPlChords: Int,
    val versionPlMelody: Int,
    val idMaxLyrics: String,
    val idMaxKaraoke: String,
    val idMaxChords: String,
    val idMaxMelody: String,
    val versionMaxLyrics: Int,
    val versionMaxKaraoke: Int,
    val versionMaxChords: Int,
    val versionMaxMelody: Int,
    val idDzenDemo: String,
    val versionDzenDemo: Int,
    val idVkDemo: String,
    val versionVkDemo: Int,
    val idTelegramDemo: String,
    val versionTelegramDemo: Int,
    val idMaxDemo: String,
    val versionMaxDemo: Int,
    val resultVersion: Long,
    val rate: Int,
    val healthReportText: String,
    val healthReportColor: String,
    val healthReportList: List<HealthReportDTO>,
    val formattedTextSong: String,
    val formattedTextTabs: String,
    val formattedTextChords: String,
    val rootId: Long,
    val audioParentId: Long,
    val audioSimilarityPercent: Int,
    val audioDeltaMs: Long,
    val exclusive: Boolean,
    val free: Boolean,
    val idTariff: Int,
    // 0 = песня не привязана к альбому (сингл). Entity-уровень (Song.albumId) — Long? (null =
    // нет альбома, настоящий SQL NULL из-за FK); здесь, как и у прочих Long-полей DTO (rootId,
    // audioParentId), используется 0 как "нет значения" — см. fromDto()/toDTO() ниже.
    val albumId: Long,
    val albumName: String,
    // Тип песни в lowercase (song/instrumental/poetry) — SongType.dbValue. Для обратной совместимости
    // со старыми записями БД без поля (и для уже сохранённых "" дефолт) SongType-геттер на стороне
    // Song, при отсутствии значения, возвращает SongType.SONG.
    val songType: String,
    val haveSourceText: Boolean,
) : Serializable,
    Comparable<SongDTO>,
    KaraokeDbTableDto {
    // fromDto() нигде не вызывается (grep "\.fromDto(" по всему karaoke-app пуст на момент написания) —
    // реализован для формального соответствия KaraokeDbTableDto по образцу AuthorDTO/PicturesDTO.
    // sourceText/resultText/sourceMarkers/statusProcess*/diffBeats сюда не входят: их нет в SongDTO
    // (тяжёлые текстовые поля намеренно не тянутся в этот DTO, см. withoutMarkersAndText в Song.kt).
    override fun fromDto(database: KaraokeConnection): Song {
        val entity = Song(database = database)
        entity.id = id
        entity.fields[SongField.NAME] = songName
        entity.fields[SongField.AUTHOR] = author
        entity.fields[SongField.ALBUM] = album
        entity.fields[SongField.DATE] = date
        entity.fields[SongField.TIME] = time
        entity.fields[SongField.YEAR] = year.toString()
        entity.fields[SongField.TRACK] = track.toString()
        entity.fields[SongField.KEY] = key
        entity.fields[SongField.BPM] = bpm.toString()
        entity.ms = ms
        entity.rootFolder = rootFolder
        entity.fileName = fileName
        entity.fields[SongField.ID_STATUS] = idStatus.toString()
        entity.fields[SongField.ID_BOOSTY] = idBoosty
        entity.fields[SongField.VERSION_BOOSTY] = versionBoosty.toString()
        entity.fields[SongField.ID_BOOSTY_FILES] = idBoostyFiles
        entity.fields[SongField.VERSION_BOOSTY_FILES] = versionBoostyFiles.toString()
        entity.fields[SongField.ID_SPONSR] = idSponsr
        entity.fields[SongField.VERSION_SPONSR] = versionSponsr.toString()
        entity.fields[SongField.INDEX_TABS_VARIANT] = indexTabsVariant.toString()
        entity.fields[SongField.ID_VK] = idVk
        entity.fields[SongField.ID_DZEN_LYRICS] = idDzenLyrics
        entity.fields[SongField.VERSION_DZEN_LYRICS] = versionDzenLyrics.toString()
        entity.fields[SongField.ID_DZEN_KARAOKE] = idDzenKaraoke
        entity.fields[SongField.VERSION_DZEN_KARAOKE] = versionDzenKaraoke.toString()
        entity.fields[SongField.ID_DZEN_CHORDS] = idDzenChords
        entity.fields[SongField.VERSION_DZEN_CHORDS] = versionDzenChords.toString()
        entity.fields[SongField.ID_DZEN_MELODY] = idDzenMelody
        entity.fields[SongField.VERSION_DZEN_MELODY] = versionDzenMelody.toString()
        entity.fields[SongField.ID_VK_LYRICS] = idVkLyrics
        entity.fields[SongField.VERSION_VK_LYRICS] = versionVkLyrics.toString()
        entity.fields[SongField.ID_VK_KARAOKE] = idVkKaraoke
        entity.fields[SongField.VERSION_VK_KARAOKE] = versionVkKaraoke.toString()
        entity.fields[SongField.ID_VK_CHORDS] = idVkChords
        entity.fields[SongField.VERSION_VK_CHORDS] = versionVkChords.toString()
        entity.fields[SongField.ID_VK_MELODY] = idVkMelody
        entity.fields[SongField.VERSION_VK_MELODY] = versionVkMelody.toString()
        entity.fields[SongField.ID_TELEGRAM_LYRICS] = idTelegramLyrics
        entity.fields[SongField.VERSION_TELEGRAM_LYRICS] = versionTelegramLyrics.toString()
        entity.fields[SongField.ID_TELEGRAM_KARAOKE] = idTelegramKaraoke
        entity.fields[SongField.VERSION_TELEGRAM_KARAOKE] = versionTelegramKaraoke.toString()
        entity.fields[SongField.ID_TELEGRAM_CHORDS] = idTelegramChords
        entity.fields[SongField.VERSION_TELEGRAM_CHORDS] = versionTelegramChords.toString()
        entity.fields[SongField.ID_TELEGRAM_MELODY] = idTelegramMelody
        entity.fields[SongField.VERSION_TELEGRAM_MELODY] = versionTelegramMelody.toString()
        entity.fields[SongField.ID_PL_LYRICS] = idPlLyrics
        entity.fields[SongField.VERSION_PL_LYRICS] = versionPlLyrics.toString()
        entity.fields[SongField.ID_PL_KARAOKE] = idPlKaraoke
        entity.fields[SongField.VERSION_PL_KARAOKE] = versionPlKaraoke.toString()
        entity.fields[SongField.ID_PL_CHORDS] = idPlChords
        entity.fields[SongField.VERSION_PL_CHORDS] = versionPlChords.toString()
        entity.fields[SongField.ID_PL_MELODY] = idPlMelody
        entity.fields[SongField.VERSION_PL_MELODY] = versionPlMelody.toString()
        entity.fields[SongField.ID_MAX_LYRICS] = idMaxLyrics
        entity.fields[SongField.VERSION_MAX_LYRICS] = versionMaxLyrics.toString()
        entity.fields[SongField.ID_MAX_KARAOKE] = idMaxKaraoke
        entity.fields[SongField.VERSION_MAX_KARAOKE] = versionMaxKaraoke.toString()
        entity.fields[SongField.ID_MAX_CHORDS] = idMaxChords
        entity.fields[SongField.VERSION_MAX_CHORDS] = versionMaxChords.toString()
        entity.fields[SongField.ID_MAX_MELODY] = idMaxMelody
        entity.fields[SongField.VERSION_MAX_MELODY] = versionMaxMelody.toString()
        entity.fields[SongField.ID_DZEN_DEMO] = idDzenDemo
        entity.fields[SongField.VERSION_DZEN_DEMO] = versionDzenDemo.toString()
        entity.fields[SongField.ID_VK_DEMO] = idVkDemo
        entity.fields[SongField.VERSION_VK_DEMO] = versionVkDemo.toString()
        entity.fields[SongField.ID_TELEGRAM_DEMO] = idTelegramDemo
        entity.fields[SongField.VERSION_TELEGRAM_DEMO] = versionTelegramDemo.toString()
        entity.fields[SongField.ID_MAX_DEMO] = idMaxDemo
        entity.fields[SongField.VERSION_MAX_DEMO] = versionMaxDemo.toString()
        entity.fields[SongField.RESULT_VERSION] = resultVersion.toString()
        entity.fields[SongField.RATE] = rate.toString()
        entity.formattedTextSong = formattedTextSong
        entity.formattedTextTabs = formattedTextTabs
        entity.formattedTextChords = formattedTextChords
        entity.rootId = rootId
        entity.audioParentId = audioParentId
        entity.audioSimilarityPercent = audioSimilarityPercent
        entity.audioDeltaMs = audioDeltaMs
        entity.exclusive = exclusive
        entity.free = free
        entity.idTariff = idTariff
        entity.fields[SongField.SONG_TYPE] = songType
        entity.tags = tags
        entity.albumId = if (albumId > 0) albumId else null
        return entity
    }

    private val sortString: String get() {
//        return listOf(
//            author, year.toString(), album, "%3d".format(track)
//        ).joinToString(" - ")
        return if (dateTimePublish == null) {
            listOf(
                author,
                year.toString(),
                album,
                "%3d".format(track),
            ).joinToString(" - ")
        } else {
            "%15d".format(dateTimePublish.time)
        }
    }

    override fun compareTo(other: SongDTO): Int = sortString.compareTo(other.sortString)

    fun toDtoDigest(): SongDTOdigest =
        SongDTOdigest(
            id = id,
            idPrevious = idPrevious,
            idNext = idNext,
            idLeft = idLeft,
            idRight = idRight,
            idStatus = idStatus,
            status = status,
            tags = tags,
            color = color,
            songName = songName,
            songNameCensored = songNameCensored,
            author = author,
            album = album,
            date = date,
            time = time,
            timecode = timecode,
            ms = ms,
            dateTimePublish = dateTimePublish,
            year = year,
            track = track,
            countVoices = countVoices,
            firstSongInAlbum = firstSongInAlbum,
            flagBoosty = flagBoosty,
            flagSponsr = flagSponsr,
            flagVk = flagVk,
            flagDzenLyrics = flagDzenLyrics,
            flagDzenKaraoke = flagDzenKaraoke,
            flagDzenChords = flagDzenChords,
            flagDzenMelody = flagDzenMelody,
            flagPlayerDemo = flagPlayerDemo,
            flagVkLyrics = flagVkLyrics,
            flagVkKaraoke = flagVkKaraoke,
            flagVkChords = flagVkChords,
            flagVkMelody = flagVkMelody,
            flagTelegramLyrics = flagTelegramLyrics,
            flagTelegramKaraoke = flagTelegramKaraoke,
            flagTelegramChords = flagTelegramChords,
            flagTelegramMelody = flagTelegramMelody,
            flagPlLyrics = flagPlLyrics,
            flagPlKaraoke = flagPlKaraoke,
            flagPlChords = flagPlChords,
            flagPlMelody = flagPlMelody,
            flagMaxLyrics = flagMaxLyrics,
            flagMaxKaraoke = flagMaxKaraoke,
            flagMaxChords = flagMaxChords,
            flagMaxMelody = flagMaxMelody,
            flagExclusive = flagExclusive,
            flagFree = flagFree,
            processColorBoosty = processColorBoosty,
            processColorSponsr = processColorSponsr,
            processColorVk = processColorVk,
            processColorMeltLyrics = processColorMeltLyrics,
            processColorMeltKaraoke = processColorMeltKaraoke,
            processColorMeltChords = processColorMeltChords,
            processColorMeltMelody = processColorMeltMelody,
            processColorPlayerDemo = processColorPlayerDemo,
            processColorDzenLyrics = processColorDzenLyrics,
            processColorDzenKaraoke = processColorDzenKaraoke,
            processColorDzenChords = processColorDzenChords,
            processColorDzenMelody = processColorDzenMelody,
            processColorVkLyrics = processColorVkLyrics,
            processColorVkKaraoke = processColorVkKaraoke,
            processColorVkChords = processColorVkChords,
            processColorVkMelody = processColorVkMelody,
            processColorTelegramLyrics = processColorTelegramLyrics,
            processColorTelegramKaraoke = processColorTelegramKaraoke,
            processColorTelegramChords = processColorTelegramChords,
            processColorTelegramMelody = processColorTelegramMelody,
            processColorPlLyrics = processColorPlLyrics,
            processColorPlKaraoke = processColorPlKaraoke,
            processColorPlChords = processColorPlChords,
            processColorPlMelody = processColorPlMelody,
            processColorMaxLyrics = processColorMaxLyrics,
            processColorMaxKaraoke = processColorMaxKaraoke,
            processColorMaxChords = processColorMaxChords,
            processColorMaxMelody = processColorMaxMelody,
            resultVersion = resultVersion,
            versionBoosty = versionBoosty,
            versionBoostyFiles = versionBoostyFiles,
            versionSponsr = versionSponsr,
            versionDzenKaraoke = versionDzenKaraoke,
            versionDzenLyrics = versionDzenLyrics,
            versionDzenChords = versionDzenChords,
            versionDzenMelody = versionDzenMelody,
            versionVkKaraoke = versionVkKaraoke,
            versionVkLyrics = versionVkLyrics,
            versionVkChords = versionVkChords,
            versionVkMelody = versionVkMelody,
            versionTelegramKaraoke = versionTelegramKaraoke,
            versionTelegramLyrics = versionTelegramLyrics,
            versionTelegramChords = versionTelegramChords,
            versionTelegramMelody = versionTelegramMelody,
            versionPlKaraoke = versionPlKaraoke,
            versionPlLyrics = versionPlLyrics,
            versionPlChords = versionPlChords,
            versionPlMelody = versionPlMelody,
            versionMaxKaraoke = versionMaxKaraoke,
            versionMaxLyrics = versionMaxLyrics,
            versionMaxChords = versionMaxChords,
            versionMaxMelody = versionMaxMelody,
            rate = rate,
            healthReportText = healthReportText,
            healthReportColor = healthReportColor,
            healthReportList = healthReportList,
            formattedTextSong = formattedTextSong,
            formattedTextTabs = formattedTextTabs,
            formattedTextChords = formattedTextChords,
            rootId = rootId,
            exclusive = exclusive,
            free = free,
            songType = songType,
            haveSourceText = haveSourceText,
            albumId = albumId,
            albumName = albumName,
        )
}
