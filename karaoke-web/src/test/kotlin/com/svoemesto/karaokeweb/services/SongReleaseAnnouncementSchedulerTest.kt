package com.svoemesto.karaokeweb.services

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.PrintStream

/**
 * KARAOKE-394 (аудит #336, находка P-37-08): ошибки писались через `println` — терялись
 * стектрейс, уровень логирования и correlation-id. Проверяем, что ошибка планировщика
 * попадает в систему логов и больше не пишется в stdout.
 *
 * **Как проверяется.** Обработка исключения вынесена в [SongReleaseAnnouncementScheduler.checkOnAirGuarded]
 * с параметром-действием (шов), поэтому ошибку можно вызвать без БД. Ловим и журнал
 * (logback appender), и stdout — `println` писал бы именно туда, минуя журнал.
 */
class SongReleaseAnnouncementSchedulerTest {
    private fun scheduler(): SongReleaseAnnouncementScheduler =
        SongReleaseAnnouncementScheduler(
            storageService = Mockito.mock(KaraokeStorageService::class.java),
            storageApiClient = Mockito.mock(StorageApiClient::class.java),
        )

    @Test
    fun `ошибка планировщика пишется в журнал а не в stdout`() {
        val logger = LoggerFactory.getLogger("infra.songrelease.announcement") as ch.qos.logback.classic.Logger
        val previousLevel = logger.level
        logger.level = Level.WARN
        val appender = CapturingAppender()
        appender.start()
        logger.addAppender(appender)

        // Перехватываем stdout: `println` писал бы именно туда, минуя журнал.
        val originalOut = System.out
        val capturedOut = ByteArrayOutputStream()
        System.setOut(PrintStream(capturedOut, true, Charsets.UTF_8))

        try {
            // Планировщик НЕ пробрасывает исключение: следующий тик не прерывается.
            scheduler().checkOnAirGuarded { throw IllegalStateException("connection reset") }
        } finally {
            System.setOut(originalOut)
            logger.detachAppender(appender)
            logger.level = previousLevel
            appender.stop()
        }

        val logged = appender.messages.firstOrNull { it.contains("songrelease:announcement:check_on_air_failed") }
        assertNotNull(logged, "ошибка должна попасть в журнал категории infra.songrelease.announcement")
        assertTrue(
            appender.hasThrowable,
            "в журнал должен уйти стектрейс (Throwable последним аргументом), а не только текст ошибки",
        )
        val stdoutText = capturedOut.toString(Charsets.UTF_8)
        // Console-appenders logback тоже пишут в stdout — это система логов, а не `println`.
        // Проверяем, что в stdout нет прежнего прямого вывода мимо журнала.
        assertTrue(
            !stdoutText.contains("checkOnAir error"),
            "в stdout не должно быть прежнего println-вывода: $stdoutText",
        )
        assertTrue(
            !stdoutText.contains("SongReleaseAnnouncementScheduler] checkOnAir"),
            "в stdout не должно быть прежнего println-вывода: $stdoutText",
        )
    }

    @Test
    fun `успешный тик не пишет в журнал об ошибке`() {
        val logger = LoggerFactory.getLogger("infra.songrelease.announcement") as ch.qos.logback.classic.Logger
        val previousLevel = logger.level
        logger.level = Level.WARN
        val appender = CapturingAppender()
        appender.start()
        logger.addAppender(appender)

        var executed = false
        try {
            scheduler().checkOnAirGuarded { executed = true }
        } finally {
            logger.detachAppender(appender)
            logger.level = previousLevel
            appender.stop()
        }

        assertTrue(executed, "действие должно быть выполнено")
        assertTrue(
            appender.messages.none { it.contains("check_on_air_failed") },
            "при успешном тике записи об ошибке быть не должно: ${appender.messages}",
        )
    }

    /** Собирает отформатированные сообщения и признак наличия стектрейса. */
    private class CapturingAppender : AppenderBase<ILoggingEvent>() {
        val messages = mutableListOf<String>()
        var hasThrowable: Boolean = false

        override fun append(event: ILoggingEvent) {
            messages.add(event.formattedMessage)
            if (event.throwableProxy != null) hasThrowable = true
        }
    }
}
