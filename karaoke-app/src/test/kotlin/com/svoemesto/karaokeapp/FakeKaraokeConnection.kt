package com.svoemesto.karaokeapp

import java.sql.Connection
import java.util.concurrent.atomic.AtomicInteger

/**
 * Поддельное [KaraokeConnection] для юнит-тестов аудита #336 (BR-038/BR-060/BR-116).
 *
 * Позволяет проверить ЖИЗНЕННЫЙ ЦИКЛ соединения потока без живой БД:
 *
 * - [getConnection] возвращает поддельное JDBC-соединение (или `null`);
 * - [closeThreadConnection] считает вызовы закрытия — по счётчику видно, закрыл ли
 *   одноразовый поток своё thread-local соединение.
 *
 * Методы [KaraokeConnection.getConnection] и [KaraokeConnection.closeThreadConnection]
 * сделаны `open` именно ради таких подмен (см. KDoc `KaraokeConnection`).
 */
class FakeKaraokeConnection(
    /** Поддельное JDBC-соединение, которое отдаёт [getConnection] (`null` — «нет БД»). */
    val jdbcConnection: Connection? = null,
    name: String = "FAKE",
) : KaraokeConnection("jdbc:fake:test", "test", "test", name) {
    /** Счётчик вызовов `closeThreadConnection()` — «след» закрытия соединения потока. */
    val closeCalls = AtomicInteger(0)

    /** Счётчик вызовов `getConnection()` — показывает, открывал ли код соединение заново. */
    val getConnectionCalls = AtomicInteger(0)

    override fun getConnection(): Connection? {
        getConnectionCalls.incrementAndGet()
        return jdbcConnection
    }

    override fun closeThreadConnection() {
        closeCalls.incrementAndGet()
        super.closeThreadConnection()
    }
}
