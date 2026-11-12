package com.svoemesto.karaokeapp.services.songdeletion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test

/**
 * Гард папки альбома по данным БД (KARAOKE-429, спека 739, FR-020, FR-021).
 *
 * **Дефект, найденный на живой приёмке 2026-11-12.** Удалена последняя песня
 * альбома; запись альбома удалилась, а папка осталась с причиной
 * `album_has_live_songs`. Причина была неверной: гард смотрел, остались ли в
 * папке ЛЮБЫЕ файлы, а не есть ли в ней файлы ЖИВЫХ песен. Мусор `[key].json`
 * от прежних удалений удерживал папку альбома навсегда.
 *
 * По FR-020 папка удаляется, если в ней нет файлов живых песен; по FR-021 папка
 * остаётся, если в ней лежат файлы песен, заведённых в базе. Проверяется это по
 * БД: `tbl_songs.root_folder` (папка) и `tbl_songs.album_id` (альбом).
 *
 * Интеграционный тест: нужен LOCAL-БД admin-машины. В CI реквизитов нет — тест
 * пропускается (`Assumptions`), как и остальные интеграционные наборы фичи.
 *
 * @see specs/739-song-delete-cascade/spec.md FR-020, FR-021
 */
class SongAlbumFolderGuardDbTest {
    @Test
    fun `папка без песен в базе не защищена`() {
        Assumptions.assumeTrue(
            SongDeleteTestDatabase.isAvailable(),
            "реквизиты LOCAL-БД не заданы — проверка FR-020 неприменима",
        )
        val database = SongDeleteTestDatabase.local()
        val liveSongs =
            SongCleanupExecutor.liveSongsInAlbumFolder(
                database,
                "/tmp/junit-739-альбом-без-песен",
                0L,
            )
        assertEquals(
            0,
            liveSongs,
            "в папке нет песен из БД — папка удаляется вместе с альбомом (FR-020)",
        )
    }

    @Test
    fun `папка с живыми песнями защищена`() {
        Assumptions.assumeTrue(
            SongDeleteTestDatabase.isAvailable(),
            "реквизиты LOCAL-БД не заданы — проверка FR-021 неприменима",
        )
        val database = SongDeleteTestDatabase.local()
        val sample = sampleSongWithAlbum(database)
        if (sample == null) {
            Assumptions.assumeTrue(false, "в LOCAL-БД нет песен с альбомом — проверка неприменима")
            return
        }
        val liveSongs =
            SongCleanupExecutor.liveSongsInAlbumFolder(
                database,
                sample.rootFolder,
                sample.albumId,
            )
        assertTrue(
            liveSongs > 0,
            "файлы живых песен защищают папку альбома (FR-021): " +
                "rootFolder=${sample.rootFolder}, albumId=${sample.albumId}",
        )
    }

    /** Живая песня с альбомом: её папка и альбом обязаны защищаться от удаления. */
    private data class Sample(
        val rootFolder: String,
        val albumId: Long,
    )

    private fun sampleSongWithAlbum(database: com.svoemesto.karaokeapp.KaraokeConnection): Sample? {
        val connection = database.getConnection() ?: return null
        return connection
            .prepareStatement(
                "SELECT root_folder, album_id FROM tbl_songs " +
                    "WHERE album_id > 0 AND root_folder IS NOT NULL AND root_folder <> '' LIMIT 1",
            ).use { ps ->
                ps.executeQuery().use { rs ->
                    if (rs.next()) {
                        Sample(rs.getString(1), rs.getLong(2))
                    } else {
                        null
                    }
                }
            }
    }
}
