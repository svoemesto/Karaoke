package com.svoemesto.karaokeapp.tuple

import com.svoemesto.karaokeapp.KaraokeProcess
import com.svoemesto.karaokeapp.KaraokeProcessStatuses

/**
 * Цикл исполнения кортежа: шаги `0..N-1` подряд, лейн занят кортежем, fail-fast.
 *
 * Сам шаг исполняется переданным [stepRunner] (в production — `runStep` в
 * `KaraokeProcessWorker.kt`), поэтому exit-code guard и `redirectErrorStream` остаются там же,
 * где их проверяет `ProcessExitWiringTest`. Состояние — через [TupleStore].
 *
 * @see specs/674-kortege-execution/contracts/tuple-scheduler.md §4
 */
class TupleExecutor(
    private val store: TupleStore,
    private val stepRunner: (KaraokeProcess) -> String,
) {
    /**
     * @return итоговый статус кортежа: `DONE`, `ERROR` или `WAITING` (force-stop/claim не получен).
     */
    fun execute(chainId: Long): String {
        var step = store.firstRunnable(chainId) ?: return KaraokeProcessStatuses.DONE.name
        while (true) {
            val status = stepRunner(step)
            when (status) {
                KaraokeProcessStatuses.DONE.name -> {
                    val next = store.nextWaiting(chainId)
                    if (next == null) {
                        store.completeTuple(chainId)
                        return KaraokeProcessStatuses.DONE.name
                    }
                    step = next
                }
                KaraokeProcessStatuses.WAITING.name -> {
                    // force-stop: шаг уже возвращён в WAITING исполнителем шага; кортеж остаётся в очереди.
                    return KaraokeProcessStatuses.WAITING.name
                }
                else -> {
                    store.failTuple(chainId, status)
                    return status
                }
            }
        }
    }
}
