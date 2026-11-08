package com.svoemesto.karaokeapp.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.LoggingEvent
import java.net.URL
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Контракт конвертера исключений (KARAOKE-387, спека 732): в логе — нормальное
 * сообщение, а не простыня кадров стека; полный стек — только по флагу
 * `LOG_STACKTRACES=true`.
 */
class CauseChainConverterTest {
    private fun event(throwable: Throwable): LoggingEvent {
        val context = LoggerContext()
        return LoggingEvent(
            "fqcn",
            context.getLogger("test"),
            Level.ERROR,
            "что-то сломалось",
            throwable,
            null,
        )
    }

    private class StackTracesOn : CauseChainConverter() {
        override fun stackTracesEnabled(): Boolean = true
    }

    @Test
    fun `исключение печатается одной строкой без кадров`() {
        val cause = IllegalStateException("нижняя причина")
        val error = java.io.IOException("обрыв связи", cause)

        val out = CauseChainConverter().convert(event(error))

        assertFalse(out.contains("\n"), "в логе не должно быть перевода строки: $out")
        assertFalse(out.contains("\tat "), "в логе не должно быть кадров стека: $out")
        assertTrue(out.contains("IOException: обрыв связи"), "нет типа и сообщения: $out")
        assertTrue(out.contains("IllegalStateException: нижняя причина"), "нет цепочки причин: $out")
        assertEquals(1, out.lines().size)
    }

    @Test
    fun `сообщение с переводом строки остаётся одной строкой`() {
        val error = IllegalArgumentException("первая строка\nвторая строка")

        val out = CauseChainConverter().convert(event(error))

        assertEquals(1, out.lines().size, "перевод строки должен быть заменён пробелом: $out")
        assertTrue(out.contains("первая строка вторая строка"), out)
    }

    @Test
    fun `полный стек печатается только по флагу LOG_STACKTRACES`() {
        val error = IllegalStateException("сбой с деталями")

        val out = StackTracesOn().convert(event(error))

        assertTrue(out.contains("\tat "), "во включающем режиме должны быть кадры: $out")
        assertTrue(out.contains("IllegalStateException: сбой с деталями"), out)
    }

    @Test
    fun `без исключения вывод пустой`() {
        val context = LoggerContext()
        val event = LoggingEvent("fqcn", context.getLogger("test"), Level.INFO, "обычное сообщение", null, null)

        assertEquals("", CauseChainConverter().convert(event))
    }

    @Test
    fun `logback spring xml подключает конвертер causeChain`() {
        val xml: URL = checkNotNull(javaClass.getResource("/logback-spring.xml")) { "logback-spring.xml не найден" }
        val doc =
            DocumentBuilderFactory
                .newInstance()
                .newDocumentBuilder()
                .parse(xml.openStream())
        val rules = doc.getElementsByTagName("conversionRule")
        assertEquals(1, rules.length, "ожидался один conversionRule в logback-spring.xml")
        val rule = rules.item(0)
        assertEquals("causeChain", rule.attributes.getNamedItem("conversionWord").nodeValue)
        assertEquals(
            "com.svoemesto.karaokeapp.logging.CauseChainConverter",
            rule.attributes.getNamedItem("class").nodeValue,
        )
    }
}
