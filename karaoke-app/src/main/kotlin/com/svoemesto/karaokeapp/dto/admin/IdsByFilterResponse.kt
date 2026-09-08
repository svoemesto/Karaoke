package com.svoemesto.karaokeapp.dto.admin

/**
 * Snapshot id, удовлетворяющих текущему фильтру (specs/319-process-bulk-actions-v2).
 *
 * Используется для подсчёта выбранных процессов в UI bulk-кнопок (FR-002, FR-004).
 * Если фильтр даёт > 10 000 id — обрезаем до `limitApplied` и UI должен предупредить
 * админа (см. `contracts/admin-process-bulk-rest-api.md` EP-5).
 *
 * @property total сколько всего id удовлетворяет фильтру в БД
 * @property ids массив id (≤ `limitApplied` элементов)
 * @property limitApplied cap, применённый на backend (= 10 000)
 *
 * @see specs/319-process-bulk-actions-v2/contracts/admin-process-bulk-rest-api.md (EP-5)
 */
data class IdsByFilterResponse(
    val total: Int,
    val ids: List<Int>,
    val limitApplied: Int = 10000,
)
