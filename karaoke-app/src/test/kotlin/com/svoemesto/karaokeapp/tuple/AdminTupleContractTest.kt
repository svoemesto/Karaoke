package com.svoemesto.karaokeapp.tuple

import com.svoemesto.karaokeapp.audit.TestRepoRoot
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 674 (FR-015/FR-016): админский контракт кортежа — top-level голова, DTO-поля, tuple-retry
 * через `inTransaction` (BR-168).
 *
 * @see specs/674-kortege-execution/tasks.md T048
 */
class AdminTupleContractTest {
    private val service =
        TestRepoRoot
            .file("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/KaraokeProcessAdminService.kt")
            .readText()
    private val dto =
        TestRepoRoot
            .file("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/dto/admin/KaraokeProcessAdminDTO.kt")
            .readText()

    @Test
    fun `top-level включает голову кортежа`() {
        assertTrue(service.contains("(process_chain_id IS NULL OR process_chain_id = id)"))
    }

    @Test
    fun `DTO содержит состояние кортежа и прогресс`() {
        assertTrue(dto.contains("chainStepIndex"))
        assertTrue(dto.contains("chainStepsTotal"))
        assertTrue(dto.contains("chainState"))
        assertTrue(dto.contains("chainReason"))
        assertTrue(dto.contains("progress"))
    }

    @Test
    fun `retry кортежа идёт через inTransaction`() {
        assertTrue(service.contains("val chainId = current.processChainId ?: id.toLong()"))
        assertTrue(service.contains("inTransaction(connection)"))
        assertTrue(service.contains("process_chain_id = ?"))
    }

    @Test
    fun `агрегат кортежа считается оконными функциями без N+1`() {
        assertTrue(service.contains("OVER (PARTITION BY process_chain_id)"))
        assertTrue(service.contains("chainStepsTotal"))
    }
}
