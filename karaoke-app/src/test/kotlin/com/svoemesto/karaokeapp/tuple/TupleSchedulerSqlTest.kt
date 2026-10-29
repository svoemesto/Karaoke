package com.svoemesto.karaokeapp.tuple

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 674 (FR-001/FR-002/FR-003/FR-011): контракт SQL выбора кортежа.
 *
 * @see specs/674-kortege-execution/tasks.md T010
 */
class TupleSchedulerSqlTest {
    private val sql = TupleSchedulerSql.SELECT_TUPLE_HEADS

    @Test
    fun `выбирается только голова кортежа`() {
        assertTrue(sql.contains("process_chain_id = h.id"), "голова: process_chain_id = id")
        assertTrue(sql.contains("p.process_chain_id = c.head_id"), "шаг выбирается по chain_id головы")
    }

    @Test
    fun `кортеж выбирается только при наличии WAITING-шага`() {
        assertTrue(sql.contains("w.process_status = 'WAITING'"))
        assertTrue(sql.contains("EXISTS"), "нужен EXISTS по WAITING")
    }

    @Test
    fun `начатый кортеж не вытесняется новым по приоритету`() {
        assertTrue(sql.contains("started DESC"), "лейн занят кортежем до завершения (FR-002)")
        assertTrue(sql.contains("'DONE', 'ERROR'"), "начатый = есть терминальные шаги")
    }

    @Test
    fun `между кортежами не сравнивается process_order`() {
        assertFalse(
            sql.contains("ORDER BY process_priority, process_order, id"),
            "старый межкортежный порядок по шагам запрещён",
        )
        assertTrue(sql.contains("prio, head_id"))
    }

    @Test
    fun `первый исполняемый шаг — минимальный order среди WAITING`() {
        val first = TupleSchedulerSql.FIRST_RUNNABLE_STEP
        assertTrue(first.contains("process_chain_id = ?"))
        assertTrue(first.contains("process_status = 'WAITING'"))
        assertTrue(first.contains("ORDER BY process_order"))
        assertTrue(first.contains("process_deleted_at IS NULL"))
    }
}
