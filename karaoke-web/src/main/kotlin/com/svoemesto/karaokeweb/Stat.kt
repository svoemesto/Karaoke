package com.svoemesto.karaokeweb

import com.svoemesto.karaokeapp.KaraokeConnection
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.sql.Timestamp
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

// getWebEvents()/getStatBySong() перенесены в com.svoemesto.karaokeapp.model.StatsByEvents (нужны
// и для webvue3-админки, и для этой Thymeleaf-страницы) — не дублировать здесь снова, см.
// MainController.doStatBySong()/doWebEvents(). Здесь остались только счётчики для главной/закромов,
// у которых нет аналога в karaoke-app.
//
// Формулы счётчиков (применяется ко всем — песни с тегом SKIP игнорируются):
//   total       — все записи tbl_settings, кроме SKIP
//   collection  — id_status >= 3 AND непустой source_markers (можно проиграть в онлайн-плеере
//                 премиум-пользователю — тот же фильтр, что рисует зелёную монетку в закромах)
//   onAir       — подмножество collection с истёкшим publish_date/publish_time
//   subscription— collection − onAir (на бэкенде, одним SQL)
//   inWork      — total − collection (сколько ещё не дошли до стадии "можно проиграть")
//
// Все значения кешируются в AtomicInteger и обновляются по cron раз в час (@See StatsCacheScheduler
// + метод refreshCache() ниже). Холодный старт инициирует синхронное обновление кеша при первом
// обращении, чтобы endpoint /api/public/stats не вернул нули после рестарта приложения, если
// scheduler ещё не успел отработать (Spring не гарантирует порядок инициализации Service/Controller).
object StatBySong {
    private val cachedTotal = AtomicInteger(-1)
    private val cachedCollection = AtomicInteger(-1)
    private val cachedOnAir = AtomicInteger(-1)
    private val cachedExclusive = AtomicInteger(-1)
    private val cachedInWork = AtomicInteger(-1)

    // Фильтр «без SKIP» в SQL. В settings.tags теги через пробел; сравнение по элементу массива
    // надёжнее 'tags LIKE %SKIP%' (не словит 'noSKIP' или подстроку внутри другого тега).
    private const val SKIP_FILTER = "(tags IS NULL OR NOT ('SKIP' = ANY(string_to_array(upper(coalesce(tags,'')), ' '))))"

    // Фильтр «можно проиграть в онлайн-плеере премиум-пользователю»: нижняя граница готовности
    // контента — id_status дошёл до PROJECT_CREATE, и есть непустые source_markers (markers —
    // последний из трёх шагов stemsReady в PublicPlayerController.stemsReady: id_status>=3,
    // mp3 accompaniment+vocal в MinIO, source_markers есть; SQL-фильтр ниже берёт самый
    // последний/стабильный из этих сигналов — наличие маркеров).
    private const val CONTENT_READY_FILTER =
        "id_status >= 3 AND btrim(coalesce(source_markers, '')) != ''"

    fun getCountSongsExclusive(database: KaraokeConnection = WORKING_DATABASE): Int =
        cachedExclusive.get().also { ensureCacheInitialized(database) }

    fun getCountSongsOnAir(database: KaraokeConnection = WORKING_DATABASE): Int =
        cachedOnAir.get().also { ensureCacheInitialized(database) }

    fun getCountSongsInCollection(database: KaraokeConnection = WORKING_DATABASE): Int =
        cachedCollection.get().also { ensureCacheInitialized(database) }

    fun getCountSongsInWork(database: KaraokeConnection = WORKING_DATABASE): Int =
        cachedInWork.get().also { ensureCacheInitialized(database) }

    fun getCountSongsTotal(database: KaraokeConnection = WORKING_DATABASE): Int =
        cachedTotal.get().also { ensureCacheInitialized(database) }

    // Вызывается из StatsCacheScheduler каждый час и при холодном старте. Под synchronized —
    // чтобы два параллельных первых запроса из REST и Thymeleaf не сделали двойной пересчёт.
    @Synchronized
    fun refreshCache(database: KaraokeConnection = WORKING_DATABASE) {
        val total =
            runCountQuery(
                database,
                """select count(DISTINCT id) as cnt from tbl_settings where $SKIP_FILTER;""",
            )
        val collection =
            runCountQuery(
                database,
                """select count(DISTINCT id) as cnt from tbl_settings where $CONTENT_READY_FILTER AND $SKIP_FILTER;""",
            )
        val onAir =
            runCountQuery(
                database,
                """select count(DISTINCT id) as cnt
                 from tbl_settings
                 where $CONTENT_READY_FILTER
                   AND $SKIP_FILTER
                   and publish_date != ''
                   and publish_date is not null
                   and publish_time != ''
                   and publish_time is not null
                   and to_timestamp(CONCAT(publish_date, ' ', publish_time), 'DD.MM.YY HH24:MI') <= current_timestamp;""",
            )
        // subscription = collection − onAir — на бэкенде одним SQL (точнее «два запроса + вычитание
        // в Kotlin»: один обход БД дороже, чем оставить так, и кол-во запросов остаётся прежним).
        val exclusive = (collection - onAir).coerceAtLeast(0)
        val inWork = (total - collection).coerceAtLeast(0)

        cachedTotal.set(total)
        cachedCollection.set(collection)
        cachedOnAir.set(onAir)
        cachedExclusive.set(exclusive)
        cachedInWork.set(inWork)
        println(
            "[${Timestamp.from(Instant.now())}] StatBySong.refreshCache: " +
                "total=$total, collection=$collection, onAir=$onAir, " +
                "subscription=$exclusive, inWork=$inWork",
        )
    }

    // Гарантирует, что кеш инициализирован перед первым чтением. Если значение -1 (cold start),
    // синхронно считает. Дальнейшие вызовы — мгновенный возврат из AtomicInteger.
    private fun ensureCacheInitialized(database: KaraokeConnection) {
        if (cachedTotal.get() < 0) {
            refreshCache(database)
        }
    }

    private fun runCountQuery(
        database: KaraokeConnection,
        sql: String,
    ): Int {
        val connection = database.getConnection()
        if (connection == null) {
            println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных ${database.name}")
            return 0
        }
        var statement: Statement? = null
        var rs: ResultSet? = null
        try {
            statement = connection.createStatement()
            rs = statement.executeQuery(sql)
            while (rs.next()) {
                return rs.getInt("cnt")
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
        return 0
    }
}
