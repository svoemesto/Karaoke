package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.FakeKaraokeConnection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.SocketException
import java.net.SocketTimeoutException
import java.sql.SQLException

/**
 * KARAOKE-386: «протухшее» соединение не должно доходить до пользователя.
 *
 * Инцидент 2026-11-02: после восстановления allow-list на проде соединения, открытые до reload'а
 * nginx, оказались мёртвыми — несколько запросов упали в интерфейс с
 * `PSQLException: Ошибка ввода/вывода при отправке бэкенду` / `Connection reset`, пока кэш
 * потоков не пересоздал каналы.
 *
 * Контракт, который фиксируют тесты:
 * - ошибка уровня соединения распознаётся по SQLState `08*`, по сокетным исключениям в цепочке
 *   `cause` и по тексту («Connection reset», «Ошибка ввода/вывода…»);
 * - ошибка логики SQL (например `23505`) не повторяется никогда — повтор не должен маскировать
 *   настоящую ошибку данных;
 * - идемпотентная операция повторяется ровно один раз на свежем соединении;
 * - после исчерпания попыток — [DatabaseUnavailableException] с машинным кодом, человекочитаемым
 *   текстом и исходной причиной в `cause`.
 *
 * Офлайн: живая БД не нужна.
 */
class DbAvailabilityTest {
    @Test
    fun `распознаёт ошибки уровня соединения`() {
        assertTrue(DbAvailability.isConnectionFailure(SQLException("x", "08006")), "класс 08 — обрыв")
        assertTrue(DbAvailability.isConnectionFailure(SQLException("x", "08003")))
        assertTrue(DbAvailability.isConnectionFailure(SQLException("x", "57P03")), "сервер не принимает соединения")
        assertTrue(DbAvailability.isConnectionFailure(SocketException("Connection reset")))
        assertTrue(DbAvailability.isConnectionFailure(SocketTimeoutException("timed out")))
        // pgjdbc оборачивает сетевую ошибку в PSQLException — смотрим цепочку cause.
        assertTrue(
            DbAvailability.isConnectionFailure(
                SQLException("Ошибка ввода/вывода при отправке бэкенду", "08006", SocketException("Broken pipe")),
            ),
        )
        assertTrue(DbAvailability.isConnectionFailure(RuntimeException("Connection reset by peer")))
    }

    @Test
    fun `не считает обрывом ошибки логики SQL`() {
        assertFalse(DbAvailability.isConnectionFailure(SQLException("duplicate key", "23505")))
        assertFalse(DbAvailability.isConnectionFailure(SQLException("syntax error", "42601")))
        assertFalse(DbAvailability.isConnectionFailure(IllegalStateException("не про БД")))
        assertFalse(DbAvailability.isConnectionFailure(null))
    }

    @Test
    fun `идемпотентная операция повторяется один раз на свежем соединении`() {
        val database = FakeKaraokeConnection()
        var calls = 0

        val result =
            DbAvailability.withRetryOnConnectionFailure(database, "loadList:tbl_songs") {
                calls++
                if (calls == 1) throw SQLException("Ошибка ввода/вывода при отправке бэкенду", "08006")
                "ok"
            }

        assertEquals("ok", result)
        assertEquals(2, calls, "ровно один прозрачный повтор")
    }

    @Test
    fun `ошибка логики SQL не повторяется`() {
        val database = FakeKaraokeConnection()
        var calls = 0
        val original = SQLException("duplicate key", "23505")

        val thrown =
            runCatching {
                DbAvailability.withRetryOnConnectionFailure(database, "insert:tbl_news") {
                    calls++
                    throw original
                }
            }.exceptionOrNull()

        assertSame(original, thrown, "исходная ошибка пробрасывается как есть")
        assertEquals(1, calls, "повтора нет — ошибки данных не маскируем")
    }

    @Test
    fun `после исчерпания попыток — DatabaseUnavailableException с кодом и причиной`() {
        val database = FakeKaraokeConnection()
        var calls = 0

        val thrown =
            runCatching {
                DbAvailability.withRetryOnConnectionFailure(database, "getListHashes:tbl_news") {
                    calls++
                    throw SocketException("Connection reset")
                }
            }.exceptionOrNull()

        val failure = assertInstanceOf(DatabaseUnavailableException::class.java, thrown)
        assertEquals(DbAvailability.CODE_UNAVAILABLE, failure.code)
        assertEquals("getListHashes:tbl_news", failure.operation)
        assertTrue(failure.userMessage.contains("Нет связи с сервером базы данных"), "текст для интерфейса")
        assertTrue(failure.message!!.contains("DB-UNAVAILABLE-001"), "код виден в сообщении")
        assertInstanceOf(SocketException::class.java, failure.cause, "причина сохранена для разбора")
        assertEquals(DbAvailability.MAX_ATTEMPTS, calls)
    }
}
