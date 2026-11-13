package com.svoemesto.karaokeapp.services.songdeletion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Предпросмотр и уборка считают папку альбома по ОДНОМУ правилу
 * (KARAOKE-429, спека 739, FR-020, FR-021, SC-004).
 *
 * **Дефект, найденный владельцем на живой приёмке 2026-11-13.** В окне
 * подтверждения значилось «папка не удаляется: в папке есть файлы живых песен»,
 * а папка была удалена: песня была последней в альбоме. Причина — предпросмотр
 * считал по СТАРОМУ правилу («в папке не должно быть никаких файлов»), а уборка —
 * по новому («в папке не должно быть файлов ЖИВЫХ песен»): остатки прежних
 * удалений (мусор `[key].json`) папку не удерживают.
 *
 * Тест закрывает оба риска:
 * - предпросмотр зовёт ТОТ ЖЕ гард, что и уборка (проверка по исходнику);
 * - гард умеет исключать саму удаляемую песню — в предпросмотре её строка ещё
 *   в базе (в уборке её уже нет), иначе предпросмотр всегда считал бы, что в
 *   папке есть живая песня.
 */
class SongDeletePreviewFolderRuleTest {
    private val previewSource =
        File("src/main/kotlin/com/svoemesto/karaokeapp/services/songdeletion/SongDeletionPreview.kt")

    @Test
    fun `предпросмотр считает папку тем же гардом, что и уборка`() {
        assertTrue(previewSource.isFile, "исходник предпросмотра не найден: ${previewSource.path}")
        val text = previewSource.readText()
        assertTrue(
            text.contains("SongCleanupExecutor.liveSongsInAlbumFolder"),
            "предпросмотр должен считать живые песни в папке тем же гардом, что и уборка " +
                "(FR-020, FR-021): иначе окно подтверждения врёт про папку альбома",
        )
        assertFalse(
            text.contains("remainingFiles <= 0"),
            "старое правило («в папке не должно быть никаких файлов») больше не применяется: " +
                "остатки прежних удалений уходят вместе с альбомом",
        )
    }

    @Test
    fun `гард папки исключает саму удаляемую песню`() {
        Assumptions.assumeTrue(
            SongDeleteTestDatabase.isAvailable(),
            "реквизиты LOCAL-БД не заданы — проверка неприменима",
        )
        val database = SongDeleteTestDatabase.local()
        val sample = sampleSongWithAlbum(database)
        if (sample == null) {
            Assumptions.assumeTrue(false, "в LOCAL-БД нет песен с альбомом — проверка неприменима")
            return
        }
        val withSong =
            SongCleanupExecutor.liveSongsInAlbumFolder(
                database,
                sample.rootFolder,
                sample.albumId,
            )
        val withoutSong =
            SongCleanupExecutor.liveSongsInAlbumFolder(
                database,
                sample.rootFolder,
                sample.albumId,
                excludeSongId = sample.songId,
            )
        assertTrue(
            withSong > 0,
            "сама песня в папке считается живой (проверка на живых данных): " +
                "rootFolder=${sample.rootFolder}, albumId=${sample.albumId}",
        )
        assertEquals(
            withSong - 1,
            withoutSong,
            "с исключением удаляемой песни счётчик уменьшается ровно на одну: " +
                "в предпросмотре её строка ещё в базе, в уборке её уже нет",
        )
    }

    /** Песня с альбомом и непустой папкой. */
    private data class Sample(
        val songId: Long,
        val rootFolder: String,
        val albumId: Long,
    )

    private fun sampleSongWithAlbum(database: com.svoemesto.karaokeapp.KaraokeConnection): Sample? {
        val connection = database.getConnection() ?: return null
        return connection
            .prepareStatement(
                "SELECT id, root_folder, album_id FROM tbl_songs " +
                    "WHERE album_id > 0 AND root_folder IS NOT NULL AND root_folder <> '' LIMIT 1",
            ).use { ps ->
                ps.executeQuery().use { rs ->
                    if (rs.next()) {
                        Sample(rs.getLong(1), rs.getString(2), rs.getLong(3))
                    } else {
                        null
                    }
                }
            }
    }
}
