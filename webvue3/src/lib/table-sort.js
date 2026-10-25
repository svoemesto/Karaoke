// BR-435 (аудит #336, пакет PKG-S3-32, OpenProject #404).
//
// Порядок строк таблицы песен.
//
// ДЕФЕКТ. `b-table` сортирует элементы САМ (по `v-model:sort-by`), а страница песни
// считалась по ИСХОДНОМУ порядку массива (`Math.floor(i / perPage) + 1`). После клика по
// заголовку видимая страница — срез отсортированного списка, а «страница» во всей
// логике компонента (`currentPageSongIds`, `updateHealthReportForCurrentPage`,
// `_collectMissingHrSongIds`, `repairAllForCurrentPage`, `reloadAssignmentStatus`,
// `notifyBackendActivePage`, переход к песне) оставалась срезом исходного порядка.
// Следствие: «Repair All для всех песен на странице», сброс кеша хранилища, загрузка
// health-report и статусов назначений применялись к ДРУГОМУ набору строк, чем видел
// пользователь, а переход к выбранной песне открывал страницу, где её нет.
//
// РЕШЕНИЕ. Порядок строк вычисляется ОДИН раз и используется и таблицей, и счётом
// страниц. Компаратор повторяет поведение `bootstrap-vue-next` (`BTable`):
//   sortCompare = getStringValue(a, key).localeCompare(getStringValue(b, key), undefined, {numeric: true}),
//   порядок — по `sortBy[i].order` ('asc' | 'desc'), без заданного порядка — как есть.
//
// Отсортированный массив отдаётся в `b-table` как `:items`; повторная сортировка
// таблицей уже отсортированного массива даёт тот же порядок (сортировка стабильна).

/**
 * Строковое представление значения поля — как в `bootstrap-vue-next` (`getStringValue`).
 *
 * @param {Object} item — строка таблицы.
 * @param {string} key — ключ поля.
 * @param {Object|undefined} field — описание поля (может содержать formatter).
 * @returns {string} строковое значение.
 */
export function getSortValue(item, key, field) {
  const raw = item ? item[key] : undefined
  if (field && field.sortByFormatted && typeof field.formatter === 'function') {
    const formatted = field.formatter(item, key, raw)
    if (formatted) return String(formatted)
  }
  if (raw !== null && typeof raw === 'object') return JSON.stringify(raw)
  return raw === null || raw === undefined ? '' : String(raw)
}

/**
 * Сортирует строки таблицы так же, как это делает `b-table`.
 *
 * @param {Array<Object>} items — строки.
 * @param {Array<{key: string, order?: string}>} sortBy — модель сортировки таблицы.
 * @param {Array<Object>} fields — описания полей таблицы.
 * @returns {Array<Object>} отсортированные строки (исходный массив не мутируется).
 */
export function sortTableItems(items, sortBy, fields) {
  const list = Array.isArray(items) ? items : []
  const keys = (Array.isArray(sortBy) ? sortBy : []).filter((s) => s && s.order)
  if (keys.length === 0) return list
  const fieldByKey = new Map()
  for (const field of fields || []) {
    if (field && typeof field === 'object' && field.key) fieldByKey.set(field.key, field)
  }
  return [...list].sort((a, b) => {
    for (const { key, order } of keys) {
      const field = fieldByKey.get(key)
      const cmp = getSortValue(a, key, field).localeCompare(
        getSortValue(b, key, field),
        undefined,
        {
          numeric: true,
        },
      )
      if (cmp !== 0) return order === 'asc' ? cmp : -cmp
    }
    return 0
  })
}

/**
 * Строит карту «id песни → страница» по ПОРЯДКУ, который реально отдан в таблицу.
 *
 * @param {Array<Object>} sortedItems — отсортированные строки (в порядке таблицы).
 * @param {number} perPage — размер страницы.
 * @returns {Map<number, number>} id песни → номер страницы (с 1).
 */
export function buildSongIdAndPageId(sortedItems, perPage) {
  const result = new Map()
  const list = Array.isArray(sortedItems) ? sortedItems : []
  const size = perPage > 0 ? perPage : 1
  for (let i = 0; i < list.length; i++) {
    const song = list[i]
    if (!song || song.id === undefined || song.id === null) continue
    result.set(song.id, Math.floor(i / size) + 1)
  }
  return result
}

/**
 * Строит карту «id песни → песня» (один проход по каталогу вместо filter/find в цикле).
 *
 * @param {Array<Object>} items — строки.
 * @returns {Map<number, Object>} id песни → строка.
 */
export function buildSongById(items) {
  const result = new Map()
  const list = Array.isArray(items) ? items : []
  for (const song of list) {
    if (!song || song.id === undefined || song.id === null) continue
    result.set(song.id, song)
  }
  return result
}
