package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.model.SettingField
import com.svoemesto.karaokeapp.model.SongVersion
import java.io.Serializable

// Онлайн-платформы, на которых выкладывается контент.
data class KaraokePlatform(
    val id: Long,
    val name: String,
    val description: String,
    val forAllVersions: Boolean,
    val haveVersionNumber: Boolean,
    val prefixPlay: String,
    val suffixPlay: String = "",
    val prefixEdit: String = "",
    val suffixEdit: String = "",
    val linkToCreate: String = "",
    val svg: String,
    val settingsFieldPublicationId: Map<String, SettingField>,
    val settingsFieldVersionNumber: Map<String, SettingField>
)  : Serializable, Comparable<KaraokePlatform> {

    companion object {
        const val STRING_TO_REPLACE_ID = "{REPLACE_TO_ID_PUBLICATION}"
        fun getList(): List<KaraokePlatform> {
            val result: MutableList<KaraokePlatform> = mutableListOf()

            result.add(
                KaraokePlatform(
                    id = 1,
                    name = "Sponsr",
                    description = "Sponsr",
                    forAllVersions = true,
                    haveVersionNumber = true,
                    svg = SVG["icon_sponsr"] ?: "",
                    prefixPlay = "https://sponsr.ru/smkaraoke/",
                    prefixEdit = "https://sponsr.ru/smkaraoke/manage/post/",
                    linkToCreate = "https://sponsr.ru/smkaraoke/manage/post/new/",
                    settingsFieldPublicationId = mapOf("ALL" to SettingField.ID_SPONSR),
                    settingsFieldVersionNumber = mapOf("ALL" to SettingField.VERSION_SPONSR),
                )
            )

            result.add(
                KaraokePlatform(
                    id = 2,
                    name = "VK Group",
                    description = "VK Group",
                    forAllVersions = true,
                    haveVersionNumber = false,
                    svg = SVG["icon_vk2"] ?: "",
                    prefixPlay = "https://vk.com/wall-",
                    settingsFieldPublicationId = mapOf("ALL" to SettingField.ID_VK),
                    settingsFieldVersionNumber = emptyMap(),
                )
            )

            result.add(
                KaraokePlatform(
                    id = 3,
                    name = "Dzen",
                    description = "Dzen",
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
                    )
                )
            )

            result.add(
                KaraokePlatform(
                    id = 4,
                    name = "VK Video",
                    description = "VK Video",
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
                    )
                )
            )

            result.add(
                KaraokePlatform(
                    id = 5,
                    name = "Platforma",
                    description = "Platforma",
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
                    )
                )
            )

            result.add(
                KaraokePlatform(
                    id = 6,
                    name = "Telegram",
                    description = "Telegram",
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
                    )
                )
            )

            return result
        }
    }

    override fun compareTo(other: KaraokePlatform): Int {
        TODO("Not yet implemented")
    }

}