package com.svoemesto.karaokeapp.services.songdeletion

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * ИНЦИДЕНТ 2026-11-13 (прогон разовой уборки №210): прогон отработал (2227 кандидатов,
 * 0 отказов), но в журнале остался в состоянии `PLANNED` без `finished_at` — машина
 * состояний отвергла переход `PLANNED → RUNNING` (допустима только цепочка
 * `PLANNED → APPROVED → RUNNING → DONE/PARTIAL`). Счётчики записались, состояние — нет:
 * админ видел завершённую уборку как «запланированную».
 *
 * Контракт: разовая уборка проходит полную цепочку состояний, а переход PLANNED → RUNNING
 * без APPROVED не используется.
 */
class OneTimeCleanupRunStateTest {
    private fun repoFile(relativePath: String): File {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        while (!File(dir, "settings.gradle.kts").isFile) {
            dir = dir.parentFile ?: error("не найден корень репозитория (settings.gradle.kts)")
        }
        val file = File(dir, relativePath)
        assertTrue(file.isFile, "файл не найден: $relativePath")
        return file
    }

    private val service =
        repoFile(
            "karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/songdeletion/SongOneTimeCleanupService.kt",
        )

    @Test
    fun `разовая уборка проходит состояния APPROVED и RUNNING`() {
        val text = service.readText()
        assertTrue(
            text.contains("updateRunState(runId, CleanupRunState.APPROVED, database)"),
            "прогон должен пройти состояние APPROVED (отпечаток набора утверждён владельцем)",
        )
        assertTrue(
            text.contains("updateRunState(runId, CleanupRunState.RUNNING, database)"),
            "прогон должен перейти в RUNNING",
        )
    }

    @Test
    fun `машина состояний не допускает перехода PLANNED в RUNNING`() {
        val machine =
            CleanupRunStateMachine.canTransition(
                CleanupRunState.PLANNED,
                CleanupRunState.RUNNING,
            )
        assertTrue(!machine, "PLANNED → RUNNING недопустим — отсюда и был дефект")
    }
}
