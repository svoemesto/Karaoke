package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.WORKING_DATABASE
import com.svoemesto.karaokeapp.services.KSS_APP
import com.svoemesto.karaokeapp.services.SAC_APP
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.sql.Timestamp
import java.time.Instant

data class StatBySongDto(
    val songId: Int,
    val description: String,
    val cntTotal: Int,
    val cntSm: Int,
    val cntBoosty: Int,
    val cntVkKaraoke: Int,
    val cntVkLyrics: Int,
    val cntDzenKaraoke: Int,
    val cntDzenLyrics: Int,
    val cntTgKaraoke: Int,
    val cntTgLyrics: Int,
)

data class WebEventDto(
    val eventType: String,
    val eventDescription: String,
    val eventDate: Timestamp,
    val eventReferer: String,
)

/**
 * Перенесённая в karaoke-app аналитика по тбл_events/tbl_settings — раньше жила только в
 * karaoke-web (Stat.kt), теперь нужна и для админки (webvue3), которая ходит за данными
 * исключительно в karaoke-app.
 */
object StatsByEvents {

    fun getWebEvents(
        database: KaraokeConnection = WORKING_DATABASE,
        limit: Int = 500,
        storageService: KaraokeStorageService = KSS_APP,
        storageApiClient: StorageApiClient = SAC_APP
    ): List<WebEventDto> {
        val result: MutableList<WebEventDto> = mutableListOf()
        val sql = """
            select * from tbl_events where last_update is not null order by last_update desc limit $limit
        """.trimIndent()

        val connection = database.getConnection()
        if (connection == null) {
            println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных ${database.name}")
            return emptyList()
        }
        var statement: Statement? = null
        var rs: ResultSet? = null
        try {
            statement = connection.createStatement()
            rs = statement.executeQuery(sql)
            while (rs.next()) {
                val eventType = rs.getString("event_type")
                when (eventType) {
                    "callRest" -> {
                        val restName = rs.getString("rest_name")
                        when (restName) {
                            "main" -> result.add(
                                WebEventDto(
                                    eventType = "Главная страница сайта",
                                    eventDescription = "",
                                    eventDate = rs.getTimestamp("last_update"),
                                    eventReferer = rs.getString("referer") ?: ""
                                )
                            )
                            "filter" -> result.add(
                                WebEventDto(
                                    eventType = "Фильтр",
                                    eventDescription = rs.getString("rest_parameters"),
                                    eventDate = rs.getTimestamp("last_update"),
                                    eventReferer = rs.getString("referer") ?: ""
                                )
                            )
                            "zakroma" -> {
                                val parameters = rs.getString("rest_parameters")
                                result.add(
                                    WebEventDto(
                                        eventType = "Закрома",
                                        eventDescription = if (parameters.contains("=")) parameters.split("=")[1].dropLast(1) else "",
                                        eventDate = rs.getTimestamp("last_update"),
                                        eventReferer = rs.getString("referer") ?: ""
                                    )
                                )
                            }
                            "song" -> {
                                val songId = rs.getInt("song_id")
                                val sett = Settings.loadFromDbById(songId.toLong(), database = database, storageService = storageService, storageApiClient = storageApiClient)
                                sett?.let {
                                    result.add(
                                        WebEventDto(
                                            eventType = "Песня",
                                            eventDescription = "[${sett.author}] - [${sett.album}] - «${sett.songName}»",
                                            eventDate = rs.getTimestamp("last_update"),
                                            eventReferer = rs.getString("referer") ?: ""
                                        )
                                    )
                                }
                            }
                        }
                    }
                    "clickToLink" -> {
                        val linkType = rs.getString("link_type")
                        when (linkType) {
                            "linkToSong" -> {
                                val songId = rs.getInt("song_id")
                                val sett = Settings.loadFromDbById(songId.toLong(), database = database, storageService = storageService, storageApiClient = storageApiClient)
                                sett?.let {
                                    result.add(
                                        WebEventDto(
                                            eventType = "Клик ${rs.getString("link_name")} ${rs.getString("song_version")}",
                                            eventDescription = "[${sett.author}] - [${sett.album}] - «${sett.songName}»",
                                            eventDate = rs.getTimestamp("last_update"),
                                            eventReferer = ""
                                        )
                                    )
                                }
                            }
                            "linkToSocialNetwork" -> result.add(
                                WebEventDto(
                                    eventType = "Соцсеть",
                                    eventDescription = rs.getString("link_name"),
                                    eventDate = rs.getTimestamp("last_update"),
                                    eventReferer = ""
                                )
                            )
                            else -> {}
                        }
                    }
                    else -> {}
                }
            }
        } catch (e: SQLException) {
            e.printStackTrace()
        } finally {
            try {
                rs?.close()
                statement?.close()
            } catch (e: SQLException) {
                e.printStackTrace()
            }
        }

        return result
    }

    fun getStatBySong(database: KaraokeConnection = WORKING_DATABASE): List<StatBySongDto> {
        val result: MutableList<StatBySongDto> = mutableListOf()
        val sql = """
            select
                tbl_events.song_id,
                sett.song_author,
                sett.song_album,
                sett.song_name,
                (CASE WHEN selSong.song is null THEN 0 ELSE selSong.song END +
                 CASE WHEN selBoosty.boosty is null THEN 0 ELSE selBoosty.boosty END +
                 CASE WHEN selVKKaraoke.vk_karaoke is null THEN 0 ELSE selVKKaraoke.vk_karaoke END +
                 CASE WHEN selVKLyrics.vk_lyrics is null THEN 0 ELSE selVKLyrics.vk_lyrics END +
                 CASE WHEN selDzenKaraoke.dzen_karaoke is null THEN 0 ELSE selDzenKaraoke.dzen_karaoke END +
                 CASE WHEN selDzenLyrics.dzen_lyrics is null THEN 0 ELSE selDzenLyrics.dzen_lyrics END +
                 CASE WHEN selTgKaraoke.tg_karaoke is null THEN 0 ELSE selTgKaraoke.tg_karaoke END +
                 CASE WHEN selTgLyrics.tg_lyrics is null THEN 0 ELSE selTgLyrics.tg_lyrics END) as total,
                CASE WHEN selSong.song is null THEN 0 ELSE selSong.song END as song,
                CASE WHEN selBoosty.boosty is null THEN 0 ELSE selBoosty.boosty END as boosty,
                CASE WHEN selVKKaraoke.vk_karaoke is null THEN 0 ELSE selVKKaraoke.vk_karaoke END as vk_kar,
                CASE WHEN selVKLyrics.vk_lyrics is null THEN 0 ELSE selVKLyrics.vk_lyrics END as vk_lyr,
                CASE WHEN selDzenKaraoke.dzen_karaoke is null THEN 0 ELSE selDzenKaraoke.dzen_karaoke END as dzen_kar,
                CASE WHEN selDzenLyrics.dzen_lyrics is null THEN 0 ELSE selDzenLyrics.dzen_lyrics END as dzen_lyr,
                CASE WHEN selTgKaraoke.tg_karaoke is null THEN 0 ELSE selTgKaraoke.tg_karaoke END as tg_kar,
                CASE WHEN selTgLyrics.tg_lyrics is null THEN 0 ELSE selTgLyrics.tg_lyrics END as tg_lyr
            from tbl_events
            left join tbl_settings sett on tbl_events.song_id = sett.id
            left join
                 (
                     select song_id, count(*) as song
                     from tbl_events
                     where rest_name = 'song'
                     group by song_id
                 ) selSong on tbl_events.song_id = selSong.song_id
            left join
                 (
                     select song_id, count(*) as boosty
                     from tbl_events
                     where link_name = 'boosty'
                     group by song_id
                 ) selBoosty on tbl_events.song_id = selBoosty.song_id
            left join
                 (
                     select song_id, count(*) as vk_lyrics
                     from tbl_events
                     where song_version = 'lyrics' and link_name = 'vk'
                     group by song_id
                 ) selVKLyrics on tbl_events.song_id = selVKLyrics.song_id
            left join
                 (
                     select song_id, count(*) as vk_karaoke
                     from tbl_events
                     where song_version = 'karaoke' and link_name = 'vk'
                     group by song_id
                 ) selVKKaraoke on tbl_events.song_id = selVKKaraoke.song_id
            left join
                 (
                     select song_id, count(*) as dzen_lyrics
                     from tbl_events
                     where song_version = 'lyrics' and link_name = 'dzen'
                     group by song_id
                 ) selDzenLyrics on tbl_events.song_id = selDzenLyrics.song_id
            left join
                 (
                     select song_id, count(*) as dzen_karaoke
                     from tbl_events
                     where song_version = 'karaoke' and link_name = 'dzen'
                     group by song_id
                 ) selDzenKaraoke on tbl_events.song_id = selDzenKaraoke.song_id
            left join
                 (
                     select song_id, count(*) as tg_lyrics
                     from tbl_events
                     where song_version = 'lyrics' and link_name = 'tg'
                     group by song_id
                 ) selTgLyrics on tbl_events.song_id = selTgLyrics.song_id
            left join
                 (
                     select song_id, count(*) as tg_karaoke
                     from tbl_events
                     where song_version = 'karaoke' and link_name = 'tg'
                     group by song_id
                 ) selTgKaraoke on tbl_events.song_id = selTgKaraoke.song_id
            where tbl_events.song_id is not null
            group by
                tbl_events.song_id,
                sett.song_author,
                sett.song_album,
                sett.song_name,
                selSong.song,
                selBoosty.boosty,
                selVKKaraoke.vk_karaoke,
                selVKLyrics.vk_lyrics,
                selDzenKaraoke.dzen_karaoke,
                selDzenLyrics.dzen_lyrics,
                selTgKaraoke.tg_karaoke,
                selTgLyrics.tg_lyrics
            order by total desc
            ;
        """.trimIndent()

        val connection = database.getConnection()
        if (connection == null) {
            println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных ${database.name}")
            return emptyList()
        }
        var statement: Statement? = null
        var rs: ResultSet? = null
        try {
            statement = connection.createStatement()
            rs = statement.executeQuery(sql)
            while (rs.next()) {
                result.add(
                    StatBySongDto(
                        songId = rs.getInt("song_id"),
                        description = "[${rs.getString("song_author")}] - [${rs.getString("song_album")}] - «${rs.getString("song_name")}»",
                        cntTotal = rs.getInt("total"),
                        cntSm = rs.getInt("song"),
                        cntBoosty = rs.getInt("boosty"),
                        cntVkKaraoke = rs.getInt("vk_kar"),
                        cntVkLyrics = rs.getInt("vk_lyr"),
                        cntDzenKaraoke = rs.getInt("dzen_kar"),
                        cntDzenLyrics = rs.getInt("dzen_lyr"),
                        cntTgKaraoke = rs.getInt("tg_kar"),
                        cntTgLyrics = rs.getInt("tg_lyr"),
                    )
                )
            }
        } catch (e: SQLException) {
            e.printStackTrace()
        } finally {
            try {
                rs?.close()
                statement?.close()
            } catch (e: SQLException) {
                e.printStackTrace()
            }
        }
        return result
    }
}
