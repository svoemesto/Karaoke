package com.svoemesto.karaokeapp.tuple

import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.KaraokeProcess
import com.svoemesto.karaokeapp.KaraokeProcessStatuses
import java.sql.Timestamp
import java.time.Instant

/**
 * Состояние кортежа, необходимое исполнителю. Отделяет цикл шагов от JDBC, чтобы unit/integration
 * тесты не требовали Postgres (`InMemoryTupleStore` в тестах).
 *
 * @see specs/674-kortege-execution/contracts/tuple-scheduler.md §4
 */
interface TupleStore {
    fun firstRunnable(chainId: Long): KaraokeProcess?

    fun claim(step: KaraokeProcess): Boolean

    fun nextWaiting(chainId: Long): KaraokeProcess?

    fun markDone(step: KaraokeProcess)

    fun markError(
        step: KaraokeProcess,
        reason: String,
    )

    fun markWaiting(step: KaraokeProcess)

    fun completeTuple(chainId: Long)

    fun failTuple(
        chainId: Long,
        reason: String,
    )

    fun aggregateState(chainId: Long): String
}

/** JDBC-реализация поверх `KaraokeProcess` (сырой JDBC, без ORM). */
class JdbcTupleStore(
    private val database: KaraokeConnection,
) : TupleStore {
    override fun firstRunnable(chainId: Long): KaraokeProcess? = KaraokeProcess.firstRunnableStep(chainId, database)

    override fun claim(step: KaraokeProcess): Boolean = step.claimForWorking()

    override fun nextWaiting(chainId: Long): KaraokeProcess? = KaraokeProcess.firstRunnableStep(chainId, database)

    override fun markDone(step: KaraokeProcess) {
        step.status = KaraokeProcessStatuses.DONE.name
        step.end = Timestamp.from(Instant.now())
        step.save()
    }

    override fun markError(
        step: KaraokeProcess,
        reason: String,
    ) {
        step.status = KaraokeProcessStatuses.ERROR.name
        step.end = Timestamp.from(Instant.now())
        step.description = KaraokeProcess.appendErrorReason(step.description, IllegalStateException(reason))
        step.save()
    }

    override fun markWaiting(step: KaraokeProcess) {
        step.status = KaraokeProcessStatuses.WAITING.name
        step.end = null
        step.save()
    }

    override fun completeTuple(chainId: Long) {
        KaraokeProcess.setChainPriority(chainId, 999, database)
    }

    override fun failTuple(
        chainId: Long,
        reason: String,
    ) {
        // Соседи уже переведены в ERROR внутри save() шага (stopChainSiblingsOnError).
        KaraokeProcess.setChainPriority(chainId, -1, database)
    }

    override fun aggregateState(chainId: Long): String {
        val rows = KaraokeProcess.loadList(mapOf("chainId" to chainId.toString()), database)
        return when {
            rows.any { it.status == KaraokeProcessStatuses.WORKING.name } -> KaraokeProcessStatuses.WORKING.name
            rows.any { it.status == KaraokeProcessStatuses.WAITING.name } -> KaraokeProcessStatuses.WAITING.name
            rows.any { it.status == KaraokeProcessStatuses.ERROR.name } -> KaraokeProcessStatuses.ERROR.name
            else -> KaraokeProcessStatuses.DONE.name
        }
    }
}
