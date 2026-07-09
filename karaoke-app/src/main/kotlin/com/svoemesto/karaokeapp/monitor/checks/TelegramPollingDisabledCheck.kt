package com.svoemesto.karaokeapp.monitor.checks

import com.svoemesto.karaokeapp.KaraokeProperties
import com.svoemesto.karaokeapp.monitor.MonitorAlert
import com.svoemesto.karaokeapp.monitor.MonitorCheck
import com.svoemesto.karaokeapp.monitor.MonitorContext
import com.svoemesto.karaokeapp.monitor.MonitorSeverity
import com.svoemesto.karaokeapp.services.TelegramUpdatesConsumer

/**
 * Фоновый отлов вышедших постов в Telegram (getUpdates) выключен - без него ссылки на отложенные
 * посты не проставляются автоматически (см. TelegramUpdatesConsumer). Одноклик-fix - включить
 * свойство и стартовать демон (TelegramUpdatesConsumer.start() идемпотентен при isWork=true).
 */
object TelegramPollingDisabledCheck : MonitorCheck {

    override fun run(ctx: MonitorContext): List<MonitorAlert> {
        if (KaraokeProperties.getBoolean("telegramPollingEnabled")) return emptyList()

        return listOf(
            MonitorAlert(
                key = "telegram.polling.off",
                severity = MonitorSeverity.WARNING,
                title = "Отлов постов Telegram выключен",
                body = "Фоновый отлов вышедших постов в Telegram-канале (getUpdates) выключен - ссылки на отложенные посты не будут проставлены автоматически.",
                category = "Telegram",
                resolveAction = {
                    KaraokeProperties.setFromString("telegramPollingEnabled", "true")
                    TelegramUpdatesConsumer.start()
                }
            )
        )
    }
}
