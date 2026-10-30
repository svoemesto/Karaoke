package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.FakeKaraokeConnection
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.mockito.Mockito
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Statement
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * FR-004 (спека 676): покрытие починки на **уровне вызывающих**, а не только вспомогательной
 * функции [popPublicationStacks].
 *
 * Прецедент: инцидент OOM 2026-10-23 возник именно в вызывающем коде
 * (`Publication.getUnPublicationList` / `Publication.getSkipedPublicationList`), а существующие
 * тесты пакета PKG-S2-12 проверяли только вспомогательную функцию — регрессия в вызывающем коде
 * проходила незамеченной.
 *
 * Ключевое решение: **product-код не меняется**. Подменяется граница JDBC — ровно та, что и
 * остальные обращения к БД в этом проекте (ADR-0001, сырой JDBC). Ни JPA, ни новых зависимостей:
 * моки JDBC — на Mockito, который уже есть в `spring-boot-starter-test` (см. [MockJdbc] —
 * готовый мок JDBC-слоя в этом же пакете).
 *
 * Демонстрация «падает на прежней реализации» — локальная и обратимая: временная подмена тела
 * раскладки в вызывающих функциях на прежнее (условие выхода — захваченный максимум размера
 * стопки) с немедленным откатом. Ключевой вывод проверки: прежняя реализация оставляет
 * **существующие** тесты `PkgS212BehaviorTest` / `PkgS212SourceContractsTest` зелёными — ловит
 * только этот тест.
 *
 * @see com.svoemesto.karaokeapp.audit.PkgS212BehaviorTest
 * @see com.svoemesto.karaokeapp.audit.PkgS212SourceContractsTest
 */
class PublicationGridCallerTest {
    // --- FR-004: раскладка конечна на уровне вызывающих -----------------------------------

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    fun `FR-004 getUnPublicationList раскладывает 500 песен в конечную сетку`() {
        val db = FakeDb(songAuthors = songAuthors(), skippedAuthors = emptyList())

        val rows = Publication.getUnPublicationList(db.database, storageService(), storageApiClient())

        assertEquals(SONGS_PER_AUTHOR, rows.size, "строк столько же, сколько песен у самого «плотного» автора")
        assertTrue(rows.all { it.size == AUTHORS }, "столбцов — число авторов с непустой стопкой")
        val published = rows.flatMap { row -> row.mapNotNull { it.publish10 } }
        assertEquals(TOTAL_SONGS, published.size, "в раскладку попали все песни")
        assertEquals(
            (1L..TOTAL_SONGS.toLong()).toSet(),
            published.map { it.id }.toSet(),
            "каждая песня попала в раскладку ровно один раз",
        )
        assertFirstRowKeepsPublicationOrder(rows, published)
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    fun `FR-004 getSkipedPublicationList раскладывает 500 песен skip-авторов`() {
        val db = FakeDb(songAuthors = songAuthors(), skippedAuthors = authorNames())

        val rows = Publication.getSkipedPublicationList(db.database, storageService(), storageApiClient())

        assertEquals(SONGS_PER_AUTHOR, rows.size, "строк столько же, сколько песен у самого «плотного» автора")
        assertTrue(rows.all { it.size == AUTHORS }, "столбцов — число авторов с непустой стопкой")
        assertEquals(TOTAL_SONGS, rows.flatMap { row -> row.mapNotNull { it.publish10 } }.size, "в раскладку попали все песни")
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    fun `FR-004 пустой набор даёт пустую раскладку без единой итерации`() {
        val db = FakeDb(songAuthors = emptyList(), skippedAuthors = emptyList())

        assertEquals(0, Publication.getUnPublicationList(db.database, storageService(), storageApiClient()).size)
        assertEquals(0, Publication.getSkipedPublicationList(db.database, storageService(), storageApiClient()).size)
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    fun `FR-004 песни skip-автора попадают только в skip-раскладку`() {
        val skipped = authorNames().take(AUTHORS / 2)
        val db = FakeDb(songAuthors = songAuthors(), skippedAuthors = skipped)

        val regular = Publication.getUnPublicationList(db.database, storageService(), storageApiClient())
        val skippedRows = Publication.getSkipedPublicationList(db.database, storageService(), storageApiClient())

        assertEquals(AUTHORS / 2, regular.first().size, "в обычной раскладке — только авторы без skip")
        assertEquals(AUTHORS / 2, skippedRows.first().size, "в skip-раскладке — только авторы со skip")
        assertEquals(SONGS_PER_AUTHOR, regular.size, "строки — максимум песен у одного автора")
        assertEquals(SONGS_PER_AUTHOR, skippedRows.size, "строки — максимум песен у одного автора")
        val regularAuthors = regular.flatMap { row -> row.mapNotNull { it.publish10 } }.map { it.author }.toSet()
        val skippedAuthors = skippedRows.flatMap { row -> row.mapNotNull { it.publish10 } }.map { it.author }.toSet()
        assertTrue(regularAuthors.intersect(skippedAuthors).isEmpty(), "наборы авторов не пересекаются")
        assertEquals(AUTHORS, regularAuthors.size + skippedAuthors.size, "каждый автор попал ровно в одну раскладку")
    }

    // --- вспомогательное -------------------------------------------------------------------

    /**
     * Первый ряд раскладки содержит по одной песне каждого автора — и это **первая** по времени
     * публикации песня автора (FR-002: «замысел “слои по максимальной длине стопки” сохранён»).
     */
    private fun assertFirstRowKeepsPublicationOrder(
        rows: List<List<Publication>>,
        published: List<Song>,
    ) {
        val minIdByAuthor = published.groupBy { it.author }.mapValues { (_, songs) -> songs.minOf { song -> song.id } }
        rows.first().forEach { publication ->
            val song = publication.publish10 ?: error("пустой слот в первом ряду")
            assertEquals(
                minIdByAuthor[song.author],
                song.id,
                "первый ряд должен начинаться с самой ранней песни автора",
            )
        }
    }

    private fun authorNames(): List<String> = (1..AUTHORS).map { "Автор $it" }

    /** Песни по авторам: у каждого автора [SONGS_PER_AUTHOR] песен, id — по порядку строк. */
    private fun songAuthors(): List<String> = authorNames().flatMap { author -> List(SONGS_PER_AUTHOR) { author } }

    private fun storageService(): KaraokeStorageService = MockJdbc.storageService()

    private fun storageApiClient(): StorageApiClient = MockJdbc.storageApiClient()

    /**
     * Подмена JDBC-границы: соединение отвечает на два запроса — список неопубликованных песен
     * (`tbl_songs`) и список авторов со `skip = true` (`tbl_authors`).
     *
     * Фактические SQL в коде: `SELECT * FROM tbl_songs WHERE publish_date = '' AND
     * publish_time = '' …` и `SELECT tbl_authors.* FROM tbl_authors WHERE skip = true`
     * (`Song.getWhereList`, `Author.getWhereList`) — диспетчеризация по имени таблицы.
     */
    private class FakeDb(
        songAuthors: List<String>,
        skippedAuthors: List<String>,
    ) {
        private val connection: Connection = Mockito.mock(Connection::class.java)
        private val statement: Statement = Mockito.mock(Statement::class.java)
        private val songs = FakeResultSet(songAuthors, idColumn = "id", authorColumn = "song_author")
        private val authors = FakeResultSet(skippedAuthors, idColumn = "id", authorColumn = "author")

        val database: FakeKaraokeConnection = FakeKaraokeConnection(jdbcConnection = connection, name = "FAKE")

        init {
            Mockito.`when`(connection.createStatement()).thenReturn(statement)
            Mockito.`when`(statement.executeQuery(Mockito.anyString())).thenAnswer { invocation ->
                resultSetFor(invocation.getArgument(0))
            }
            Mockito.`when`(connection.prepareStatement(Mockito.anyString())).thenAnswer { invocation ->
                val sql = invocation.getArgument<String>(0)
                val prepared = Mockito.mock(PreparedStatement::class.java)
                Mockito.`when`(prepared.executeQuery()).thenReturn(resultSetFor(sql))
                prepared
            }
        }

        /**
         * Набор строк переиспользуется на каждый запрос: настоящая БД отдаёт те же строки
         * повторному `SELECT`, а мок [ResultSet] конечный и одноразовый.
         */
        private fun resultSetFor(sql: String): ResultSet =
            (if (sql.contains("tbl_songs")) songs else authors).also { it.reset() }.rs
    }

    private companion object {
        /** Число авторов в наборе. */
        const val AUTHORS = 10

        /** Песен у одного автора. */
        const val SONGS_PER_AUTHOR = 50

        /** Всего песен в наборе (SC-002 — 500). */
        const val TOTAL_SONGS = AUTHORS * SONGS_PER_AUTHOR
    }
}

/**
 * Мок [ResultSet] с заданными строками: `id` — номер строки, колонка автора — имя автора,
 * остальные строковые колонки — пустая строка (значения `null` setter'ы non-null полей
 * `Author` не принимают).
 *
 * Набор строк переиспользуется на каждый запрос ([reset]) — как настоящая БД, отдающая те же
 * строки повторному `SELECT`.
 */
private class FakeResultSet(
    private val rows: List<String>,
    private val idColumn: String,
    private val authorColumn: String,
) {
    private val row = AtomicInteger(-1)

    val rs: ResultSet = Mockito.mock(ResultSet::class.java)

    init {
        Mockito.`when`(rs.next()).thenAnswer { row.incrementAndGet() < rows.size }
        Mockito.`when`(rs.getString(Mockito.anyString())).thenAnswer { invocation ->
            val column = invocation.getArgument<String>(0)
            if (column == authorColumn) rows.getOrElse(row.get()) { "" } else ""
        }
        Mockito.`when`(rs.getInt(Mockito.anyString())).thenReturn(0)
        Mockito.`when`(rs.getInt(Mockito.eq(idColumn))).thenAnswer { row.get() + 1 }
    }

    /** Начать чтение набора с первой строки. */
    fun reset() {
        row.set(-1)
    }
}
