package com.svoemesto.karaokeapp.services

import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.model.News
import com.svoemesto.karaokeapp.model.Song
import java.sql.SQLException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

/**
 * Автоматическое создание двух независимых видов новостей о песне (specs/101-song-news-flag,
 * заменяет прежний единый механизм specs/089-auto-news-song-release/specs/092-fix-auto-news-triggers):
 *
 * - **«Доступна»** (`category="premium"`) — [detectAndAnnounceAvailability], вызывается ТОЛЬКО из
 *   `karaoke-web` `MainController.doChangeRecords` в момент применения синхронизации, когда
 *   обнаруживается переход флага [Song.newsAvailableAnnounced] false→true.
 * - **«В эфире»** (`category="air"`) — [checkOnAirWindow], вызывается ТОЛЬКО из `karaoke-web`
 *   `SongReleaseAnnouncementScheduler` (плановая проверка на проде, ~раз в 5 минут) или создаётся
 *   вручную администратором. Синхронизация и апрув задания редактора больше НЕ создают эту новость
 *   напрямую (FR-006/FR-007 spec.md).
 *
 * Ни один из двух механизмов не использует отдельную вспомогательную таблицу учёта
 * (`tbl_song_news_announced` удалена) — идемпотентность «доступна» строится на самом флаге песни,
 * идемпотентность «в эфире» — на узком скользящем окне проверки + существовании новости
 * ([News.existsAutoAnnouncement]).
 *
 * @see docs/features/dual-db-sync.md
 */
object SongReleaseAnnouncementService {
    // Тот же порядок величины, что SongSyncTarget.rowChunkSize (25) — тяжёлые текстовые поля/маркеры
    // одной песни делают полную загрузку тысяч строк разом OOM-опасной (см.
    // specs/082-fix-import-folder-oom, тот же класс бага).
    private const val CHUNK_SIZE = 25

    // Запас в 2× периодичность плановой проверки (~5 минут) — компенсирует дрейф между тиками
    // @Scheduled(fixedDelay=...), который не гарантирует точного попадания в границы 5-минутных
    // интервалов (research.md п.4). Дубли внутри окна отсекаются существование-проверкой в
    // checkOnAirWindow, а не сужением окна.
    private const val ON_AIR_WINDOW_LOOKBACK_MINUTES = 10L

    /** Альбом и год песни в виде суффикса `" (альбом «X», Y)"` для заголовка новости — пустая строка, если ни альбом, ни год не заполнены. */
    private fun albumYearSuffix(song: Song): String {
        val parts = mutableListOf<String>()
        if (song.album.isNotBlank()) parts.add("альбом «${song.album}»")
        if (song.year > 0) parts.add(song.year.toString())
        return if (parts.isEmpty()) "" else " (" + parts.joinToString(", ") + ")"
    }

    /** Автор + альбом/год одной строкой для тела новости. */
    private fun bodyDetails(song: Song): String {
        val parts = mutableListOf(song.author)
        if (song.album.isNotBlank()) parts.add("альбом «${song.album}»")
        if (song.year > 0) parts.add(song.year.toString())
        return parts.joinToString(", ")
    }

    /**
     * Детекция перехода флага [Song.newsAvailableAnnounced] false→true при применении синхронизации
     * на сервере (FR-004 spec.md) — вызывается ПОСЛЕ того, как соответствующий `UPDATE`/`INSERT` в
     * `tbl_songs` уже применён. [wasAvailableBefore] — значение флага ДО применения этого конкретного
     * изменения (читает вызывающий код заранее, см. `MainController.doChangeRecords`); `false` для
     * совсем новой (только что вставленной) строки. Если сервер уже хранил `true`, переход не
     * обнаруживается повторно — новость не дублируется (идемпотентно без отдельной таблицы учёта).
     * Ошибки логируются и не пробрасываются — сбой детекции не должен ронять уже применённую
     * синхронизацию.
     *
     * @return true, если новость была создана в этом вызове.
     */
    fun detectAndAnnounceAvailability(
        database: KaraokeConnection,
        storageService: KaraokeStorageService,
        storageApiClient: StorageApiClient,
        songId: Long,
        wasAvailableBefore: Boolean,
    ): Boolean {
        if (wasAvailableBefore) return false
        return try {
            val song =
                Song.loadFromDbById(songId, database = database, storageService = storageService, storageApiClient = storageApiClient)
                    ?: return false
            if (!song.newsAvailableAnnounced) return false
            News.createAutoAnnouncement(
                songId = song.id,
                title = "Новая песня: ${song.author} — ${song.songName}${albumYearSuffix(song)}",
                body = "Песня «${song.songName}» (${bodyDetails(song)}) появилась в коллекции.",
                link = "/song?id=${song.id}",
                category = "premium",
                database = database,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
            true
        } catch (e: Exception) {
            println("SongReleaseAnnouncementService.detectAndAnnounceAvailability error (songId=$songId): ${e.message}")
            false
        }
    }

    /**
     * Момент "сейчас" в московской таймзоне — та же точка отсчёта, что использует [Song.onAir], чтобы
     * окно проверки было согласовано с тем, что в итоге проверяет `isPubliclyWatchable` на загруженных
     * полных объектах.
     */
    private fun nowMoscow(): Date = Calendar.getInstance(TimeZone.getTimeZone("Europe/Moscow")).time

    /** Тот же парсинг, что использует [Song.dateTimePublish] — `null`, если поля пустые/некорректные. */
    private fun parseDateTimePublish(
        date: String,
        time: String,
    ): Date? =
        if (date.isBlank() || time.isBlank()) {
            null
        } else {
            try {
                // Явная таймзона MSK: в контейнере karaoke-app JVM-локаль = UTC (TZ env не
                // применяется к SimpleDateFormat), а publish_date/publish_time в БД
                // интерпретируются как московское время. Без явной таймзоны парсинг
                // даёт UTC-время, и сравнение с nowMoscow() (MSK) даёт ошибку в 3 часа.
                SimpleDateFormat("dd.MM.yy HH:mm")
                    .apply {
                        timeZone = TimeZone.getTimeZone("Europe/Moscow")
                    }.parse("$date $time")
            } catch (_: Exception) {
                null
            }
        }

    /**
     * Дешёвый первый проход для механизма «в эфире» (research.md п.4): только `id` + текстовые
     * `publish_date`/`publish_time` для песен со статусом >= 6 (без текста/маркеров/base64) —
     * оставляет только те id, чей вычисленный момент эфира попал в скользящее окно
     * `(now - lookbackMinutes, now]`. Старые (давно вышедшие в эфир) песни физически никогда не
     * попадают в это окно — поэтому очистка `tbl_news` не создаёт по ним новостей задним числом.
     */
    private fun windowCandidateIds(
        database: KaraokeConnection,
        lookbackMinutes: Long,
    ): List<Long> {
        val connection = database.getConnection() ?: return emptyList()
        val result = mutableListOf<Long>()
        val now = nowMoscow()
        val windowStart = Date(now.time - lookbackMinutes * 60_000L)
        try {
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT id, publish_date, publish_time FROM tbl_songs WHERE id_status >= 6").use { rs ->
                    while (rs.next()) {
                        val date = rs.getString("publish_date") ?: ""
                        val time = rs.getString("publish_time") ?: ""
                        val publishAt = parseDateTimePublish(date, time) ?: continue
                        if (publishAt > windowStart && publishAt <= now) {
                            result.add(rs.getLong("id"))
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            println("SongReleaseAnnouncementService.windowCandidateIds SQLException: ${e.message}")
        }
        return result
    }

    /**
     * Плановая проверка «песня вышла в эфир» (FR-006 spec.md) — вызывается ТОЛЬКО из
     * `SongReleaseAnnouncementScheduler` (~раз в 5 минут, PROD). Рассматривает только кандидатов из
     * [windowCandidateIds] (не весь каталог), загружает их полными объектами чанками, фильтрует по
     * [Song.isPubliclyWatchable], и для каждого — создаёт ровно одну новость категории `"air"`, если
     * такой (автоматической или созданной вручную администратором) для этой песни ещё нет
     * ([News.existsAutoAnnouncement]). Ошибки логируются и не пробрасываются.
     *
     * @return id песен, для которых в этом вызове была создана новость.
     */
    fun checkOnAirWindow(
        database: KaraokeConnection,
        storageService: KaraokeStorageService,
        storageApiClient: StorageApiClient,
        lookbackMinutes: Long = ON_AIR_WINDOW_LOOKBACK_MINUTES,
    ): List<Long> {
        val created = mutableListOf<Long>()
        try {
            val candidateIds = windowCandidateIds(database, lookbackMinutes)
            candidateIds.chunked(CHUNK_SIZE).forEach { chunk ->
                val loaded =
                    Song.loadListFromDb(
                        args = mapOf("ids" to chunk.joinToString(",")),
                        database = database,
                        storageService = storageService,
                        storageApiClient = storageApiClient,
                    )
                loaded.filter { it.isPubliclyWatchable }.forEach { song ->
                    val link = "/song?id=${song.id}"
                    if (!News.existsAnnouncement(songId = song.id, link = link, category = "air", database = database)) {
                        News.createAutoAnnouncement(
                            songId = song.id,
                            title = "Новая песня: ${song.author} — ${song.songName}${albumYearSuffix(song)}",
                            body = "Песня «${song.songName}» (${bodyDetails(song)}) вышла в эфир.",
                            link = link,
                            category = "air",
                            database = database,
                            storageService = storageService,
                            storageApiClient = storageApiClient,
                        )
                        created.add(song.id)
                    }
                }
            }
        } catch (e: Exception) {
            println("SongReleaseAnnouncementService.checkOnAirWindow error: ${e.message}")
        }
        return created
    }

    /**
     * Одноразовый backfill флага «доступна для новости» (FR-012 spec.md) — выставляет
     * [Song.newsAvailableAnnounced] в `true` через обычный `Song.saveToDb()` (НЕ raw SQL, см.
     * research.md п.3 — гарантирует консистентный `recordhash` без риска разойтись между LOCAL/PROD)
     * для песен, уже удовлетворяющих [Song.isContentReady], но у которых флаг ещё `false`. Не создаёт
     * никакой новости — единственная цель backfill'а именно в этом, иначе первая же обычная
     * синхронизация уже готовой песни создала бы новость «доступна» из ничего. Вызывается
     * администратором вручную, отдельно на `Connection.local()` и на `Connection.remote()` — до
     * очистки `tbl_news`/удаления `tbl_song_news_announced`.
     *
     * @return число песен, у которых флаг был выставлен в этом вызове.
     */
    fun backfillNewsAvailableFlag(
        database: KaraokeConnection,
        storageService: KaraokeStorageService,
        storageApiClient: StorageApiClient,
    ): Int {
        var count = 0
        try {
            val hashes = Song.listHashes(database = database, whereText = "WHERE id_status = 6") ?: emptyList()
            hashes.map { it.id }.chunked(CHUNK_SIZE).forEach { chunk ->
                val loaded =
                    Song.loadListFromDb(
                        args = mapOf("ids" to chunk.joinToString(",")),
                        database = database,
                        storageService = storageService,
                        storageApiClient = storageApiClient,
                    )
                loaded.filter { it.isContentReady && !it.newsAvailableAnnounced }.forEach { song ->
                    song.newsAvailableAnnounced = true
                    song.saveToDb()
                    count++
                }
            }
        } catch (e: Exception) {
            println("SongReleaseAnnouncementService.backfillNewsAvailableFlag error: ${e.message}")
        }
        return count
    }
}
