package com.svoemesto.karaokeapp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.sql.SQLException

/**
 * KARAOKE-387: лог ошибки — одна машиночитаемая строка с кодом, без стектрейсов в обычном режиме.
 *
 * Инцидент 2026-11-02: сорок строк стека Spring/Tomcat вокруг одного
 * `PSQLException: Ошибка ввода/вывода при отправке бэкенду`; полезное (что, где, с чем) тонуло
 * в фреймах, а технический текст доходил до интерфейса.
 *
 * Контракт: строка всегда одна (`event=`, `code=`, пары `ключ=значение`, `cause=` как цепочка
 * типов), переводы строк в значениях схлопываются; стек печатается только при
 * `LOG_STACKTRACES=true`.
 */
class AppLogTest {
    @Test
    fun `строка события содержит код поля и цепочку cause`() {
        val error = SQLException("Ошибка ввода/вывода при отправке бэкенду", "08006", IOException("Connection reset"))

        val line =
            AppLog.format(
                event = "db.connect.failed",
                code = "DB-CONNECT-001",
                fields = mapOf("target" to "SERVER", "host" to "188.127.240.124:5433"),
                error = error,
            )

        assertFalse(line.contains("\n"), "событие обязано занимать ровно одну строку")
        assertTrue(line.startsWith("event=db.connect.failed code=DB-CONNECT-001"))
        assertTrue(line.contains("target=SERVER"))
        assertTrue(line.contains("host=188.127.240.124:5433"))
        assertTrue(line.contains("cause=SQLException: Ошибка ввода/вывода при отправке бэкенду"))
        assertTrue(line.contains(" <- IOException: Connection reset"), "цепочка cause сохраняется для разбора")
    }

    @Test
    fun `переводы строк в значениях схлопываются`() {
        val line =
            AppLog.format(
                event = "db.read.failed",
                code = "DB-READ-003",
                fields = mapOf("detail" to "первая строка\nвторая\tстрока"),
            )

        assertFalse(line.contains("\n"))
        assertFalse(line.contains("\t"))
        assertTrue(line.contains("detail=первая строка вторая строка"))
    }

    @Test
    fun `без исключения блока cause нет`() {
        val line = AppLog.format(event = "policy.ok", code = "STORAGE-POLICY-OK")

        assertEquals("event=policy.ok code=STORAGE-POLICY-OK", line)
    }

    @Test
    fun `пустые поля не попадают в строку`() {
        val line =
            AppLog.format(
                event = "chat.unread.count.failed",
                code = "CHAT-UNREAD-001",
                fields = mapOf("sqlState" to null, "operation" to "count"),
            )

        assertFalse(line.contains("sqlState"))
        assertTrue(line.contains("operation=count"))
    }

    @Test
    fun `стеки по умолчанию выключены`() {
        val envValue = System.getenv("LOG_STACKTRACES")?.trim()?.equals("true", ignoreCase = true) ?: false
        assertEquals(envValue, AppLog.isStackTracesEnabled(), "стек включается только флагом LOG_STACKTRACES=true")
    }
}
