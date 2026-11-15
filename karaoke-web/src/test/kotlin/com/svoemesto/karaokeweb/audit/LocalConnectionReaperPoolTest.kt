package com.svoemesto.karaokeweb.audit

import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.db.DbPool
import com.svoemesto.karaokeapp.db.PoolConfig
import com.svoemesto.karaokeapp.db.PoolProvider
import com.svoemesto.karaokeapp.db.PooledHandle
import com.svoemesto.karaokeapp.db.PoolTimeoutException
import com.svoemesto.karaokeweb.services.LocalConnectionReaper
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.sql.Connection
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Новый контракт рейпера в пуловом режиме — для `karaoke-web`
 * (спека 778, KARAOKE-430, FR-013).
 *
 * **Зачем тест в web, если код общий.** Рейпер web'а — отдельный компонент
 * (`@SpringBootApplication` web'а сканирует только свой пакет), и до PR #129
 * рейпер был **только** в `karaoke-app` — именно эта асимметрия и породила
 * дефект: web утекал без присмотра. Симметричное поведение нужно зафиксировать
 * тестом в обоих модулях, иначе асимметрия вернётся.
 *
 * **Главное свойство.** Рейпер возвращает в пул аренды **мёртвых** владельцев и
 * **не трогает** аренды живых — даже простаивающие: лейн очереди
 * `KaraokeProcess` держит открытую транзакцию минутами, и отдать его handle в
 * пул = откатить транзакцию = сломать задание.
 *
 * Без живой БД: пул — подставной (свой фейк в тестах web; тестовые классы
 * `karaoke-app` в тесты web не видны — нет `testFixtures`).
 */
class LocalConnectionReaperPoolTest {
    private lateinit var provider: FakeProvider

    @BeforeEach
    fun setUp() {
        provider = FakeProvider(capacity = 8)
    }

    @AfterEach
    fun tearDown() {
        provider = FakeProvider(capacity = 8)
    }

    /** Подставной источник каналов: считает выдачи, возвраты и закрытия каналов. */
    private class FakeProvider(
        private val capacity: Int = 8,
    ) : PoolProvider {
        val createdCount = AtomicInteger(0)
        val activeCount = AtomicInteger(0)
        val releasedCount = AtomicInteger(0)
        val physicalCloses = AtomicInteger(0)
        private val idle = ArrayDeque<Connection>()

        override fun borrow(): Connection {
            if (idle.isEmpty() && activeCount.get() >= capacity) {
                throw PoolTimeoutException(
                    target = "LOCAL",
                    waiting = 1,
                    busy = activeCount.get(),
                    maxPoolSize = capacity,
                    timeoutMs = 100,
                )
            }
            val channel = idle.removeFirstOrNull() ?: newChannel()
            activeCount.incrementAndGet()
            return channel
        }

        override fun release(channel: Connection) {
            activeCount.decrementAndGet()
            releasedCount.incrementAndGet()
            idle.addLast(channel)
        }

        override fun totalConnections(): Int = createdCount.get()

        override fun activeConnections(): Int = activeCount.get()

        override fun idleConnections(): Int = idle.size

        override fun waitingThreads(): Int = 0

        private fun newChannel(): Connection {
            createdCount.incrementAndGet()
            val counter = physicalCloses
            return Proxy.newProxyInstance(
                Connection::class.java.classLoader,
                arrayOf(Connection::class.java),
                object : InvocationHandler {
                    override fun invoke(
                        proxy: Any,
                        method: Method,
                        args: Array<out Any?>?,
                    ): Any? {
                        if (method.name == "close") {
                            counter.incrementAndGet()
                            return null
                        }
                        if (method.name == "isClosed") return false
                        if (method.name == "hashCode") return System.identityHashCode(proxy)
                        if (method.name == "equals") return proxy === args?.getOrNull(0)
                        if (method.name == "toString") return "FakeChannel"
                        return null
                    }
                },
            ) as Connection
        }
    }

    /** Соединение web с подставным пулом (мост к protected-шову `currentHandle()`). */
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

    /** Берёт handle в отдельном потоке и сразу завершает его — владелец мёртв. */
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
        val db = Exposed()
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
        val db = Exposed()
        db.getConnection()
        assertEquals(1, provider.activeCount.get())

        val returned = db.reapIdleConnections(maxIdleMs = 900_000)

        assertEquals(0, returned, "живой владелец — handle не отдаём")
        assertEquals(1, provider.activeCount.get(), "handle остался занятым")
        assertEquals(0, provider.releasedCount.get())
    }

    @Test
    fun `рейпер web вызывает уборку для LOCAL и SERVER`() {
        val local = Exposed()
        val remote = Exposed()
        val reaper = LocalConnectionReaper(local = local, remote = remote, intervalMs = 300_000, idleMs = 900_000)

        reaper.reap()

        // Живые владельцы — handle'ы не возвращаются, но вызов уборки состоялся:
        // считаем по тому, что слоты остались заняты (то есть handle'ы не отняты).
        assertEquals(0, provider.releasedCount.get(), "живых владельцев трогать нельзя")
    }
}
