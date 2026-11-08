package com.svoemesto.karaokeapp.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.PatternLayout
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.joran.JoranConfigurator
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.LoggingEvent
import ch.qos.logback.core.OutputStreamAppender
import ch.qos.logback.core.read.ListAppender
import ch.qos.logback.core.status.Status
import java.net.URL
import java.nio.file.Files
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.logging.LoggingInitializationContext
import org.springframework.boot.logging.LoggingSystem
import org.springframework.mock.env.MockEnvironment

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
    fun `spring boot грузит конфигурацию и печатает исключение одной строкой`() {
        // Тот же путь, что в приложении: Spring Boot сам находит logback-spring.xml,
        // применяет logging.pattern.console и поднимает конвертор causeChain.
        val system = LoggingSystem.get(javaClass.classLoader)
        system.beforeInitialize()
        system.initialize(
            LoggingInitializationContext(MockEnvironment()),
            null,
            null,
        )
        val context = org.slf4j.LoggerFactory.getILoggerFactory() as ch.qos.logback.classic.LoggerContext
        val appender = ListAppender<ILoggingEvent>()
        appender.start()
        context.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME).addAppender(appender)

        org.slf4j.LoggerFactory
            .getLogger("test-converter")
            .error("что-то сломалось", java.io.IOException("обрыв связи", IllegalStateException("нижняя причина")))

        val event = appender.list.last()
        val layout = PatternLayout()
        layout.context = context
        // %nopex — иначе logback дописал бы стандартный вывод исключения (со стеком)
        // поверх однострочной цепочки причин.
        layout.pattern = "%msg%n%causeChain%nopex"
        layout.start()
        val formatted = layout.doLayout(event)
        assertFalse(formatted.contains("at java."), "в логе не должно быть кадров: $formatted")
        assertTrue(formatted.contains("IOException: обрыв связи"), formatted)
        assertTrue(formatted.contains("IllegalStateException: нижняя причина"), formatted)
    }

    @Test
    fun `реальный конфиг вешает CONSOLE на root и печатает исключение без кадров`() {
        // Регресс, пойманный на проде 2026-11-08: в logback-spring.xml был подключён
        // console-appender.xml БЕЗ <root> с appender-ref. Logback тогда писал
        // «Appender named [CONSOLE] not referenced» и «No appenders present in
        // context [default]» — то есть ВСЕ сообщения logback (включая ошибки
        // приложения) пропадали из консоли. Проверяем РЕАЛЬНЫЙ файл конфигурации.
        val xml: URL = checkNotNull(javaClass.getResource("/logback-spring.xml")) { "logback-spring.xml не найден" }
        val previousPattern = System.getProperty("CONSOLE_LOG_PATTERN")
        val previousLogFile = System.getProperty("LOG_FILE")
        val logFile = Files.createTempFile("karaoke-logback", ".log")
        System.setProperty("CONSOLE_LOG_PATTERN", "%msg%n%causeChain%nopex")
        System.setProperty("LOG_FILE", logFile.toAbsolutePath().toString())
        try {
            val context = LoggerContext()
            val configurator = JoranConfigurator()
            configurator.context = context
            configurator.doConfigure(xml)

            val root = context.getLogger(Logger.ROOT_LOGGER_NAME)
            val console = root.getAppender("CONSOLE")
            assertNotNull(console, "у root нет appender CONSOLE — сообщения logback не попадут в консоль")

            val warnings = context.statusManager.copyOfStatusList.filter { it.level >= Status.WARN }
            val warningMessages = warnings.map { it.message }
            assertTrue(
                warningMessages.none { message -> message.contains("not referenced") || message.contains("No appenders present") },
                "logback предупреждает о потере appender: $warningMessages",
            )

            val encoder = (console as OutputStreamAppender<ILoggingEvent>).encoder
            assertTrue(encoder is PatternLayoutEncoder, "неожиданный encoder: $encoder")
            val pattern = (encoder as PatternLayoutEncoder).pattern
            assertTrue(pattern.contains("%causeChain"), "в шаблоне нет однострочной цепочки причин: $pattern")
            assertTrue(pattern.contains("%nopex"), "в шаблоне нет %nopex (иначе logback допишет стек): $pattern")

            // Сквозная проверка по РЕАЛЬНОМУ шаблону конфигурации.
            val listAppender = ListAppender<ILoggingEvent>()
            listAppender.start()
            root.addAppender(listAppender)
            context
                .getLogger("test-console")
                .error(
                    "что-то сломалось",
                    java.io.IOException("обрыв связи", IllegalStateException("нижняя причина")),
                )
            val layout = PatternLayout()
            layout.context = context
            layout.pattern = pattern
            layout.start()
            val formatted = layout.doLayout(listAppender.list.last())
            assertFalse(formatted.contains("at java."), "в логе не должно быть кадров: $formatted")
            assertFalse(formatted.contains("at com.svoemesto"), "в логе не должно быть кадров: $formatted")
            assertTrue(formatted.contains("IOException: обрыв связи"), formatted)
            assertTrue(formatted.contains("IllegalStateException: нижняя причина"), formatted)
        } finally {
            if (previousPattern == null) {
                System.clearProperty("CONSOLE_LOG_PATTERN")
            } else {
                System.setProperty("CONSOLE_LOG_PATTERN", previousPattern)
            }
            if (previousLogFile == null) {
                System.clearProperty("LOG_FILE")
            } else {
                System.setProperty("LOG_FILE", previousLogFile)
            }
            Files.deleteIfExists(logFile)
        }
    }

    @Test
    fun `шаблоны логирования заканчиваются однострочной цепочкой причин`() {
        // Шаблон из application.yml — то, что реально применяет Spring Boot.
        // %nopex обязателен: без него logback дописывает полный стек после цепочки.
        val yaml: URL = checkNotNull(javaClass.getResource("/application.yml")) { "application.yml не найден" }
        val text = yaml.readText()
        val console = text.lineSequence().first { it.trimStart().startsWith("console: \"") }
        val file = text.lineSequence().first { it.trimStart().startsWith("file: \"") }
        assertTrue(console.contains("%causeChain%nopex"), console)
        assertTrue(file.contains("%causeChain%nopex"), file)
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
