package com.svoemesto.karaokeapp.db

/**
 * Источник физических JDBC-соединений для [PooledHandle] (спека 778, KARAOKE-430).
 *
 * **Зачем интерфейс, а не сразу HikariCP.** Тесты в CI работают **без живой БД**
 * (джоб `Lint / backend unit tests` не поднимает PostgreSQL), поэтому логика
 * пула проверяется через подставную реализацию этого интерфейса. Hikari-специфика
 * (реальные каналы, keepalive, maxLifetime) проверяется на стенде по
 * `quickstart.md`.
 *
 * **Контракт `borrow()`/`release()`.** `borrow()` выдаёт **физический** канал;
 * `release()` возвращает его в пул. Канал не закрывается при возврате — закрывать
 * его должен только пул (по `maxLifetime`/`idleTimeout`). Это и есть отличие от
 * прежней модели, где канал принадлежал потоку.
 *
 * @see com.svoemesto.karaokeapp.db.DbPool
 */
interface PoolProvider {
    /**
     * Выдаёт физический канал из пула.
     *
     * Блокируется до освобождения слота; при исчерпании пула и истечении
     * таймаута ожидания бросает исключение (пул логирует `DB-POOL-002`).
     *
     * @throws PoolTimeoutException если свободный слот не появился за отведённое время
     */
    fun borrow(): java.sql.Connection

    /**
     * Возвращает канал в пул. Канал **не закрывается** — его закрытие — дело пула.
     *
     * @param channel канал, ранее выданный [borrow]
     */
    fun release(channel: java.sql.Connection)

    /** Всего физических каналов у пула (включая занятые). */
    fun totalConnections(): Int

    /** Каналов занято арендами прямо сейчас. */
    fun activeConnections(): Int

    /** Каналов свободно. */
    fun idleConnections(): Int

    /** Потоков ждут свободный слот (для события об исчерпании). */
    fun waitingThreads(): Int
}

/**
 * Отказ получить соединение: пул исчерпан, свободный слот не появился за
 * отведённое время.
 *
 * Отличается от отказа БД (`SQLException` при подключении) — по нему приложение
 * может отличить «не хватило слотов у нас» от «БД недоступна».
 *
 * @param target роль БД (`LOCAL` / `SERVER`)
 * @param waiting потоков ожидают слот
 * @param busy занято слотов
 * @param maxPoolSize потолок пула
 * @param timeoutMs сколько ждали
 */
class PoolTimeoutException(
    val target: String,
    val waiting: Int,
    val busy: Int,
    val maxPoolSize: Int,
    val timeoutMs: Long,
) : RuntimeException(
        "Пул соединений к БД ($target) исчерпан: $busy/$maxPoolSize занято, ожидающих $waiting, таймаут ${timeoutMs}мс",
    )
