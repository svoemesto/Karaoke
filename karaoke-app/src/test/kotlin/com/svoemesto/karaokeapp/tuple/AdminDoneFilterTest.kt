package com.svoemesto.karaokeapp.tuple

import com.svoemesto.karaokeapp.audit.TestRepoRoot
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 674 (FR-010/FR-015): purge удаляет только полностью DONE-кортежи; голова живого кортежа
 * остаётся видимой в top-level; audit-таблица не затрагивается.
 */
class AdminDoneFilterTest {
    private val process =
        TestRepoRoot
            .file("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcess.kt")
            .readText()
    private val admin =
        TestRepoRoot
            .file("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/KaraokeProcessAdminService.kt")
            .readText()

    @Test
    fun `purge удаляет только кортеж без не-DONE шагов`() {
        assertTrue(process.contains("DELETE FROM tbl_processes p "))
        assertTrue(process.contains("s.process_status <> 'DONE'"))
        assertTrue(process.contains("fun deleteDone("))
        assertTrue(process.contains("return purgeFinishedTuples(database)"))
    }

    @Test
    fun `audit-таблица не затрагивается`() {
        val purgeBlock = process.substringAfter("fun purgeFinishedTuples").substringBefore("fun normalizeTuples")
        assertFalse(purgeBlock.contains("tbl_processes_audit"))
    }

    @Test
    fun `голова живого кортежа видна в top-level`() {
        assertTrue(admin.contains("(process_chain_id IS NULL OR process_chain_id = id)"))
        assertFalse(admin.contains("where += \"process_chain_id IS NULL\""))
    }
}
