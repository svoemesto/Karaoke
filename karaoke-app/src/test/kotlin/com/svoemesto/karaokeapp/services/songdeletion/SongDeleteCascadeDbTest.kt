package com.svoemesto.karaokeapp.services.songdeletion

import com.svoemesto.karaokeapp.WORKING_DATABASE
import com.svoemesto.karaokeapp.services.APP_WORK_IN_CONTAINER
import com.svoemesto.karaokeapp.services.APP_WORK_ON_SERVER
import com.svoemesto.karaokeapp.services.DB_LOCAL_POSTGRES_PASSWORD
import com.svoemesto.karaokeapp.services.DB_LOCAL_POSTGRES_USER
import com.svoemesto.karaokeapp.services.DB_REMOTE_HOST
import com.svoemesto.karaokeapp.services.DB_REMOTE_PORT
import com.svoemesto.karaokeapp.services.DB_REMOTE_SSL_MODE
import com.svoemesto.karaokeapp.services.DB_SERVER_POSTGRES_PASSWORD
import com.svoemesto.karaokeapp.services.DB_SERVER_POSTGRES_USER
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.sql.Timestamp
import java.time.LocalDateTime

/**
 * Интеграционный тест каскадного удаления в БД (KARAOKE-429, спека 739, FR-005…FR-009).
 *
 * **Тест ничего не оставляет в базе:** все проверки идут в транзакции, которая
 * откатывается в `finally`. Реальные песни каталога не затрагиваются.
 *
 * Проверяется самое опасное место — SQL каскада: чистятся только записи из белого
 * списка, события без привязки к песни не трогаются, новость остаётся (с обнулённой
 * привязкой — миграция 58), потомок остаётся живым с обнулённой связью.
 *
 * @see specs/739-song-delete-cascade/spec.md FR-005, FR-006, FR-007, FR-008, FR-009
 */
class SongDeleteCascadeDbTest {
    /** Идентификатор синтетической песни — заведомо не существующий в каталоге. */
    private val songId = 900_000_001L

    /** Идентификатор синтетического потомка. */
    private val childId = 900_000_002L

    companion object {
        /** Идентификатор синтетической новости — константа (используется в фикстуре). */
        private const val NEWS_ID = 900_000_042L

        /**
         * Подготовка окружения БД для теста.
         *
         * Глобали приложения (`APP_WORK_ON_SERVER`, реквизиты БД) выставляет Spring в
         * `KaraokeAppService` — в юнит-тесте их нет, а `Connection` без них падает на
         * инициализации. Тест читает их из переменных окружения; если их нет —
         * тест пропускается (БД недоступна в этом окружении).
         *
         * Реквизиты БД в репозиторий НЕ попадают: только имена переменных.
         */
        @JvmStatic
        @BeforeAll
        fun prepareEnvironment() {
            val user = System.getenv("DB_LOCAL_POSTGRES_USER")
            val password = System.getenv("DB_LOCAL_POSTGRES_PASSWORD")
            if (user.isNullOrBlank() || password.isNullOrBlank()) return
            APP_WORK_IN_CONTAINER = false
            APP_WORK_ON_SERVER = false
            DB_LOCAL_POSTGRES_USER = user
            DB_LOCAL_POSTGRES_PASSWORD = password
            DB_SERVER_POSTGRES_USER = System.getenv("DB_SERVER_POSTGRES_USER") ?: user
            DB_SERVER_POSTGRES_PASSWORD = System.getenv("DB_SERVER_POSTGRES_PASSWORD") ?: password
            DB_REMOTE_HOST = System.getenv("DB_REMOTE_HOST") ?: "localhost"
            DB_REMOTE_PORT = System.getenv("DB_REMOTE_PORT") ?: "5432"
            DB_REMOTE_SSL_MODE = System.getenv("DB_REMOTE_SSL_MODE") ?: "prefer"
        }
    }

    @Test
    fun `каскад чистит связанные записи и не трогает чужие`() {
        Assumptions.assumeTrue(
            !System.getenv("DB_LOCAL_POSTGRES_USER").isNullOrBlank(),
            "реквизиты LOCAL-БД не заданы в окружении — интеграционный тест неприменим",
        )
        val connection: Connection =
            WORKING_DATABASE.getConnection()
                ?: run {
                    Assumptions.assumeTrue(false, "LOCAL-БД недоступна — интеграционный тест неприменим")
                    return
                }
        val previousAutoCommit = connection.autoCommit
        try {
            connection.autoCommit = false
            val seeded = seed(connection)
            // Счётчик событий без привязки к песне ДО каскада: он не должен измениться (FR-007).
            val eventsWithoutSongBefore = count(connection, "tbl_events", "song_id", 0)

            // Шаги плана — как в SongDeletionService.deleteLocal.
            SongDeletionPlanner.plan(songId).forEach { step -> execute(connection, step.sql, step.params) }
            val descendants = SongDeletionPlanner.descendantsStep(songId)
            execute(connection, descendants.sql, descendants.params)
            val songStep = SongDeletionPlanner.songStep(songId)
            execute(connection, songStep.sql, songStep.params)

            if (seeded.assignments) {
                assertEquals(0, count(connection, "tbl_song_assignments", "song_id", songId), "назначения удалены")
            }
            if (seeded.playlistItems) {
                assertEquals(0, count(connection, "tbl_site_playlist_items", "song_id", songId), "позиции плейлистов удалены")
            }
            if (seeded.shareLinks) {
                assertEquals(0, count(connection, "tbl_song_share_links", "song_id", songId), "ссылки удалены")
            }
            assertEquals(0, count(connection, "tbl_listening_history", "song_id", songId), "история прослушиваний удалена")

            // FR-007: события без привязки к песне (song_id = 0) остаются.
            assertEquals(0, count(connection, "tbl_events", "song_id", songId), "события песни удалены")
            assertEquals(
                eventsWithoutSongBefore,
                count(connection, "tbl_events", "song_id", 0),
                "события без привязки к песне не тронуты (FR-007)",
            )

            // FR-008: потомок жив, связь обнулена.
            assertEquals(1, count(connection, "tbl_songs", "id", childId), "потомок остался жив")
            assertEquals(
                1,
                countWhere(connection, "tbl_songs", "id = ? AND audio_parent_id = 0", listOf(childId)),
                "связь потомка с родителем обнулена (0 = «нет родителя»)",
            )

            // FR-009: новость осталась, привязка обнулена (миграция 58).
            assertEquals(1, count(connection, "tbl_news", "id", NEWS_ID), "новость осталась")
            assertEquals(
                0,
                countWhere(connection, "tbl_news", "song_id = ?", listOf(songId)),
                "привязка новости обнулена",
            )

            // Строка песни удалена.
            assertEquals(0, count(connection, "tbl_songs", "id", songId), "строка песни удалена")
        } finally {
            try {
                connection.rollback()
            } catch (e: Exception) {
                // откат не удался — фикстуры всё равно чистятся удалением строк
            }
            try {
                connection.autoCommit = previousAutoCommit
            } catch (e: Exception) {
                // игнорируем
            }
        }
    }

    /** Момент истечения тестовой ссылки. */
    private fun expiresAt(): Timestamp {
        val moment = LocalDateTime.now().plusDays(1)
        return Timestamp.valueOf(moment)
    }

    /** Что удалось засеять (у части таблиц есть FK на живые строки каталога). */
    private data class Seeded(
        val assignments: Boolean,
        val playlistItems: Boolean,
        val shareLinks: Boolean,
    )

    /** Синтетические данные: песня, её потомок, назначение, позиция плейлиста, события, новость. */
    private fun seed(connection: Connection): Seeded {
        connection
            .prepareStatement(
                "INSERT INTO tbl_songs (id, file_name, root_folder, song_name, song_author, song_album, song_year) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)",
            ).use { ps ->
                ps.setLong(1, songId)
                ps.setString(2, "Тестовая песня 739")
                ps.setString(3, "/sm-karaoke/work/Тестовый/2020 - Тестовый альбом")
                ps.setString(4, "Тестовая песня 739")
                ps.setString(5, "Тестовый")
                ps.setString(6, "Тестовый альбом")
                ps.setInt(7, 2020)
                ps.executeUpdate()
            }
        connection
            .prepareStatement(
                "INSERT INTO tbl_songs (id, file_name, root_folder, song_name, song_author, song_album, song_year, audio_parent_id) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            ).use { ps ->
                ps.setLong(1, childId)
                ps.setString(2, "Тестовая песня 739 (потомок)")
                ps.setString(3, "/sm-karaoke/work/Тестовый/2020 - Тестовый альбом")
                ps.setString(4, "Тестовая песня 739 (потомок)")
                ps.setString(5, "Тестовый")
                ps.setString(6, "Тестовый альбом")
                ps.setInt(7, 2020)
                ps.setLong(8, songId)
                ps.executeUpdate()
            }
        // Назначения, позиции плейлистов и ссылки ссылаются на живые строки
        // пользователей/плейлистов (FK) — берём существующие, иначе фикстуру пропускаем.
        val assignments =
            insertWithParent(connection, "tbl_song_assignments", "assignee_id", "tbl_site_users")
        val playlistItems =
            insertWithParent(connection, "tbl_site_playlist_items", "playlist_id", "tbl_site_playlists")
        val shareLinks =
            insertWithParent(
                connection,
                "tbl_song_share_links",
                "owner_site_user_id",
                "tbl_site_users",
                extraColumns =
                    listOf(
                        "token_hash" to "test-token-739",
                        "expires_at" to expiresAt(),
                    ),
            )
        insertWithParent(connection, "tbl_listening_history", "site_user_id", "tbl_site_users")
        connection
            .prepareStatement("INSERT INTO tbl_events (song_id) VALUES (?)")
            .use { ps ->
                ps.setLong(1, songId)
                ps.executeUpdate()
            }
        connection
            .prepareStatement("INSERT INTO tbl_events (song_id) VALUES (0)")
            .use { ps -> ps.executeUpdate() }
        val newsSql =
            "INSERT INTO tbl_news (id, title, body, song_id) OVERRIDING SYSTEM VALUE VALUES (?, ?, ?, ?)"
        connection
            .prepareStatement(newsSql)
            .use { ps ->
                ps.setLong(1, NEWS_ID)
                ps.setString(2, "Тестовая новость 739")
                ps.setString(3, "Тестовая новость 739")
                ps.setLong(4, songId)
                ps.executeUpdate()
            }
        return Seeded(assignments, playlistItems, shareLinks)
    }

    /**
     * Вставка строки с внешним ключом на существующую строку справочника.
     *
     * @return `true`, если фикстура создана; `false`, если справочник пуст.
     */
    private fun insertWithParent(
        connection: Connection,
        table: String,
        parentColumn: String,
        parentTable: String,
        extraColumns: List<Pair<String, Any?>> = emptyList(),
    ): Boolean {
        val parentId =
            connection
                .prepareStatement("SELECT min(id) FROM $parentTable")
                .use { ps ->
                    ps.executeQuery().use { rs ->
                        if (rs.next()) rs.getLong(1) else -1L
                    }
                }
        if (parentId <= 0L) return false
        val columns = (listOf("song_id", parentColumn) + extraColumns.map { it.first }).joinToString(", ")
        val placeholders = (listOf("?", "?") + extraColumns.map { "?" }).joinToString(", ")
        val sql = "INSERT INTO $table ($columns) VALUES ($placeholders)"
        connection
            .prepareStatement(sql)
            .use { ps ->
                ps.setLong(1, songId)
                ps.setLong(2, parentId)
                extraColumns.forEachIndexed { index, column ->
                    when (val value = column.second) {
                        is java.sql.Timestamp -> ps.setTimestamp(index + 3, value)
                        is Number -> ps.setLong(index + 3, value.toLong())
                        else -> ps.setString(index + 3, value?.toString())
                    }
                }
                ps.executeUpdate()
            }
        return true
    }

    private fun execute(
        connection: Connection,
        sql: String,
        params: List<Any?>,
    ) {
        connection.prepareStatement(sql).use { ps ->
            params.forEachIndexed { index, value ->
                if (value is Number) ps.setLong(index + 1, value.toLong()) else ps.setString(index + 1, value?.toString())
            }
            ps.executeUpdate()
        }
    }

    private fun count(
        connection: Connection,
        table: String,
        column: String,
        value: Long,
    ): Int = countWhere(connection, table, "$column = ?", listOf(value))

    private fun countWhere(
        connection: Connection,
        table: String,
        where: String,
        params: List<Any?>,
    ): Int =
        connection
            .prepareStatement("SELECT count(*) FROM $table WHERE $where")
            .use { ps ->
                params.forEachIndexed { index, value ->
                    if (value is Number) ps.setLong(index + 1, value.toLong()) else ps.setString(index + 1, value?.toString())
                }
                ps.executeQuery().use { rs ->
                    if (rs.next()) rs.getInt(1) else 0
                }
            }
}
