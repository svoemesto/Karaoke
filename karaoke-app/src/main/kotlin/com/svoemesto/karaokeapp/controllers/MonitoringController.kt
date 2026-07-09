package com.svoemesto.karaokeapp.controllers

import com.svoemesto.karaokeapp.monitor.MonitorAlertDto
import com.svoemesto.karaokeapp.monitor.MonitoringService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * REST для подсистемы мониторинга (см. MonitoringService/MonitorRegistry). GET /alerts нужен для
 * первичной загрузки при открытии/перезагрузке страницы webvue3 - SSE (тип MONITOR_ALERTS) доносит
 * только новые тики планировщика, а не текущее состояние в момент подключения.
 */
@RestController
@RequestMapping("/api/monitor")
class MonitoringController(
    private val monitoringService: MonitoringService
) {

    @GetMapping("/alerts")
    fun alerts(): List<MonitorAlertDto> = monitoringService.currentDtos()

    @PostMapping("/resolve")
    fun resolve(@RequestParam key: String) {
        monitoringService.resolve(key)
    }

    @PostMapping("/markRead")
    fun markRead(@RequestParam key: String) {
        monitoringService.markRead(key)
    }

    @PostMapping("/markUnread")
    fun markUnread(@RequestParam key: String) {
        monitoringService.markUnread(key)
    }

    @PostMapping("/reset")
    fun reset() {
        monitoringService.reset()
    }
}
