package com.svoemesto.karaokeapp.monitor.checks

import com.svoemesto.karaokeapp.Connection
import com.svoemesto.karaokeapp.model.SiteChatMessage
import com.svoemesto.karaokeapp.monitor.MonitorAlert
import com.svoemesto.karaokeapp.monitor.MonitorCheck
import com.svoemesto.karaokeapp.monitor.MonitorContext
import com.svoemesto.karaokeapp.monitor.MonitorSeverity

/**
 * BR-134 (аудит #336, пакет `PKG-S3-23`): категория логов проверок мониторинга,
 * читающих прод-БД.
 */
private val monitorCheckLog = org.slf4j.LoggerFactory.getLogger("infra.monitor.check")

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
                // BR-134 (аудит #336, пакет `PKG-S3-23`): зеркало StemJobsStuckCheck —
                // ошибка не подменяется нулём «проблем нет», а пробрасывается:
                // MonitoringService.tick() превратит её в WARNING `check.<Name>.failure`.
                monitorCheckLog.error("UnreadChatMessagesCheck: не удалось посчитать непрочитанные сообщения", e)
                throw e
            } finally {
                try {
                    db.getConnection()?.close()
                } catch (_: Exception) {
                }
            }

        // BR-193 (аудит #336, пакет `PKG-S3-24`): -1 = счётчик не удалось получить
        // (сбой БД). Раньше такой случай выглядел как «непрочитанных нет» — проверка
        // молчала, и оператор не видел, что чат недоступен.
        if (unread == SiteChatMessage.COUNT_ERROR) {
            val error = IllegalStateException("chat.unread.count.unavailable: не удалось посчитать непрочитанные сообщения")
            monitorCheckLog.error("UnreadChatMessagesCheck: {}", error.message)
            throw error
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
