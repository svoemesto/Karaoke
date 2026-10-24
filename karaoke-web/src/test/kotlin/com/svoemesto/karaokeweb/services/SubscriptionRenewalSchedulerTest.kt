package com.svoemesto.karaokeweb.services

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import com.svoemesto.karaokeapp.model.SiteUser
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.slf4j.LoggerFactory

/**
 * Регрессионный тест «до/после» для аудита BR-515 «renewal-loop-not-isolated:subscription»
 * (OpenProject #378).
 *
 * **ДО правки** цикл автопродления шёл как `users.forEach { tryRenew(user) }` без `try/catch`:
 * исключение от ПЕРВОГО пользователя (обрыв соединения, таймаут, конфликт IDENTITY) выходило из
 * цикла, и все остальные кандидаты в этом тике не продлевались — их премиум истекал в течение
 * суток, автосписания не было.
 *
 * **ПОСЛЕ правки** сбой одного пользователя логируется с `siteUserId` (подробно — не чаще раза в
 * 6 часов на пользователя, остальные тики — `debug`) и цикл продолжается; в конце пишется сводка
 * о неудачах.
 */
class SubscriptionRenewalSchedulerTest {
    private fun scheduler(): SubscriptionRenewalScheduler =
        SubscriptionRenewalScheduler(
            storageService = Mockito.mock(KaraokeStorageService::class.java),
            storageApiClient = Mockito.mock(StorageApiClient::class.java),
            paymentService = Mockito.mock(PaymentService::class.java),
        )

    private fun user(id: Long): SiteUser = Mockito.mock(SiteUser::class.java).also { Mockito.`when`(it.id).thenReturn(id) }

    @Test
    fun `сбой одного пользователя не прерывает продление остальных`() {
        val processed = mutableListOf<Long>()
        val users = listOf(user(1), user(2), user(3))

        scheduler().renewEach(users) { user ->
            processed.add(user.id)
            if (user.id == 2L) throw IllegalStateException("connection reset")
        }

        assertEquals(listOf(1L, 2L, 3L), processed, "третий пользователь должен быть обработан после сбоя второго")
    }

    @Test
    fun `сводка о неудачах содержит siteUserId сбойного пользователя`() {
        val appender = CapturingAppender().also { it.start() }
        val logger = LoggerFactory.getLogger(SubscriptionRenewalScheduler::class.java) as ch.qos.logback.classic.Logger
        val previousLevel = logger.level
        logger.level = Level.DEBUG
        logger.addAppender(appender)
        try {
            scheduler().renewEach(listOf(user(7), user(8))) { user ->
                if (user.id == 7L) throw IllegalStateException("boom")
            }
        } finally {
            logger.detachAppender(appender)
            logger.level = previousLevel
            appender.stop()
        }

        assertTrue(
            appender.messages.any { it.contains("subscription:renew:failed siteUserId=7") },
            "должна быть подробная запись с siteUserId: ${appender.messages}",
        )
        assertTrue(
            appender.messages.any { it.contains("не удалось продлить 1 из 2") },
            "должна быть сводка о неудачах за тик: ${appender.messages}",
        )
    }

    @Test
    fun `повторный тик не пишет подробную ошибку того же пользователя снова`() {
        val appender = CapturingAppender().also { it.start() }
        val logger = LoggerFactory.getLogger(SubscriptionRenewalScheduler::class.java) as ch.qos.logback.classic.Logger
        val previousLevel = logger.level
        logger.level = Level.DEBUG
        logger.addAppender(appender)
        try {
            val scheduler = scheduler()
            // Два тика подряд: подробная запись — только на первом (интервал 6 часов).
            repeat(2) {
                scheduler.renewEach(listOf(user(7), user(8))) { user ->
                    if (user.id == 7L) throw IllegalStateException("boom")
                }
            }
        } finally {
            logger.detachAppender(appender)
            logger.level = previousLevel
            appender.stop()
        }

        val detailed = appender.messages.filter { it.contains("subscription:renew:failed siteUserId=7") }
        assertEquals(1, detailed.size, "подробная запись об одном пользователе — не чаще раза в 6 часов: $detailed")
        assertTrue(
            appender.messages.any { it.contains("subscription:renew:failed (повтор) siteUserId=7") },
            "на повторном тике — только debug-запись: ${appender.messages}",
        )
    }

    @Test
    fun `пустой список не обрабатывается`() {
        var calls = 0
        scheduler().renewEach(emptyList()) { calls++ }
        assertEquals(0, calls)
    }

    /** Собирает отформатированные сообщения журнала шедулера. */
    private class CapturingAppender : AppenderBase<ILoggingEvent>() {
        val messages = mutableListOf<String>()

        override fun append(event: ILoggingEvent) {
            messages.add(event.formattedMessage)
        }
    }
}
