package com.svoemesto.karaokeapp.tuple

import com.svoemesto.karaokeapp.audit.TestRepoRoot
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 674 (FR-012/FR-014): легаси без отпечатка отделён от реально чужого файла; правок нет.
 */
class ArtifactIntegrityTest {
    private val src =
        TestRepoRoot
            .file("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/tuple/ArtifactIntegrity.kt")
            .readText()

    @Test
    fun `легаси без sidecar — отдельный вид находки`() {
        assertTrue(src.contains("\"legacyNoSidecar\""))
        assertTrue(src.contains("KIND_LEGACY_NO_SIDECAR"))
    }

    @Test
    fun `чужая длительность и mismatch остаются ошибками`() {
        assertTrue(src.contains("KIND_FOREIGN_DURATION"))
        assertTrue(src.contains("KIND_SIDECAR_MISMATCH"))
        assertTrue(src.contains("TOLERANCE_MS = 500L"))
    }

    @Test
    fun `проверка ничего не удаляет и не ставит заданий`() {
        val code = src.lines().filterNot { it.trimStart().startsWith("*") }.joinToString("\n")
        assertTrue(!code.contains("deleteRecursively"))
        assertTrue(!code.contains("createProcess("))
    }
}
