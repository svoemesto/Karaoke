package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.model.SongField
import com.svoemesto.karaokeapp.model.Song
import com.svoemesto.karaokeapp.model.SongVersion

/**
 * Перечисление возможных значений для karaoke platform.
 *
 * @see archive/archive/docs/features/dual-db-sync.md
 */
enum class KaraokePlatform(
    val id: Long,
    @Suppress("unused") val caption: String,
    val description: String,
    val forAllVersions: Boolean,
    val haveVersionNumber: Boolean,
    val suffix: String,
    val prefixPlay: String,
    val suffixPlay: String = "",
    val prefixEdit: String = "",
    val suffixEdit: String = "",
    val linkToCreate: String = "",
    @Suppress("unused") val svg: String,
    val settingsFieldPublicationId: Map<String, SongField>,
    val settingsFieldVersionNumber: Map<String, SongField>,
    val onAirPublications: Boolean,
) {
    SPONSR(
        id = 1,
        caption = "Sponsr",
        description = "Sponsr",
        suffix = " [sponsr]",
        forAllVersions = true,
        haveVersionNumber = true,
        svg = SVG["icon_sponsr"] ?: "",
        prefixPlay = "https://sponsr.ru/smkaraoke/",
        prefixEdit = "https://sponsr.ru/smkaraoke/manage/post/",
        linkToCreate = "https://sponsr.ru/smkaraoke/manage/post/new/",
        settingsFieldPublicationId = mapOf("ALL" to SongField.ID_SPONSR),
        settingsFieldVersionNumber = mapOf("ALL" to SongField.VERSION_SPONSR),
        onAirPublications = false,
    ),
    VKGROUP(
        id = 2,
        caption = "VK Group",
        description = "VK Group",
        suffix = " [VKlink]",
        forAllVersions = true,
        haveVersionNumber = false,
        svg = SVG["icon_vk2"] ?: "",
        prefixPlay = "https://vk.ru/wall-",
        settingsFieldPublicationId = mapOf("ALL" to SongField.ID_VK),
        settingsFieldVersionNumber = emptyMap(),
        onAirPublications = true,
    ),
    DZEN(
        id = 3,
        caption = "Dzen",
        description = "Dzen",
        suffix = "",
        forAllVersions = false,
        haveVersionNumber = true,
        svg = SVG["icon_dzen"] ?: "",
        prefixPlay = "https://dzen.ru/video/watch/",
        prefixEdit = "https://dzen.ru/profile/editor/svoemesto/publications?videoEditorPublicationId=",
        settingsFieldPublicationId =
            mapOf(
                SongVersion.KARAOKE.name to SongField.ID_DZEN_KARAOKE,
                SongVersion.LYRICS.name to SongField.ID_DZEN_LYRICS,
                SongVersion.CHORDS.name to SongField.ID_DZEN_CHORDS,
                SongVersion.TABS.name to SongField.ID_DZEN_MELODY,
            ),
        settingsFieldVersionNumber =
            mapOf(
                SongVersion.KARAOKE.name to SongField.VERSION_DZEN_KARAOKE,
                SongVersion.LYRICS.name to SongField.VERSION_DZEN_LYRICS,
                SongVersion.CHORDS.name to SongField.VERSION_DZEN_CHORDS,
                SongVersion.TABS.name to SongField.VERSION_DZEN_MELODY,
            ),
        onAirPublications = true,
    ),
    VKVIDEO(
        id = 4,
        caption = "VK Video",
        description = "VK Video",
        suffix = "",
        forAllVersions = false,
        haveVersionNumber = true,
        svg = SVG["icon_vk"] ?: "",
        prefixPlay = "https://vkvideo.ru/video",
        settingsFieldPublicationId =
            mapOf(
                SongVersion.KARAOKE.name to SongField.ID_VK_KARAOKE,
                SongVersion.LYRICS.name to SongField.ID_VK_LYRICS,
                SongVersion.CHORDS.name to SongField.ID_VK_CHORDS,
                SongVersion.TABS.name to SongField.ID_VK_MELODY,
            ),
        settingsFieldVersionNumber =
            mapOf(
                SongVersion.KARAOKE.name to SongField.VERSION_VK_KARAOKE,
                SongVersion.LYRICS.name to SongField.VERSION_VK_LYRICS,
                SongVersion.CHORDS.name to SongField.VERSION_VK_CHORDS,
                SongVersion.TABS.name to SongField.VERSION_VK_MELODY,
            ),
        onAirPublications = true,
    ),
    PLATFORMA(
        id = 5,
        caption = "Platforma",
        description = "Platforma",
        suffix = "",
        forAllVersions = false,
        haveVersionNumber = true,
        svg = SVG["icon_pl"] ?: "",
        prefixPlay = "https://plvideo.ru/watch?v=",
        prefixEdit = "https://studio.plvideo.ru/channel/bbj0HWC8H7ii/video/",
        suffixEdit = "/edit",
        settingsFieldPublicationId =
            mapOf(
                SongVersion.KARAOKE.name to SongField.ID_PL_KARAOKE,
                SongVersion.LYRICS.name to SongField.ID_PL_LYRICS,
                SongVersion.CHORDS.name to SongField.ID_PL_CHORDS,
                SongVersion.TABS.name to SongField.ID_PL_MELODY,
            ),
        settingsFieldVersionNumber =
            mapOf(
                SongVersion.KARAOKE.name to SongField.VERSION_PL_KARAOKE,
                SongVersion.LYRICS.name to SongField.VERSION_PL_LYRICS,
                SongVersion.CHORDS.name to SongField.VERSION_PL_CHORDS,
                SongVersion.TABS.name to SongField.VERSION_PL_MELODY,
            ),
        onAirPublications = true,
    ),
    TELEGRAM(
        id = 6,
        caption = "Telegram",
        description = "Telegram",
        suffix = "",
        forAllVersions = false,
        haveVersionNumber = true,
        svg = SVG["icon_telegram"] ?: "",
        prefixPlay = "https://t.me/svoemestokaraoke/",
        settingsFieldPublicationId =
            mapOf(
                SongVersion.KARAOKE.name to SongField.ID_TELEGRAM_KARAOKE,
                SongVersion.LYRICS.name to SongField.ID_TELEGRAM_LYRICS,
                SongVersion.CHORDS.name to SongField.ID_TELEGRAM_CHORDS,
                SongVersion.TABS.name to SongField.ID_TELEGRAM_MELODY,
            ),
        settingsFieldVersionNumber =
            mapOf(
                SongVersion.KARAOKE.name to SongField.VERSION_TELEGRAM_KARAOKE,
                SongVersion.LYRICS.name to SongField.VERSION_TELEGRAM_LYRICS,
                SongVersion.CHORDS.name to SongField.VERSION_TELEGRAM_CHORDS,
                SongVersion.TABS.name to SongField.VERSION_TELEGRAM_MELODY,
            ),
        onAirPublications = true,
    ),
    MAX(
        id = 7,
        caption = "Max",
        description = "Max",
        suffix = "",
        forAllVersions = false,
        haveVersionNumber = true,
        svg = SVG["icon_max"] ?: "",
        prefixPlay = "https://max.ru/c/-70935843913828/",
        settingsFieldPublicationId =
            mapOf(
                SongVersion.KARAOKE.name to SongField.ID_MAX_KARAOKE,
                SongVersion.LYRICS.name to SongField.ID_MAX_LYRICS,
                SongVersion.CHORDS.name to SongField.ID_MAX_CHORDS,
                SongVersion.TABS.name to SongField.ID_MAX_MELODY,
            ),
        settingsFieldVersionNumber =
            mapOf(
                SongVersion.KARAOKE.name to SongField.VERSION_MAX_KARAOKE,
                SongVersion.LYRICS.name to SongField.VERSION_MAX_LYRICS,
                SongVersion.CHORDS.name to SongField.VERSION_MAX_CHORDS,
                SongVersion.TABS.name to SongField.VERSION_MAX_MELODY,
            ),
        onAirPublications = true,
    ),
    ;

    fun actionToCreatePicture(
        song: Song,
        pathToFile: String,
    ) {
        when (this) {
            SPONSR -> {
                createSponsrTeaserPicture(song = song, fileName = pathToFile)
            }

            VKGROUP -> {
                createVKLinkPicture(song = song, fileName = pathToFile)
            }

            DZEN,
            VKVIDEO,
            PLATFORMA,
            TELEGRAM,
            MAX,
            -> {}
        }
    }
}
