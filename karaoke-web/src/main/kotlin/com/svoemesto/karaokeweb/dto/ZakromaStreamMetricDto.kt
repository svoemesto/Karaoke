package com.svoemesto.karaokeweb.dto

import com.fasterxml.jackson.annotation.JsonInclude

/**
 * Метрика одного события стрима /api/public/zakroma/stream (FR-FE-010).
 *
 * Каждый элемент в массиве, который фронт шлёт при `pagehide` через
 * `sendBeacon` в `POST /api/public/zakroma/stream/metrics`. Backend
 * регистрирует каждое событие в `tbl_events` для последующего анализа
 * (SC-004: median firstChunkMs, expectedCount vs receivedCount, abort rate).
 *
 * **События**:
 * - `zakroma_stream_start` — `start()` композиции вызван, fetch начат.
 * - `zakroma_stream_done` — пришло `done` сообщение, стрим завершён.
 * - `zakroma_stream_error` — сетевая ошибка / 5xx / abort через controller.
 * - `zakroma_stream_abort` — посетитель нажал «Отмена» (`controller.abort()`).
 *
 * Поля nullable, чтобы фронт мог слать разные срезы (`start` без
 * `receivedCount`/`durationMs`, `done` — со всем).
 *
 * @see archive/docs/features/zakroma-stream-progress.md
 * @see specs/181-zakroma-author-load-progress/spec.md
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ZakromaStreamMetricDto(
    val eventType: String,
    val author: String,
    val firstChunkMs: Long? = null,
    val durationMs: Long? = null,
    val expectedCount: Long? = null,
    val receivedCount: Long? = null,
    val streamAborted: Boolean = false,
    val errorCategory: String? = null,
) {
    companion object {
        const val EVENT_START: String = "zakroma_stream_start"
        const val EVENT_DONE: String = "zakroma_stream_done"
        const val EVENT_ERROR: String = "zakroma_stream_error"
        const val EVENT_ABORT: String = "zakroma_stream_abort"
    }
}
