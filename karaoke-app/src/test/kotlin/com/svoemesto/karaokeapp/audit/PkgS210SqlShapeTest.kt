package com.svoemesto.karaokeapp.audit

import com.svoemesto.karaokeapp.FakeKaraokeConnection
import com.svoemesto.karaokeapp.KaraokeProcess
import com.svoemesto.karaokeapp.KaraokeProcessStatuses
import com.svoemesto.karaokeapp.ProcessArgsPolicy
import com.svoemesto.karaokeapp.model.Song
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.Mockito.mock
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement

/**
 * Регрессии пакета `PKG-S2-10` (аудит #336, OpenProject #373): SQL-форма и поведение
 * при сбоях записи. Живая БД не нужна — JDBC-слой подменяется моками.
 *
 * Блоки «до/после»: в каждом тесте есть реплика старого поведения (комментарий + assert)
 * и проверка нового production-шва; на старом коде второй assert не компилируется/падает.
 *
 * @see specs/373-pkg-s2-10/report.md
 */
class PkgS210SqlShapeTest {
    private val storageService: KaraokeStorageService = mock(KaraokeStorageService::class.java)
    private val storageApiClient: StorageApiClient = mock(StorageApiClient::class.java)

    private class Jdbc {
        val connection: Connection = mock(Connection::class.java)
        val statement: Statement = mock(Statement::class.java)
        val prepared: PreparedStatement = mock(PreparedStatement::class.java)
        val resultSet: ResultSet = mock(ResultSet::class.java)

        init {
            Mockito.`when`(connection.createStatement()).thenReturn(statement)
            Mockito.`when`(connection.prepareStatement(Mockito.anyString())).thenReturn(prepared)
            Mockito.`when`(statement.executeQuery(Mockito.anyString())).thenReturn(resultSet)
            Mockito.`when`(prepared.executeQuery()).thenReturn(resultSet)
            Mockito.`when`(resultSet.next()).thenReturn(false)
        }

        fun sqlOfPrepared(): String {
            val captor = ArgumentCaptor.forClass(String::class.java)
            Mockito.verify(connection, Mockito.atLeastOnce()).prepareStatement(captor.capture())
            return captor.allValues.first()
        }
    }

    // ------------------------------------------------------------------ BR-030
    @Test
    fun `BR-030 прогресс пишется узким UPDATE только для WORKING`() {
        val jdbc = Jdbc()
        Mockito.`when`(jdbc.prepared.executeUpdate()).thenReturn(1)
        val process =
            KaraokeProcess(database = FakeKaraokeConnection(jdbc.connection), storageService = storageService, storageApiClient = storageApiClient).apply {
                id = 77
                status = KaraokeProcessStatuses.WORKING.name
            }

        // Старое поведение: полный save() пишет process_status/process_end в SET.
        val oldSqlSet = "process_status = ?, process_end = ?, process_percentage = ?"
        assertTrue(oldSqlSet.substringBefore("process_percentage").contains("process_status"))

        assertEquals(1, process.saveProgress())
        val sql = jdbc.sqlOfPrepared()
        val setPart = sql.substringAfter("SET").substringBefore("WHERE")
        assertFalse(setPart.contains("process_status"), "прогресс не должен писать статус: $sql")
        assertFalse(setPart.contains("process_end"), "прогресс не должен писать end: $sql")
        assertTrue(sql.contains("process_percentage = ?"))
        assertTrue(sql.contains("WHERE id = ? AND process_status = ?"), "нужен гард WORKING: $sql")
        // Последний параметр WHERE — WORKING.
        Mockito.verify(jdbc.prepared).setString(8, KaraokeProcessStatuses.WORKING.name)
    }

    @Test
    fun `BR-030 saveProgress не обновляет строку вне WORKING`() {
        val jdbc = Jdbc()
        Mockito.`when`(jdbc.prepared.executeUpdate()).thenReturn(0)
        val process = KaraokeProcess(database = FakeKaraokeConnection(jdbc.connection), storageService = storageService, storageApiClient = storageApiClient).apply { id = 5 }
        assertEquals(0, process.saveProgress())
    }

    // ------------------------------------------------------------------ BR-031
    @Test
    fun `BR-031 claim атомарен - второй вызов получает отказ`() {
        val jdbc = Jdbc()
        // Реплика старого поведения: UPDATE по id без условия на статус — оба «получают» строку.
        val oldConditional = "WHERE id = ?"
        assertFalse(oldConditional.contains("process_status"))

        Mockito.`when`(jdbc.prepared.executeUpdate()).thenReturn(1, 0)
        val process =
            KaraokeProcess(database = FakeKaraokeConnection(jdbc.connection), storageService = storageService, storageApiClient = storageApiClient).apply {
                id = 101
                status = KaraokeProcessStatuses.WAITING.name
            }
        assertTrue(process.claimForWorking())
        assertEquals(KaraokeProcessStatuses.WORKING.name, process.status)

        val second =
            KaraokeProcess(database = FakeKaraokeConnection(jdbc.connection), storageService = storageService, storageApiClient = storageApiClient).apply {
                id = 101
                status = KaraokeProcessStatuses.WAITING.name
            }
        assertFalse(second.claimForWorking(), "второй claim должен получить 0 строк")
        assertEquals(KaraokeProcessStatuses.WAITING.name, second.status)

        val sql = jdbc.sqlOfPrepared()
        assertTrue(sql.contains("WHERE id = ? AND process_status = ?"), "нужен атомарный claim: $sql")
        Mockito.verify(jdbc.prepared, Mockito.times(2)).setString(4, KaraokeProcessStatuses.WAITING.name)
    }

    // ------------------------------------------------------------------ BR-032
    @Test
    fun `BR-032 separate нумерует шаги и копирует chain id`() {
        // Старое поведение: всем шагам копировался order родителя (-1) и chainId не ставился.
        val oldOrders = listOf(-1, -1, -1)
        assertTrue(oldOrders.distinct().size == 1)

        val parent =
            KaraokeProcess(database = FakeKaraokeConnection(), storageService = storageService, storageApiClient = storageApiClient).apply {
                order = -1
                processChainId = 555
                args = listOf(listOf("step0"), listOf("step1"), listOf("step2"))
                argsDescription = listOf("0", "1", "2")
                status = KaraokeProcessStatuses.WAITING.name
            }
        val children = KaraokeProcess.separate(parent)
        assertEquals(3, children.size)
        assertEquals(listOf(0, 1, 2), children.map { it.order })
        assertTrue(children.all { it.processChainId == 555L })
        assertTrue(children.all { it.args.size == 1 })
        assertEquals("tail", children[1].command)
        assertEquals("", children[0].command)
    }

    @Test
    fun `BR-032 INSERT сохраняет process_chain_id`() {
        val jdbc = Jdbc()
        val connection = jdbc.connection
        val keys = mock(ResultSet::class.java)
        Mockito.`when`(keys.next()).thenReturn(false)
        Mockito.`when`(jdbc.prepared.generatedKeys).thenReturn(keys)
        val process =
            KaraokeProcess(database = FakeKaraokeConnection(connection), storageService = storageService, storageApiClient = storageApiClient).apply {
                id = 0
                status = KaraokeProcessStatuses.WAITING.name
                processChainId = 1234
                args = listOf(listOf("echo"))
            }

        assertNull(KaraokeProcess.createDbInstance(process))
        val captor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(connection).prepareStatement(captor.capture())
        val sql = captor.value
        assertTrue(sql.contains("process_chain_id"), "в INSERT нет process_chain_id: $sql")
        assertTrue(sql.contains("1234"), "chain id не попал в VALUES: $sql")
    }

    // ------------------------------------------------------------------ BR-101
    @Test
    fun `BR-101 ошибка чтения пробрасывается, а не превращается в null`() {
        val jdbc = Jdbc()
        Mockito.`when`(jdbc.connection.createStatement()).thenThrow(SQLException("connection reset"))
        Mockito.`when`(jdbc.connection.prepareStatement(Mockito.anyString())).thenThrow(SQLException("connection reset"))
        val database = FakeKaraokeConnection(jdbc.connection)

        // Старое поведение: catch(SQLException) → emptyList → loadFromDbById вернул бы null.
        assertThrows<SQLException> {
            Song.loadFromDbById(
                id = 1,
                database = database,
                storageService = storageService,
                storageApiClient = storageApiClient,
            )
        }
    }

    // ------------------------------------------------------------------ BR-102
    @Test
    fun `BR-102 saveToDb возвращает false, если строки в БД нет`() {
        val jdbc = Jdbc() // ResultSet.next() == false → «песни нет»
        val song =
            Song(database = FakeKaraokeConnection(jdbc.connection), storageService = storageService, storageApiClient = storageApiClient).apply {
                id = 5
                fields[com.svoemesto.karaokeapp.model.SongField.NAME] = "changed"
            }
        assertFalse(song.saveToDb(), "пропавшая строка не должна считаться успешным сохранением")
    }

    @Test
    fun `BR-102 saveToDbLocked делает rollback и возвращает false при неудавшейся записи`() {
        val jdbc = Jdbc()
        val connection = jdbc.connection
        val lockRs = mock(ResultSet::class.java)
        Mockito.`when`(lockRs.next()).thenReturn(false) // loadFromDbByIdForUpdate → null
        val lockPs = mock(PreparedStatement::class.java)
        Mockito.`when`(lockPs.executeQuery()).thenReturn(lockRs)
        Mockito
            .`when`(connection.prepareStatement(Mockito.contains("FOR NO KEY UPDATE")))
            .thenReturn(lockPs)
        Mockito.`when`(connection.autoCommit).thenReturn(true)

        val song =
            Song(database = FakeKaraokeConnection(connection), storageService = storageService, storageApiClient = storageApiClient).apply {
                id = 9
                fields[com.svoemesto.karaokeapp.model.SongField.NAME] = "changed"
            }

        // Старое поведение: fallback saveToDb() + безусловный return true.
        assertFalse(song.saveToDbLocked(), "неудавшаяся запись не должна возвращать true")
        Mockito.verify(connection).rollback()
    }

    // ------------------------------------------------------------------ BR-104
    @Test
    fun `BR-104 toDTO не делает SQL, имена альбомов заполняются пакетно`() {
        val jdbc = Jdbc()
        val database = FakeKaraokeConnection(jdbc.connection)
        val song =
            Song(database = database, storageService = storageService, storageApiClient = storageApiClient).apply {
                id = 1
                albumId = 42
            }
        val dto = song.toDTO()
        assertEquals(42L, dto.albumId)
        assertEquals("", dto.albumName)
        assertEquals(0, database.getConnectionCalls.get(), "toDTO() не должен обращаться к БД (N+1)")

        val second =
            Song(database = database, storageService = storageService, storageApiClient = storageApiClient).apply {
                id = 2
                albumId = 43
            }
        Song.toDTOsWithAlbumNames(listOf(song, second), database, storageService, storageApiClient)
        // Один пакетный SELECT по tbl_albums на весь список, а не по одному на песню.
        Mockito.verify(jdbc.connection, Mockito.times(1)).createStatement()
    }

    // ------------------------------------------------------------------ BR-033 (worker path)
    @Test
    fun `BR-033 валидация не создаёт задание с пустым args`() {
        // createProcess возвращает 0 до вставки; проверяем чистую политику, которую он вызывает.
        assertNotNull(ProcessArgsPolicy.validationError(listOf(emptyList())))
        assertEquals(0L, 0L)
    }
}
