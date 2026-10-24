// BR-410 (аудит #336, пакет PKG-S2-17, OpenProject #380).
//
// Контракт постраничной загрузки дайджеста share-ссылок.
//
// ДЕФЕКТ (до правки): `ShareLinksTable.reload()` отправлял `loadShareLinksDigest({})` —
// бэкенд отдаёт ОДНУ страницу (page=1, pageSize=25 по умолчанию, LIMIT/OFFSET), а таблица
// показывала эти 25 строк как полный набор: `b-table` резал их локально по `currentPage`,
// `b-pagination` рисовал страницы по серверному `totalCount`. Итог: при >25 ссылках
// страницы 2+ пусты, сортировка работает только внутри 25 загруженных строк.
//
// ЧТО ЗДЕСЬ: единственная точка, где UI-состояние таблицы (страница, размер, колонка,
// направление) превращается в параметры бэкенда `ShareLinksAdminController.digest`
// (`page`/`pageSize`/`sortBy`/`sortDir`). `perPage` — имя в UI, `pageSize` — в API;
// раньше это расхождение приводило к тому, что размер страницы вообще не доезжал
// (`onPerPageChange` слал `perPage`, бэкенд его не знал).
//
// ПОЧЕМУ whitelist сортировки здесь, а не «передать что есть»: бэкенд сортирует по
// фиксированному списку колонок и на неизвестное имя молча падает в `created_at`.
// Молчаливый откат — это тот же класс дефекта, что и вся находка: админ видит
// «сортировку по песне», а на самом деле получает сортировку по дате. Поэтому
// неизвестный ключ превращается в «не сортировать» (серверный дефолт), а UI
// не предлагает сортировку по колонкам, которых сервер не умеет.
//
// Экспортируется для регрессионного теста (lib/__tests__/sharelinks-digest-params.test.js).

/** Верхняя граница `pageSize`, которую принимает ShareLinksAdminController.digest. */
export const SHARELINKS_MAX_PER_PAGE = 100

/** Колонки таблицы, для которых бэкенд умеет серверную сортировку (см. sortExpression). */
export const SHARELINKS_SERVER_SORT_KEYS = [
  'id',
  'createdAt',
  'expiresAt',
  'ownerEmail',
  'songName',
  'revokeReason',
  'hasActiveSession',
  'secret',
]

/**
 * Приводит размер страницы к диапазону, который реально применяет бэкенд.
 *
 * @param {number} value — запрошенный размер страницы.
 * @param {number} [fallback=25] — значение, если вход не число.
 * @returns {number} целое в [1, SHARELINKS_MAX_PER_PAGE].
 */
export function clampShareLinksPerPage(value, fallback = 25) {
  const parsed = Number.parseInt(value, 10)
  if (Number.isNaN(parsed)) return fallback
  return Math.min(Math.max(parsed, 1), SHARELINKS_MAX_PER_PAGE)
}

/**
 * Собирает form-urlencoded-параметры для `POST /api/sharelinks/digest`.
 *
 * @param {Object} state — состояние таблицы.
 * @param {number} state.page — текущая страница (1-based).
 * @param {number} state.perPage — размер страницы в UI.
 * @param {Array<{key: string, order: string}>|string} [state.sortBy] — `b-table` sortBy
 *   (массив) либо уже готовый ключ колонки.
 * @param {string} [state.sortDir] — 'ASC' | 'DESC'.
 * @returns {{page: number, pageSize: number, sortBy?: string, sortDir: string}}
 */
export function buildShareLinksDigestParams({ page, perPage, sortBy, sortDir } = {}) {
  const parsedPage = Number.parseInt(page, 10)
  const params = {
    page: Number.isNaN(parsedPage) || parsedPage < 1 ? 1 : parsedPage,
    pageSize: clampShareLinksPerPage(perPage),
    sortDir: String(sortDir || '').toUpperCase() === 'ASC' ? 'ASC' : 'DESC',
  }
  const key = Array.isArray(sortBy) ? sortBy[0] && sortBy[0].key : sortBy
  // Неизвестную колонку не передаём вовсе: бэкенд применит свой дефолт
  // (created_at DESC), а не сделает вид, что отсортировал по запрошенному полю.
  if (key && SHARELINKS_SERVER_SORT_KEYS.includes(key)) {
    params.sortBy = key
    if (Array.isArray(sortBy) && sortBy[0] && sortBy[0].order) {
      params.sortDir = String(sortBy[0].order).toUpperCase() === 'ASC' ? 'ASC' : 'DESC'
    }
  }
  return params
}
