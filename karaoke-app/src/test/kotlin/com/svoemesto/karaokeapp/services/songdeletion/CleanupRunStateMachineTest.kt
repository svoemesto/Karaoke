package com.svoemesto.karaokeapp.services.songdeletion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Юнит-тесты машины состояний прогона уборки (KARAOKE-429, спека 739, FR-034, SC-003).
 *
 * Ключевой инвариант SC-003: «ложного успеха» не бывает — прогон не может быть
 * помечен `DONE`, если по нему есть отказы.
 *
 * @see specs/739-song-delete-cascade/spec.md FR-034, SC-003
 */
class CleanupRunStateMachineTest {
    @Test
    fun `штатная цепочка переходов допустима`() {
        assertTrue(CleanupRunStateMachine.canTransition(CleanupRunState.PLANNED, CleanupRunState.APPROVED))
        assertTrue(CleanupRunStateMachine.canTransition(CleanupRunState.APPROVED, CleanupRunState.RUNNING))
        assertTrue(CleanupRunStateMachine.canTransition(CleanupRunState.RUNNING, CleanupRunState.DONE))
        assertTrue(CleanupRunStateMachine.canTransition(CleanupRunState.RUNNING, CleanupRunState.PARTIAL))
        assertTrue(CleanupRunStateMachine.canTransition(CleanupRunState.RUNNING, CleanupRunState.FAILED))
    }

    @Test
    fun `переходы назад и из терминальных состояний запрещены`() {
        assertFalse(CleanupRunStateMachine.canTransition(CleanupRunState.RUNNING, CleanupRunState.APPROVED))
        assertFalse(CleanupRunStateMachine.canTransition(CleanupRunState.DONE, CleanupRunState.RUNNING))
        assertFalse(CleanupRunStateMachine.canTransition(CleanupRunState.PARTIAL, CleanupRunState.RUNNING))
        assertFalse(CleanupRunStateMachine.canTransition(CleanupRunState.FAILED, CleanupRunState.RUNNING))
        assertFalse(CleanupRunStateMachine.canTransition(CleanupRunState.PLANNED, CleanupRunState.DONE))
    }

    @Test
    fun `DONE невозможен при отказах`() {
        assertFalse(
            CleanupRunStateMachine.canFinishDone(filesFailed = 1, objectsFailed = 0),
            "при отказах по файлам DONE недопустим (SC-003)",
        )
        assertFalse(
            CleanupRunStateMachine.canFinishDone(filesFailed = 0, objectsFailed = 2),
            "при отказах по объектам DONE недопустим (SC-003)",
        )
        assertTrue(CleanupRunStateMachine.canFinishDone(filesFailed = 0, objectsFailed = 0))
    }

    @Test
    fun `при отказах итоговое состояние PARTIAL, а не DONE`() {
        assertEquals(CleanupRunState.PARTIAL, CleanupRunStateMachine.terminalState(hasFailures = true))
        assertEquals(CleanupRunState.DONE, CleanupRunStateMachine.terminalState(hasFailures = false))
    }

    @Test
    fun `терминальные состояния помечены верно`() {
        assertTrue(CleanupRunState.DONE.isTerminal)
        assertTrue(CleanupRunState.PARTIAL.isTerminal)
        assertTrue(CleanupRunState.FAILED.isTerminal)
        assertFalse(CleanupRunState.PLANNED.isTerminal)
        assertFalse(CleanupRunState.APPROVED.isTerminal)
        assertFalse(CleanupRunState.RUNNING.isTerminal)
    }
}
