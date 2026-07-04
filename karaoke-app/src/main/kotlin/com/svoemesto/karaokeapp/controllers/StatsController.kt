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

    @GetMapping("/api/webevents")
    fun webEvents(
        @RequestParam(required = false) target: String?,
        @RequestParam(required = false, defaultValue = "1") page: Int,
        @RequestParam(required = false, defaultValue = "50") pageSize: Int,
    ): Map<String, Any> {
        val db = resolveDb(target)
        val offset = (page - 1).coerceAtLeast(0) * pageSize
        val items = StatsByEvents.getWebEvents(database = db, limit = pageSize, offset = offset)
        val totalCount = StatsByEvents.getWebEventsCount(database = db)
        return mapOf("items" to items, "totalCount" to totalCount)
    }
}
