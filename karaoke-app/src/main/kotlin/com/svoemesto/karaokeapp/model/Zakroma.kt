package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.censored
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import java.io.Serializable

/**
 * Класс Zakroma.
 *
 * @see archive/docs/features/dual-db-sync.md
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
         * @see archive/docs/features/special-orders.md
         */
        fun getZakroma(
            author: String,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
            onlyPublished: Boolean = false,
            // specs/293-skip-author-toggle: если true — пользователь имеет право видеть SKIP-песни
            // (тег `SKIP` в `tbl_songs.tags`); если false — фильтруем их на уровне Kotlin
            // (SQL-фильтр уже применяется на уровне `tbl_authors.skip` через `withSkiped` в
            // вызывающем контроллере, см. MainController/PublicApiController).
            canSeeSkipped: Boolean = false,
        ): List<Zakroma> {
            val args = mutableMapOf("author" to author)
            if (onlyPublished) args["id_status"] = ">=6"
            val loaded =
                Song.loadListFromDb(
                    args = args,
                    database = database,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                    withoutMarkersAndText = true,
                )
            val songList = if (canSeeSkipped) loaded else loaded.filterNot(::songHasSkipTag)
            return buildFromSongs(songList, database, storageService, storageApiClient)
        }

        /**
         * Спецзаказные авторы («Отдельные песни разных авторов») одним SQL-запросом.
         *
         * Вместо N последовательных вызовов [getZakroma] на каждого спецзаказного автора
         * (N+1, см. историю бага в archive/docs/features/special-orders.md) грузит имена авторов
         * с `is_special_order=true` один раз, затем все их песни одним запросом через
         * уже существующий `author_in`-фильтр [Song.getWhereList].
         *
         * @param onlyPublished при true — только песни со статусом готовности >= 6, см.
         * [getZakroma].
         * @param canSeeSkipped — см. [getZakroma].
         * @see archive/docs/features/special-orders.md
         */
        fun getZakromaBySpecialOrder(
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
            onlyPublished: Boolean = false,
            canSeeSkipped: Boolean = false,
        ): List<Zakroma> {
            val names =
                Song.loadListAuthors(
                    withSkiped = canSeeSkipped,
                    isSpecialOrder = true,
                    database = database,
                )
            if (names.isEmpty()) return emptyList()
            val args = mutableMapOf("author_in" to names.joinToString(Song.AUTHOR_IN_DELIMITER))
            if (onlyPublished) args["id_status"] = ">=6"
            val loaded =
                Song.loadListFromDb(
                    args = args,
                    database = database,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                    withoutMarkersAndText = true,
                )
            val songList = if (canSeeSkipped) loaded else loaded.filterNot(::songHasSkipTag)
            return buildFromSongs(songList, database, storageService, storageApiClient)
        }

        // specs/293-skip-author-toggle: проверка тега `SKIP` в `tbl_songs.tags`. Та же логика,
        // что в ListeningHistoryController.songHasSkipTag и PublicOgSongController.isSkipped —
        // split по пробелам, case-insensitive. Скопирована сюда локально, чтобы не тянуть
        // зависимость между модулями (karaoke-app используется и в web, и в app).
        private fun songHasSkipTag(song: Song): Boolean =
            (song.tags ?: "").split(" ").any { it.trim().equals("SKIP", ignoreCase = true) }

        /**
         * Группирует плоский список песен в структуру Автор→Альбом→Песни для закромов.
         *
         * Ключ группировки альбома внутри автора — пара (год, название), а НЕ одно только
         * название: у автора могут быть два разных альбома с одинаковым названием, но разными
         * годами (переиздание и т.п.) — их идентичность в БД это тройка
         * автор+год+название (`tbl_albums_author_year_name_key`). Группировка по одному
         * названию схлопывала бы такие альбомы в одну карточку.
         *
         * **Pass 186 (specs/186-zakroma-songs-fast-load)**: переписана на batch lookup'ы —
         * [Pictures.getPicturesByNames] + [Album.getAlbumsByIds]. Раньше для каждого альбома
         * делались 3 отдельных SQL (`Pictures × 2` + `Album.getAlbumById × 1`), для крупного
         * автора с 30 альбомами это **93 SQL-запроса на одну загрузку страницы**. Теперь —
         * **5 SQL** (4 batch на pictures + 1 batch на albums), плюс `Author.getAuthorByName`
         * (1 SQL — вызывается ровно для одного автора при `Zakroma.getZakroma(author=…)`).
         * Подробности + замеры: [research.md R1](../../specs/186-zakroma-songs-fast-load/research.md).
         * @see archive/docs/features/special-orders.md
         * @see archive/docs/features/zakroma-stream-progress.md
         */
        private fun buildFromSongs(
            songList: List<Song>,
            database: KaraokeConnection,
            storageService: KaraokeStorageService,
            storageApiClient: StorageApiClient,
        ): List<Zakroma> {
            val songsByAuthor = songList.groupBy { it.author }

            // === Pass 186: предсбор данных для batch lookup'ов ===
            // 1) Все имена портретов авторов (для `picture` + `picturePreviewFileName`).
            val authorNames = songsByAuthor.keys.toList()
            // 2) Все имена обложек альбомов в формате `"$authorName - $year - $albumName"`.
            //    Дедуплицируем: один альбом может содержать много песен с одинаковым ключом.
            val albumPictureNames: List<String> =
                songsByAuthor
                    .flatMap { (authorName, songs) ->
                        songs.map { "$authorName - ${it.year} - ${it.album}" }
                    }.distinct()
            // 3) Все albumId песен (для батчевой загрузки реальных Album-сущностей).
            val albumIds: List<Long> =
                songsByAuthor.values
                    .flatMap { songs -> songs.mapNotNull { it.albumId } }
                    .distinct()

            // Batch 1: портреты авторов (`ignoreUseInList = true` для `picture.full`).
            val authorPicturesByName: Map<String, Pictures> =
                Pictures.getPicturesByNames(
                    names = authorNames,
                    database = database,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                    ignoreUseInList = true,
                )
            // Batch 2: те же портреты авторов, но `ignoreUseInList = false` для preview.
            // `getPicturesByNames` дедуплицирует `names`, так что повторный SQL по тем же
            // именам с другим флагом — необходимость, а не дубликат (preview и main имеют
            // разные значения `use_in_list`).
            val authorPreviewPicturesByName: Map<String, Pictures> =
                Pictures.getPicturesByNames(
                    names = authorNames,
                    database = database,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                    ignoreUseInList = false,
                )
            // Batch 3: обложки альбомов (`ignoreUseInList = true` для `picture.full`).
            val albumPicturesByName: Map<String, Pictures> =
                Pictures.getPicturesByNames(
                    names = albumPictureNames,
                    database = database,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                    ignoreUseInList = true,
                )
            // Batch 4: те же обложки альбомов для preview (`ignoreUseInList = false`).
            val albumPreviewPicturesByName: Map<String, Pictures> =
                Pictures.getPicturesByNames(
                    names = albumPictureNames,
                    database = database,
                    storageService = storageService,
                    storageApiClient = storageApiClient,
                    ignoreUseInList = false,
                )
            // Batch 5: реальные Album-сущности по всем albumId песен.
            val albumsById: Map<Long, Album> =
                if (albumIds.isEmpty()) {
                    emptyMap()
                } else {
                    Album.getAlbumsByIds(
                        ids = albumIds,
                        database = database,
                        storageService = storageService,
                        storageApiClient = storageApiClient,
                    )
                }

            // specs/277-song-name-censored: цензурированные названия читаются из БД-колонки
            // `tbl_songs.song_name_censored` через `song.songNameCensored` — никаких inline-вызовов
            // `songName.censored(database)` и загрузки `CensoredWordsDictionary` на странице закро́ма
            // (Pass 239 hotfix 2026-08-26 был вынужденным workaround для per-row цензурирования,
            // сейчас же колонка уже предвычислена — миграция 42 + CustomFunction реckan, и
            // 0 SQL-запросов к tbl_dictionaries на загрузку страницы).
            //
            // Fallback на inline-censoring оставлен ТОЛЬКО на случай рассинхрона (старая песня
            // с непустым songName но пустым songNameCensored — крайне редкий случай после
            // backfill миграции 42) — иначе пользователь увидел бы raw-нецензурированное название.

            return songsByAuthor.map { (authorName, songsByAuthor) ->
                val zakroma = Zakroma(database)
                zakroma.author = authorName
                // Портрет автора (full) — O(1) lookup из batch-карты.
                zakroma.picture = authorPicturesByName[authorName]?.full ?: ""
                // Портрет автора (preview) — O(1) lookup из второй batch-карты.
                zakroma.picturePreviewFileName =
                    authorPreviewPicturesByName[authorName]?.storageFileNamePreview ?: ""
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
                        // specs/293-skip-author-toggle: прокидываем флаг tbl_authors.skip
                        // для бейджа «SKIP» в UI karaoke-public.
                        zakroma.authorSkip = linkedAuthor.skip
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
                            val pictureName = "$authorName - ${album.year} - $albumName"
                            album.picture = albumPicturesByName[pictureName]?.full ?: ""
                            album.picturePreviewFileName =
                                albumPreviewPicturesByName[pictureName]?.storageFileNamePreview ?: ""
                            // specs/011-album-song-rename FR-007: если песни этого альбома уже
                            // привязаны к реальному Album (бэкфилл/ручная привязка), берём его
                            // albumType/sortOrder — иначе остаются дефолты (сортировка по алфавиту,
                            // как и было раньше для ещё не забэкфилленных данных).
                            // specs/012-entity-description-fields FR-017: дополнительно берём
                            // description/shortDescription/warning и КАНОНИЧЕСКОЕ название альбома
                            // из сущности Album (не из свободнотекстовой группировки по песням).
                            // Pass 186: O(1) lookup из batch-карты `albumsById` вместо SQL.
                            songsByAlbum
                                .firstOrNull { it.albumId != null }
                                ?.albumId
                                ?.let { linkedAlbumId ->
                                    albumsById[linkedAlbumId]?.let { linkedAlbum ->
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
                                        // Pass 239: иконка плеера без per-row readiness — используем
                                        // строгую проверку через persistent-флаги Pass 100
                                        // (см. Song.isContentReady). На проде все песни имеют
                                        // проставленные флаги (idStatus>=6 И стемы И картинки И
                                        // маркеры), бэкенд backfill'ит их автоматически при заливке
                                        // файла и через HealthReport.recalculatePlayerReadiness.
                                        zakromaAlbumSong.idStatus = song.idStatus
                                        zakromaAlbumSong.contentReady = song.isContentReady
                                        // specs/293-skip-author-toggle: прокидываем флаг тега SKIP
                                        // в tbl_songs.tags для бейджа «SKIP» в UI karaoke-public.
                                        zakromaAlbumSong.contentRemoved = songHasSkipTag(song)
                                        zakromaAlbumSong.track = song.track
                                        // specs/277-song-name-censored: предвычисленное значение из
                                        // tbl_songs.song_name_censored (см. comment выше про Pass 239 +
                                        // миграцию 42). Fallback на inline-censoring — defensive,
                                        // на случай старой записи без backfill колонки.
                                        zakromaAlbumSong.songName =
                                            if (song.songNameCensored.isNotEmpty() || song.songName.isEmpty()) {
                                                song.songNameCensored
                                            } else {
                                                song.songName.censored(database)
                                            }
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

    // specs/293-skip-author-toggle: флаг `tbl_authors.skip = TRUE` копируется из сущности Author
    // в buildFromSongs(). Используется UI karaoke-public для условного рендера бейджа «SKIP»
    // рядом с именем автора (только для пользователей с canWorkWithSkipped=true).
    var authorSkip: Boolean = false

    override fun compareTo(other: Zakroma): Int = author.compareTo(other.author)
}

/**
 * Класс Zakroma Album Song.
 *
 * @see archive/docs/features/dual-db-sync.md
 */
class ZakromaAlbumSong :
    Serializable,
    Comparable<ZakromaAlbumSong> {
    var id: Long = 0
    var track: Long = 0
    var songName: String = ""
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

    // Pass 239 (specs/239-zakroma-author-songs-batch-render): проброс статуса и готовности контента
    // в NDJSON/DTO — фронт иконку плеера рисует без per-row readiness-запроса.
    var idStatus: Long = 0L
    var contentReady: Boolean = false

    // specs/293-skip-author-toggle: флаг «контент удалён по требованию правообладателя» (тег SKIP
    // в `tbl_songs.tags`). Используется UI karaoke-public для условного рендера бейджа «SKIP»
    // в карточке песни (только для пользователей с canWorkWithSkipped=true).
    var contentRemoved: Boolean = false

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
 * @see archive/docs/features/dual-db-sync.md
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
