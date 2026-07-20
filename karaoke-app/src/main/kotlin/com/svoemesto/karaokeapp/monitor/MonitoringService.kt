package com.svoemesto.karaokeapp.monitor

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.svoemesto.karaokeapp.KaraokeProperties
import com.svoemesto.karaokeapp.WORKING_DATABASE
import com.svoemesto.karaokeapp.model.SseNotification
import com.svoemesto.karaokeapp.services.KSS_APP
import com.svoemesto.karaokeapp.services.SAC_APP
import com.svoemesto.karaokeapp.services.SNS
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * Фоновый планировщик мониторинга ключевых моментов проекта (см. DEVELOPMENT.md, раздел «Подсистема
 * мониторинга»). Раз в минуту прогоняет MonitorRegistry.checks, хранит актуальный снапшот алертов
 * в памяти и рассылает его по SSE (тип MONITOR_ALERTS, broadcast всем вкладкам webvue3 - см.
 * SseNotificationService.addressedTypes). Работает только пока запущен karaoke-app (тот же
 * инвариант, что у SponsrSyncScheduler/checkLastAlbum) - это не 24/7 аптайм-монитор.
 */
@Component
class MonitoringService {
    private val objectMapper = ObjectMapper()

    // key -> актуальный алерт этой проверки (перезаписывается целиком на каждом tick()).
    @Volatile private var snapshot: Map<String, MonitorAlert> = emptyMap()

    // key -> contentHash, при котором алерт был помечен прочитанным. Персистится в KaraokeProperty
    // "monitorDismissed" (JSON), чтобы разобранные предупреждения не всплывали заново после
    // перезапуска karaoke-app.
    private val dismissed = ConcurrentHashMap<String, String>()

    init {
        loadDismissed()
    }

    private fun ctx() = MonitorContext(localDb = WORKING_DATABASE, storageService = KSS_APP, storageApiClient = SAC_APP)

    @Scheduled(fixedRate = 60_000L, initialDelay = 20_000L)
    fun tick() {
        val alerts =
            MonitorRegistry.checks.flatMap { check ->
                try {
                    check.run(ctx())
                } catch (e: Exception) {
                    listOf(checkFailureAlert(check, e))
                }
            }
        snapshot = alerts.associateBy { it.key }
        pruneDismissed()
        broadcast()
    }

    private fun checkFailureAlert(
        check: MonitorCheck,
        e: Exception,
    ): MonitorAlert {
        val checkName = check::class.simpleName ?: check.javaClass.simpleName
        println("[MonitoringService] проверка $checkName упала: ${e.message}")
        return MonitorAlert(
            key = "check.$checkName.failure",
            severity = MonitorSeverity.WARNING,
            title = "Проверка мониторинга завершилась ошибкой",
            body = "Проверка «$checkName» упала с исключением: ${e.message}",
            category = "Мониторинг",
        )
    }

    fun currentDtos(): List<MonitorAlertDto> =
        snapshot.values
            .sortedByDescending { it.severity.rank }
            .map { it.toDto(read = isRead(it)) }

    private fun isRead(alert: MonitorAlert): Boolean = dismissed[alert.key] == alert.contentHash()

    private fun broadcast() {
        try {
            SNS.send(SseNotification.monitorAlerts(currentDtos()))
        } catch (e: Exception) {
            println("[MonitoringService] ошибка broadcast: ${e.message}")
        }
    }

    fun markRead(key: String) {
        snapshot[key]?.let { alert ->
            dismissed[key] = alert.contentHash()
            persist()
            broadcast()
        }
    }

    fun markUnread(key: String) {
        dismissed.remove(key)
        persist()
        broadcast()
    }

    fun reset() {
        dismissed.clear()
        persist()
        broadcast()
    }

    /**
     * Ре-деривация действия по key: лямбда resolveAction живёт только в свежем снапшоте (DTO её не
     * переносит) - ровно как HealthReport.getHealthReport(dto)?.executeSolutionActions(). Если между
     * отрисовкой на фронте и кликом снапшот успел пересчитаться и алерт исчез/потерял
     * resolveAction - тихий no-op; последующий tick() всё равно покажет актуальное состояние.
     */
    fun resolve(key: String) {
        snapshot[key]?.takeIf { it.canResolve }?.executeResolve()
        tick()
    }

    private fun pruneDismissed() {
        val activeKeys = snapshot.keys
        val stale = dismissed.keys.filter { it !in activeKeys }
        if (stale.isNotEmpty()) {
            stale.forEach { dismissed.remove(it) }
            persist()
        }
    }

    private fun persist() {
        try {
            KaraokeProperties.set("monitorDismissed", objectMapper.writeValueAsString(dismissed))
        } catch (e: Exception) {
            println("[MonitoringService] ошибка сохранения monitorDismissed: ${e.message}")
        }
    }

    private fun loadDismissed() {
        try {
            val json = KaraokeProperties.getString("monitorDismissed")
            if (json.isNotBlank() && json != "{}") {
                val map: Map<String, String> = objectMapper.readValue(json, object : TypeReference<Map<String, String>>() {})
                dismissed.putAll(map)
            }
        } catch (e: Exception) {
            println("[MonitoringService] ошибка загрузки monitorDismissed: ${e.message}")
        }
    }
}
