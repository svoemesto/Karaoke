package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.censored
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import java.io.Serializable

/**
 * Класс Zakroma.
 *
 * @see docs/features/dual-db-sync.md
 */
class Zakroma(
    val database: KaraokeConnection,
) : Serializable,
    Comparable<Zakroma> {
    companion object {
        /**
         * Закрома автора: все его песни, сгруппированные по альбомам.
         *
         * @param onlyPublished при true — только песни со статусом готовности >= 3 (то же
         * определение «песня в коллекции», что уже используется для публичных счётчиков в
         * [StatBySong]). Публичные (проде) call-site'ы обязаны передавать true; admin-путь
         * оставляет значение по умолчанию (false), чтобы редакторы видели все песни.
         * @see docs/features/special-orders.md
         */
        fun getZakroma(
            author: String,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
            onlyPublished: Boolean = false,
        ): List<Zakroma> {
            val args = mutableMapOf("author" to author)
            if (onlyPublished) args["id_status"] = ">=6"
            val songList =
                Song.loadListFromDb(
                    args = args,
                    database = database,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                    withoutMarkersAndText = true,
                )
            return buildFromSongs(songList, database, storageService, storageApiClient)
        }

        /**
         * Спецзаказные авторы («Отдельные песни разных авторов») одним SQL-запросом.
         *
         * Вместо N последовательных вызовов [getZakroma] на каждого спецзаказного автора
         * (N+1, см. историю бага в docs/features/special-orders.md) грузит имена авторов
         * с `is_special_order=true` один раз, затем все их песни одним запросом через
         * уже существующий `author_in`-фильтр [Song.getWhereList].
         *
         * @param onlyPublished при true — только песни со статусом готовности >= 6, см.
         * [getZakroma].
         * @see docs/features/special-orders.md
         */
        fun getZakromaBySpecialOrder(
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
            onlyPublished: Boolean = false,
        ): List<Zakroma> {
            val names =
                Song.loadListAuthors(
                    withSkiped = false,
                    isSpecialOrder = true,
                    database = database,
                )
            if (names.isEmpty()) return emptyList()
            val args = mutableMapOf("author_in" to names.joinToString(Song.AUTHOR_IN_DELIMITER))
            if (onlyPublished) args["id_status"] = ">=6"
            val songList =
                Song.loadListFromDb(
                    args = args,
                    database = database,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                    withoutMarkersAndText = true,
                )
            return buildFromSongs(songList, database, storageService, storageApiClient)
        }

        /**
         * Группирует плоский список песен в структуру Автор→Альбом→Песни для закромов.
         *
         * Ключ группировки альбома внутри автора — пара (год, название), а НЕ одно только
         * название: у автора могут быть два разных альбома с одинаковым названием, но разными
         * годами (переиздание и т.п.) — их идентичность в БД это тройка
         * автор+год+название (`tbl_albums_author_year_name_key`). Группировка по одному
         * названию схлопывала бы такие альбомы в одну карточку.
         * @see docs/features/special-orders.md
         */
        private fun buildFromSongs(
            songList: List<Song>,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): List<Zakroma> {
            val songsByAuthor = songList.groupBy { it.author }
            return songsByAuthor.map { (authorName, songsByAuthor) ->
                val zakroma = Zakroma(database)
                zakroma.author = authorName
                zakroma.picture = Pictures
                    .getPictureByName(
                        name = authorName,
                        database = database,
                        storageService = storageService,
                        storageApiClient = storageApiClient,
                    )?.full ?: ""
                val picForAuthorPreview =
                    Pictures.getPictureByName(
                        name = authorName,
                        database = database,
                        storageService = storageService,
                        storageApiClient = storageApiClient,
                        ignoreUseInList = false,
                    )
                zakroma.picturePreviewFileName = picForAuthorPreview?.storageFileNamePreview ?: ""
                // specs/012-entity-description-fields FR-011/012/013: описание/короткое
                // описание/предупреждение автора — из сущности Author по имени (пусто, если
                // автор ещё не заведён как отдельная сущность, например спецзаказные).
                Author
                    .getAuthorByName(
                        author = authorName,
                        database = database,
                        storageService = storageService,
                        storageApiClient = storageApiClient,
                    )?.let { linkedAuthor ->
                        zakroma.authorDescription = linkedAuthor.description
                        zakroma.authorShortDescription = linkedAuthor.shortDescription
                        zakroma.authorWarning = linkedAuthor.warning
                    }
                // Ключ группировки — (год, название), а не только название: у автора могут быть
                // два РАЗНЫХ альбома с одинаковым названием, но разными годами (см. уникальный
                // констрейнт tbl_albums_author_year_name_key на Album — идентичность альбома это
                // тройка автор+год+название). Группировка по одному названию схлопывала такие
                // альбомы в одну карточку и «теряла» год/песни второго. specs/018-fix-album-name-year-grouping.
                val songsByAlbum = songsByAuthor.groupBy { it.year to it.album }
                zakroma.albums =
                    songsByAlbum
                        .map { (albumKey, songsByAlbum) ->
                            val (albumYear, albumName) = albumKey
                            val album = ZakromaAlbum()
                            album.albumName = albumName
                            album.year = albumYear
                            album.picture = Pictures
                                .getPictureByName(
                                    name = "$authorName - ${album.year} - $albumName",
                                    database = database,
                                    storageService = storageService,
                                    storageApiClient = storageApiClient,
                                )?.full ?: ""
                            val pictureName = "$authorName - ${album.year} - $albumName"
                            val picForPreview =
                                Pictures.getPictureByName(
                                    name = pictureName,
                                    database = database,
                                    storageService = storageService,
                                    storageApiClient = storageApiClient,
                                    ignoreUseInList = false,
                                )
                            album.picturePreviewFileName = picForPreview?.storageFileNamePreview ?: ""
                            // specs/011-album-song-rename FR-007: если песни этого альбома уже
                            // привязаны к реальному Album (бэкфилл/ручная привязка), берём его
                            // albumType/sortOrder — иначе остаются дефолты (сортировка по алфавиту,
                            // как и было раньше для ещё не забэкфилленных данных).
                            // specs/012-entity-description-fields FR-017: дополнительно берём
                            // description/shortDescription/warning и КАНОНИЧЕСКОЕ название альбома
                            // из сущности Album (не из свободнотекстовой группировки по песням).
                            songsByAlbum
                                .firstOrNull { it.albumId != null }
                                ?.albumId
                                ?.let { linkedAlbumId ->
                                    Album
                                        .getAlbumById(
                                            id = linkedAlbumId,
                                            database = database,
                                            storageService = storageService,
                                            storageApiClient = storageApiClient,
                                        )?.let { linkedAlbum ->
                                            album.albumName = linkedAlbum.name
                                            album.albumType = linkedAlbum.albumType
                                            album.sortOrder = linkedAlbum.sortOrder
                                            album.description = linkedAlbum.description
                                            album.shortDescription = linkedAlbum.shortDescription
                                            album.warning = linkedAlbum.warning
                                            album.albumTypeLabel = linkedAlbum.albumTypeEnum.description
                                        }
                                }
                            album.albumSongs =
                                songsByAlbum
                                    .map { song ->
                                        val zakromaAlbumSong = ZakromaAlbumSong()
                                        zakromaAlbumSong.id = song.id
                                        zakromaAlbumSong.onAir = song.onAir
                                        zakromaAlbumSong.datePublish = song.datePublish
                                        zakromaAlbumSong.airTimestamp = song.dateTimePublish?.time
                                        zakromaAlbumSong.songSubscriptionAvailable = song.idTariff >= 0
                                        zakromaAlbumSong.alwaysFree = song.free
                                        zakromaAlbumSong.freelyAvailableNow = song.isFreelyAvailableNow
                                        zakromaAlbumSong.freeAccessWindowEndText = song.freeAccessWindowEndText
                                        zakromaAlbumSong.track = song.track
                                        zakromaAlbumSong.songName = song.songName.censored(database)
                                        zakromaAlbumSong.linkBoosty = song.linkBoostyTxt
                                        zakromaAlbumSong.linkSponsrPlay = song.linkSponsrPlay
                                        zakromaAlbumSong.linkDzenKaraoke = song.linkDzenKaraoke
                                        zakromaAlbumSong.linkDzenLyrics = song.linkDzenLyrics
                                        zakromaAlbumSong.linkDzenTabs = song.linkDzenTabs
                                        zakromaAlbumSong.linkDzenChords = song.linkDzenChords
                                        zakromaAlbumSong.linkVkKaraoke = song.linkVkKaraoke
                                        zakromaAlbumSong.linkVkLyrics = song.linkVkLyrics
                                        zakromaAlbumSong.linkVkTabs = song.linkVkTabs
                                        zakromaAlbumSong.linkVkChords = song.linkVkChords
                                        zakromaAlbumSong.linkTgKaraoke = song.linkTgKaraoke
                                        zakromaAlbumSong.linkTgLyrics = song.linkTgLyrics
                                        zakromaAlbumSong.linkTgTabs = song.linkTgTabs
                                        zakromaAlbumSong.linkTgChords = song.linkTgChords
                                        zakromaAlbumSong.linkPlKaraoke = song.linkPlKaraoke
                                        zakromaAlbumSong.linkPlLyrics = song.linkPlLyrics
                                        zakromaAlbumSong.linkPlTabs = song.linkPlTabs
                                        zakromaAlbumSong.linkPlChords = song.linkPlChords
                                        zakromaAlbumSong.linkMaxKaraoke = song.linkMaxKaraoke
                                        zakromaAlbumSong.linkMaxLyrics = song.linkMaxLyrics
                                        zakromaAlbumSong.linkMaxTabs = song.linkMaxTabs
                                        zakromaAlbumSong.linkMaxChords = song.linkMaxChords
                                        zakromaAlbumSong
                                    }.sorted()
                                    .toMutableList()
                            album
                        }.sorted()
                        .toMutableList()
                zakroma
            }
        }
    }

    var author: String = ""
    var picture: String = ""
    var picturePreviewFileName: String = ""
    var albums: MutableList<ZakromaAlbum> = mutableListOf()

    // specs/012-entity-description-fields: описание/короткое описание/предупреждение автора,
    // копируются из сущности Author по имени в buildFromSongs() (пусто, если автор не найден).
    var authorDescription: String = ""
    var authorShortDescription: String = ""
    var authorWarning: String = ""

    override fun compareTo(other: Zakroma): Int = author.compareTo(other.author)
}

/**
 * Класс Zakroma Album Song.
 *
 * @see docs/features/dual-db-sync.md
 */
class ZakromaAlbumSong :
    Serializable,
    Comparable<ZakromaAlbumSong> {
    var id: Long = 0
    var track: Long = 0
    var songName: String = ""
    var linkBoosty: String = ""
    var linkSponsrPlay: String = ""
    var linkDzenKaraoke: String = ""
    var linkDzenLyrics: String = ""
    var linkDzenTabs: String = ""
    var linkDzenChords: String = ""
    var linkVkKaraoke: String = ""
    var linkVkLyrics: String = ""
    var linkVkTabs: String = ""
    var linkVkChords: String = ""
    var linkTgKaraoke: String = ""
    var linkTgLyrics: String = ""
    var linkTgTabs: String = ""
    var linkTgChords: String = ""
    var linkPlKaraoke: String = ""
    var linkPlLyrics: String = ""
    var linkPlTabs: String = ""
    var linkPlChords: String = ""
    var linkMaxKaraoke: String = ""
    var linkMaxLyrics: String = ""
    var linkMaxTabs: String = ""
    var linkMaxChords: String = ""
    var onAir: Boolean = false
    var datePublish: String = ""

    // Момент эфира в epoch millis — null, если дата эфира вообще не назначена (в отличие от
    // datePublish/"Дата пока не определена", которая тоже строка и для этого случая, и её нельзя
    // отличить от реальной даты программно без парсинга; specs/143-song-free-access-window,
    // симметрично SongPublicDto.airTimestamp). Используется фронтом, чтобы не показывать «Будет в
    // эфире с …» для песен без назначенной даты (FR-009 spec.md подразумевал только случай «дата
    // ещё не наступила», не «дата не назначена вовсе»).
    var airTimestamp: Long? = null
    var songSubscriptionAvailable: Boolean = false

    // specs/143-song-free-access-window: alwaysFree = флаг "всегда бесплатно" (song.free);
    // freelyAvailableNow = доступна бесплатно прямо сейчас (song.isFreelyAvailableNow);
    // freeAccessWindowEndText = отформатированный конец окна бесплатного доступа для "В эфире до …"
    // (null, если alwaysFree или эфир ещё не наступил).
    var alwaysFree: Boolean = false
    var freelyAvailableNow: Boolean = false
    var freeAccessWindowEndText: String? = null

    override fun compareTo(other: ZakromaAlbumSong): Int {
        val compTrack = track.compareTo(other.track)
        if (compTrack == 0) {
            return songName.compareTo(other.songName)
        }
        return compTrack
    }
}

/**
 * Класс Zakroma Album.
 *
 * @see docs/features/dual-db-sync.md
 */
class ZakromaAlbum :
    Serializable,
    Comparable<ZakromaAlbum> {
    var albumName: String = ""
    var year: Long = 0
    var picture: String = ""
    var picturePreviewFileName: String = ""
    var albumSongs: MutableList<ZakromaAlbumSong> = mutableListOf()

    // specs/011-album-song-rename FR-001/FR-007, дополнено сквозной сортировкой альбомов автора:
    // тип и заданный порядок отображения — сквозной по автору (не по году) — из реального Album,
    // если песни альбома уже к нему привязаны (см. buildFromSongs). Int.MAX_VALUE — сентинел
    // "не привязано", такие альбомы уходят в конец сортировки.
    var albumType: String = AlbumType.STUDIO.dbValue
    var sortOrder: Int = Int.MAX_VALUE

    // specs/012-entity-description-fields: описание/короткое описание/предупреждение альбома,
    // копируются из связанной сущности Album (если песни альбома уже к ней привязаны) в
    // buildFromSongs() — иначе остаются пустыми. albumTypeLabel — каноническая русская подпись
    // типа (AlbumType.description), единый источник правды вместо дублирующей RU-мапы на фронте.
    var description: String = ""
    var shortDescription: String = ""
    var warning: String = ""
    var albumTypeLabel: String = AlbumType.STUDIO.description

    override fun compareTo(other: ZakromaAlbum): Int {
        val compSortOrder = sortOrder.compareTo(other.sortOrder)
        if (compSortOrder != 0) return compSortOrder
        val compYear = year.compareTo(other.year)
        if (compYear != 0) return compYear
        return albumName.compareTo(other.albumName)
    }
}
