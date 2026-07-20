package com.svoemesto.karaokeapp.monitor.checks

import com.svoemesto.karaokeapp.Connection
import com.svoemesto.karaokeapp.model.SiteChatMessage
import com.svoemesto.karaokeapp.monitor.MonitorAlert
import com.svoemesto.karaokeapp.monitor.MonitorCheck
import com.svoemesto.karaokeapp.monitor.MonitorContext
import com.svoemesto.karaokeapp.monitor.MonitorSeverity

/**
 * Непрочитанные сообщения от пользователей в «Чате с автором проекта». Таблица
 * tbl_site_chat_messages живёт целиком на PROD-БД (см. SiteChatMessage) — MonitorContext даёт только
 * localDb, поэтому проверка сама открывает Connection.remote() и обязательно закрывает его (паттерн —
 * ProdContainerCheck.pingRemoteDb()). body намеренно не содержит числа — оно в detail (см.
 * MonitorAlert.contentHash()), иначе алерт «мигал» бы read/unread на каждом тике.
 */
object UnreadChatMessagesCheck : MonitorCheck {
    override fun run(ctx: MonitorContext): List<MonitorAlert> {
        val db = Connection.remote()
        val unread =
            try {
                SiteChatMessage.countUnreadFromUsers(db)
            } catch (e: Exception) {
                println("UnreadChatMessagesCheck: ${e.message}")
                0
            } finally {
                try {
                    db.getConnection()?.close()
                } catch (_: Exception) {
                }
            }

        if (unread <= 0) return emptyList()

        return listOf(
            MonitorAlert(
                key = "chat.unread",
                severity = MonitorSeverity.INFO,
                title = "Непрочитанные сообщения от пользователей",
                body = "В чате с автором есть непрочитанные сообщения от пользователей.",
                category = "Чат",
                detail = "$unread шт.",
                recommendations = "Откройте раздел «Чат» в webvue3 и ответьте пользователям.",
            ),
        )
    }
}
