package com.svoemesto.karaokeapp.services.songdeletion

import com.svoemesto.karaokeapp.FakeKaraokeConnection
import com.svoemesto.karaokeapp.services.KSS_APP
import com.svoemesto.karaokeapp.services.SAC_APP
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import java.sql.Connection
import java.sql.PreparedStatement

/**
 * Тест фазы B — удаления в SERVER-БД (KARAOKE-429, спека 739, FR-001, FR-045, В-07).
 *
 * Фаза B обязана снимать строку песни **и** её запись в очереди синхронизации
 * `tbl_songs_sync` (write-only очередь на SERVER, потребителя в проекте нет).
 *
 * Тест НЕ ходит на прод: подменяется соединение SERVER-контура, и тест проверяет
 * выполненные SQL. Фактическая приёмка на обеих базах — ручной сценарий
 * (`quickstart.md`), она выполняется на admin-машине с включённым флагом.
 *
 * @see specs/739-song-delete-cascade/spec.md FR-001, FR-045
 */
class SongDeleteBothDatabasesTest {
    @Test
    fun `фаза B удаляет строку песни и её запись в очереди синхронизации`() {
        val statements = mutableListOf<String>()
        val connection = Mockito.mock(Connection::class.java)
        Mockito.`when`(connection.prepareStatement(Mockito.anyString())).thenAnswer { invocation ->
            val sql = invocation.getArgument<String>(0)
            statements.add(sql)
            val prepared = Mockito.mock(PreparedStatement::class.java)
            Mockito.`when`(prepared.executeUpdate()).thenReturn(1)
            prepared
        }
        val serverDb = FakeKaraokeConnection(connection, name = "SERVER")

        val service =
            SongDeletionService(
                database = FakeKaraokeConnection(null, name = "LOCAL"),
                remoteDatabaseProvider = { serverDb },
                storageServiceProvider = { KSS_APP },
                storageApiClientProvider = { SAC_APP },
            )

        // Фаза B вызывается напрямую через повторное удаление: строка песни в LOCAL
        // отсутствует, поэтому сценарий «удаление уже состоялось» не подходит —
        // вместо этого проверяем SQL фазы B отдельным вызовом сервиса удаления на
        // существующей записи невозможно без БД, поэтому проверяем через
        // повторный вызов с подставным соединением SERVER.
        val captor = ArgumentCaptor.forClass(String::class.java)

        // Вызываем приватную по сути операцию через публичный контракт сервиса:
        // повторное удаление несуществующей песни не трогает SERVER, поэтому
        // фазу B проверяем отдельным юнитом — см. `retryServerDeletion`.
        service.retryServerDeletion(songId = 4242L)

        Mockito.verify(connection, Mockito.atLeastOnce()).prepareStatement(captor.capture())
        val sqls = captor.allValues
        assertTrue(
            sqls.any { it.contains("DELETE FROM tbl_songs WHERE id = ?") },
            "строка песни в SERVER удаляется: $sqls",
        )
        assertTrue(
            sqls.any { it.contains("DELETE FROM tbl_songs_sync WHERE id = ?") },
            "запись очереди синхронизации в SERVER удаляется (В-07): $sqls",
        )
        assertEquals(2, statements.size, "в SERVER выполняются ровно два DELETE: $statements")
    }
}
