package com.svoemesto.karaokeapp.db

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Контракт событий пула (спека 778, KARAOKE-430, FR-008/FR-009,
 * `contracts/db-pool-events.md`).
 *
 * **Зачем проверять именно события.** Отказ приложения (пул исчерпан) и отказ
 * БД (`too many clients`) выглядят снаружи одинаково — 503. Различить их можно
 * только по журналу, поэтому коды событий — часть контракта, а не деталь
 * реализации:
 *
 * - `DB-POOL-002` — исчерпание пула (отказ **приложения**);
 * - `DB-POOL-003` — аренда удерживается дольше порога, со стеком места взятия
 *   (показывает, где именно утекает);
 * - `DB-POOL-004` — рейпер вернул аренды мёртвых владельцев (ключевая метрика:
 *   ненулевое значение означает, что возврат по смерти потока работает).
 *
 * Без живой БД: пул — подставной (`FakePoolProvider`).
 */
class DbPoolEventsTest {
    /** Собирает сообщения журнала категории `infra.db.pool`. */
    private class CapturingAppender : AppenderBase<ILoggingEvent>() {
        val messages = mutableListOf<String>()

        override fun append(event: ILoggingEvent) {
            messages += event.formattedMessage
        }
    }

    private fun capturePoolLogs(block: (MutableList<String>) -> Unit) {
        val logger = LoggerFactory.getLogger("infra.db.pool") as Logger
        val appender = CapturingAppender()
        appender.context = logger.loggerContext
        appender.start()
        val previousLevel = logger.level
        logger.level = Level.WARN
        logger.addAppender(appender)
        try {
            block(appender.messages)
        } finally {
            logger.detachAppender(appender)
            logger.level = previousLevel
            appender.stop()
        }
    }

    /** Соединение с подставным пулом. */
    private inner class Exposed(
        provider: FakePoolProvider,
    ) : com.svoemesto.karaokeapp.KaraokeConnection(
            url = "jdbc:postgresql://fake:5432/karaoke",
            username = "u",
            password = "p",
            name = "LOCAL",
        ) {
        private val fakePool =
            DbPool(
                target = "LOCAL",
                jdbcUrl = "jdbc:postgresql://fake:5432/karaoke",
                username = "u",
                password = "p",
                config = PoolConfig(maxPoolSize = 4, minIdle = 0),
                providerFactory = { provider },
            )

        override fun pool(): DbPool? = fakePool
    }

    @Test
    fun `DB-POOL-004 пишется когда рейпер вернул аренду мёртвого владельца`() {
        val provider = FakePoolProvider(capacity = 4)
        val db = Exposed(provider)

        // Handle берёт поток, который сразу умирает, не закрывая handle.
        val started = CountDownLatch(1)
        val thread =
            Thread {
                started.countDown()
                db.getConnection()
            }
        thread.start()
        started.await(5, TimeUnit.SECONDS)
        thread.join(5_000)

        capturePoolLogs { messages ->
            val returned = db.reapIdleConnections(maxIdleMs = 900_000)

            assertEquals(1, returned, "аренда мёртвого владельца должна быть возвращена")
            assertTrue(
                messages.any { it.contains("DB-POOL-004") },
                "ожидалось событие DB-POOL-004, получено: $messages",
            )
            assertTrue(
                messages.any { it.contains("returned=1") },
                "в событии должно быть число возвращенных аренд, получено: $messages",
            )
        }
    }

    @Test
    fun `DB-POOL-003 пишется когда аренда удерживается дольше порога`() {
        val provider = FakePoolProvider(capacity = 4)
        val db = Exposed(provider)

        db.getConnection() // handle взят текущим (живым) потоком

        capturePoolLogs { messages ->
            val reported = db.reportLongHeldHandles(thresholdMs = 0)

            assertEquals(1, reported, "долгое удержание должно быть обнаружено")
            assertTrue(
                messages.any { it.contains("DB-POOL-003") },
                "ожидалось событие DB-POOL-003, получено: $messages",
            )
            assertTrue(
                messages.any { it.contains("stack=") },
                "в событии должен быть стек места взятия аренды, получено: $messages",
            )
        }
    }

    @Test
    fun `DB-POOL-003 не пишется когда аренды нет или порог не пройден`() {
        val provider = FakePoolProvider(capacity = 4)
        val db = Exposed(provider)

        capturePoolLogs { messages ->
            db.reportLongHeldHandles(thresholdMs = 0)

            assertTrue(
                messages.none { it.contains("DB-POOL-003") },
                "без аренды события быть не должно, получено: $messages",
            )
        }
    }
}
