package com.svoemesto.karaokeapp

/**
 * Пагинация legacy Thymeleaf-страниц (`/publications`, `/unpublications`).
 *
 * BR-200/BR-206/BR-214: страницы рендерили весь каталог одним `th:each` (127–438 МиБ HTML),
 * поэтому выборка ограничивается страницей, а в шаблон передаются номер страницы и признак
 * «есть следующая». Модуль намеренно не зависит от Spring и БД — поведение под тестом.
 *
 * Легаси-страницы песен (`/songs`, `/songs2`) удалены 2026-10-26 — ноль обращений за месяц
 * в прод-логе, возможности перенесены в SPA.
 */

/** Размер страницы legacy-списков. 200 строк — предел, при котором HTML остаётся в мегабайтах. */
const val LEGACY_PAGE_SIZE: Int = 200

/** Номер страницы 1-based; отсутствующий/некорректный (0, отрицательный) — первая. */
fun normalizePageNumber(page: Int?): Int = (page ?: 1).coerceAtLeast(1)

/** SQL-сдвиг для `LIMIT/OFFSET`; `page` нормализуется тем же правилом. */
fun pageOffset(
    page: Int?,
    pageSize: Int = LEGACY_PAGE_SIZE,
): Int = (normalizePageNumber(page) - 1) * pageSize.coerceAtLeast(1)

/**
 * Срез готового списка по странице (для случаев, когда модель строится в памяти:
 * расписание публикаций, где строка — это день, а не запись БД).
 */
fun <T> pageSlice(
    items: List<T>,
    page: Int?,
    pageSize: Int = LEGACY_PAGE_SIZE,
): List<T> {
    val size = pageSize.coerceAtLeast(1)
    val from = pageOffset(page, size)
    if (from >= items.size) return emptyList()
    return items.subList(from, minOf(from + size, items.size))
}

/** Есть ли следующая страница у среза [pageSlice] по полному списку [items]. */
fun hasNextPage(
    items: List<*>,
    page: Int?,
    pageSize: Int = LEGACY_PAGE_SIZE,
): Boolean {
    val size = pageSize.coerceAtLeast(1)
    return pageOffset(page, size) + size < items.size
}

/**
 * Строк расписания публикаций на страницу (строка — день, в ней 14 слотов).
 *
 * BR-200: `/publications` отдавал 133 646 465 байт (127 МиБ) за 9,5 с — браузер такой DOM
 * не переваривает. 31 день держит ответ в единицах мегабайт.
 */
const val PUBLICATION_PAGE_SIZE: Int = 31
