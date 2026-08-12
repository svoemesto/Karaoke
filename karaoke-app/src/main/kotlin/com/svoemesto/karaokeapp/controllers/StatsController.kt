package com.svoemesto.karaokeapp.controllers

import com.svoemesto.karaokeapp.Connection
import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.model.MonetizationStats
import com.svoemesto.karaokeapp.model.StatsByEvents
import com.svoemesto.karaokeapp.model.StatsCacheKey
import com.svoemesto.karaokeapp.services.StatsCache
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.sql.SQLException

// Аналитика по событиям сайта (бывшие /statbysong и /webevents в karaoke-web) — перенесена
// сюда, чтобы её показывала админка webvue3, а не публичный сайт. SecurityConfig сейчас
// делает permitAll для всего, кроме приватного префикса, а тот ничем не подкреплён -
// нет активного механизма аутентификации, AuthorizationServerConfig полностью закомментирован,
// поэтому эти эндпоинты заведены под обычным /api, как и весь остальной API, которым
// сегодня пользуется webvue3.

/**
 * Контроллер (HTTP endpoints) для stats.
 *
 * Поведение после фикса 174-fix-stats-connection-leak:
 * - 6 чистых агрегатов (summary, timeseries, channels, countries,
 *   referrers, monetization) оборачиваются в TTL-кеш через StatsCache (60 сек).
 *   Параметризованные endpoint-ы сохраняют поведение «новый запрос на каждый вызов».
 * - При сбое getConnection (включая too many clients already)
 *   возвращается 503 stats.unavailable с заголовком Retry-After: 10 и телом
 *   errorCode, retryAfterSeconds, endpoint. Фронт показывает DbOverloadBanner
 *   вместо пустых графиков.
 *
 * @see AGENTS.md
 */
@RestController
class StatsController {
    private val log = LoggerFactory.getLogger(StatsController::class.java)

    private fun resolveDb(target: String?): KaraokeConnection =
        if (target == "remote") Connection.remote() else Connection.local()

    // resolveDb() создаёт НОВЫЙ объект Connection.local()/remote() на каждый вызов, а он открывает
    // собственное физическое JDBC-соединение и кэширует его в себе (KaraokeConnection); stats-функции
    // закрывают только ResultSet/Statement, но не connection. Без явного close() соединение висит до
    // обрыва — дашборд с ~11 эндпоинтами исчерпывал пул Postgres за несколько загрузок
    // ("FATAL: sorry, too many clients already"). withDb даёт каждому запросу собственное соединение
    // (потокобезопасность параллельных вызовов дашборда сохраняется — shared connection тут был бы
    // небезопасен) и гарантированно закрывает его сразу после использования.
    private fun <T> withDb(
        target: String?,
        block: (KaraokeConnection) -> T,
    ): T {
        val db = resolveDb(target)
        return try {
            block(db)
        } finally {
            try {
                db.getConnection()?.close()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Обёртка для 6 кешируемых endpoint-ов: добавляет catch на SQLException +
     * 503 stats.unavailable. Используется вместо голого ResponseEntity.ok(body) —
     * паттерн заимствован из спеки 167 (share.internal).
     */
    private fun respondCached(
        endpoint: String,
        requestUri: String,
        target: String?,
        compute: (KaraokeConnection) -> Map<String, Any>,
    ): ResponseEntity<Map<String, Any>> =
        try {
            val key = StatsCacheKey(endpoint, emptyMap())

            @Suppress("UNCHECKED_CAST")
            val cached = StatsCache.get(key) as? Map<String, Any>
            if (cached != null) {
                ResponseEntity.ok(cached)
            } else {
                val body = withDb(target) { db -> compute(db) }
                StatsCache.put(key, body as Any)
                ResponseEntity.ok(body)
            }
        } catch (e: SQLException) {
            statsUnavailableResponse(requestUri, e)
        }

    @GetMapping("/api/stats/by-song")
    fun statsBySong(
        @RequestParam(required = false) target: String?,
        @RequestParam(required = false, defaultValue = "1") page: Int,
        @RequestParam(required = false, defaultValue = "50") pageSize: Int,
    ): Map<String, Any> =
        withDb(target) { db ->
            val offset = (page - 1).coerceAtLeast(0) * pageSize
            val items = StatsByEvents.getStatBySong(database = db, limit = pageSize, offset = offset)
            val totalCount = StatsByEvents.getStatBySongCount(database = db)
            mapOf("items" to items, "totalCount" to totalCount)
        }

    // Топ песен, которые реально слушают в онлайнере до 75% (или до конца). Админ-вкладка
    // «Слушают» в webvue3/StatsView. Метрика «дослушано» = (progress='75' OR ended) — обе вехи
    // означают «прослушано ≥75%» (ended = 100%). Порядок по числу таких событий DESC.
    @GetMapping("/api/stats/top-listened")
    fun topListened(
        @RequestParam(required = false) target: String?,
        @RequestParam(required = false, defaultValue = "1") page: Int,
        @RequestParam(required = false, defaultValue = "50") pageSize: Int,
    ): Map<String, Any> =
        withDb(target) { db ->
            val offset = (page - 1).coerceAtLeast(0) * pageSize
            val items = StatsByEvents.getTopListenedSongs(database = db, limit = pageSize, offset = offset)
            val totalCount = StatsByEvents.getTopListenedSongsCount(database = db)
            mapOf("items" to items, "totalCount" to totalCount)
        }

    // Лог событий с опциональными фильтрами: тип события, период (дней), конкретный пользователь
    // (drill-down по строке из топа пользователей).
    @GetMapping("/api/webevents")
    fun webEvents(
        @RequestParam(required = false) target: String?,
        @RequestParam(required = false, defaultValue = "1") page: Int,
        @RequestParam(required = false, defaultValue = "50") pageSize: Int,
        @RequestParam(required = false) eventType: String?,
        @RequestParam(required = false) days: Int?,
        @RequestParam(required = false) siteUserId: Long?,
    ): Map<String, Any> =
        withDb(target) { db ->
            val offset = (page - 1).coerceAtLeast(0) * pageSize
            val items =
                StatsByEvents.getWebEvents(
                    database = db,
                    limit = pageSize,
                    offset = offset,
                    eventType = eventType,
                    fromDays = days,
                    siteUserId = siteUserId,
                )
            val totalCount = StatsByEvents.getWebEventsCount(database = db, eventType = eventType, fromDays = days, siteUserId = siteUserId)
            mapOf("items" to items, "totalCount" to totalCount)
        }

    /** FR-004: summary — кешируется 60s. FR-003: 503 при сбое БД. */
    @GetMapping("/api/stats/summary")
    fun summary(
        @RequestParam(required = false) target: String?,
    ): ResponseEntity<Map<String, Any>> =
        respondCached(
            endpoint = "summary",
            requestUri = "/api/stats/summary",
            target = target,
            compute = { db ->
                mapOf("summary" to StatsByEvents.getSummary(database = db))
            },
        )

    /** Монетизация (подписки): выручка, конверсия по источникам премиума, топ песен по подписке.
     *  FR-004: кешируется 60s. FR-003: 503 при сбое БД. */
    @GetMapping("/api/stats/monetization")
    fun monetizationSummary(
        @RequestParam(required = false) target: String?,
    ): ResponseEntity<Map<String, Any>> =
        respondCached(
            endpoint = "monetization",
            requestUri = "/api/stats/monetization",
            target = target,
            compute = { db ->
                mapOf("summary" to MonetizationStats.getSummary(database = db))
            },
        )

    @GetMapping("/api/stats/monetization/top-songs")
    fun monetizationTopSongs(
        @RequestParam(required = false) target: String?,
        @RequestParam(required = false, defaultValue = "20") limit: Int,
    ): Map<String, Any> =
        withDb(target) { db ->
            mapOf("items" to MonetizationStats.getTopSubscribedSongs(database = db, limit = limit))
        }

    /** FR-004: timeseries — кешируется 60s (frontend использует дефолты days=30, mode=all). */
    @GetMapping("/api/stats/timeseries")
    fun timeseries(
        @RequestParam(required = false) target: String?,
        @RequestParam(required = false, defaultValue = "30") days: Int,
        @RequestParam(required = false, defaultValue = "all") mode: String,
    ): ResponseEntity<Map<String, Any>> =
        respondCached(
            endpoint = "timeseries",
            requestUri = "/api/stats/timeseries",
            target = target,
            compute = { db ->
                mapOf("items" to StatsByEvents.getEventsTimeSeries(database = db, days = days, mode = mode))
            },
        )

    @GetMapping("/api/stats/by-type")
    fun byType(
        @RequestParam(required = false) target: String?,
        @RequestParam(required = false) days: Int?,
    ): Map<String, Any> =
        withDb(target) { db ->
            mapOf("items" to StatsByEvents.getEventsByType(database = db, fromDays = days))
        }

    /** FR-004: channels — кешируется 60s. */
    @GetMapping("/api/stats/channels")
    fun channels(
        @RequestParam(required = false) target: String?,
    ): ResponseEntity<Map<String, Any>> =
        respondCached(
            endpoint = "channels",
            requestUri = "/api/stats/channels",
            target = target,
            compute = { db ->
                mapOf("items" to StatsByEvents.getChannelBreakdown(database = db))
            },
        )

    /** География посетителей по client_ip. FR-004: кешируется 60s. */
    @GetMapping("/api/stats/countries")
    fun countries(
        @RequestParam(required = false) target: String?,
    ): ResponseEntity<Map<String, Any>> =
        respondCached(
            endpoint = "countries",
            requestUri = "/api/stats/countries",
            target = target,
            compute = { db ->
                mapOf("items" to StatsByEvents.getCountryBreakdown(database = db))
            },
        )

    /** Топ внешних источников перехода. FR-004: кешируется 60s. */
    @GetMapping("/api/stats/referrers")
    fun referrers(
        @RequestParam(required = false) target: String?,
    ): ResponseEntity<Map<String, Any>> =
        respondCached(
            endpoint = "referrers",
            requestUri = "/api/stats/referrers",
            target = target,
            compute = { db ->
                mapOf("items" to StatsByEvents.getTopReferrers(database = db))
            },
        )

    // Детализация по комбинациям event_type + link_type/rest_name/link_name (перемотка/старт/стоп
    // плеера, соцсети, платформы, UI-действия и т.п.).
    @GetMapping("/api/stats/by-detail")
    fun byDetail(
        @RequestParam(required = false) target: String?,
        @RequestParam(required = false) days: Int?,
    ): Map<String, Any> =
        withDb(target) { db ->
            mapOf("items" to StatsByEvents.getEventsDetailed(database = db, fromDays = days))
        }

    @GetMapping("/api/stats/top-users")
    fun topUsers(
        @RequestParam(required = false) target: String?,
        @RequestParam(required = false, defaultValue = "1") page: Int,
        @RequestParam(required = false, defaultValue = "50") pageSize: Int,
    ): Map<String, Any> =
        withDb(target) { db ->
            val offset = (page - 1).coerceAtLeast(0) * pageSize
            val items = StatsByEvents.getTopUsers(database = db, limit = pageSize, offset = offset)
            val totalCount = StatsByEvents.getTopUsersCount(database = db)
            mapOf("items" to items, "totalCount" to totalCount)
        }

    // Drill-down: все события конкретного пользователя (переиспользует /api/webevents с фильтром).
    // Пользователь — либо залогиненный (siteUserId>0), либо аноним (anonId, тогда site_user_id=0).
    @GetMapping("/api/stats/user-events")
    fun userEvents(
        @RequestParam(required = false) target: String?,
        @RequestParam(required = false, defaultValue = "0") siteUserId: Long,
        @RequestParam(required = false) anonId: String?,
        @RequestParam(required = false, defaultValue = "1") page: Int,
        @RequestParam(required = false, defaultValue = "50") pageSize: Int,
    ): Map<String, Any> =
        withDb(target) { db ->
            val offset = (page - 1).coerceAtLeast(0) * pageSize
            val suid = siteUserId.takeIf { it > 0 }
            val items = StatsByEvents.getWebEvents(database = db, limit = pageSize, offset = offset, siteUserId = suid, anonId = anonId)
            val totalCount = StatsByEvents.getWebEventsCount(database = db, siteUserId = suid, anonId = anonId)
            mapOf("items" to items, "totalCount" to totalCount)
        }

    // Drill-down: все события конкретной песни (переиспользует /api/webevents с фильтром по song_id)
    // — клик по строке таблицы «Топ песен по событиям».
    @GetMapping("/api/stats/song-events")
    fun songEvents(
        @RequestParam(required = false) target: String?,
        @RequestParam(required = false) songId: Long,
        @RequestParam(required = false, defaultValue = "1") page: Int,
        @RequestParam(required = false, defaultValue = "50") pageSize: Int,
    ): Map<String, Any> =
        withDb(target) { db ->
            val offset = (page - 1).coerceAtLeast(0) * pageSize
            val items = StatsByEvents.getWebEvents(database = db, limit = pageSize, offset = offset, songId = songId)
            val totalCount = StatsByEvents.getWebEventsCount(database = db, songId = songId)
            mapOf("items" to items, "totalCount" to totalCount)
        }
}
