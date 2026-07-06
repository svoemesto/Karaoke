package com.svoemesto.karaokeapp.controllers

import com.svoemesto.karaokeapp.Connection
import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.model.StatBySongDto
import com.svoemesto.karaokeapp.model.StatsByEvents
import com.svoemesto.karaokeapp.model.WebEventDto
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

// Аналитика по событиям сайта (бывшие /statbysong и /webevents в karaoke-web) — перенесена
// сюда, чтобы её показывала админка webvue3, а не публичный сайт. SecurityConfig сейчас
// делает permitAll для всего, кроме приватного префикса, а тот ничем не подкреплён -
// нет активного механизма аутентификации, AuthorizationServerConfig полностью закомментирован,
// поэтому эти эндпоинты заведены под обычным /api, как и весь остальной API, которым
// сегодня пользуется webvue3.
@RestController
class StatsController {

    private fun resolveDb(target: String?): KaraokeConnection =
        if (target == "remote") Connection.remote() else Connection.local()

    @GetMapping("/api/stats/by-song")
    fun statsBySong(
        @RequestParam(required = false) target: String?,
        @RequestParam(required = false, defaultValue = "1") page: Int,
        @RequestParam(required = false, defaultValue = "50") pageSize: Int,
    ): Map<String, Any> {
        val db = resolveDb(target)
        val offset = (page - 1).coerceAtLeast(0) * pageSize
        val items = StatsByEvents.getStatBySong(database = db, limit = pageSize, offset = offset)
        val totalCount = StatsByEvents.getStatBySongCount(database = db)
        return mapOf("items" to items, "totalCount" to totalCount)
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
    ): Map<String, Any> {
        val db = resolveDb(target)
        val offset = (page - 1).coerceAtLeast(0) * pageSize
        val items = StatsByEvents.getWebEvents(database = db, limit = pageSize, offset = offset, eventType = eventType, fromDays = days, siteUserId = siteUserId)
        val totalCount = StatsByEvents.getWebEventsCount(database = db, eventType = eventType, fromDays = days, siteUserId = siteUserId)
        return mapOf("items" to items, "totalCount" to totalCount)
    }

    @GetMapping("/api/stats/summary")
    fun summary(@RequestParam(required = false) target: String?): Map<String, Any> =
        mapOf("summary" to StatsByEvents.getSummary(database = resolveDb(target)))

    @GetMapping("/api/stats/timeseries")
    fun timeseries(
        @RequestParam(required = false) target: String?,
        @RequestParam(required = false, defaultValue = "30") days: Int,
        @RequestParam(required = false, defaultValue = "all") mode: String,
    ): Map<String, Any> =
        mapOf("items" to StatsByEvents.getEventsTimeSeries(database = resolveDb(target), days = days, mode = mode))

    @GetMapping("/api/stats/by-type")
    fun byType(
        @RequestParam(required = false) target: String?,
        @RequestParam(required = false) days: Int?,
    ): Map<String, Any> =
        mapOf("items" to StatsByEvents.getEventsByType(database = resolveDb(target), fromDays = days))

    @GetMapping("/api/stats/channels")
    fun channels(@RequestParam(required = false) target: String?): Map<String, Any> =
        mapOf("items" to StatsByEvents.getChannelBreakdown(database = resolveDb(target)))

    // География посетителей по client_ip (страна через GeoIpService + кэш tbl_ip_country).
    @GetMapping("/api/stats/countries")
    fun countries(@RequestParam(required = false) target: String?): Map<String, Any> =
        mapOf("items" to StatsByEvents.getCountryBreakdown(database = resolveDb(target)))

    // Топ внешних источников перехода (referer = document.referrer заход-лендинга).
    @GetMapping("/api/stats/referrers")
    fun referrers(@RequestParam(required = false) target: String?): Map<String, Any> =
        mapOf("items" to StatsByEvents.getTopReferrers(database = resolveDb(target)))

    // Детализация по комбинациям event_type + link_type/rest_name/link_name (перемотка/старт/стоп
    // плеера, соцсети, платформы, UI-действия и т.п.).
    @GetMapping("/api/stats/by-detail")
    fun byDetail(
        @RequestParam(required = false) target: String?,
        @RequestParam(required = false) days: Int?,
    ): Map<String, Any> =
        mapOf("items" to StatsByEvents.getEventsDetailed(database = resolveDb(target), fromDays = days))

    @GetMapping("/api/stats/top-users")
    fun topUsers(
        @RequestParam(required = false) target: String?,
        @RequestParam(required = false, defaultValue = "1") page: Int,
        @RequestParam(required = false, defaultValue = "50") pageSize: Int,
    ): Map<String, Any> {
        val db = resolveDb(target)
        val offset = (page - 1).coerceAtLeast(0) * pageSize
        val items = StatsByEvents.getTopUsers(database = db, limit = pageSize, offset = offset)
        val totalCount = StatsByEvents.getTopUsersCount(database = db)
        return mapOf("items" to items, "totalCount" to totalCount)
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
    ): Map<String, Any> {
        val db = resolveDb(target)
        val offset = (page - 1).coerceAtLeast(0) * pageSize
        val suid = siteUserId.takeIf { it > 0 }
        val items = StatsByEvents.getWebEvents(database = db, limit = pageSize, offset = offset, siteUserId = suid, anonId = anonId)
        val totalCount = StatsByEvents.getWebEventsCount(database = db, siteUserId = suid, anonId = anonId)
        return mapOf("items" to items, "totalCount" to totalCount)
    }
}
