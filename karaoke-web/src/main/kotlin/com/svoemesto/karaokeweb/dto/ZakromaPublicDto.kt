package com.svoemesto.karaokeweb.dto

import com.svoemesto.karaokeapp.model.Zakroma
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class ZakromaAlbumSettingsPublicDto(
    val id: Long,
    val track: Long,
    val songName: String,
    val onAir: Boolean,
    val datePublish: String,
    val linkBoosty: String,
    val linkSponsrPlay: String,
    val linkDzenKaraoke: String,
    val linkDzenLyrics: String,
    val linkDzenTabs: String,
    val linkDzenChords: String,
    val linkVkKaraoke: String,
    val linkVkLyrics: String,
    val linkVkTabs: String,
    val linkVkChords: String,
    val linkTgKaraoke: String,
    val linkTgLyrics: String,
    val linkTgTabs: String,
    val linkTgChords: String,
    val linkPlKaraoke: String,
    val linkPlLyrics: String,
    val linkPlTabs: String,
    val linkPlChords: String,
    val linkMaxKaraoke: String,
    val linkMaxLyrics: String,
    val linkMaxTabs: String,
    val linkMaxChords: String,
)

data class ZakromaAlbumPublicDto(
    val albumName: String,
    val year: Long,
    val albumPictureUrl: String,
    val albumSettings: List<ZakromaAlbumSettingsPublicDto>,
)

data class ZakromaPublicDto(
    val author: String,
    val authorPictureUrl: String,
    val albums: List<ZakromaAlbumPublicDto>,
) {
    companion object {
        fun fromZakroma(list: List<Zakroma>): List<ZakromaPublicDto> = list.map { zak ->
            ZakromaPublicDto(
                author = zak.author,
                authorPictureUrl = if (zak.picturePreviewFileName.isNotEmpty())
                    "/api/public/picture?file=${URLEncoder.encode(zak.picturePreviewFileName, StandardCharsets.UTF_8)}"
                else "",
                albums = zak.albums.map { alb ->
                    ZakromaAlbumPublicDto(
                        albumName = alb.albumName,
                        year = alb.year,
                        albumPictureUrl = if (alb.picturePreviewFileName.isNotEmpty())
                            "/api/public/picture?file=${URLEncoder.encode(alb.picturePreviewFileName, StandardCharsets.UTF_8)}"
                        else "",
                        albumSettings = alb.albumSettings.map { s ->
                            ZakromaAlbumSettingsPublicDto(
                                id = s.id,
                                track = s.track,
                                songName = s.songName,
                                onAir = s.onAir,
                                datePublish = s.datePublish,
                                linkBoosty = s.linkBoosty,
                                linkSponsrPlay = s.linkSponsrPlay,
                                linkDzenKaraoke = s.linkDzenKaraoke,
                                linkDzenLyrics = s.linkDzenLyrics,
                                linkDzenTabs = s.linkDzenTabs,
                                linkDzenChords = s.linkDzenChords,
                                linkVkKaraoke = s.linkVkKaraoke,
                                linkVkLyrics = s.linkVkLyrics,
                                linkVkTabs = s.linkVkTabs,
                                linkVkChords = s.linkVkChords,
                                linkTgKaraoke = s.linkTgKaraoke,
                                linkTgLyrics = s.linkTgLyrics,
                                linkTgTabs = s.linkTgTabs,
                                linkTgChords = s.linkTgChords,
                                linkPlKaraoke = s.linkPlKaraoke,
                                linkPlLyrics = s.linkPlLyrics,
                                linkPlTabs = s.linkPlTabs,
                                linkPlChords = s.linkPlChords,
                                linkMaxKaraoke = s.linkMaxKaraoke,
                                linkMaxLyrics = s.linkMaxLyrics,
                                linkMaxTabs = s.linkMaxTabs,
                                linkMaxChords = s.linkMaxChords,
                            )
                        }
                    )
                }
            )
        }
    }
}
