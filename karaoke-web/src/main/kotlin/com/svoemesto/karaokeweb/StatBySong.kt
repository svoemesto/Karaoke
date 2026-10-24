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
import java.util.concurrent.atomic.AtomicReference

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
// Все значения кешируются в ОДНОМ неизменяемом снимке и обновляются по cron раз в час (@See
// StatsCacheScheduler + метод refreshCache() ниже).
//
// specs/289-fix-statbysong-cache-on-cold-start: cold-start больше НЕ блокирует HTTP-тред.
// `ensureCacheInitialized()` запускает `refreshCache()` в фоне через `bgExecutor` (single-thread,
// daemon) с single-flight guard через `refreshing`. При cold-start HTTP-запрос возвращает fallback
// (0) за < 100 мс вместо блокировки 12 сек (SC-001). Контракт WARN/INFO логов —
// см. specs/289-fix-statbysong-cache-on-cold-start/contracts/log-format.md.
//
// BR-016 (аудит #336, OpenProject #385): ошибка БД больше не публикуется как ноль. Снимок
// заменяется только при успехе ВСЕХ трёх запросов; при сбое остаются прежние значения, а
// состояние кеша становится ERROR. Состояния отдаются наружу (`StatsCacheState`), чтобы API и
// шаблон отличали «нет данных» (warming_up) от «настоящего нуля» (ready) и от «ошибки» (error).

/**
 * Состояние кеша счётчиков главной страницы (BR-016).
 *
 * - [WARMING_UP] — ни одного успешного пересчёта: данных нет, getter'ы отдают fallback `0`.
 * - [READY] — последний пересчёт успешен: `0` в счётчике — настоящий ноль.
 * - [ERROR] — последний пересчёт упал: значения от предыдущего успеха (могут отсутствовать).
 */
enum class StatsCacheState(
    val apiValue: String,
) {
    WARMING_UP("warming_up"),
    READY("ready"),
    ERROR("error"),
}

/**
 * Согласованный снимок счётчиков + маркер состояния (BR-016).
 *
 * [hasData] отделён от [state]: после успешного пересчёта `hasData=true`, даже если пришла
 * ошибка (`state=ERROR` — показываем последние успешные цифры). Пока `hasData=false`,
 * `ensureCacheInitialized()` повторяет пересчёт на следующем запросе — «холодный» кеш не
 * залипает после неудачной первой попытки.
 */
internal data class StatBySongCounters(
    val state: StatsCacheState,
    val hasData: Boolean,
    val total: Int = 0,
    val collection: Int = 0,
    val freeNow: Int = 0,
    val subscriptionOnly: Int = 0,
    val inWork: Int = 0,
)

/**
 * Кеш счётчиков: одна атомарная ссылка на неизменяемый [StatBySongCounters].
 *
 * Атомарная замена всего набора (а не пяти отдельных `AtomicInteger`) исключает
 * «полусостояние»: читатель не увидит `total` от нового пересчёта и `collection` от прежнего,
 * а неудачный пересчёт не может записать нули в часть полей (BR-016).
 */
internal class StatBySongCache {
    private val counters = AtomicReference(StatBySongCounters(state = StatsCacheState.WARMING_UP, hasData = false))

    fun current(): StatBySongCounters = counters.get()

    fun hasData(): Boolean = counters.get().hasData

    /** Успешный пересчёт: публикуем весь набор одной операцией. */
    fun store(
        total: Int,
        collection: Int,
        freeNow: Int,
    ) {
        counters.set(
            StatBySongCounters(
                state = StatsCacheState.READY,
                hasData = true,
                total = total,
                collection = collection,
                freeNow = freeNow,
                subscriptionOnly = (collection - freeNow).coerceAtLeast(0),
                inWork = (total - collection).coerceAtLeast(0),
            ),
        )
    }

    /** Сбой: помечаем состояние, значения предыдущего успеха не трогаем (BR-016). */
    fun markFailure() {
        counters.updateAndGet { it.copy(state = StatsCacheState.ERROR) }
    }
}

/**
 * Singleton-объект Stat By Song.
 *
 * Cold-start (specs/289-fix-statbysong-cache-on-cold-start):
 * - `hasData=false` означает cold-start (ещё не прогрет).
 * - `ensureCacheInitialized()` НЕ блокирует HTTP-тред — запускает `refreshCache()` в фоне.
 * - HTTP-запрос получает fallback (0) немедленно; через ~12 сек (когда refresh завершится)
 *   getter'ы начнут возвращать актуальные значения, а [getCacheState] — `ready`.
 * - Single-flight guard через `AtomicBoolean refreshing` — только ОДИН поток запускает refresh.
 * - При ошибке refresh — WARN `infra.cache.statbysong - cache:refreshFailed`, getter'ы продолжают
 *   возвращать прежние значения (при cold-start — fallback `0`, состояние `error`/`warming_up`).
 *
 * BR-016: состояние кеша (`ready`/`warming_up`/`error`) отдаётся через [getCacheState] и попадает
 * в ответ `/api/public/stats` и в модель `main.html`.
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
    private val cache = StatBySongCache()

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

    private val totalSql = """SELECT COALESCE(SUM(total_songs_count), 0) AS cnt FROM tbl_authors WHERE skip = false;"""

    private val collectionSql = """SELECT COALESCE(SUM(ready_songs_count), 0) AS cnt FROM tbl_authors WHERE skip = false;"""

    // freeNow — Pass 297: читать из MATERIALIZED VIEW mv_songs_free_now
    // (обновляется через cron refresh_mv_songs_free_now() каждые 5 минут).
    // В MV уже применены JOIN + фильтры по skip/id_status/source_markers.
    // Здесь остаётся только runtime-фильтр по free/publish_date/publish_time.
    // До оптимизации: 2-5 сек (JOIN tbl_songs+tbl_authors с seq_scan).
    // После: ~5 мс (Index Only Scan на mv_songs_free_now_song_author_idx).
    // См. specs/297-fix-tbl-songs-perf/spec.md (FR-009) и
    // deploy/karaoke-db/44_optimize_tbl_songs.sql.
    private val freeNowSql =
        """SELECT count(*) AS cnt FROM mv_songs_free_now s
           WHERE (s.tags IS NULL OR NOT ('SKIP' = ANY(string_to_array(upper(coalesce(s.tags,'')), ' '))))
             AND (
               s.free = true
               OR (
                 s.publish_date != '' AND s.publish_date is not null
                 AND s.publish_time != '' AND s.publish_time is not null
                 AND to_timestamp(s.publish_date || ' ' || s.publish_time, 'DD.MM.YY HH24:MI') <= current_timestamp
                 AND to_timestamp(s.publish_date || ' ' || s.publish_time, 'DD.MM.YY HH24:MI') + INTERVAL '1 month' > current_timestamp
               )
             );"""

    /**
     * Состояние кеша для API/шаблона (BR-016): `ready` / `warming_up` / `error`.
     *
     * В отличие от счётчиков, состояние НЕ запускает фоновый пересчёт — его и так запускает
     * любой getter (или cron).
     */
    fun getCacheState(): StatsCacheState = cache.current().state

    /**
     * Согласованный снимок счётчиков + состояние (BR-016). Один атомарный `get`: значения и
     * состояние относятся к одному и тому же пересчёту.
     */
    internal fun getCounters(database: KaraokeConnection = WORKING_DATABASE): StatBySongCounters =
        cache.current().also { ensureCacheInitialized(database) }

    // specs/289 (FR-009): getter'ы возвращают 0 при cold-start (`hasData == false`) — НЕ -1.
    // BR-016: 0 здесь — только fallback до первого успеха; отличить его от настоящего нуля
    // позволяет [getCacheState]/[getCounters].
    // Cold-start background refresh запускается через `also { ensureCacheInitialized(database) }` —
    // НЕ блокирует HTTP-тред (ensureCacheInitialized использует CAS + bgExecutor).
    fun getCountSongsSubscriptionOnly(database: KaraokeConnection = WORKING_DATABASE): Int =
        getCounters(database).subscriptionOnly

    fun getCountSongsFreeNow(database: KaraokeConnection = WORKING_DATABASE): Int =
        getCounters(database).freeNow

    fun getCountSongsInCollection(database: KaraokeConnection = WORKING_DATABASE): Int =
        getCounters(database).collection

    fun getCountSongsInWork(database: KaraokeConnection = WORKING_DATABASE): Int =
        getCounters(database).inWork

    fun getCountSongsTotal(database: KaraokeConnection = WORKING_DATABASE): Int =
        getCounters(database).total

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
        refreshCacheInto(cache, database)
    }

    /**
     * Шов для регрессионных тестов BR-016: тот же пересчёт, но в переданный кеш (глобальное
     * состояние `object`-а в тестах не трогаем).
     *
     * Публикация набора — только если ВСЕ три запроса вернули значение. При любом сбое
     * (`null`: нет соединения / SQLException / нет строки) снимок не меняется, состояние
     * становится [StatsCacheState.ERROR].
     */
    internal fun refreshCacheInto(
        cache: StatBySongCache,
        database: KaraokeConnection,
    ) {
        val startMs = System.currentTimeMillis()
        // Быстрые SUM-агрегации по 126 авторам (~2 мс каждая).
        val total = runCountQuery(database, totalSql)
        val collection = runCountQuery(database, collectionSql)
        val freeNow = runCountQuery(database, freeNowSql)
        if (total == null || collection == null || freeNow == null) {
            cache.markFailure()
            cacheLog.warn(
                "cache:refreshFailed total={} collection={} freeNow={} — прежние значения сохранены",
                total, collection, freeNow,
            )
            return
        }
        cache.store(total, collection, freeNow)
        val subscriptionOnly = (collection - freeNow).coerceAtLeast(0)
        val inWork = (total - collection).coerceAtLeast(0)
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
    // - Если `hasData == false` (cold-start) И `refreshing.compareAndSet(false, true)` —
    //   этот поток выигрывает CAS, логирует WARN и запускает `bgExecutor.submit { ... }`.
    // - Другие потоки (одновременные HTTP-запросы) получают `false` от CAS и сразу возвращают fallback.
    // - `finally { refreshing.set(false) }` гарантирует сброс guard даже при exception.
    //
    // Результат: HTTP-тред возвращает fallback (0) за < 100 мс вместо блокировки 12 сек (SC-001).
    // Через ~12 сек, когда background refresh завершится, getter'ы начнут возвращать актуальные значения.
    //
    // BR-016: после неудачной первой попытки `hasData` остаётся false, поэтому следующий запрос
    // снова запускает пересчёт — «холодный» кеш не залипает до часового cron.
    @Synchronized
    private fun ensureCacheInitialized(database: KaraokeConnection) {
        if (!cache.hasData() && refreshing.compareAndSet(false, true)) {
            cacheLog.warn("cache:coldStart triggering background refresh")
            bgExecutor.submit {
                try {
                    refreshCache(database)
                } catch (e: Exception) {
                    cache.markFailure()
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

    /**
     * Счётчик или `null` — «значения нет» (BR-016). Раньше любой сбой возвращал `0`, и вызывающий
     * не мог отличить его от настоящего нуля.
     */
    private fun runCountQuery(
        database: KaraokeConnection,
        sql: String,
    ): Int? {
        val connection = database.getConnection()
        if (connection == null) {
            println("[${Timestamp.from(Instant.now())}] Невозможно установить соединение с базой данных ${database.name}")
            cacheLog.warn("cache:queryFailed database={} reason=noConnection", database.name)
            return null
        }
        var statement: Statement? = null
        var rs: ResultSet? = null
        try {
            statement = connection.createStatement()
            rs = statement.executeQuery(sql)
            if (rs.next()) {
                return rs.getInt("cnt")
            }
            cacheLog.warn("cache:queryFailed database={} reason=noRow", database.name)
        } catch (e: SQLException) {
            cacheLog.warn(
                "cache:queryFailed database={} reason=sqlException exceptionClass={}",
                database.name, e::class.java.name, e,
            )
        } finally {
            try {
                rs?.close()
                statement?.close()
            } catch (e: SQLException) {
                cacheLog.warn("cache:closeFailed exceptionClass={}", e::class.java.name, e)
            }
        }
        return null
    }
}
