package com.svoemesto.karaokeapp.monitor

import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient

/**
 * Общий доступ проверок мониторинга к БД/сервисам - чтобы не тянуть глобалы (WORKING_DATABASE/
 * KSS_APP/SAC_APP) напрямую из каждой проверки.
 */
data class MonitorContext(
    val localDb: KaraokeConnection,
    val storageService: KaraokeStorageService,
    val storageApiClient: StorageApiClient,
)

/**
 * Одна системная проверка мониторинга. Возвращает 0..N алертов; пустой список = проблемы нет.
 * Добавление новой проверки - один object : MonitorCheck (см. пакет monitor.checks) + строка в
 * MonitorRegistry.checks. Исключения из run() ловит MonitoringService.tick() и превращает упавшую
 * проверку в отдельный WARNING-алерт - но ожидаемые ошибки (сеть, БД) проверка должна обрабатывать
 * сама, там где это осмысленно (см. ProdContainerCheck).
 */
fun interface MonitorCheck {
    fun run(ctx: MonitorContext): List<MonitorAlert>
}
