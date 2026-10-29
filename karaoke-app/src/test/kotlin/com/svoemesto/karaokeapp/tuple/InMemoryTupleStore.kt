package com.svoemesto.karaokeapp.tuple

import com.svoemesto.karaokeapp.KaraokeProcess
import com.svoemesto.karaokeapp.KaraokeProcessStatuses

/**
 * Тестовая реализация [TupleStore] без Postgres и без JDBC.
 *
 * @see specs/674-kortege-execution/spec.md FR-001, FR-003, FR-007, FR-018
 */
class InMemoryTupleStore(
    initial: List<KaraokeProcess> = emptyList(),
) : TupleStore {
    private val steps = initial.sortedBy { it.order }.toMutableList()
    val statuses = mutableMapOf<Long, String>()
    val done = mutableListOf<Long>()
    val errors = mutableMapOf<Long, String>()
    val waiting = mutableListOf<Long>()
    var completed: Long? = null
    var failed: Pair<Long, String>? = null
    var claims = 0

    override fun firstRunnable(chainId: Long): KaraokeProcess? = nextWaiting(chainId)

    override fun claim(step: KaraokeProcess): Boolean {
        claims++
        return true
    }

    override fun nextWaiting(chainId: Long): KaraokeProcess? =
        steps
            .filter {
                it.processChainId == chainId &&
                    (statuses[it.id] ?: it.status) == KaraokeProcessStatuses.WAITING.name
            }.minByOrNull { it.order }

    override fun markDone(step: KaraokeProcess) {
        statuses[step.id] = KaraokeProcessStatuses.DONE.name
        done += step.id
    }

    override fun markError(
        step: KaraokeProcess,
        reason: String,
    ) {
        statuses[step.id] = KaraokeProcessStatuses.ERROR.name
        errors[step.id] = reason
    }

    override fun markWaiting(step: KaraokeProcess) {
        statuses[step.id] = KaraokeProcessStatuses.WAITING.name
        waiting += step.id
    }

    override fun completeTuple(chainId: Long) {
        completed = chainId
    }

    override fun failTuple(
        chainId: Long,
        reason: String,
    ) {
        failed = chainId to reason
    }

    override fun aggregateState(chainId: Long): String =
        when {
            steps.any { (statuses[it.id] ?: it.status) == KaraokeProcessStatuses.WORKING.name } ->
                KaraokeProcessStatuses.WORKING.name
            steps.any { (statuses[it.id] ?: it.status) == KaraokeProcessStatuses.WAITING.name } ->
                KaraokeProcessStatuses.WAITING.name
            steps.any { (statuses[it.id] ?: it.status) == KaraokeProcessStatuses.ERROR.name } ->
                KaraokeProcessStatuses.ERROR.name
            else -> KaraokeProcessStatuses.DONE.name
        }
}
