package com.svoemesto.karaokeapp.tuple

/**
 * SQL выбора кортежа на лейн и первого исполняемого шага.
 *
 * Контракт (`specs/674-kortege-execution/contracts/tuple-scheduler.md` §2):
 * - лейн получает **один** кортеж;
 * - уже начатый кортеж (есть `DONE`/`ERROR`-строки) имеет приоритет перед неначатыми —
 *   лейн занят кортежем до его завершения (FR-002);
 * - среди неначатых порядок — `(process_priority, head.id)`, `process_order` между
 *   кортежами не сравнивается (FR-001);
 * - выбирается первый не-`DONE` шаг кортежа (`MIN(process_order)` среди `WAITING`), а не
 *   обязательно голова — так работает resume с середины (FR-011).
 *
 * @see specs/674-kortege-execution/spec.md FR-001, FR-002, FR-003, FR-011
 */
object TupleSchedulerSql {
    /**
     * Один шаг (первый исполняемый) на лейн.
     *
     * Возвращает строку `tbl_processes` (`p.*`), поэтому существующий маппинг
     * `KaraokeProcess.getProcessesToStart` не меняется.
     */
    val SELECT_TUPLE_HEADS: String =
        """
        WITH tuples AS (
            SELECT h.id AS head_id,
                   h.thread_id,
                   h.process_priority AS prio,
                   CASE WHEN EXISTS (
                       SELECT 1 FROM tbl_processes d
                       WHERE d.process_chain_id = h.id
                         AND d.process_status IN ('DONE', 'ERROR')
                   ) THEN 1 ELSE 0 END AS started
            FROM tbl_processes h
            WHERE h.process_chain_id = h.id
              AND h.process_deleted_at IS NULL
              AND EXISTS (
                  SELECT 1 FROM tbl_processes w
                  WHERE w.process_chain_id = h.id
                    AND w.process_status = 'WAITING'
                    AND w.process_deleted_at IS NULL
              )
        ), ranked AS (
            SELECT head_id, thread_id,
                   ROW_NUMBER() OVER (
                       PARTITION BY thread_id
                       ORDER BY started DESC, prio, head_id
                   ) AS rn
            FROM tuples
        ), chosen AS (
            SELECT head_id FROM ranked WHERE rn = 1
        )
        SELECT p.*
        FROM tbl_processes p
        JOIN chosen c ON p.process_chain_id = c.head_id
        WHERE p.process_status = 'WAITING'
          AND p.process_deleted_at IS NULL
          AND p.process_order = (
              SELECT MIN(s.process_order)
              FROM tbl_processes s
              WHERE s.process_chain_id = c.head_id
                AND s.process_status = 'WAITING'
                AND s.process_deleted_at IS NULL
          )
        """.trimIndent()

    /** Первый исполняемый шаг конкретного кортежа (для resume и тестов). */
    val FIRST_RUNNABLE_STEP: String =
        """
        SELECT * FROM tbl_processes
        WHERE process_chain_id = ?
          AND process_status = 'WAITING'
          AND process_deleted_at IS NULL
        ORDER BY process_order
        LIMIT 1
        """.trimIndent()

    /** Следующий `WAITING`-шаг кортежа (тот же контракт, что [FIRST_RUNNABLE_STEP]). */
    val NEXT_WAITING_STEP: String = FIRST_RUNNABLE_STEP
}
