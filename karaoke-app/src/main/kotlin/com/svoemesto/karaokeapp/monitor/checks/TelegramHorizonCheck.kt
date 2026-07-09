package com.svoemesto.karaokeapp.monitor.checks

import com.svoemesto.karaokeapp.KaraokeProperties
import com.svoemesto.karaokeapp.model.Settings
import com.svoemesto.karaokeapp.monitor.MonitorAlert
import com.svoemesto.karaokeapp.monitor.MonitorCheck
import com.svoemesto.karaokeapp.monitor.MonitorContext
import com.svoemesto.karaokeapp.monitor.MonitorSeverity
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Предупреждает, если горизонт уже запланированных (отложенных, ещё не вышедших) постов в
 * Telegram-канале меньше monitorTelegramHorizonDays суток. Отложенный пост - песня, у которой хотя
 * бы одно из полей id_telegram_* равно "-" (см. CLAUDE.md / TelegramUpdatesConsumer): пост создан
 * вручную как отложенный, но ещё не вышел в эфир. Горизонт = максимальная dateTimePublish среди
 * таких песен минус текущее время.
 *
 * body намеренно не содержит изменчивых чисел (часов) - только detail (см. MonitorAlert.contentHash),
 * иначе сообщение "мигало" бы read/unread на каждом тике планировщика.
 */
object TelegramHorizonCheck : MonitorCheck {

    override fun run(ctx: MonitorContext): List<MonitorAlert> {
        val thresholdDays = KaraokeProperties.getLong("monitorTelegramHorizonDays").takeIf { it > 0 } ?: 2L
        val todayStr = SimpleDateFormat("dd.MM.yy").format(Date())

        // Сужаем выборку до песен с датой публикации сегодня/в будущем - не тянем всю таблицу
        // (getWhereList поддерживает ">" как "publish_date >= указанной даты").
        val candidates = Settings.loadListFromDb(
            args = mapOf("publish_date" to ">$todayStr"),
            database = ctx.localDb,
            storageService = ctx.storageService,
            storageApiClient = ctx.storageApiClient,
            withoutMarkersAndText = true
        )

        val scheduled = candidates.filter { settings ->
            settings.idTelegramLyrics == "-" || settings.idTelegramKaraoke == "-" ||
                settings.idTelegramChords == "-" || settings.idTelegramMelody == "-"
        }

        val maxPublish = scheduled.mapNotNull { it.dateTimePublish }.maxOrNull()
        val now = Date()
        val horizonHours = if (maxPublish == null) 0.0 else (maxPublish.time - now.time) / 3_600_000.0
        val thresholdHours = thresholdDays * 24.0

        if (horizonHours >= thresholdHours) return emptyList()

        val detail = if (maxPublish == null) {
            "нет ни одного запланированного поста"
        } else {
            "горизонт ~${horizonHours.toInt()} ч"
        }

        return listOf(
            MonitorAlert(
                key = "telegram.horizon",
                severity = MonitorSeverity.WARNING,
                title = "Мало запланированных постов в Telegram",
                body = "Запланированных к публикации постов в Telegram-канале осталось меньше, чем на $thresholdDays сут. вперёд.",
                category = "Telegram",
                detail = detail,
                recommendations = "Подготовьте и запланируйте новые отложенные посты в разделе «Публикация», чтобы горизонт запланированных постов был не меньше $thresholdDays суток."
            )
        )
    }
}
