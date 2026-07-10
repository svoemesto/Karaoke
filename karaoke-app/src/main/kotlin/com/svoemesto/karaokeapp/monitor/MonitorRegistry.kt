package com.svoemesto.karaokeapp.monitor

import com.svoemesto.karaokeapp.monitor.checks.ProdContainerCheck
import com.svoemesto.karaokeapp.monitor.checks.RenderQueueStalledCheck
import com.svoemesto.karaokeapp.monitor.checks.SubmittedAssignmentsCheck
import com.svoemesto.karaokeapp.monitor.checks.TelegramHorizonCheck
import com.svoemesto.karaokeapp.monitor.checks.TelegramPollingDisabledCheck
import com.svoemesto.karaokeapp.monitor.checks.UnreadChatMessagesCheck

/**
 * Реестр всех проверок мониторинга. Добавление новой проверки - один object : MonitorCheck в
 * пакете monitor.checks + одна строка здесь.
 */
object MonitorRegistry {
    val checks: List<MonitorCheck> = listOf(
        TelegramHorizonCheck,
        ProdContainerCheck,
        RenderQueueStalledCheck,
        TelegramPollingDisabledCheck,
        UnreadChatMessagesCheck,
        SubmittedAssignmentsCheck,
    )
}
