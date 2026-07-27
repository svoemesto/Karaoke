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
        fun getZakroma(
            author: String,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): List<Zakroma> {
            val listSettings =
                Song.loadListFromDb(
                    args = mapOf("author" to author),
                    database = database,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                    withoutMarkersAndText = true,
                )
            return buildFromSettings(listSettings, database, storageService, storageApiClient)
        }

        /**
         * Спецзаказные авторы («Отдельные песни разных авторов») одним SQL-запросом.
         *
         * Вместо N последовательных вызовов [getZakroma] на каждого спецзаказного автора
         * (N+1, см. историю бага в docs/features/special-orders.md) грузит имена авторов
         * с `is_special_order=true` один раз, затем все их песни одним запросом через
         * уже существующий `author_in`-фильтр [Song.getWhereList].
         *
         * @see docs/features/special-orders.md
         */
        fun getZakromaBySpecialOrder(
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): List<Zakroma> {
            val names =
                Song.loadListAuthors(
                    withSkiped = false,
                    isSpecialOrder = true,
                    database = database,
                )
            if (names.isEmpty()) return emptyList()
            val listSettings =
                Song.loadListFromDb(
                    args = mapOf("author_in" to names.joinToString(Song.AUTHOR_IN_DELIMITER)),
                    database = database,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                    withoutMarkersAndText = true,
                )
            return buildFromSettings(listSettings, database, storageService, storageApiClient)
        }

        private fun buildFromSettings(
            listSettings: List<Song>,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): List<Zakroma> {
            val settingsByAuthor = listSettings.groupBy { it.author }
            return settingsByAuthor.map { (authorName, settingsByAuthor) ->
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
                val settingsByAlbum = settingsByAuthor.groupBy { it.album }
                zakroma.albums =
                    settingsByAlbum
                        .map { (albumName, settingsByAlbum) ->
                            val album = ZakromaAlbum()
                            album.albumName = albumName
                            album.year = settingsByAlbum.first().year
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
                            settingsByAlbum
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
                            album.albumSettings =
                                settingsByAlbum
                                    .map { settings ->
                                        val zakromaAlbumSettings = ZakromaAlbumSettings()
                                        zakromaAlbumSettings.id = settings.id
                                        zakromaAlbumSettings.onAir = settings.onAir
                                        zakromaAlbumSettings.exclusive = settings.exclusive
                                        zakromaAlbumSettings.datePublish = settings.datePublish
                                        zakromaAlbumSettings.songSubscriptionAvailable = settings.idTariff >= 0
                                        zakromaAlbumSettings.track = settings.track
                                        zakromaAlbumSettings.songName = settings.songName.censored()
                                        zakromaAlbumSettings.linkBoosty = settings.linkBoostyTxt
                                        zakromaAlbumSettings.linkSponsrPlay = settings.linkSponsrPlay
                                        zakromaAlbumSettings.linkDzenKaraoke = settings.linkDzenKaraoke
                                        zakromaAlbumSettings.linkDzenLyrics = settings.linkDzenLyrics
                                        zakromaAlbumSettings.linkDzenTabs = settings.linkDzenTabs
                                        zakromaAlbumSettings.linkDzenChords = settings.linkDzenChords
                                        zakromaAlbumSettings.linkVkKaraoke = settings.linkVkKaraoke
                                        zakromaAlbumSettings.linkVkLyrics = settings.linkVkLyrics
                                        zakromaAlbumSettings.linkVkTabs = settings.linkVkTabs
                                        zakromaAlbumSettings.linkVkChords = settings.linkVkChords
                                        zakromaAlbumSettings.linkTgKaraoke = settings.linkTgKaraoke
                                        zakromaAlbumSettings.linkTgLyrics = settings.linkTgLyrics
                                        zakromaAlbumSettings.linkTgTabs = settings.linkTgTabs
                                        zakromaAlbumSettings.linkTgChords = settings.linkTgChords
                                        zakromaAlbumSettings.linkPlKaraoke = settings.linkPlKaraoke
                                        zakromaAlbumSettings.linkPlLyrics = settings.linkPlLyrics
                                        zakromaAlbumSettings.linkPlTabs = settings.linkPlTabs
                                        zakromaAlbumSettings.linkPlChords = settings.linkPlChords
                                        zakromaAlbumSettings.linkMaxKaraoke = settings.linkMaxKaraoke
                                        zakromaAlbumSettings.linkMaxLyrics = settings.linkMaxLyrics
                                        zakromaAlbumSettings.linkMaxTabs = settings.linkMaxTabs
                                        zakromaAlbumSettings.linkMaxChords = settings.linkMaxChords
                                        zakromaAlbumSettings
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
    // копируются из сущности Author по имени в buildFromSettings() (пусто, если автор не найден).
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
class ZakromaAlbumSettings :
    Serializable,
    Comparable<ZakromaAlbumSettings> {
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
    var exclusive: Boolean = false
    var datePublish: String = ""
    var songSubscriptionAvailable: Boolean = false

    override fun compareTo(other: ZakromaAlbumSettings): Int {
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
    var albumSettings: MutableList<ZakromaAlbumSettings> = mutableListOf()

    // specs/011-album-song-rename FR-001/FR-007, дополнено сквозной сортировкой альбомов автора:
    // тип и заданный порядок отображения — сквозной по автору (не по году) — из реального Album,
    // если песни альбома уже к нему привязаны (см. buildFromSettings). Int.MAX_VALUE — сентинел
    // "не привязано", такие альбомы уходят в конец сортировки.
    var albumType: String = AlbumType.STUDIO.dbValue
    var sortOrder: Int = Int.MAX_VALUE

    // specs/012-entity-description-fields: описание/короткое описание/предупреждение альбома,
    // копируются из связанной сущности Album (если песни альбома уже к ней привязаны) в
    // buildFromSettings() — иначе остаются пустыми. albumTypeLabel — каноническая русская подпись
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
