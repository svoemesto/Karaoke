package com.svoemesto.karaokeapp.tuple

import com.svoemesto.karaokeapp.audit.TestRepoRoot
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 674 (FR-017): `tbl_processes` не синхронизируется между БД, схема не менялась.
 */
class SyncTargetContractTest {
    @Test
    fun `реестр синхронизации не содержит очередь`() {
        val registry =
            TestRepoRoot
                .file("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/sync/SyncTarget.kt")
                .readText()
        assertFalse(registry.contains("tbl_processes"))
        assertTrue(registry.contains("object SyncRegistry"))
    }

    @Test
    fun `канонический recordhash-файл на месте`() {
        val sql = TestRepoRoot.file("deploy/recordhash_processes.sql")
        assertTrue(sql.exists())
        assertTrue(sql.readText().contains("update_tbl_processes_recordhash"))
    }
}
