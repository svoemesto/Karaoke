package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.censored
import java.io.Serializable

class Zakroma(val database: KaraokeConnection): Serializable, Comparable<Zakroma> {

    companion object {
        fun getZakroma(author: String, database: KaraokeConnection): List<Zakroma> {
            val listSettings = Settings.loadListFromDb(mapOf("author" to author), database)
            val settingsByAuthor = listSettings.groupBy { it.author }
            return settingsByAuthor.map { (authorName, settingsByAuthor) ->
                val zakroma = Zakroma(database)
                zakroma.author = authorName
                zakroma.picture = Pictures.load(authorName, database)?.full ?: ""
                val settingsByAlbum = settingsByAuthor.groupBy { it.album }
                zakroma.albums = settingsByAlbum.map { (albumName, settingsByAlbum) ->
                    val album = ZakromaAlbum()
                    album.albumName = albumName
                    album.year = settingsByAlbum.first().year
                    album.picture = Pictures.load("$authorName - ${album.year} - $albumName", database)?.full ?: ""
                    album.albumSettings = settingsByAlbum.map { settings ->
                        val zakromaAlbumSettings = ZakromaAlbumSettings()
                        zakromaAlbumSettings.id = settings.id
                        zakromaAlbumSettings.onAir = settings.onAir
                        zakromaAlbumSettings.datePublish = settings.datePublish
                        zakromaAlbumSettings.track = settings.track
                        zakromaAlbumSettings.songName = settings.songName.censored()
                        zakromaAlbumSettings.linkBoosty = settings.linkBoostyTxt
                        zakromaAlbumSettings.linkDzenKaraoke = settings.linkDzenKaraoke
                        zakromaAlbumSettings.linkDzenLyrics = settings.linkDzenLyrics
                        zakromaAlbumSettings.linkVkKaraoke = settings.linkVkKaraoke
                        zakromaAlbumSettings.linkVkLyrics = settings.linkVkLyrics
                        zakromaAlbumSettings.linkTgKaraoke = settings.linkTgKaraoke
                        zakromaAlbumSettings.linkTgLyrics = settings.linkTgLyrics
                        zakromaAlbumSettings
                    }.sorted().toMutableList()
                    album
                }.sorted().toMutableList()
                zakroma
            }
        }
    }



    var author: String = ""
    var picture: String = ""
    var albums: MutableList<ZakromaAlbum> = mutableListOf()

    override fun compareTo(other: Zakroma): Int {
        return author.compareTo(other.author)
    }

}

class ZakromaAlbumSettings: Serializable, Comparable<ZakromaAlbumSettings> {
    var id: Long = 0
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
    var picture: String = ""
    var albumSettings: MutableList<ZakromaAlbumSettings> = mutableListOf()
    override fun compareTo(other: ZakromaAlbum): Int {
        val compYear = year.compareTo(other.year)
        if (compYear == 0) {
            return albumName.compareTo(other.albumName)
        }
        return compYear
    }

}