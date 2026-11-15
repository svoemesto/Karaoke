package com.svoemesto.karaokeapp.db

import com.svoemesto.karaokeapp.KaraokeConnection
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Тест интеграции пула с `KaraokeConnection` (спека 778, KARAOKE-430, US1/US3).
 *
 * **Без живой БД** (в CI PostgreSQL нет): пул подменяется на
 * `FakePoolProvider`, подключение — `java.lang.reflect.Proxy`.
 *
 * Проверяется главное свойство фичи: **публичный контракт `getConnection()`
 * не меняется** — прикладной код (284 места вызова) не трогаем, а ThreadLocal-кеш
 * на поток сохраняется (ADR-0009).
 */
class KaraokeConnectionPoolTest {
    private lateinit var provider: FakePoolProvider

    @BeforeEach
    fun setUp() {
        provider = FakePoolProvider(capacity = 4)
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

    /**
     * Соединение с пулом, у которого источник каналов — подставной.
     *
     * `pool()` переопределён (это шов для тестов, как `getConnection()`): так
     * тест не создаёт HikariCP и не ходит в сеть.
     */
    private fun connectionWithFakePool(provider: FakePoolProvider): KaraokeConnection =
        object : KaraokeConnection(
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
        }

    /**
     * Соединение, открывающее доступ к своему пулу (protected-шов `pool()`).
     *
     * Нужно, чтобы проверить решение базового класса о создании пула **без**
     * обращения к БД: без пула `getConnection()` пошёл бы в сеть.
     */
    private class ExposedConnection :
        KaraokeConnection(
            url = "jdbc:postgresql://fake:5432/karaoke",
            username = "u",
            password = "p",
            name = "LOCAL",
        ) {
        fun currentPool(): DbPool? = pool()
    }

    @Test
    fun `getConnection возвращает Connection и кеш на поток сохраняется`() {
        val db = connectionWithFakePool(provider)

        val first = db.getConnection()
        val second = db.getConnection()

        assertNotNull(first, "getConnection() должен вернуть соединение")
        assertSame(first, second, "ThreadLocal-кеш: повторный вызов из того же потока → тот же объект")
    }

    @Test
    fun `в пуловом режиме физический канал не создаётся напрямую`() {
        val db = connectionWithFakePool(provider)
        db.getConnection()

        assertEquals(1, provider.createdCount.get(), "канал должен прийти из пула")
        assertEquals(1, provider.activeCount.get(), "канал занят арендой")
    }

    @Test
    fun `closeThreadConnection возвращает аренду в пул а не закрывает канал`() {
        val db = connectionWithFakePool(provider)
        db.getConnection()

        db.closeThreadConnection()

        assertEquals(1, provider.releasedCount.get(), "аренда должна вернуться в пул")
        assertEquals(0, provider.activeCount.get(), "слот освободился")
        assertEquals(0, provider.physicalCloses.get(), "физический канал закрывать нельзя")
    }

    @Test
    fun `повторный вызов после close берёт тот же канал из пула`() {
        val db = connectionWithFakePool(provider)
        val first = db.getConnection()
        db.closeThreadConnection()
        val second = db.getConnection()

        assertNotNull(second)
        assertEquals(1, provider.createdCount.get(), "канал переиспользуется, а не создаётся заново")
        assertNotSame(first, second, "после close возвращается новая аренда")
    }

    @Test
    fun `пул выключен - базовый класс не создаёт пул`() {
        DbPoolSettings.apply(
            enabled = false,
            maxLocal = 8,
            maxServer = 8,
            minIdle = 0,
            acquireTimeoutMs = 100,
            maxLifetimeMs = 1_800_000,
            keepaliveMs = 60_000,
            validationTimeoutMs = 3_000,
        )
        // Базовый класс (без переопределения pool()) при выключенном пуле
        // работает как раньше — через прямой DriverManager, пул не создаётся.
        // Проверяем это БЕЗ обращения к БД: сам факт отсутствия пула.
        val db = ExposedConnection()
        assertNull(db.currentPool(), "при выключенном пуле pool() должен быть null")
        assertEquals(0, provider.createdCount.get(), "подставной пул не использовался")
    }

    @Test
    fun `пул включён - базовый класс создаёт пул`() {
        val db = ExposedConnection()
        val created = db.currentPool()
        assertNotNull(created, "при включённом пуле pool() должен создать пул")
        assertEquals("LOCAL", created!!.target)
        assertEquals(8, created.config.maxPoolSize, "размер пула взят из настроек")
    }

    @Test
    fun `handle разных потоков получают разные каналы`() {
        val db = connectionWithFakePool(provider)
        val first = db.getConnection()
        val other = arrayOfNulls<java.sql.Connection>(1)
        val thread = Thread { other[0] = db.getConnection() }
        thread.start()
        thread.join(5_000)

        assertNotNull(first)
        assertNotNull(other[0])
        assertNotSame(first, other[0], "разные потоки — разные аренды")
        assertEquals(2, provider.activeCount.get())
    }

    @Test
    fun `self-healing - после SQLException следующий вызов берёт новую аренду`() {
        val db = connectionWithFakePool(provider)
        val first = db.getConnection()
        assertNotNull(first)

        // Имитация «битого» соединения: SQLException на прокси помечает
        // соединение битым (BR-274), и следующий getConnection() переоткрывает.
        db.markConnectionBroken()

        val second = db.getConnection()
        assertNotNull(second, "после битого состояния вызов должен вернуть новое соединение")
        assertNotSame(first, second, "битое соединение не возвращается")
    }
}
