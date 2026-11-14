package com.svoemesto.karaokeweb.services

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import com.fasterxml.jackson.databind.ObjectMapper
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.slf4j.LoggerFactory
import org.springframework.messaging.simp.SimpMessagingTemplate
import java.io.ByteArrayOutputStream
import java.io.PrintStream

/**
 * KARAOKE-394 (аудит #336, находка P-37-08): признак режима работы при старте печатался
 * в stdout через `println` — мимо системы логов, без уровня и без correlation-id.
 *
 * Проверяем, что старт пишет в журнал категории `infra.web.start` и что прежнего
 * прямого вывода в stdout не осталось.
 */
class KaraokeWebServiceLoggingTest {
    @Test
    fun `признак режима работы пишется в журнал а не в stdout`() {
        val logger = LoggerFactory.getLogger("infra.web.start") as ch.qos.logback.classic.Logger
        val previousLevel = logger.level
        logger.level = Level.INFO
        val appender = CapturingAppender()
        appender.start()
        logger.addAppender(appender)

        val originalOut = System.out
        val capturedOut = ByteArrayOutputStream()
        System.setOut(PrintStream(capturedOut, true, Charsets.UTF_8))

        try {
            service()
        } finally {
            System.setOut(originalOut)
            logger.detachAppender(appender)
            logger.level = previousLevel
            appender.stop()
        }

        val logged = appender.messages.firstOrNull { it.contains("web:start:WEB_WORK_ON_SERVER") }
        assertNotNull(logged, "признак режима работы должен попасть в журнал категории infra.web.start")

        val stdoutText = capturedOut.toString(Charsets.UTF_8)
        assertTrue(
            !stdoutText.contains("WEB_WORK_ON_SERVER = "),
            "в stdout не должно быть прежнего println-вывода: $stdoutText",
        )
    }

    /**
     * Создаёт сервис с тестовыми зависимостями. `init` выставляет глобалы
     * `karaoke-web`/`karaoke-app` — это побочный эффект конструктора, проверяемый здесь
     * как раз в момент старта.
     */
    private fun service(): KaraokeWebService =
        KaraokeWebService(
            webSocket = Mockito.mock(SimpMessagingTemplate::class.java),
            objectMapper = ObjectMapper(),
            karaokeStorageService = Mockito.mock(KaraokeStorageService::class.java),
            storageApiClient = Mockito.mock(StorageApiClient::class.java),
            wic = 0L,
            wos = 1L,
            dbLocalPostgresUser = "test",
            dbLocalPostgresPassword = "test",
            dbRemotePostgresUser = "test",
            dbRemotePostgresPassword = "test",
            dbRemoteHost = "localhost",
            dbRemotePort = "5432",
        )

    /** Собирает отформатированные сообщения журнала. */
    private class CapturingAppender : AppenderBase<ILoggingEvent>() {
        val messages = mutableListOf<String>()

        override fun append(event: ILoggingEvent) {
            messages.add(event.formattedMessage)
        }
    }
}
