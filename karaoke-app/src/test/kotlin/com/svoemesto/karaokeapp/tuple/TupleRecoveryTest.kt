package com.svoemesto.karaokeapp.tuple

import com.svoemesto.karaokeapp.audit.TestRepoRoot
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 674 (FR-003/FR-007/FR-011): ретроактивный fail-fast и resume с первого не-DONE.
 */
class TupleRecoveryTest {
    private val process =
        TestRepoRoot
            .file("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcess.kt")
            .readText()

    @Test
    fun `нормализация гасит WAITING в кортеже с ERROR`() {
        assertTrue(process.contains("кортеж содержит ошибку"))
        assertTrue(process.contains("WHERE process_status IN ('WAITING', 'CREATING')"))
        assertTrue(process.contains("SELECT DISTINCT process_chain_id FROM tbl_processes"))
    }

    @Test
    fun `резюме не берёт шаг при более раннем не-DONE`() {
        val sql = TupleSchedulerSql.FIRST_RUNNABLE_STEP
        assertTrue(sql.contains("NOT EXISTS"))
        assertTrue(sql.contains("e.process_order < p.process_order"))
        assertTrue(sql.contains("e.process_status <> 'DONE'"))
    }

    @Test
    fun `CREATING и WORKING сбрасываются при старте`() {
        assertTrue(process.contains("process_status IN ('WORKING', 'CREATING')"))
        assertFalse(process.contains("WHERE process_status = 'WORKING' AND thread_id"))
    }
}
