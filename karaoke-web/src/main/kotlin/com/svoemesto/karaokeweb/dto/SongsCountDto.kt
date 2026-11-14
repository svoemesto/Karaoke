package com.svoemesto.karaokeweb.dto

/**
 * Ответ `GET /api/public/songs/count` — число песен, подпадающих под фильтры
 * публичного поиска (те же фильтры, что у `GET /api/public/songs`).
 *
 * **Ровно одно поле**: фронту нужен счётчик для плашек «найдено N», превью
 * фильтров и пагинации — там, где тянуть десяток полноценных DTO песен
 * (ради одного числа) невыгодно.
 *
 * Значение совпадает с `totalCount` из [PagedSongsDto] для тех же фильтров:
 * оба значения считает один и тот же запрос `Song.countMatchingAttr` по одной
 * и той же карте аргументов (см. `PublicApiController.songFilterArgs`).
 *
 * @see specs/390-songs-count
 */
data class SongsCountDto(
    val count: Long = 0,
)
