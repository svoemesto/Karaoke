package com.svoemesto.karaokeapp

import java.sql.DriverManager

/**
 * Базовый класс подключения к БД — кеширует по одному физическому
 * `java.sql.Connection` **на поток выполнения** (не одно общее на весь
 * инстанс/приложение, см. specs/087-fix-shared-db-connection).
 *
 * До этой фичи `connection` было общим `@Volatile`-полем: один физический
 * канал, которым конкурентно пользовались и HTTP-потоки, и поток очереди
 * заданий — PostgreSQL JDBC `Connection` не рассчитан на параллельное
 * использование из разных потоков, что приводило к протокольным сбоям
 * (`SocketTimeoutException`/«соединение уже закрыто») и роняло главный цикл
 * очереди. Теперь у каждого потока — своя запись в [ThreadLocal], поэтому
 * потоки не мешают друг другу.
 *
 * Self-healing не меняется: если закешированное для текущего потока
 * соединение отсутствует/закрыто/невалидно — пересоздаётся прозрачно для
 * вызывающего кода (сигнатура [getConnection] не менялась).
 *
 * @see docs/features/async-process-queue.md
 */
abstract class KaraokeConnection(
    open val url: String,
    open val username: String,
    open val password: String,
    open val name: String,
) {
    private val threadLocalConnection = ThreadLocal<java.sql.Connection?>()

    /**
     * Возвращает рабочее соединение для текущего потока, пересоздавая его
     * при необходимости (см. KDoc класса).
     */
    fun getConnection(): java.sql.Connection? {
        val conn = threadLocalConnection.get()
        if (conn == null || conn.isClosed || !conn.isValid(3)) {
            Class.forName("org.postgresql.Driver")
            try {
                threadLocalConnection.set(DriverManager.getConnection(url, username, password))
            } catch (e: Exception) {
                println("KaraokeConnection getConnection Exception: ${e.message}")
            }
        }
        return threadLocalConnection.get()
    }
}
