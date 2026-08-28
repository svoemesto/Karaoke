package com.svoemesto.karaokeweb.dto

import com.svoemesto.karaokeapp.model.Song
import com.svoemesto.karaokeapp.model.SongAssignmentBriefDto

/**
 * DTO для song public: сериализуемое представление для API/UI.
 *
 * @see AGENTS.md
 */
data class SongPublicDto(
    val id: Long,
    val songName: String,
    val author: String,
    // Числовой ID автора из tbl_authors (FK по имени song.author). Заполняется в
    // PublicApiController.song() через Author.loadIdsByNames([author], db) — нужно SongView для
    // динамического back-link: при открытии /song?id=X шапка ведёт на /zakroma/<authorId>
    // (страница песен этого автора), см. specs/259-playlist-clickable-links.
    // null если автор удалён из tbl_authors (запись была, но name больше не матчится).
    val authorId: Long? = null,
    val authorAlias: String = "",
    val album: String,
    val year: Long,
    val track: Long,
    val key: String,
    val bpm: Long,
    val onAir: Boolean,
    val datePublish: String,
    val airTimestamp: Long?,
    // specs/143-song-free-access-window
    val alwaysFree: Boolean,
    val freelyAvailableNow: Boolean,
    val freeAccessWindowEndText: String?,
    val songPictureUrl: String,
    val formattedTextSong: String,
    val formattedTextTabs: String,
    val formattedTextChords: String,
    val description: String = "",
    val shortDescription: String = "",
    val warning: String = "",
    val idVkKaraoke: String,
    val idVkKaraokeOID: String,
    val idVkKaraokeID: String,
    val idVkLyrics: String,
    val idVkLyricsOID: String,
    val idVkLyricsID: String,
    val idVkMelody: String,
    val idVkMelodyOID: String,
    val idVkMelodyID: String,
    val idVkChords: String,
    val idVkChordsOID: String,
    val idVkChordsID: String,
    val contentRemoved: Boolean,
    // Доступна ли отдельная подписка на эту песню (id_tariff: 0 по умолчанию = разрешено тарифом
    // по умолчанию; -1 = автор запретил в карточке песни, webvue3). Без is-префикса — иначе Jackson
    // съел бы его в JSON-ключе (инвариант проекта).
    val songSubscriptionAvailable: Boolean,
    // Статус пайплайна песни (0..7). Нужен фронту, чтобы прятать self-assign-кнопку
    // для готовых песен (idStatus >= 6 — финальная, задание на разметку неуместно).
    // Без is-префикса: Kotlin-геттер уже без него, Jackson-ключ и так = idStatus.
    val idStatus: Long,
    // Spec 261 (FR-001..FR-002): зеркало флага `Song.isContentReady` — Pass 239, иконка плеера
    // в результатах поиска получает фактический статус доступа (зелёная/золотая/серая), а не
    // всегда-серую как раньше (bug: DTO не содержал это поле, иконка трактовала результаты как
    // «не готово»). Источник истины — `Song.isContentReady` (см. PublicPlayerController.stemsReady).
    // Без is-префикса — инвариант Jackson проекта.
    val contentReady: Boolean = false,
    // Spec 261 (FR-006, Clarification Q2 → A): URL превью обложки альбома в MinIO.
    // Шаблон ключа: `${author}/${year} - ${album}/${author} - ${year} - ${album}.preview.album.png`
    // (см. PublicPlaylistController.albumPreviewUrl). `""` если автора/альбома/year-нет или
    // превью не залито — фронт показывает плейсхолдер «♪».
    val albumPictureUrl: String = "",
    // Spec 261 (FR-006, Clarification Q2 → A): URL превью автора в MinIO.
    // Шаблон ключа: `${author}/${author}.preview.author.png`
    // (см. PublicPlaylistController.authorPreviewUrl). `""` если автора нет или превью
    // не залито — фронт показывает плейсхолдер «👤».
    val authorPictureUrl: String = "",
    // Self-assign (FR-008, specs/182-editor-self-assign-tasks): null = песня свободна,
    // non-null = есть назначение (своё или чужое). Заполняется ТОЛЬКО для self-assign-редакторов
    // в /api/public/song/{id} — для остальных всегда null (лишний JOIN/SQL не идёт).
    val assignment: SongAssignmentBriefDto? = null,
) {
    companion object {
        /**
         * includeDetails=false пропускает getVKPictureBase64()/formattedText* — это тяжёлые поля,
         * нужные только на странице одной песни. Список/поиск их не показывали и в старых шаблонах
         * (filter.html/zakroma.html), а getVKPictureBase64() может полезть в rootFolder-фоллбек
         * karaoke-app (Constants.PROJECT_ROOT_FOLDERS), который требует APP_WORK_ON_SERVER —
         * а её в процессе karaoke-web никто не инициализирует (KaraokeAppService там не поднимается).
         */
        fun fromSong(
            s: Song,
            includeDetails: Boolean = true,
            // Spec 261: URL превью из контроллера (там же делается batch-lookup по карте picture-ов).
            // Defaults "" — для call-site'ов, которые пока эти URL'ы не передают (например,
            // /api/public/song/{id} через includeDetails=false на странице конкретной песни).
            albumPictureUrl: String = "",
            authorPictureUrl: String = "",
        ): SongPublicDto =
            SongPublicDto(
                id = s.id,
                songName = s.songName,
                author = s.author,
                album = s.album,
                year = s.year,
                track = s.track,
                key = s.key,
                bpm = s.bpm,
                onAir = s.onAir,
                datePublish = s.datePublish,
                airTimestamp = s.dateTimePublish?.time,
                alwaysFree = s.free,
                freelyAvailableNow = s.isFreelyAvailableNow,
                freeAccessWindowEndText = s.freeAccessWindowEndText,
                songPictureUrl = "/api/public/song-picture/${s.id}",
                formattedTextSong = if (includeDetails) s.formattedTextSong else "",
                formattedTextTabs = if (includeDetails) s.formattedTextTabs else "",
                formattedTextChords = if (includeDetails) s.formattedTextChords else "",
                description = if (includeDetails) s.description else "",
                shortDescription = if (includeDetails) s.shortDescription else "",
                warning = if (includeDetails) s.warning else "",
                idVkKaraoke = s.idVkKaraoke,
                idVkKaraokeOID = s.idVkKaraokeOID,
                idVkKaraokeID = s.idVkKaraokeID,
                idVkLyrics = s.idVkLyrics,
                idVkLyricsOID = s.idVkLyricsOID,
                idVkLyricsID = s.idVkLyricsID,
                idVkMelody = s.idVkMelody,
                idVkMelodyOID = s.idVkMelodyOID,
                idVkMelodyID = s.idVkMelodyID,
                idVkChords = s.idVkChords,
                idVkChordsOID = s.idVkChordsOID,
                idVkChordsID = s.idVkChordsID,
                contentRemoved =
                    s.tags
                        .split(" ")
                        .map { it.uppercase() }
                        .contains("SKIP"),
                songSubscriptionAvailable = s.idTariff >= 0,
                idStatus = s.idStatus,
                // Spec 261 (FR-001): зеркало Song.isContentReady для иконки плеера в поиске.
                contentReady = s.isContentReady,
                // Spec 261 (FR-006): URL превью из контроллера / по умолчанию "".
                albumPictureUrl = albumPictureUrl,
                authorPictureUrl = authorPictureUrl,
            )
    }
}
