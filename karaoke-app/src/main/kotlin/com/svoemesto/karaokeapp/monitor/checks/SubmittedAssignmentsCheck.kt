package com.svoemesto.karaokeapp.monitor.checks

import com.svoemesto.karaokeapp.Connection
import com.svoemesto.karaokeapp.model.SongAssignment
import com.svoemesto.karaokeapp.monitor.MonitorAlert
import com.svoemesto.karaokeapp.monitor.MonitorCheck
import com.svoemesto.karaokeapp.monitor.MonitorContext
import com.svoemesto.karaokeapp.monitor.MonitorSeverity

/**
 * Задания онлайн-редактора разметки, отправленные пользователем «на проверку» (SUBMITTED). Реальный
 * рабочий цикл (назначить → работа → апрув) чаще всего идёт целиком на PROD (см. SongEditorController,
 * SongEditor/store.js defaultTarget='remote') — проверка смотрит remote, тем же источником, что бейдж
 * пункта меню «Задания редактора» в webvue3. body намеренно не содержит числа — оно в detail (см.
 * MonitorAlert.contentHash()), иначе алерт «мигал» бы read/unread на каждом тике.
 */
object SubmittedAssignmentsCheck : MonitorCheck {

    override fun run(ctx: MonitorContext): List<MonitorAlert> {
        val db = Connection.remote()
        val submitted = try {
            SongAssignment.countSubmitted(db, ctx.storageService, ctx.storageApiClient)
        } catch (e: Exception) {
            println("SubmittedAssignmentsCheck: ${e.message}")
            0
        } finally {
            try { db.getConnection()?.close() } catch (_: Exception) {}
        }

        if (submitted <= 0) return emptyList()

        return listOf(
            MonitorAlert(
                key = "songeditor.submitted",
                severity = MonitorSeverity.INFO,
                title = "Задания редактора на проверке",
                body = "Есть задания онлайн-редактора разметки, отправленные пользователями на проверку.",
                category = "Редактор",
                detail = "$submitted шт.",
                recommendations = "Откройте раздел «Задание редактора» в webvue3 и проверьте разметку.",
            )
        )
    }
}
