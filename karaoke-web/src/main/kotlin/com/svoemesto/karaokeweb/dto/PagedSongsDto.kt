package com.svoemesto.karaokeweb.dto

/**
 * Обёртка ответа эндпоинта `GET /api/public/songs` при пагинированном
 * запросе (спека 262-search-pagination). Возвращается только если клиент
 * передал хотя бы один из query-параметров `page` или `pageSize`; без них
 * эндпоинт сохраняет обратную совместимость и возвращает
 * `List<SongPublicDto>` напрямую.
 *
 * **Сортировка**: стабильная, `Song.id ASC` — гарантирует, что разные
 * страницы одного запроса не пересекаются и порядок воспроизводим между
 * повторными вызовами (SC-003, SC-005 спеки).
 *
 * **Подсчёт totalCount**: через `Song.countMatchingAttr(attr, ...)` —
 * отдельный `SELECT COUNT(*) FROM tbl_songs WHERE <getWhereList(args)>`;
 * паттерн взят из `Author.countWithNewAlbum`. Тот же фильтр, что и для
 * `items`, — никакого дублирования WHERE-логики.
 *
 * **Default'ы**:
 * - `items` — пустой массив `[]` (никогда `null`).
 * - `totalCount` — `0L` (если нет соединения или SQL вернул NULL).
 * - `page` / `pageSize` / `hasMore` — вычисляемые echo, всегда non-null.
 *
 * @see specs/262-search-pagination/contracts/api-songs.md
 */
data class PagedSongsDto(
    val items: List<SongPublicDto> = emptyList(),
    val totalCount: Long = 0,
    val page: Int = 1,
    val pageSize: Int = 35,
    val hasMore: Boolean = false,
)
