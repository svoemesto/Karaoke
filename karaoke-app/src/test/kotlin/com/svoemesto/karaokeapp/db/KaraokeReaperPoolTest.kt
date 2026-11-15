package com.svoemesto.karaokeapp.db

import com.svoemesto.karaokeapp.KaraokeConnection
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Тест нового контракта рейпера в пуловом режиме
 * (спека 778, KARAOKE-430, US2, FR-012/FR-013).
 *
 * **Главное свойство:** рейпер возвращает в пул handle'ы **мёртвых** владельцев
 * и **не трогает** handle'ы живых — даже простаивающие. Лейн очереди
 * `KaraokeProcess` держит открытую транзакцию минутами; отдать его handle в
 * пул = откатить транзакцию = сломать задание.
 *
 * Без живой БД: пул — подставной (`FakePoolProvider`).
 */
class KaraokeReaperPoolTest {
    private lateinit var provider: FakePoolProvider

    @BeforeEach
    fun setUp() {
        provider = FakePoolProvider(capacity = 8)
        DbPoolSettings.apply(
            enabled = true,
            maxLocal = 8,
            maxServer = 8,
            minIdle = 0,
            acquireTimeoutMs = 100,
            maxLifetimeMs = 1_800_000,
            keepaliveMs = 60_000,
            validationTimeoutMs = 3_000,
        )
    }

    @AfterEach
    fun tearDown() {
        DbPoolSettings.apply(
            enabled = false,
            maxLocal = 128,
            maxServer = 20,
            minIdle = 2,
            acquireTimeoutMs = 5_000,
            maxLifetimeMs = 1_800_000,
            keepaliveMs = 60_000,
            validationTimeoutMs = 3_000,
        )
    }

    /** Соединение с подставным пулом (мост для доступа к handle). */
    private fun connection(): Exposed =
        Exposed()

    /** Мост к protected-шову `currentHandle()`. */
    private inner class Exposed :
        KaraokeConnection(
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
                config = PoolConfig(maxPoolSize = 8, minIdle = 0),
                providerFactory = { provider },
            )

        override fun pool(): DbPool? = fakePool

        fun handle(): PooledHandle? = currentHandle()
    }

    /**
     * Берёт handle в отдельном потоке и сразу завершает его — владелец мёртв.
     *
     * Handle берётся через `getConnection()`: именно он кладёт handle в
     * ThreadLocal потока и регистрирует владельца. Поток завершается, не
     * закрывая handle — это и есть проверяемый сценарий утечки.
     *
     * @return handle мёртвого потока
     */
    private fun handleOfDeadThread(db: Exposed): PooledHandle {
        val started = CountDownLatch(1)
        var handle: PooledHandle? = null
        val thread =
            Thread {
                started.countDown()
                db.getConnection()
                handle = db.handle()
            }
        thread.start()
        started.await(5, TimeUnit.SECONDS)
        thread.join(5_000)
        assertNotNull(handle, "handle должен быть взят потоком до его завершения")
        return handle!!
    }

    @Test
    fun `рейпер возвращает handle мёртвого владельца в пул`() {
        val db = connection()
        handleOfDeadThread(db)
        assertEquals(1, provider.activeCount.get(), "handle занят")

        val returned = db.reapIdleConnections(maxIdleMs = 900_000)

        assertEquals(1, returned, "handle мёртвого потока должен быть возвращён")
        assertEquals(1, provider.releasedCount.get(), "канал вернулся в пул")
        assertEquals(0, provider.activeCount.get(), "слот освободился")
        assertEquals(0, provider.physicalCloses.get(), "физический канал закрывать нельзя")
    }

    @Test
    fun `рейпер не трогает handle живого владельца`() {
        val db = connection()
        db.getConnection()
        assertEquals(1, provider.activeCount.get())

        val returned = db.reapIdleConnections(maxIdleMs = 900_000)

        assertEquals(0, returned, "живой владелец — handle не отдаём")
        assertEquals(1, provider.activeCount.get(), "handle остался занятым")
        assertEquals(0, provider.releasedCount.get())
    }

    @Test
    fun `рейпер не трогает handle живого владельца даже при большом простое`() {
        val db = connection()
        db.getConnection()
        // Простой — минуты: лейн очереди держит транзакцию минутами, и отдать
        // его handle в пул значило бы откатить транзакцию.
        Thread.sleep(50)

        val returned = db.reapIdleConnections(maxIdleMs = 1)

        assertEquals(0, returned, "простой не является основанием для возврата")
        assertEquals(1, provider.activeCount.get())
    }

    @Test
    fun `возврат не ждёт idle - происходит в первом же цикле рейпера`() {
        val db = connection()
        handleOfDeadThread(db)

        // idle-ms = 900 000 (15 минут) — но возврат мёртвого владельца не
        // должен ждать этого порога: худший случай = один цикл рейпера.
        val returned = db.reapIdleConnections(maxIdleMs = 900_000)

        assertEquals(1, returned, "возврат по смерти владельца — без ожидания idle-ms")
    }

    @Test
    fun `повторный проход рейпера ничего не возвращает`() {
        val db = connection()
        handleOfDeadThread(db)

        assertEquals(1, db.reapIdleConnections(maxIdleMs = 900_000))
        assertEquals(0, db.reapIdleConnections(maxIdleMs = 900_000), "handle уже возвращён")
    }

    @Test
    fun `openConnectionCount показывает число занятых аренд`() {
        val db = connection()
        assertEquals(0, db.openConnectionCount())
        db.getConnection()
        assertEquals(1, db.openConnectionCount(), "одна аренда занята")
        db.closeThreadConnection()
        assertEquals(0, db.openConnectionCount())
    }
}
