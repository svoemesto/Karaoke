package com.svoemesto.karaokeapp.tuple

import com.svoemesto.karaokeapp.audit.TestRepoRoot
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 674 (FR-010): single-flight по кортежу, терминальные строки живого кортежа не удаляются.
 */
class SingleFlightTest {
    private val src =
        TestRepoRoot
            .file("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcess.kt")
            .readText()

    @Test
    fun `живой кортеж блокирует постановку целиком`() {
        assertTrue(src.contains("hasActive"))
        assertTrue(src.contains("WORKING.name"))
        assertTrue(src.contains("processDeletedAt == null"))
        assertTrue(src.contains("if (hasActive) return 0"))
    }

    @Test
    fun `удаляется только завершённый кортеж`() {
        assertTrue(src.contains("deleteChain(chainId, song.database)"))
        assertTrue(src.contains("NOT EXISTS (SELECT 1 FROM tbl_processes s"))
        assertTrue(src.contains("s.process_status <> 'DONE'"))
    }

    @Test
    fun `нет удаления по одной строке в single-flight`() {
        assertFalse(src.contains("else -> delete(existedProcess.id, song.database)"))
    }
}
