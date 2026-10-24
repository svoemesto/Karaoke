package com.svoemesto.karaokeapp.services.cleanup

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * Регрессия BR-111 (аудит #336, PKG-S2-11): отчёты `plan` не накапливаются в heap
 * безгранично.
 *
 * До правки `plans` был `ConcurrentHashMap` без удаления: каждый `POST /plan`
 * навсегда оставлял полный список путей. Тест строит больше отчётов, чем лимит,
 * и проверяет, что самый первый вытеснен (404), а число доступных равно лимиту.
 */
class CleanupPlanRetentionTest {
    @Test
    fun `old plans are evicted instead of retained forever`(
        @TempDir tempDir: Path,
    ) {
        val service =
            ArtifactCleanupService(
                planner = ArtifactCleanupPlanner(roots = listOf(tempDir.toString())),
                journal = CleanupJournal(tempDir.resolve("logs").toString()),
            )

        val ids =
            (1..ArtifactCleanupService.MAX_RETAINED_PLANS + 5).map {
                service.plan(ArtifactCleanupClass.DEMO_MP4.name).planId
            }

        assertThrows(CleanupException::class.java) { service.planById(ids.first()) }
        val alive = ids.count { runCatching { service.planById(it) }.isSuccess }
        assertEquals(ArtifactCleanupService.MAX_RETAINED_PLANS, alive)
    }
}
