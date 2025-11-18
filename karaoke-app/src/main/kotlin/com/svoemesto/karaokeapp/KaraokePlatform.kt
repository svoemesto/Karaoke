package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.model.SettingField
import com.svoemesto.karaokeapp.model.Settings
import com.svoemesto.karaokeapp.model.SongVersion

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
    val settingsFieldPublicationId: Map<String, SettingField>,
    val settingsFieldVersionNumber: Map<String, SettingField>,
    val onAirPublications: Boolean
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
        settingsFieldPublicationId = mapOf("ALL" to SettingField.ID_SPONSR),
        settingsFieldVersionNumber = mapOf("ALL" to SettingField.VERSION_SPONSR),
        onAirPublications = false
    ),
    VKGROUP(
        id = 2,
        caption = "VK Group",
        description = "VK Group",
        suffix = " [vkgroup]",
        forAllVersions = true,
        haveVersionNumber = false,
        svg = SVG["icon_vk2"] ?: "",
        prefixPlay = "https://vk.com/wall-",
        settingsFieldPublicationId = mapOf("ALL" to SettingField.ID_VK),
        settingsFieldVersionNumber = emptyMap(),
        onAirPublications = true
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
        settingsFieldPublicationId = mapOf(
            SongVersion.KARAOKE.name to SettingField.ID_DZEN_KARAOKE,
            SongVersion.LYRICS.name to SettingField.ID_DZEN_LYRICS,
            SongVersion.CHORDS.name to SettingField.ID_DZEN_CHORDS,
            SongVersion.TABS.name to SettingField.ID_DZEN_MELODY,
        ),
        settingsFieldVersionNumber = mapOf(
            SongVersion.KARAOKE.name to SettingField.VERSION_DZEN_KARAOKE,
            SongVersion.LYRICS.name to SettingField.VERSION_DZEN_LYRICS,
            SongVersion.CHORDS.name to SettingField.VERSION_DZEN_CHORDS,
            SongVersion.TABS.name to SettingField.VERSION_DZEN_MELODY,
        ),
        onAirPublications = true
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
        settingsFieldPublicationId = mapOf(
            SongVersion.KARAOKE.name to SettingField.ID_VK_KARAOKE,
            SongVersion.LYRICS.name to SettingField.ID_VK_LYRICS,
            SongVersion.CHORDS.name to SettingField.ID_VK_CHORDS,
            SongVersion.TABS.name to SettingField.ID_VK_MELODY,
        ),
        settingsFieldVersionNumber = mapOf(
            SongVersion.KARAOKE.name to SettingField.VERSION_VK_KARAOKE,
            SongVersion.LYRICS.name to SettingField.VERSION_VK_LYRICS,
            SongVersion.CHORDS.name to SettingField.VERSION_VK_CHORDS,
            SongVersion.TABS.name to SettingField.VERSION_VK_MELODY,
        ),
        onAirPublications = true
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
        settingsFieldPublicationId = mapOf(
            SongVersion.KARAOKE.name to SettingField.ID_PL_KARAOKE,
            SongVersion.LYRICS.name to SettingField.ID_PL_LYRICS,
            SongVersion.CHORDS.name to SettingField.ID_PL_CHORDS,
            SongVersion.TABS.name to SettingField.ID_PL_MELODY,
        ),
        settingsFieldVersionNumber = mapOf(
            SongVersion.KARAOKE.name to SettingField.VERSION_PL_KARAOKE,
            SongVersion.LYRICS.name to SettingField.VERSION_PL_LYRICS,
            SongVersion.CHORDS.name to SettingField.VERSION_PL_CHORDS,
            SongVersion.TABS.name to SettingField.VERSION_PL_MELODY,
        ),
        onAirPublications = true
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
        settingsFieldPublicationId = mapOf(
            SongVersion.KARAOKE.name to SettingField.ID_TELEGRAM_KARAOKE,
            SongVersion.LYRICS.name to SettingField.ID_TELEGRAM_LYRICS,
            SongVersion.CHORDS.name to SettingField.ID_TELEGRAM_CHORDS,
            SongVersion.TABS.name to SettingField.ID_TELEGRAM_MELODY,
        ),
        settingsFieldVersionNumber = mapOf(
            SongVersion.KARAOKE.name to SettingField.VERSION_TELEGRAM_KARAOKE,
            SongVersion.LYRICS.name to SettingField.VERSION_TELEGRAM_LYRICS,
            SongVersion.CHORDS.name to SettingField.VERSION_TELEGRAM_CHORDS,
            SongVersion.TABS.name to SettingField.VERSION_TELEGRAM_MELODY,
        ),
        onAirPublications = true
    );

    fun actionToCreatePicture(settings: Settings, pathToFile: String) {
        when (this) {
            SPONSR -> { createSponsrTeaserPicture(settings = settings, fileName = pathToFile) }

            VKGROUP -> { createVKLinkPicture(settings = settings, fileName = pathToFile) }

            DZEN,
            VKVIDEO,
            PLATFORMA,
            TELEGRAM -> {}
        }
    }
}