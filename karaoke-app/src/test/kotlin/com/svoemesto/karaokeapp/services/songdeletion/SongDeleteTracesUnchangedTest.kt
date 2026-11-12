package com.svoemesto.karaokeapp.services.songdeletion

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Тест неизменности системных следов (KARAOKE-429, спека 739, FR-038).
 *
 * Удаление песни **не** меняет: историю песен (`songs_history.txt`), журнал
 * аудита (`audit_log`) и старые записи кеша хранилища (кроме инвалидации строк
 * по удалённым ключам — это FR-037, отдельное требование).
 *
 * @see specs/739-song-delete-cascade/spec.md FR-038, FR-037
 */
class SongDeleteTracesUnchangedTest {
    private val featureDir = File("src/main/kotlin/com/svoemesto/karaokeapp/services/songdeletion")

    private fun featureSources(): List<File> =
        (featureDir.listFiles()?.filter { it.extension == "kt" }.orEmpty()) +
            listOf(
                File("src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt"),
                File("src/main/kotlin/com/svoemesto/karaokeapp/model/SongNameHistory.kt"),
            )

    @Test
    fun `каскад не трогает историю песен и журнал аудита`() {
        featureSources().forEach { file ->
            if (!file.isFile) return@forEach
            val text = file.readText()
            assertFalse(
                Regex("""(DELETE\s+FROM|UPDATE|INSERT\s+INTO)\s+audit_log""", RegexOption.IGNORE_CASE)
                    .containsMatchIn(text),
                "журнал аудита при удалении песни не меняется (FR-038): ${file.name}",
            )
            val tracesTouched =
                listOf("songs_history.txt", "SongsHistory").any { marker ->
                    val pattern = "(DELETE\\s+FROM|UPDATE|INSERT\\s+INTO)[^;]*" + Regex.escape(marker)
                    Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(text)
                }
            assertFalse(
                tracesTouched,
                "история песен при удалении песни не меняется (FR-038): ${file.name}",
            )
        }
    }

    @Test
    fun `план каскада состоит только из белого списка таблиц`() {
        SongDeletionPlanner.CASCADE_TABLES.forEach { table ->
            listOf("audit_log", "tbl_songs_sync", "tbl_song_name_history", "tbl_song_cleanup_runs").forEach { forbidden ->
                assertFalse(
                    table == forbidden,
                    "в каскад не должна попадать таблица $forbidden (FR-038): $table",
                )
            }
        }
    }

    @Test
    fun `кеш хранилища инвалидируется только по удалённым ключам`() {
        val executor = File("src/main/kotlin/com/svoemesto/karaokeapp/services/songdeletion/SongCleanupExecutor.kt")
        val text = executor.readText()
        assertTrue(
            text.contains("refreshKeys"),
            "после удаления объектов кеш хранилища приводится в соответствие (FR-037)",
        )
        assertFalse(
            Regex("""DELETE\s+FROM\s+tbl_storage_metadata_cache""", RegexOption.IGNORE_CASE).containsMatchIn(text),
            "строки кеша хранилища удаляются только через refreshKeys по ключу (FR-037)",
        )
    }
}
