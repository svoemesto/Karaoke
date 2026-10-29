package com.svoemesto.karaokeapp.tuple

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 674 (FR-008): успех подтверждается длительностью (±0.5 с), свежестью и наличием.
 */
class ArtifactVerificationTest {
    private fun probe(
        path: String,
        duration: Long?,
        mtime: Long = 2000,
        exists: Boolean = true,
        size: Long = 100,
    ) = ArtifactIntegrity.StemProbe(path, exists, size, duration, mtime)

    @Test
    fun `допуск 500 мс проходит, 600 мс — нет`() {
        val ok = ArtifactIntegrity.classifyStems(listOf(probe("/s", 5400)), 5000, 1000)
        assertTrue(ok.isEmpty(), "5.0/5.4 — в допуске")

        val bad = ArtifactIntegrity.classifyStems(listOf(probe("/s", 5600)), 5000, 1000)
        assertEquals(listOf(ArtifactIntegrity.KIND_FOREIGN_DURATION), bad.map { it.kind })
    }

    @Test
    fun `отсутствующий и пустой стем — ошибка`() {
        val missing = ArtifactIntegrity.classifyStems(listOf(probe("/s", null, exists = false)), 5000, 1000)
        assertEquals(listOf(ArtifactIntegrity.KIND_MISSING_STEM), missing.map { it.kind })

        val empty = ArtifactIntegrity.classifyStems(listOf(probe("/s", 5000, size = 0)), 5000, 1000)
        assertEquals(listOf(ArtifactIntegrity.KIND_MISSING_STEM), empty.map { it.kind })
    }

    @Test
    fun `стем старше источника — не готов`() {
        val stale = ArtifactIntegrity.classifyStems(listOf(probe("/s", 5000, mtime = 500)), 5000, 1000)
        assertEquals(listOf(ArtifactIntegrity.KIND_STALE_ARTIFACT), stale.map { it.kind })
    }

    @Test
    fun `нечитаемый источник — fail-closed`() {
        val bad = ArtifactIntegrity.classifyStems(listOf(probe("/s", 5000)), null, 1000)
        assertEquals(listOf(ArtifactIntegrity.KIND_FOREIGN_DURATION), bad.map { it.kind })
    }
}
