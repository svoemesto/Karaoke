package com.svoemesto.karaokeapp.controllers

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

    @GetMapping("/api/stats/by-song")
    fun statsBySong(): List<StatBySongDto> = StatsByEvents.getStatBySong()

    @GetMapping("/api/webevents")
    fun webEvents(@RequestParam(required = false, defaultValue = "500") limit: Int): List<WebEventDto> =
        StatsByEvents.getWebEvents(limit = limit)
}
