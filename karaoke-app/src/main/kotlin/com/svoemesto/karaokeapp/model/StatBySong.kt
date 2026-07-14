package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.WORKING_DATABASE
import com.svoemesto.karaokeapp.services.GeoIpService
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
    val cntTotal: Int,     // все события песни (не только показанные каналы)
    val cntSm: Int,        // просмотры страницы песни на сайте (rest_name='song')
    val cntPlayer: Int,    // события онлайн-плеера всего (event_type='player')
    // детализация онлайн-плеера по действиям (link_type при event_type='player')
    val cntPlayerShown: Int,
    val cntPlayerOpened: Int,
    val cntPlayerPlay: Int,
    val cntPlayerPause: Int,
    val cntPlayerSeek: Int,
    val cntPlayerExport: Int,
    val cntPlayerProgress: Int,
    val cntPlayerEnded: Int,
    val cntVkKaraoke: Int,
    val cntVkLyrics: Int,
    val cntDzenKaraoke: Int,
    val cntDzenLyrics: Int,
    val cntTgKaraoke: Int,
    val cntTgLyrics: Int,
    val cntMax: Int,
    val cntSponsr: Int,
)

// Топ песен, у которых онлайн-плеер был реально дослушан до 75% (или до конца).
// Используется для админского дашборда webvue3, вкладка «Слушают».
// Метрика "дослушали до 75% и дальше": событие progress@75 ИЛИ ended (трек автоматически >= 75%).
// pListened = кол-во таких событий по песне; pPlayed — для контекста (сколько раз вообще нажали play);
// доля дослушивания считается на фронте как pListened / max(pPlayed, 1).
data class TopListenedSongDto(
    val songId: Int,
    val description: String,
    // Имена полей выбраны однозначно-lowercase, чтобы Jackson сериализовал их ровно так же
    // (он срезает get-префикс по BeanInfo и не умеет корректно разделять "p" и "L" в pListened —
    // получалось бы "plistened"). Фронт webvue3 (TopListenedSongsTable.vue) ждёт именно эти ключи.
    val listened: Int,     // дослушали до 75% и дальше (progress='75' или ended)
    val played: Int,       // сколько раз нажали play (контекст: доля дослушивания)
    val ended: Int,        // из них доиграли до конца
    val cntSm: Int,        // просмотры страницы песни (контекст популярности страницы)
    val cntMax: Int,       // клики по MAX
    val cntSponsr: Int,    // клики по Sponsr
    // Уникальные посетители, дослушавшие песню до 75%+. Залогиненные (site_user_id>0) отдельно
    // от анонимных (anon_id считается отдельно, чтобы один человек, сначала зашедший анонимно и
    // потом залогинившийся, не считался дважды). Сумму фронт считает сам как uniqUsers+uniqAnon.
    val uniqUsers: Int,    // дослушали до 75% и дальше, залогиненные (site_user_id>0)
    val uniqAnon: Int,     // дослушали до 75% и дальше, анонимные (site_user_id=0, по anon_id)
)

// Строка лога событий: и человекочитаемые поля (eventType/eventDescription), и ВСЕ сырые колонки
// tbl_events — чтобы админ-таблица могла показать полный набор полей БД.
data class WebEventDto(
    val id: Long = 0,
    val eventType: String,          // человекочитаемый ярлык
    val eventTypeRaw: String = "",  // сырое значение event_type из БД
    val eventDescription: String,
    val eventDate: Timestamp,
    val eventReferer: String,
    val clientIp: String = "",
    val anonId: String = "",
    val siteUserId: Long = 0,
    val userAgent: String = "",
    // сырые колонки tbl_events
    val restName: String = "",
    val restParameters: String = "",
    val linkType: String = "",
    val linkName: String = "",
    val songId: Long = 0,
    val songVersion: String = "",
    val referer: String = "",
    val country: String = "",       // ISO-код страны по client_ip (GeoIpService), "" если не определилась
    val songName: String = "",      // человекочитаемое имя песни (для группировки дерева по странице)
)

// Детализация события: event_type + человекочитаемая подпись комбинации + число. eventType нужен
// фронтенду для drill-down по клику на сегмент донат-графика «Типы событий».
data class DetailCountDto(
    val eventType: String,
    val name: String,
    val count: Int,
)

// Сводные показатели для KPI-карточек дашборда.
data class StatsSummaryDto(
    val totalEvents: Int,
    val uniqueVisitors: Int,
    val registeredEvents: Int,
    val uniqueRegisteredUsers: Int,
    val eventsToday: Int,
    val events7d: Int,
    val events30d: Int,
    val topChannel: String,
    val topChannelCount: Int,
)

// Точка временного ряда: дата (YYYY-MM-DD), опциональный тип события, число.
data class TimePointDto(
    val date: String,
    val eventType: String,
    val count: Int,
)

// Пара «ключ → число» для разбивок (типы событий, каналы).
data class NamedCountDto(
    val name: String,
    val count: Int,
)

// Строка топа пользователей. Булевы поля без is-префикса — иначе Jackson сериализует их как
// "premium"/"banned" (bean convention), см. правило в DEVELOPMENT.md.
data class TopUserDto(
    val siteUserId: Long,
    val anonId: String = "",        // идентификатор анонимной строки (для drill-down); у залогиненных ""
    val displayName: String,
    val email: String,
    val premium: Boolean,
    val eventCount: Int,
    val lastActivity: Timestamp?,
)

/**
 * Перенесённая в karaoke-app аналитика по тбл_events/tbl_settings — раньше жила только в
 * karaoke-web (Stat.kt), теперь нужна и для админки (webvue3), которая ходит за данными
 * исключительно в karaoke-app.
 */
object StatsByEvents {

    // Собирает WHERE-условия лога событий из опциональных фильтров (тип события, дата "с",
    // конкретный пользователь для drill-down). Значения санируются: eventType — из белого списка
    // enum EventType; siteUserId — Long; fromDate — целое число дней. SQL-инъекция исключена.
    private fun buildEventsWhere(eventType: String?, fromDays: Int?, siteUserId: Long?, anonId: String? = null, songId: Long? = null): String {
        val conds = mutableListOf("last_update is not null")
        eventType?.let { et -> EventType.fromDb(et)?.let { conds.add("event_type = '${it.dbValue}'") } }
        fromDays?.let { if (it > 0) conds.add("last_update >= now() - interval '$it days'") }
        siteUserId?.let { if (it > 0) conds.add("site_user_id = $it") }
        // Drill-down по анониму: site_user_id=0 (иначе бы попали события того же anon_id после логина).
        // Значение санируем экранированием кавычки и ограничением длины (anon_id — UUID, ≤64).
        anonId?.takeIf { it.isNotBlank() }?.let {
            val safe = it.take(64).replace("'", "''")
            conds.add("site_user_id = 0 and anon_id = '$safe'")
        }
        songId?.let { if (it > 0) conds.add("song_id = $it") }
        return conds.joinToString(" and ")
    }

    fun getWebEventsCount(
        database: KaraokeConnection = WORKING_DATABASE,
        eventType: String? = null,
        fromDays: Int? = null,
        siteUserId: Long? = null,
        anonId: String? = null,
        songId: Long? = null,
    ): Int {
        val connection = database.getConnection() ?: return 0
        var statement: Statement? = null
        var rs: ResultSet? = null
        try {
            statement = connection.createStatement()
            rs = statement.executeQuery("select count(*) as cnt from tbl_events where ${buildEventsWhere(eventType, fromDays, siteUserId, anonId, songId)}")
            return if (rs.next()) rs.getInt("cnt") else 0
        } catch (e: SQLException) {
            e.printStackTrace()
            return 0
        } finally {
            try {
                rs?.close()
                statement?.close()
            } catch (e: SQLException) {
                e.printStackTrace()
            }
        }
    }

    fun getWebEvents(
        database: KaraokeConnection = WORKING_DATABASE,
        limit: Int = 500,
        offset: Int = 0,
        eventType: String? = null,
        fromDays: Int? = null,
        siteUserId: Long? = null,
        anonId: String? = null,
        songId: Long? = null,
        storageService: KaraokeStorageService = KSS_APP,
        storageApiClient: StorageApiClient = SAC_APP
    ): List<WebEventDto> {
        val result: MutableList<WebEventDto> = mutableListOf()
        val sql = """
            select * from tbl_events where ${buildEventsWhere(eventType, fromDays, siteUserId, anonId, songId)} order by last_update desc, id desc limit $limit offset $offset
        """.trimIndent()

        val connection = database.getConnection()
        if (connection == null) {
            println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных ${database.name}")
            return emptyList()
        }
        var statement: Statement? = null
        var rs: ResultSet? = null
        try {
            // Первый проход: читаем ВСЕ сырые строки страницы в память (без N+1 загрузки Settings).
            val rows = mutableListOf<RawEventRow>()
            statement = connection.createStatement()
            rs = statement.executeQuery(sql)
            while (rs.next()) {
                rows.add(RawEventRow(
                    id = rs.getLong("id"),
                    eventType = rs.getString("event_type") ?: "",
                    eventDate = rs.getTimestamp("last_update"),
                    clientIp = rs.getString("client_ip") ?: "",
                    anonId = rs.getString("anon_id") ?: "",
                    siteUserId = rs.getLong("site_user_id"),
                    userAgent = rs.getString("user_agent") ?: "",
                    restName = rs.getString("rest_name") ?: "",
                    restParameters = rs.getString("rest_parameters") ?: "",
                    linkType = rs.getString("link_type") ?: "",
                    linkName = rs.getString("link_name") ?: "",
                    songId = rs.getLong("song_id"),
                    songVersion = rs.getString("song_version") ?: "",
                    referer = rs.getString("referer") ?: "",
                ))
            }
            rs.close(); rs = null
            statement.close(); statement = null

            // Одним запросом подтягиваем названия песен для всех song_id страницы (вместо N+1).
            val songIds = rows.map { it.songId }.filter { it > 0 }.distinct()
            val songNames = HashMap<Long, String>()
            if (songIds.isNotEmpty()) {
                val st2 = connection.createStatement()
                val rs2 = st2.executeQuery(
                    "select id, song_author, song_album, song_name from tbl_settings where id in (${songIds.joinToString(",")})"
                )
                while (rs2.next()) {
                    songNames[rs2.getLong("id")] =
                        "[${rs2.getString("song_author")}] - [${rs2.getString("song_album")}] - «${rs2.getString("song_name")}»"
                }
                rs2.close(); st2.close()
            }

            // Одним батчем определяем страну по всем IP страницы (кэш GeoIpService — без N+1 по сети).
            // Лимит внешних резолвов на страницу лога, чтобы не задерживать ответ на холодном кэше.
            val countries = GeoIpService.resolveMany(rows.map { it.clientIp }, maxFetch = 100)

            rows.forEach { r ->
                val songName = songNames[r.songId] ?: ""
                val (humanType, description) = webEventHuman(r, songName)
                result.add(WebEventDto(
                    id = r.id,
                    eventType = humanType,
                    eventTypeRaw = r.eventType,
                    eventDescription = description,
                    eventDate = r.eventDate,
                    // referer теперь несёт настоящий внешний источник перехода (document.referrer),
                    // а не продублированный IP — показываем как есть, без фолбэка на clientIp.
                    eventReferer = r.referer,
                    clientIp = r.clientIp,
                    anonId = r.anonId,
                    siteUserId = r.siteUserId,
                    userAgent = r.userAgent,
                    restName = r.restName,
                    restParameters = r.restParameters,
                    linkType = r.linkType,
                    linkName = r.linkName,
                    songId = r.songId,
                    songVersion = r.songVersion,
                    referer = r.referer,
                    country = countries[r.clientIp.split(",").first().trim()] ?: "",
                    songName = songName,
                ))
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

    // Сырая строка tbl_events для двухпроходной сборки лога (без N+1 загрузки Settings).
    private data class RawEventRow(
        val id: Long, val eventType: String, val eventDate: Timestamp, val clientIp: String,
        val anonId: String, val siteUserId: Long, val userAgent: String, val restName: String,
        val restParameters: String, val linkType: String, val linkName: String, val songId: Long,
        val songVersion: String, val referer: String,
    )

    // Человекочитаемые тип + описание события. Строка создаётся для ЛЮБОГО типа (в т.ч.
    // engagement/ui/play/player-progress/ended, которые старая версия молча пропускала).
    private fun webEventHuman(r: RawEventRow, songName: String): Pair<String, String> = when (r.eventType) {
        EventType.CALL_REST.dbValue -> when (r.restName) {
            RestName.MAIN.dbValue -> "Главная страница сайта" to ""
            RestName.FILTER.dbValue -> "Поиск" to r.restParameters
            RestName.ZAKROMA.dbValue -> "Закрома" to (if (r.restParameters.contains("=")) r.restParameters.substringAfter("=").trimEnd('}') else "")
            RestName.SONG.dbValue -> "Просмотр песни" to songName
            else -> "Просмотр: ${r.restName}" to ""
        }
        EventType.CLICK_TO_LINK.dbValue -> when (r.linkType) {
            LinkType.LINK_TO_SONG.dbValue -> "Ссылка: ${r.linkName} ${r.songVersion}".trim() to songName
            LinkType.LINK_TO_SOCIAL_NETWORK.dbValue -> "Соцсеть: ${r.linkName}" to ""
            else -> "Клик: ${r.linkType}" to r.linkName
        }
        EventType.PLAYER.dbValue -> detailLabel("player", r.linkType) to
            (if (r.linkName.isNotEmpty()) "${r.linkName} · song_id=${r.songId}" else "song_id=${r.songId}")
        EventType.ENGAGEMENT.dbValue -> "Время на странице" to "${r.linkName}с · ${r.restName}"
        EventType.UI.dbValue -> detailLabel("ui", r.linkType) to r.linkName
        EventType.PLAY.dbValue -> "Видео на странице" to (if (r.songVersion.isNotEmpty()) "версия ${r.songVersion}" else "")
        else -> (r.eventType.ifEmpty { "неизвестно" }) to ""
    }

    fun getStatBySongCount(database: KaraokeConnection = WORKING_DATABASE): Int {
        val connection = database.getConnection() ?: return 0
        var statement: Statement? = null
        var rs: ResultSet? = null
        try {
            statement = connection.createStatement()
            rs = statement.executeQuery("select count(distinct song_id) as cnt from tbl_events where song_id is not null and song_id > 0")
            return if (rs.next()) rs.getInt("cnt") else 0
        } catch (e: SQLException) {
            e.printStackTrace()
            return 0
        } finally {
            try {
                rs?.close()
                statement?.close()
            } catch (e: SQLException) {
                e.printStackTrace()
            }
        }
    }

    fun getStatBySong(database: KaraokeConnection = WORKING_DATABASE, limit: Int = 50, offset: Int = 0): List<StatBySongDto> {
        val result: MutableList<StatBySongDto> = mutableListOf()
        // Одна группировка по song_id + условные count(*) filter вместо 8 LEFT JOIN-подзапросов.
        // total = ВСЕ события песни (не только показанные каналы). Boosty убран как неактуальный,
        // добавлена колонка «Плеер» (event_type='player') — новый значимый сигнал вовлечённости.
        val sql = """
            select
                e.song_id,
                sett.song_author,
                sett.song_album,
                sett.song_name,
                count(*) as total,
                count(*) filter (where e.rest_name = 'song') as song,
                count(*) filter (where e.event_type = 'player') as player,
                count(*) filter (where e.event_type = 'player' and e.link_type = 'shown') as p_shown,
                count(*) filter (where e.event_type = 'player' and e.link_type = 'opened') as p_opened,
                count(*) filter (where e.event_type = 'player' and e.link_type = 'play') as p_play,
                count(*) filter (where e.event_type = 'player' and e.link_type = 'pause') as p_pause,
                count(*) filter (where e.event_type = 'player' and e.link_type = 'seek') as p_seek,
                count(*) filter (where e.event_type = 'player' and e.link_type = 'export') as p_export,
                count(*) filter (where e.event_type = 'player' and e.link_type = 'progress') as p_progress,
                count(*) filter (where e.event_type = 'player' and e.link_type = 'ended') as p_ended,
                count(*) filter (where e.song_version = 'karaoke' and e.link_name = 'vk') as vk_kar,
                count(*) filter (where e.song_version = 'lyrics' and e.link_name = 'vk') as vk_lyr,
                count(*) filter (where e.song_version = 'karaoke' and e.link_name = 'dzen') as dzen_kar,
                count(*) filter (where e.song_version = 'lyrics' and e.link_name = 'dzen') as dzen_lyr,
                count(*) filter (where e.song_version = 'karaoke' and e.link_name = 'tg') as tg_kar,
                count(*) filter (where e.song_version = 'lyrics' and e.link_name = 'tg') as tg_lyr,
                count(*) filter (where e.link_name = 'max') as max_clicks,
                count(*) filter (where e.link_name = 'sponsr') as sponsr_clicks
            from tbl_events e
            left join tbl_settings sett on e.song_id = sett.id
            where e.song_id is not null and e.song_id > 0
            group by e.song_id, sett.song_author, sett.song_album, sett.song_name
            order by total desc, e.song_id asc
            limit $limit offset $offset
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
                        cntPlayer = rs.getInt("player"),
                        cntPlayerShown = rs.getInt("p_shown"),
                        cntPlayerOpened = rs.getInt("p_opened"),
                        cntPlayerPlay = rs.getInt("p_play"),
                        cntPlayerPause = rs.getInt("p_pause"),
                        cntPlayerSeek = rs.getInt("p_seek"),
                        cntPlayerExport = rs.getInt("p_export"),
                        cntPlayerProgress = rs.getInt("p_progress"),
                        cntPlayerEnded = rs.getInt("p_ended"),
                        cntVkKaraoke = rs.getInt("vk_kar"),
                        cntVkLyrics = rs.getInt("vk_lyr"),
                        cntDzenKaraoke = rs.getInt("dzen_kar"),
                        cntDzenLyrics = rs.getInt("dzen_lyr"),
                        cntTgKaraoke = rs.getInt("tg_kar"),
                        cntTgLyrics = rs.getInt("tg_lyr"),
                        cntMax = rs.getInt("max_clicks"),
                        cntSponsr = rs.getInt("sponsr_clicks"),
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

    // Топ песен, которые РЕАЛЬНО слушают: дослушали онлайн-плеер до 75% или до конца.
    // Использует выражение (link_type='progress' AND link_name='75') OR (link_type='ended') — оба
    // варианта означают «прослушано ≥75%» (ended автоматически даёт 100%). Сортировка по числу
    // таких событий DESC — главная метрика «слушают». Доп. поля (cntSm / Sponsr / MAX) даны для
    // контекста, чтобы админ видел коммерческую отдачу песни рядом с фактическим прослушиванием.
    fun getTopListenedSongsCount(database: KaraokeConnection = WORKING_DATABASE): Int {
        val connection = database.getConnection() ?: return 0
        var statement: Statement? = null
        var rs: ResultSet? = null
        try {
            statement = connection.createStatement()
            rs = statement.executeQuery(
                """
                select count(distinct song_id) as cnt from tbl_events
                where song_id is not null and song_id > 0
                  and event_type = 'player'
                  and ((link_type = 'progress' and link_name = '75') or link_type = 'ended')
                """.trimIndent()
            )
            return if (rs.next()) rs.getInt("cnt") else 0
        } catch (e: SQLException) {
            e.printStackTrace()
            return 0
        } finally {
            try { rs?.close(); statement?.close() } catch (e: SQLException) { e.printStackTrace() }
        }
    }

    fun getTopListenedSongs(database: KaraokeConnection = WORKING_DATABASE, limit: Int = 50, offset: Int = 0): List<TopListenedSongDto> {
        val result: MutableList<TopListenedSongDto> = mutableListOf()
        // Базовый набор строк — это события «прослушали ≥75%» (progress='75' или ended).
        // count/filter по нему дают listened и ended. Для контекстных полей (play/song/max/sponsr)
        // используем scalar-подзапросы в SELECT — иначе фильтр listened-events их обнулит:
        // callRest('song') и clickToLink(max/sponsr) физически не пересекаются с player-progress.
        val sql = """
            select
                s.song_id,
                sett.song_author,
                sett.song_album,
                sett.song_name,
                count(*) as p_listened,
                count(*) filter (where e.link_type = 'ended') as p_ended,
                -- Уникальные залогиненные дослушали до 75%+ (на site_user_id — потом повторный
                -- логин одного человека не задвоит счёт).
                count(distinct e.site_user_id) filter (where e.site_user_id > 0) as uniq_users,
                -- Уникальные анонимы дослушали до 75%+ (anon_id, site_user_id=0). Защита от
                -- задвоения через count+distinct в одном выражении.
                count(distinct case when e.site_user_id = 0 then anon_id end) as uniq_anon,
                (select count(*) from tbl_events where song_id = s.song_id
                    and event_type = 'player' and link_type = 'play') as p_played,
                (select count(*) from tbl_events where song_id = s.song_id
                    and event_type = 'callRest' and rest_name = 'song') as cnt_sm,
                (select count(*) from tbl_events where song_id = s.song_id
                    and event_type = 'clickToLink' and link_type = 'linkToSong' and link_name = 'max') as cnt_max,
                (select count(*) from tbl_events where song_id = s.song_id
                    and event_type = 'clickToLink' and link_type = 'linkToSong' and link_name = 'sponsr') as cnt_sponsr
            from tbl_events e
            left join tbl_settings sett on sett.id = e.song_id
            -- Один срез событий «дослушали ≥75%» по конкретному song_id. Внутренний подзапрос
            -- фильтрует до агрегации, чтобы count(*) считался только по нужным строкам. Затем
            -- GROUP BY по song_id даёт одну строку на песню.
            inner join (
                select id, song_id from tbl_events
                where song_id is not null and song_id > 0
                  and event_type = 'player'
                  and ((link_type = 'progress' and link_name = '75') or link_type = 'ended')
            ) s on s.id = e.id
            group by s.song_id, sett.song_author, sett.song_album, sett.song_name
            order by p_listened desc, s.song_id asc
            limit $limit offset $offset
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
                    TopListenedSongDto(
                        songId = rs.getInt("song_id"),
                        description = "[${rs.getString("song_author")}] - [${rs.getString("song_album")}] - «${rs.getString("song_name")}»",
                        listened = rs.getInt("p_listened"),
                        played = rs.getInt("p_played"),
                        ended = rs.getInt("p_ended"),
                        cntSm = rs.getInt("cnt_sm"),
                        cntMax = rs.getInt("cnt_max"),
                        cntSponsr = rs.getInt("cnt_sponsr"),
                        uniqUsers = rs.getInt("uniq_users"),
                        uniqAnon = rs.getInt("uniq_anon"),
                    )
                )
            }
        } catch (e: SQLException) {
            e.printStackTrace()
        } finally {
            try { rs?.close(); statement?.close() } catch (e: SQLException) { e.printStackTrace() }
        }
        return result
    }

    // --- Дашборд: сводки и разбивки (ручной JDBC, тот же стиль что и выше) ---

    private fun scalarInt(database: KaraokeConnection, sql: String): Int {
        val connection = database.getConnection() ?: return 0
        var statement: Statement? = null
        var rs: ResultSet? = null
        try {
            statement = connection.createStatement()
            rs = statement.executeQuery(sql)
            return if (rs.next()) rs.getInt(1) else 0
        } catch (e: SQLException) {
            e.printStackTrace()
            return 0
        } finally {
            try { rs?.close(); statement?.close() } catch (e: SQLException) { e.printStackTrace() }
        }
    }

    // Понятные подписи платформ (значения link_name у clickToLink/linkToSong).
    private fun channelLabel(linkName: String?): String = when (linkName) {
        "vk" -> "VK"
        "dzen" -> "Dzen"
        "tg" -> "Telegram"
        "max" -> "MAX"
        "sponsr" -> "Sponsr"
        "vkgroup" -> "ВК-группа"
        "pl" -> "Плейлист"
        "boosty" -> "Boosty"
        else -> linkName ?: "—"
    }

    // Каналы переходов = клики по внешним ссылкам песни (clickToLink/linkToSong), сгруппированные
    // по платформе (link_name). Динамически — какие платформы реально есть (max/sponsr/… появляются
    // автоматически). Boosty исключён как неактуальный.
    fun getChannelBreakdown(database: KaraokeConnection = WORKING_DATABASE): List<NamedCountDto> {
        val result = mutableListOf<NamedCountDto>()
        val connection = database.getConnection() ?: return emptyList()
        val sql = """
            select link_name, count(*) as cnt
            from tbl_events
            where event_type = 'clickToLink' and link_type = 'linkToSong'
              and link_name is not null and link_name <> '' and link_name <> 'boosty'
            group by link_name
            order by cnt desc
        """.trimIndent()
        var statement: Statement? = null
        var rs: ResultSet? = null
        try {
            statement = connection.createStatement()
            rs = statement.executeQuery(sql)
            while (rs.next()) {
                result.add(NamedCountDto(channelLabel(rs.getString("link_name")), rs.getInt("cnt")))
            }
        } catch (e: SQLException) {
            e.printStackTrace()
        } finally {
            try { rs?.close(); statement?.close() } catch (e: SQLException) { e.printStackTrace() }
        }
        return result
    }

    // Топ внешних источников перехода: страницы в интернете, с которых пришли на сайт по прямой
    // ссылке (referer = document.referrer, кросс-домен, только заход-лендинг — см. karaoke-public/
    // services/entryReferrer.js). Показывает, откуда идёт трафик помимо закромов/поиска.
    fun getTopReferrers(database: KaraokeConnection = WORKING_DATABASE, limit: Int = 30): List<NamedCountDto> {
        val result = mutableListOf<NamedCountDto>()
        val connection = database.getConnection() ?: return emptyList()
        // Только URL-подобные referer'ы (http/https). Отсекаем легаси-строки, где в referer раньше
        // писался clientIp (до перехода на document.referrer) — там лежат голые IP, не источники.
        val sql = """
            select referer, count(*) as cnt
            from tbl_events
            where referer like 'http%'
            group by referer
            order by cnt desc
            limit $limit
        """.trimIndent()
        var statement: Statement? = null
        var rs: ResultSet? = null
        try {
            statement = connection.createStatement()
            rs = statement.executeQuery(sql)
            while (rs.next()) {
                result.add(NamedCountDto(rs.getString("referer") ?: "—", rs.getInt("cnt")))
            }
        } catch (e: SQLException) {
            e.printStackTrace()
        } finally {
            try { rs?.close(); statement?.close() } catch (e: SQLException) { e.printStackTrace() }
        }
        return result
    }

    // Человекочитаемые названия стран по ISO-коду (самые частые для этого сайта); неизвестные —
    // как ISO-код, пустой — «Не определено».
    private fun countryLabel(code: String): String = when (code.uppercase()) {
        "" -> "Не определено"
        "RU" -> "Россия"
        "UA" -> "Украина"
        "BY" -> "Беларусь"
        "KZ" -> "Казахстан"
        "DE" -> "Германия"
        "US" -> "США"
        "PL" -> "Польша"
        "IL" -> "Израиль"
        "LV" -> "Латвия"
        "LT" -> "Литва"
        "EE" -> "Эстония"
        "GB" -> "Великобритания"
        "FR" -> "Франция"
        "IT" -> "Италия"
        "ES" -> "Испания"
        "NL" -> "Нидерланды"
        "FI" -> "Финляндия"
        "CZ" -> "Чехия"
        "TR" -> "Турция"
        "GE" -> "Грузия"
        "AM" -> "Армения"
        "AZ" -> "Азербайджан"
        "UZ" -> "Узбекистан"
        "KG" -> "Киргизия"
        "MD" -> "Молдова"
        "CA" -> "Канада"
        "TH" -> "Таиланд"
        "RS" -> "Сербия"
        "BG" -> "Болгария"
        "CN" -> "Китай"
        else -> code.uppercase()
    }

    // География посетителей: страна по client_ip (GeoIpService). Уникальных IP — тысячи (не 250k
    // строк), поэтому группируем в SQL по IP, а сведение IP→страна и повторную агрегацию делаем в
    // Kotlin (без колонки country в tbl_events, без миграции — см. DEVELOPMENT.md «статистика»).
    fun getCountryBreakdown(database: KaraokeConnection = WORKING_DATABASE, limit: Int = 30): List<NamedCountDto> {
        val connection = database.getConnection() ?: return emptyList()
        val ipCounts = LinkedHashMap<String, Int>()
        var statement: Statement? = null
        var rs: ResultSet? = null
        try {
            statement = connection.createStatement()
            rs = statement.executeQuery(
                "select client_ip, count(*) as cnt from tbl_events " +
                    "where client_ip is not null and client_ip <> '' group by client_ip"
            )
            while (rs.next()) ipCounts[rs.getString("client_ip")] = rs.getInt("cnt")
        } catch (e: SQLException) {
            e.printStackTrace()
            return emptyList()
        } finally {
            try { rs?.close(); statement?.close() } catch (e: SQLException) { e.printStackTrace() }
        }
        // Лимит внешних резолвов за один показ дашборда — кэш наполнится за несколько обновлений.
        val countries = GeoIpService.resolveMany(ipCounts.keys, maxFetch = 150)
        val byCountry = HashMap<String, Int>()
        for ((ip, cnt) in ipCounts) {
            val c = countries[ip.split(",").first().trim()] ?: ""
            byCountry[c] = (byCountry[c] ?: 0) + cnt
        }
        return byCountry.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { NamedCountDto(countryLabel(it.key), it.value) }
    }

    fun getSummary(database: KaraokeConnection = WORKING_DATABASE): StatsSummaryDto {
        val total = scalarInt(database, "select count(*) from tbl_events where last_update is not null")
        val uniqueVisitors = scalarInt(database, "select count(distinct anon_id) from tbl_events where anon_id is not null and anon_id <> ''")
        val registeredEvents = scalarInt(database, "select count(*) from tbl_events where site_user_id > 0")
        val uniqueUsers = scalarInt(database, "select count(distinct site_user_id) from tbl_events where site_user_id > 0")
        val today = scalarInt(database, "select count(*) from tbl_events where last_update >= date_trunc('day', now())")
        val d7 = scalarInt(database, "select count(*) from tbl_events where last_update >= now() - interval '7 days'")
        val d30 = scalarInt(database, "select count(*) from tbl_events where last_update >= now() - interval '30 days'")
        val topChannel = getChannelBreakdown(database).maxByOrNull { it.count }
        return StatsSummaryDto(
            totalEvents = total,
            uniqueVisitors = uniqueVisitors,
            registeredEvents = registeredEvents,
            uniqueRegisteredUsers = uniqueUsers,
            eventsToday = today,
            events7d = d7,
            events30d = d30,
            topChannel = topChannel?.name ?: "-",
            topChannelCount = topChannel?.count ?: 0,
        )
    }

    fun getEventsByType(database: KaraokeConnection = WORKING_DATABASE, fromDays: Int? = null): List<NamedCountDto> {
        val result = mutableListOf<NamedCountDto>()
        val where = if (fromDays != null && fromDays > 0)
            "last_update is not null and last_update >= now() - interval '$fromDays days'"
        else "last_update is not null"
        val connection = database.getConnection() ?: return emptyList()
        var statement: Statement? = null
        var rs: ResultSet? = null
        try {
            statement = connection.createStatement()
            rs = statement.executeQuery("select event_type, count(*) as cnt from tbl_events where $where group by event_type order by cnt desc")
            while (rs.next()) {
                val et = rs.getString("event_type") ?: "неизвестно"
                result.add(NamedCountDto(et, rs.getInt("cnt")))
            }
        } catch (e: SQLException) {
            e.printStackTrace()
        } finally {
            try { rs?.close(); statement?.close() } catch (e: SQLException) { e.printStackTrace() }
        }
        return result
    }

    // Временной ряд по дням. mode: "all" — одна линия; "type" — линии по event_type; "detail" —
    // линии по детальной комбинации (player: перемотка/старт/…, соцсети, платформы, UI).
    fun getEventsTimeSeries(database: KaraokeConnection = WORKING_DATABASE, days: Int = 30, mode: String = "all"): List<TimePointDto> {
        val result = mutableListOf<TimePointDto>()
        val safeDays = days.coerceIn(1, 365)
        val detailed = mode == "detail"
        val byType = mode == "type"
        val dimCol = when { detailed -> "event_type, detail," ; byType -> "event_type," ; else -> "" }
        val dimGroup = when { detailed -> ", event_type, detail" ; byType -> ", event_type" ; else -> "" }
        val from = if (detailed) "(select *, $DETAIL_CASE as detail from tbl_events) t" else "tbl_events"
        val sql = """
            select to_char(date_trunc('day', last_update), 'YYYY-MM-DD') as d, $dimCol count(*) as cnt
            from $from
            where last_update is not null and last_update >= now() - interval '$safeDays days'
            group by d$dimGroup
            order by d asc
        """.trimIndent()
        val connection = database.getConnection() ?: return emptyList()
        var statement: Statement? = null
        var rs: ResultSet? = null
        try {
            statement = connection.createStatement()
            rs = statement.executeQuery(sql)
            while (rs.next()) {
                val series = when {
                    detailed -> detailLabel(rs.getString("event_type"), rs.getString("detail"))
                    byType -> rs.getString("event_type") ?: "неизвестно"
                    else -> "all"
                }
                result.add(TimePointDto(
                    date = rs.getString("d") ?: "",
                    eventType = series,
                    count = rs.getInt("cnt"),
                ))
            }
        } catch (e: SQLException) {
            e.printStackTrace()
        } finally {
            try { rs?.close(); statement?.close() } catch (e: SQLException) { e.printStackTrace() }
        }
        return result
    }

    // Человекочитаемая подпись комбинации event_type + деталь (link_type/rest_name/…). detail —
    // компактный ключ, посчитанный в SQL с контролируемой кардинальностью (без сырого link_name
    // там, где он высококардинален — позиции seek в секундах, секунды engagement).
    private fun detailLabel(eventType: String?, detail: String?): String {
        val d = detail ?: ""
        return when (eventType) {
            "player" -> when (d) {
                "shown" -> "Плеер: показан"
                "opened" -> "Плеер: открыт из списка"
                "play" -> "Плеер: старт"
                "pause" -> "Плеер: пауза"
                "seek" -> "Плеер: перемотка"
                "export" -> "Плеер: экспорт стема"
                "progress" -> "Плеер: прогресс прослушивания"
                "ended" -> "Плеер: завершение трека"
                else -> "Плеер: $d"
            }
            "callRest" -> when (d) {
                "main" -> "Просмотр: главная"
                "zakroma" -> "Просмотр: закрома"
                "filter" -> "Просмотр: поиск"
                "song" -> "Просмотр: страница песни"
                else -> "Просмотр: $d"
            }
            "clickToLink" -> when {
                d.startsWith("linkToSocialNetwork") -> "Соцсеть: ${d.substringAfter(':', "")}"
                d.startsWith("linkToSong") -> "Ссылка на песню: ${d.substringAfter(':', "")}"
                else -> "Клик: $d"
            }
            "ui" -> when (d) {
                "navigate" -> "UI: навигация"
                "theme" -> "UI: смена темы"
                "scroll" -> "UI: скролл страницы"
                else -> "UI: $d"
            }
            "play" -> "Видео на странице"
            "engagement" -> "Время на странице"
            else -> (eventType ?: "неизвестно") + (if (d.isNotEmpty()) ": $d" else "")
        }
    }

    // SQL-выражение «детали» события с контролируемой кардинальностью: для player/engagement сырой
    // link_name (секунды) НЕ берётся — только link_type; для clickToLink — link_type:link_name
    // (низкая кардинальность: соцсеть/платформа); для callRest — rest_name.
    private val DETAIL_CASE = """
        case
            when event_type = 'callRest' then coalesce(rest_name, '')
            when event_type = 'clickToLink' then coalesce(link_type, '') || coalesce(nullif(':' || link_name, ':'), '')
            when event_type = 'player' then coalesce(link_type, '')
            when event_type = 'ui' then coalesce(link_type, '')
            else ''
        end
    """.trimIndent()

    // Детализация событий по комбинациям event_type + link_type/rest_name/link_name. eventType в
    // DTO — для drill-down по клику на сегмент донат-графика «Типы событий». fromDays — опц. период.
    fun getEventsDetailed(database: KaraokeConnection = WORKING_DATABASE, fromDays: Int? = null, limit: Int = 60): List<DetailCountDto> {
        val result = mutableListOf<DetailCountDto>()
        val where = if (fromDays != null && fromDays > 0)
            "last_update is not null and last_update >= now() - interval '$fromDays days'"
        else "last_update is not null"
        val sql = """
            select event_type, detail, count(*) as cnt
            from (
                select event_type, $DETAIL_CASE as detail
                from tbl_events
                where $where
            ) t
            group by event_type, detail
            order by cnt desc
            limit $limit
        """.trimIndent()
        val connection = database.getConnection() ?: return emptyList()
        var statement: Statement? = null
        var rs: ResultSet? = null
        try {
            statement = connection.createStatement()
            rs = statement.executeQuery(sql)
            while (rs.next()) {
                val et = rs.getString("event_type") ?: ""
                result.add(DetailCountDto(
                    eventType = et,
                    name = detailLabel(et, rs.getString("detail")),
                    count = rs.getInt("cnt"),
                ))
            }
        } catch (e: SQLException) {
            e.printStackTrace()
        } finally {
            try { rs?.close(); statement?.close() } catch (e: SQLException) { e.printStackTrace() }
        }
        return result
    }

    // Всего строк топа = уникальные залогиненные (site_user_id>0) + уникальные анонимы (по anon_id
    // среди событий без привязки к пользователю). Для серверной пагинации.
    fun getTopUsersCount(database: KaraokeConnection = WORKING_DATABASE): Int =
        scalarInt(database, "select count(distinct site_user_id) from tbl_events where site_user_id > 0") +
        scalarInt(database, "select count(distinct anon_id) from tbl_events where site_user_id = 0 and anon_id is not null and anon_id <> ''")

    // Топ «пользователей»: сначала все залогиненные (kind=0), затем анонимы-бакеты по anon_id
    // (kind=1). Анонимные строки: site_user_id=0, anonId=anon_id (для drill-down), без имени/email/
    // premium. Одним запросом с признаком типа, чтобы серверная пагинация «залогиненные первыми»
    // была консистентной поверх limit/offset.
    fun getTopUsers(database: KaraokeConnection = WORKING_DATABASE, limit: Int = 50, offset: Int = 0): List<TopUserDto> {
        val result = mutableListOf<TopUserDto>()
        val sql = """
            select case when e.site_user_id > 0 then 0 else 1 end as kind,
                   e.site_user_id,
                   case when e.site_user_id > 0 then '' else e.anon_id end as anon_key,
                   u.display_name, u.email,
                   (u.is_premium or u.is_permanent_premium) as premium,
                   count(*) as cnt, max(e.last_update) as last_activity
            from tbl_events e
            left join tbl_site_users u on u.id = e.site_user_id
            where e.site_user_id > 0 or (e.anon_id is not null and e.anon_id <> '')
            group by kind, e.site_user_id, anon_key, u.display_name, u.email, u.is_premium, u.is_permanent_premium
            order by kind asc, cnt desc, e.site_user_id asc, anon_key asc
            limit $limit offset $offset
        """.trimIndent()
        val connection = database.getConnection() ?: return emptyList()
        var statement: Statement? = null
        var rs: ResultSet? = null
        try {
            statement = connection.createStatement()
            rs = statement.executeQuery(sql)
            while (rs.next()) {
                result.add(TopUserDto(
                    siteUserId = rs.getLong("site_user_id"),
                    anonId = rs.getString("anon_key") ?: "",
                    displayName = rs.getString("display_name") ?: "",
                    email = rs.getString("email") ?: "",
                    premium = rs.getBoolean("premium"),
                    eventCount = rs.getInt("cnt"),
                    lastActivity = rs.getTimestamp("last_activity"),
                ))
            }
        } catch (e: SQLException) {
            e.printStackTrace()
        } finally {
            try { rs?.close(); statement?.close() } catch (e: SQLException) { e.printStackTrace() }
        }
        return result
    }
}
