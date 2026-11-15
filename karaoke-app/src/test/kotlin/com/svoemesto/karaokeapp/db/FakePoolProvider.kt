package com.svoemesto.karaokeapp.db

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.sql.Connection
import java.util.concurrent.atomic.AtomicInteger

/**
 * Подставной источник каналов для тестов (спека 778, KARAOKE-430).
 *
 * **Зачем.** В CI нет живой PostgreSQL (джоб `Lint / backend unit tests` не
 * поднимает БД), поэтому логика пула проверяется без сети: этот класс выдаёт
 * поддельные `java.sql.Connection` и считает, сколько каналов создано, выдано
 * и возвращено.
 *
 * Поддельное соединение — обычный `java.lang.reflect.Proxy` (запрещены только
 * `mockStatic`/`mockito-inline`/`mockk`).
 */
class FakePoolProvider(
    private val pool: DbPool? = null,
    /** Сколько каналов пул может выдать одновременно. */
    private val capacity: Int = 8,
    /** Через сколько миллисекунд ожидание слота истекает. */
    private val acquireTimeoutMs: Long = 100,
    /** Роль БД для сообщения об исчерпании. */
    private val target: String = "LOCAL",
    /** Потолок пула для сообщения об исчерпании. */
    private val maxPoolSize: Int = capacity,
) : PoolProvider {
    /** Сколько физических каналов создано всего. */
    val createdCount = AtomicInteger(0)

    /** Сколько каналов выдано и ещё не возвращено. */
    val activeCount = AtomicInteger(0)

    /** Сколько каналов возвращено в пул. */
    val releasedCount = AtomicInteger(0)

    /** Сколько вызовов `close()` дошло до физического канала (должно быть 0). */
    val physicalCloses = AtomicInteger(0)

    private val idle = ArrayDeque<Connection>()

    override fun borrow(): Connection {
        // Пул исчерпан: свободного слота нет → тот же отказ, что у HikariCP
        // по истечении connectionTimeout (см. DbPool.borrowHandle).
        if (idle.isEmpty() && activeCount.get() >= capacity) {
            throw PoolTimeoutException(
                target = target,
                waiting = 1,
                busy = activeCount.get(),
                maxPoolSize = maxPoolSize,
                timeoutMs = acquireTimeoutMs,
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

    /** Создаёт поддельный канал, считающий закрытия. */
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
