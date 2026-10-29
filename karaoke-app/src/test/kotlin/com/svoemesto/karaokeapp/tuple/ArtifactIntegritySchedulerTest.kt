package com.svoemesto.karaokeapp.tuple

import com.svoemesto.karaokeapp.audit.TestRepoRoot
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 674 (FR-014, M7): проверка выключена по умолчанию и ничего не правит.
 */
class ArtifactIntegritySchedulerTest {
    private val src =
        TestRepoRoot
            .file("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/ArtifactIntegrityScheduler.kt")
            .readText()

    @Test
    fun `disabled guard не запускает проверку`() {
        assertTrue(src.contains("queue.integrity.enabled"))
        assertTrue(src.contains("if (!KaraokeProperties.getBoolean(\"queue.integrity.enabled\")) return"))
    }

    @Test
    fun `проверка report-only и не удаляет артефакты`() {
        assertTrue(src.contains("writeReport"))
        assertFalse(src.contains("deleteRecursively"))
        assertFalse(src.contains("createProcess("))
    }
}
