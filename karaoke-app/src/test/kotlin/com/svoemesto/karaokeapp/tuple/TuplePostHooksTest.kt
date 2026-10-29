package com.svoemesto.karaokeapp.tuple

import com.svoemesto.karaokeapp.audit.TestRepoRoot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 674 (FR-002/FR-016): пост-хуки — один раз на кортеж, после завершения, а не на каждом шаге.
 */
class TuplePostHooksTest {
    private val src =
        TestRepoRoot
            .file("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcessWorker.kt")
            .readText()

    @Test
    fun `хук HealthReport вызывается из runPostHooks и ровно один раз`() {
        assertEquals(1, Regex("HealthReport\\.onRepairProcessFinished\\(").findAll(src).count())
        val hookFn = src.indexOf("private fun runPostHooks")
        val hookCall = src.indexOf("HealthReport.onRepairProcessFinished(", hookFn)
        val callSite = src.indexOf("runPostHooks(it)")
        assertTrue(hookFn > 0 && hookCall > hookFn, "вызов должен жить в runPostHooks")
        assertTrue(callSite > src.indexOf("executor.execute(chainId)"), "хук — после цикла кортежа")
    }
}
