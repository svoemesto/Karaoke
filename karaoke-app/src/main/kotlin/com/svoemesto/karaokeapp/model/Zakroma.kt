package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.Connection
import com.svoemesto.karaokeapp.WORKING_DATABASE
import com.svoemesto.karaokeapp.censored
import java.io.Serializable

class Zakroma(val database: Connection = WORKING_DATABASE): Serializable, Comparable<Zakroma> {

    companion object {
        fun getZakroma(author: String, database: Connection): List<Zakroma> {
            val listSettings = Settings.loadListFromDb(mapOf("author" to author), database)
            val settingsByAuthor = listSettings.groupBy { it.author }
            return settingsByAuthor.map { (authorName, settingsByAuthor) ->
                val zakroma = Zakroma(database)
                zakroma.author = authorName
                val settingsByAlbum = settingsByAuthor.groupBy { it.album }
                zakroma.albums = settingsByAlbum.map { (albumName, settingsByAlbum) ->
                    val album = ZakromaAlbum()
                    album.albumName = albumName
                    album.year = settingsByAlbum.first().year
                    album.albumSettings = settingsByAlbum.map { settings ->
                        val zakromaAlbumSettings = ZakromaAlbumSettings()
                        zakromaAlbumSettings.onAir = settings.onAir
                        zakromaAlbumSettings.datePublish = if (settings.date == "" || settings.time == "") "Дата пока не определена" else "${settings.date} ${settings.time}"
                        zakromaAlbumSettings.track = settings.track
                        zakromaAlbumSettings.songName = settings.songName.censored()
                        zakromaAlbumSettings.linkBoosty = if (settings.idBoosty == "") "" else settings.linkBoosty!!
                        zakromaAlbumSettings.linkDzenKaraoke = if (settings.idYoutubeKaraoke == "") "" else settings.linkYoutubeKaraokePlay!!
                        zakromaAlbumSettings.linkDzenLyrics = if (settings.idYoutubeLyrics == "") "" else settings.linkYoutubeLyricsPlay!!
                        zakromaAlbumSettings.linkVkKaraoke = if (settings.idVkKaraoke == "") "" else settings.linkVkKaraokePlay!!
                        zakromaAlbumSettings.linkVkLyrics = if (settings.idVkLyrics == "") "" else settings.linkVkLyricsPlay!!
                        zakromaAlbumSettings.linkTgKaraoke = if (settings.idTelegramKaraoke == "" || settings.idTelegramKaraoke == "-") "" else settings.linkTelegramKaraokePlay!!
                        zakromaAlbumSettings.linkTgLyrics = if (settings.idTelegramLyrics == "" || settings.idTelegramLyrics == "-") "" else settings.linkTelegramLyricsPlay!!
                        zakromaAlbumSettings
                    }.sorted().toMutableList()
                    album
                }.sorted().toMutableList()
                zakroma
            }
        }
    }



    var author: String = ""
    var albums: MutableList<ZakromaAlbum> = mutableListOf()

    override fun compareTo(other: Zakroma): Int {
        return author.compareTo(other.author)
    }

}

class ZakromaAlbumSettings: Serializable, Comparable<ZakromaAlbumSettings> {
    var track: Long = 0
    var songName: String = ""
    var linkBoosty: String = ""
    var linkDzenKaraoke: String = ""
    var linkDzenLyrics: String = ""
    var linkVkKaraoke: String = ""
    var linkVkLyrics: String = ""
    var linkTgKaraoke: String = ""
    var linkTgLyrics: String = ""
    var onAir: Boolean = false
    var datePublish: String = ""
    override fun compareTo(other: ZakromaAlbumSettings): Int {
        val compTrack = track.compareTo(other.track)
        if (compTrack == 0) {
            return songName.compareTo(other.songName)
        }
        return compTrack
    }

}

class ZakromaAlbum: Serializable, Comparable<ZakromaAlbum> {
    var albumName: String = ""
    var year: Long = 0
    var albumSettings: MutableList<ZakromaAlbumSettings> = mutableListOf()
    override fun compareTo(other: ZakromaAlbum): Int {
        val compYear = year.compareTo(other.year)
        if (compYear == 0) {
            return albumName.compareTo(other.albumName)
        }
        return compYear
    }

}