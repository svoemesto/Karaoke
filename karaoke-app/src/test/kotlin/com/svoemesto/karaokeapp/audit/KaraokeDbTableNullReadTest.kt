package com.svoemesto.karaokeapp.audit

import com.svoemesto.karaokeapp.FakeKaraokeConnection
import com.svoemesto.karaokeapp.model.Author
import com.svoemesto.karaokeapp.model.KaraokeDbTable
import com.svoemesto.karaokeapp.model.shouldAssignColumnValue
import com.svoemesto.karaokeapp.services.KSS_APP
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.SAC_APP
import com.svoemesto.karaokeapp.services.StorageApiClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Statement

/**
 * KARAOKE-429 (инцидент 2026-11-14, прод): новые песни не доезжали до прода.
 *
 * Один из двух корней проблемы — рефлексивный загрузчик `KaraokeDbTable.loadListInternal`.
 * Он читал SQL NULL в колонку, объявленную в Kotlin как non-null, и передавал `null` в
 * сеттер: `property.setter.call(entity, null)` → NPE «Parameter specified as non-null is
 * null: method …setLastAlbumProcessed, parameter <set-?>». Такой NPE — НЕ `PSQLException`,
 * поэтому прежний `catch (_: PSQLException)` его не перехватывал: падала загрузка ВСЕЙ
 * таблицы `tbl_authors`, а с ней — весь target синхронизации авторов. Далее по цепочке FK
 * падали альбомы (`tbl_albums.author_id` → `tbl_authors`) и песни
 * (`tbl_songs.album_id` → `tbl_albums`), потому что прод применяет батч одной транзакцией.
 *
 * Контракт после правки:
 * - SQL NULL в not-null-поле НЕ роняет загрузку таблицы (остаётся значение конструктора);
 * - SQL NULL в nullable-поле пишется как `null`;
 * - одна «битая» строка/поле не обрушивает загрузку остальных.
 *
 * Офлайн: живая БД не нужна — JDBC замокан, `ResultSet` отдаёт нужные значения.
 */
class KaraokeDbTableNullReadTest {
    // KSS_APP/SAC_APP — lateinit-глобалы приложения (KaraokeAppService). В тестовой среде
    // Spring-контекста нет, поэтому подменяем их моками.
    private val storageService: KaraokeStorageService = Mockito.mock(KaraokeStorageService::class.java)
    private val storageApiClient: StorageApiClient = Mockito.mock(StorageApiClient::class.java)

    init {
        KSS_APP = storageService
        SAC_APP = storageApiClient
    }

    /**
     * Мок-JDBC, отдающий одну строку `tbl_authors`, где `last_album_processed` = SQL NULL.
     */
    private class Jdbc(
        private val lastAlbumProcessed: String?,
        private val authorName: String = "Author name",
    ) {
        val connection: Connection = Mockito.mock(Connection::class.java)
        private val statement: Statement = Mockito.mock(Statement::class.java)
        private val prepared: PreparedStatement = Mockito.mock(PreparedStatement::class.java)
        val resultSet: ResultSet = Mockito.mock(ResultSet::class.java)

        init {
            Mockito.`when`(connection.createStatement()).thenReturn(statement)
            Mockito.`when`(connection.prepareStatement(ArgumentMatchers.anyString())).thenReturn(prepared)
            Mockito.`when`(statement.executeQuery(ArgumentMatchers.anyString())).thenReturn(resultSet)
            Mockito.`when`(prepared.executeQuery()).thenReturn(resultSet)

            // Одна строка: next() → true, затем false.
            Mockito.`when`(resultSet.next()).thenReturn(true, false)
            Mockito.`when`(resultSet.getLong("id")).thenReturn(7L)
            Mockito.`when`(resultSet.getString("author")).thenReturn(authorName)
            Mockito.`when`(resultSet.getString("ym_id")).thenReturn("")
            Mockito.`when`(resultSet.getString("vk_id")).thenReturn("")
            Mockito.`when`(resultSet.getString("last_album_ym")).thenReturn("")
            Mockito.`when`(resultSet.getString("last_album_vk")).thenReturn("")
            Mockito.`when`(resultSet.getString("last_album_processed")).thenReturn(lastAlbumProcessed)
            Mockito.`when`(resultSet.getString("aliases")).thenReturn("")
            Mockito.`when`(resultSet.getString("description")).thenReturn("")
            Mockito.`when`(resultSet.getString("short_description")).thenReturn("")
            Mockito.`when`(resultSet.getString("warning")).thenReturn("")
            Mockito.`when`(resultSet.getBoolean("watched")).thenReturn(false)
            Mockito.`when`(resultSet.getBoolean("skip")).thenReturn(false)
        }
    }

    private fun loadAuthors(jdbc: Jdbc): List<Author> =
        KaraokeDbTable
            .loadList(
                clazz = Author::class,
                tableName = "tbl_authors",
                whereList = emptyList(),
                database = FakeKaraokeConnection(jdbcConnection = jdbc.connection),
                storageService = storageService,
                storageApiClient = storageApiClient,
                // ignoreUseInList=true — SELECT без списка колонок (как в loadByIds), чтобы тест
                // не зависел от information_schema.
                ignoreUseInList = true,
            ).filterIsInstance<Author>()

    @Test
    fun `SQL NULL в not-null-колонке не роняет загрузку таблицы`() {
        val jdbc = Jdbc(lastAlbumProcessed = null)

        val authors = loadAuthors(jdbc)

        assertEquals(1, authors.size, "строка с NULL обязана загрузиться, а не уронить таблицу")
        assertEquals(7L, authors.first().id)
        assertEquals("Author name", authors.first().author)
    }

    @Test
    fun `SQL NULL в not-null-поле оставляет значение конструктора`() {
        val jdbc = Jdbc(lastAlbumProcessed = null)

        val author = loadAuthors(jdbc).first()

        assertEquals("", author.lastAlbumProcessed, "непрочитанное поле сохраняет значение по умолчанию")
    }

    @Test
    fun `остальные строки таблицы не теряются из-за одной аномальной`() {
        // Мок отдаёт две строки: у первой last_album_processed = NULL, у второй — значение.
        val jdbc = Jdbc(lastAlbumProcessed = null)
        Mockito.`when`(jdbc.resultSet.next()).thenReturn(true, true, false)
        Mockito.`when`(jdbc.resultSet.getLong("id")).thenReturn(7L, 8L)
        Mockito.`when`(jdbc.resultSet.getString("last_album_processed")).thenReturn(null, "album-42")

        val authors = loadAuthors(jdbc)

        assertEquals(2, authors.size, "аномальная строка не должна отбрасывать остальные")
        assertEquals(8L, authors.last().id)
        assertEquals("album-42", authors.last().lastAlbumProcessed)
    }

    @Test
    fun `чистое значение читается без изменений`() {
        val jdbc = Jdbc(lastAlbumProcessed = "album-42", authorName = "Мумий Тролль")

        val author = loadAuthors(jdbc).first()

        assertEquals("album-42", author.lastAlbumProcessed)
        assertEquals("Мумий Тролль", author.author)
    }

    @Test
    fun `решение о присвоении значения вынесено в чистую функцию`() {
        // Контракт чистой функции — без БД и без reflection.
        assertTrue(
            shouldAssignColumnValue(value = "x", isMarkedNullable = false),
            "непустое значение присваивается всегда",
        )
        assertTrue(
            shouldAssignColumnValue(value = null, isMarkedNullable = true),
            "в nullable-поле NULL присваивается",
        )
        assertTrue(
            !shouldAssignColumnValue(value = null, isMarkedNullable = false),
            "в not-null-поле NULL НЕ присваивается (иначе NPE в сеттере)",
        )
    }
}
