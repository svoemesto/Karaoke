// BR-344 / BR-345 (аудит #336, пакет PKG-S3-27, OpenProject #399): пагинация поиска.
//
// BR-344 — ЧТО БЫЛО. Синхронизация URL→state работала только при изменении query, но не при
// первом рендере: `watch '$route.query.page'` был с `immediate: false`, а `onSearch()` для
// `?page>1` грузил только первую страницу (и опирался на несуществующий watch
// `'searchPagination.page'`). Итог при F5 на `?text=…&page=3`: загружена страница 1, в URL
// по-прежнему `page=3`. Дополнительно `restoreFromUrl()` для `targetPage <= 1` выходил сразу,
// ничего не сбрасывая — «назад» с `?page=2` на `?page=1` оставлял state на странице 2.
//
// BR-345 — ЧТО БЫЛО. Один и тот же флаг `searchPaginationError` использовался и для ошибки
// первичного поиска, и для ошибки подгрузки. Ошибка первичного поиска показывала «Не удалось
// загрузить ещё», а кнопка «Повторить» вызывала `onLoadMore()`, который упирался в guard
// `if (!pagination.hasMore || pagination.isLoadingMore) return` и НИЧЕГО не запрашивал.
//
// Модуль без зависимостей — тест запускается node --test без node_modules.

export const SEARCH_ERROR_SCOPE = Object.freeze({
  PRIMARY: 'primary',
  LOAD_MORE: 'loadMore',
})

/**
 * Страница из URL (query.page). Некорректное значение → 1.
 *
 * @param {object} query — $route.query
 * @returns {number} >= 1
 */
export function urlPage(query) {
  const raw = query && query.page
  const n = Number(raw)
  if (!Number.isFinite(n) || n < 1) return 1
  return Math.floor(n)
}

/**
 * Нужно ли восстанавливать срез выдачи (страницы 1..N) из URL.
 *
 * @param {number} targetPage — страница из URL
 * @param {number} statePage — текущая страница в store
 * @returns {boolean}
 */
export function needsSliceRestore(targetPage, statePage) {
  return targetPage > 1 && targetPage !== statePage
}

/**
 * План восстановления среза: сначала первая страница, затем добираем остальные.
 *
 * @param {number} targetPage — целевая страница из URL
 * @returns {{firstPage: number, extraPages: number}}
 */
export function restorePlan(targetPage) {
  const target = Number.isFinite(targetPage) && targetPage > 1 ? Math.floor(targetPage) : 1
  return { firstPage: 1, extraPages: Math.max(0, target - 1) }
}

/**
 * Какой Scope у ошибки поиска — первичного запроса или подгрузки.
 *
 * @param {string} scope
 * @returns {string}
 */
export function searchErrorScope(scope) {
  return scope === SEARCH_ERROR_SCOPE.LOAD_MORE
    ? SEARCH_ERROR_SCOPE.LOAD_MORE
    : SEARCH_ERROR_SCOPE.PRIMARY
}

/**
 * Текст ошибки поиска.
 *
 * @param {string} scope — SEARCH_ERROR_SCOPE
 * @returns {string}
 */
export function searchErrorText(scope) {
  return searchErrorScope(scope) === SEARCH_ERROR_SCOPE.LOAD_MORE
    ? 'Не удалось загрузить ещё.'
    : 'Не удалось выполнить поиск.'
}
