package com.svoemesto.karaokeweb
import com.svoemesto.karaokeapp.KaraokeConnection
import org.slf4j.LoggerFactory
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.sql.Timestamp
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

// getWebEvents()/getStatBySong() перенесены в com.svoemesto.karaokeapp.model.StatsByEvents (нужны
// и для webvue3-админки, и для этой Thymeleaf-страницы) — не дублировать здесь снова, см.
// MainController.doStatBySong()/doWebEvents(). Здесь остались только счётчики для главной/закромов,
// у которых нет аналога в karaoke-app.
//
// Формулы счётчиков (применяется ко всем — песни с тегом SKIP игнорируются). specs/143-song-free-
// access-window: freeNow/subscriptionOnly заменяют бывшие onAir/exclusive — правило доступа теперь
// учитывает окно в 1 календарный месяц после эфира и флаг free ("всегда бесплатно"), а не просто
// факт наступления эфира.
//   total            — все записи tbl_songs, кроме SKIP
//   collection       — id_status >= 6 AND непустой source_markers (можно проиграть в онлайн-плеере
//                      премиум-пользователю — тот же фильтр, что рисует зелёную монетку в закромах)
//   freeNow          — подмножество collection, доступное бесплатно ПРЯМО СЕЙЧАС: free=true, либо
//                      эфир наступил и ещё не прошёл 1 календарный месяц (Song.isFreelyAvailableNow)
//   subscriptionOnly — collection − freeNow (на бэкенде, одним вычитанием)
//   inWork           — total − collection (сколько ещё не дошли до стадии "можно проиграть")
//
// Все значения кешируются в AtomicInteger и обновляются по cron раз в час (@See StatsCacheScheduler
// + метод refreshCache() ниже).
//
// specs/289-fix-statbysong-cache-on-cold-start: cold-start больше НЕ блокирует HTTP-тред.
// `ensureCacheInitialized()` запускает `refreshCache()` в фоне через `bgExecutor` (single-thread,
// daemon) с single-flight guard через `refreshing`. При cold-start HTTP-запрос возвращает fallback
// (0) за < 100 мс вместо блокировки 12 сек (SC-001). Контракт WARN/INFO логов —
// см. specs/289-fix-statbysong-cache-on-cold-start/contracts/log-format.md.

/**
 * Singleton-объект Stat By Song.
 *
 * Cold-start (specs/289-fix-statbysong-cache-on-cold-start):
 * - `cachedTotal.get() == -1` означает cold-start (ещё не прогрет).
 * - `ensureCacheInitialized()` НЕ блокирует HTTP-тред — запускает `refreshCache()` в фоне.
 * - HTTP-запрос получает fallback (0) немедленно; через ~12 сек (когда refresh завершится)
 *   getter'ы начнут возвращать актуальные значения.
 * - Single-flight guard через `AtomicBoolean refreshing` — только ОДИН поток запускает refresh.
 * - При ошибке refresh — WARN `infra.cache.statbysong - cache:refreshFailed`, getter'ы продолжают
 *   возвращать fallback.
 *
 * Логирование (per local-0005):
 * - WARN `infra.cache.statbysong - cache:coldStart triggering background refresh` — при cold-start.
 * - INFO `infra.cache.statbysong - cache:refreshed total=N ... durationMs=X` — при успехе.
 * - WARN `infra.cache.statbysong - cache:refreshFailed ...` — при ошибке.
 *
 * @see archive/docs/features/dual-db-sync.md
 * @see archive/docs/features/song-free-access.md
 * @see livedocs/architecture/decisions/local-0005-structured-logging-karaoke-app.md
 * @see specs/289-fix-statbysong-cache-on-cold-start/contracts/log-format.md
 */
object StatBySong {
    private val cachedTotal = AtomicInteger(-1)
    private val cachedCollection = AtomicInteger(-1)
    private val cachedFreeNow = AtomicInteger(-1)
    private val cachedSubscriptionOnly = AtomicInteger(-1)
    private val cachedInWork = AtomicInteger(-1)

    // specs/289: SLF4J-логгер для cold-start и ошибок refresh.
    private val cacheLog = LoggerFactory.getLogger("infra.cache.statbysong")

    // specs/289: single-flight guard. Только ОДИН поток выигрывает compareAndSet(false, true) при
    // cold-start; остальные потоки возвращают fallback (0) без запуска второго refresh.
    private val refreshing = AtomicBoolean(false)

    // specs/289: background executor для cold-start refresh. Single-thread, daemon — не блокирует
    // JVM shutdown. Не требует явного shutdown().
    private val bgExecutor: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "StatBySong-ColdStart").apply { isDaemon = true }
        }

    // Взводится karaoke-app (через InternalStatsController.markDirty) при сохранении/синхронизации
    // песни, у которой мог измениться free-статус — свободно/по подписке (specs/143-song-free-
    // access-window). Часовой cron (refreshHourly) остаётся единственным источником истины для
    // перехода "наступил эфир" (для него ничего не "взводится" — время просто проходит), см.
    // StatsCacheScheduler.refreshIfDirty() — лёгкий ежеминутный тик, который лишь проверяет флаг,
    // не пересчитывает счётчики сам по себе.
    private val dirty = AtomicBoolean(false)

    fun markDirty() {
        dirty.set(true)
    }

    // Атомарно читает и сбрасывает флаг — вызывающий (refreshIfDirty) обязан пересчитать кеш, если
    // вернулось true, иначе взведённое состояние потеряется без пересчёта.
    fun consumeDirty(): Boolean = dirty.getAndSet(false)

    // Фильтр «без SKIP» в SQL. В song.tags теги через пробел; сравнение по элементу массива
    // надёжнее 'tags LIKE %SKIP%' (не словит 'noSKIP' или подстроку внутри другого тега).
    private const val SKIP_FILTER = "(tags IS NULL OR NOT ('SKIP' = ANY(string_to_array(upper(coalesce(tags,'')), ' '))))"

    // Фильтр «можно проиграть в онлайн-плеере премиум-пользователю»: нижняя граница готовности
    // контента — id_status дошёл до READY (6, specs/022-song-status-lifecycle), и есть непустые
    // source_markers (markers — последний из трёх шагов stemsReady в
    // PublicPlayerController.stemsReady: id_status>=6, mp3 accompaniment+vocal в MinIO,
    // source_markers есть; SQL-фильтр ниже берёт самый последний/стабильный из этих сигналов —
    // наличие маркеров).
    private const val CONTENT_READY_FILTER =
        "id_status >= 6 AND btrim(coalesce(source_markers, '')) != ''"

    // specs/289 (FR-009): getter'ы возвращают 0 при cold-start (`cachedTotal.get() < 0`) — НЕ -1.
    // Безопасное значение для UI (главная показывает «0», не 500-ошибку).
    // Cold-start background refresh запускается через `also { ensureCacheInitialized(database) }` —
    // НЕ блокирует HTTP-тред (ensureCacheInitialized использует CAS + bgExecutor).
    fun getCountSongsSubscriptionOnly(database: KaraokeConnection = WORKING_DATABASE): Int =
        cachedSubscriptionOnly.get().coerceAtLeast(0).also { ensureCacheInitialized(database) }

    fun getCountSongsFreeNow(database: KaraokeConnection = WORKING_DATABASE): Int =
        cachedFreeNow.get().coerceAtLeast(0).also { ensureCacheInitialized(database) }

    fun getCountSongsInCollection(database: KaraokeConnection = WORKING_DATABASE): Int =
        cachedCollection.get().coerceAtLeast(0).also { ensureCacheInitialized(database) }

    fun getCountSongsInWork(database: KaraokeConnection = WORKING_DATABASE): Int =
        cachedInWork.get().coerceAtLeast(0).also { ensureCacheInitialized(database) }

    fun getCountSongsTotal(database: KaraokeConnection = WORKING_DATABASE): Int =
        cachedTotal.get().coerceAtLeast(0).also { ensureCacheInitialized(database) }

// Вызывается из StatsCacheScheduler каждый час. Под @Synchronized — чтобы два параллельных вызова
    // (scheduled + ежеминутный refreshIfDirty) не сделали двойной пересчёт.
    // specs/289: также вызывается из background executor при cold-start (см. ensureCacheInitialized).
    //
    // specs/289 (Variant A, 2026-09-01): total/collection берутся из предрассчитанных счётчиков
    // `tbl_authors.total_songs_count` / `ready_songs_count` (specs/286-author-song-counts-cache).
    // Это ускоряет refresh с ~12 сек до ~2.1 сек (total/collection: <5 мс вместо ~8 сек, freeNow
    // остаётся ~2 сек через JOIN с tbl_authors). Семантика слегка меняется: считаются песни
    // не-skip авторов (WHERE a.skip = false), а не песни без SKIP-тега. Расхождение ~56 песен
    // (skip-авторы с не-SKIP песнями).
    @Synchronized
    fun refreshCache(database: KaraokeConnection = WORKING_DATABASE) {
        val startMs = System.currentTimeMillis()
        // Быстрые SUM-агрегации по 126 авторам (~2 мс каждая).
        val total =
            runCountQuery(
                database,
                """SELECT COALESCE(SUM(total_songs_count), 0) AS cnt FROM tbl_authors WHERE skip = false;""",
            )
        val collection =
            runCountQuery(
                database,
                """SELECT COALESCE(SUM(ready_songs_count), 0) AS cnt FROM tbl_authors WHERE skip = false;""",
            )
        // freeNow остаётся через tbl_songs (зависит от publish_date — runtime, нельзя денормализовать).
        // JOIN с tbl_authors ускоряет за счёт hash на 119 авторах.
        val freeNow =
            runCountQuery(
                database,
                """SELECT count(*) AS cnt FROM tbl_songs s
                   JOIN tbl_authors a ON a.author = s.song_author
                   WHERE a.skip = false
                     AND (s.tags IS NULL OR NOT ('SKIP' = ANY(string_to_array(upper(coalesce(s.tags,'')), ' '))))
                     AND s.id_status >= 6
                     AND btrim(coalesce(s.source_markers, '')) != ''
                     AND (
                       s.free = true
                       OR (
                         s.publish_date != '' AND s.publish_date is not null
                         AND s.publish_time != '' AND s.publish_time is not null
                         AND to_timestamp(s.publish_date || ' ' || s.publish_time, 'DD.MM.YY HH24:MI') <= current_timestamp
                         AND to_timestamp(s.publish_date || ' ' || s.publish_time, 'DD.MM.YY HH24:MI') + INTERVAL '1 month' > current_timestamp
                       )
                     );""",
            )
        // subscriptionOnly = collection − freeNow — на бэкенде одним вычитанием.
        val subscriptionOnly = (collection - freeNow).coerceAtLeast(0)
        val inWork = (total - collection).coerceAtLeast(0)

        cachedTotal.set(total)
        cachedCollection.set(collection)
        cachedFreeNow.set(freeNow)
        cachedSubscriptionOnly.set(subscriptionOnly)
        cachedInWork.set(inWork)
        val durationMs = System.currentTimeMillis() - startMs

        // specs/289 (FR-007): SLF4J INFO после успешного refresh (sync или background).
        cacheLog.info(
            "cache:refreshed total={} collection={} freeNow={} subscriptionOnly={} inWork={} durationMs={}",
            total, collection, freeNow, subscriptionOnly, inWork, durationMs,
        )

        println(
            "[${Timestamp.from(Instant.now())}] StatBySong.refreshCache: " +
                "total=$total, collection=$collection, freeNow=$freeNow, " +
                "subscriptionOnly=$subscriptionOnly, inWork=$inWork, durationMs=$durationMs",
        )
    }

    // specs/289 (FR-004, FR-006, FR-008): async cold-start refresh.
    //
    // Вместо синхронного `refreshCache()` запускаем refresh в фоне:
    // - Если `cachedTotal.get() < 0` (cold-start) И `refreshing.compareAndSet(false, true)` —
    //   этот поток выигрывает CAS, логирует WARN и запускает `bgExecutor.submit { ... }`.
    // - Другие потоки (одновременные HTTP-запросы) получают `false` от CAS и сразу возвращают fallback.
    // - `finally { refreshing.set(false) }` гарантирует сброс guard даже при exception.
    //
    // Результат: HTTP-тред возвращает fallback (0) за < 100 мс вместо блокировки 12 сек (SC-001).
    // Через ~12 сек, когда background refresh завершится, getter'ы начнут возвращать актуальные значения.
    //
    // После успешного первого refresh `cachedTotal.get() >= 0` → условие `cachedTotal.get() < 0`
    // false → no-op (быстрый путь).
    @Synchronized
    private fun ensureCacheInitialized(database: KaraokeConnection) {
        if (cachedTotal.get() < 0 && refreshing.compareAndSet(false, true)) {
            cacheLog.warn("cache:coldStart triggering background refresh")
            bgExecutor.submit {
                try {
                    refreshCache(database)
                } catch (e: Exception) {
                    cacheLog.warn(
                        "cache:refreshFailed error=\"{}\" exceptionClass={}",
                        e.message, e::class.java.name, e,
                    )
                } finally {
                    refreshing.set(false)
                }
            }
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
