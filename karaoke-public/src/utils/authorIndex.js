// BR-347 (аудит #336, пакет PKG-S3-28, OpenProject #400): индекс авторов `Map` вместо
// линейного `find` на каждой строке результата поиска.
//
// ЧТО БЫЛО. `SearchView.authorIdFor(name)` делал `authorTiles.value.find(...)` —
// линейный поиск по всему каталогу авторов (`GET /api/public/authors-tiles`) — и
// вызывался ДВАЖДЫ на каждую строку выдачи (`v-if` + `params` в `:to`). Сложность
// рендера — O(строк × тайлов × 2), и она повторялась на каждое локальное изменение
// состояния строки (битое превью, смена страницы, событие плеера).
//
// ЧТО СТАЛО. Каталог один раз разворачивается в `Map<authorName, authorId>`
// (пересобирается только при смене `authorTiles`), а поиск по имени — O(1).
//
// Модуль без зависимостей — тест запускается node --test без node_modules.

/**
 * Развернуть каталог тайлов авторов в `Map<authorName, authorId>`.
 *
 * Первый тайл с таким именем выигрывает: каталог может содержать одноимённых
 * авторов (переименование/дубли в `tbl_authors`) — поведение совпадает с
 * прежним `Array.prototype.find`, который тоже брал ПЕРВОЕ совпадение.
 *
 * @param {Array<{author?: string, id?: number|string}>} tiles — тайлы авторов
 * @returns {Map<string, number|string>}
 */
export function buildAuthorIndex(tiles) {
  const index = new Map()
  const list = Array.isArray(tiles) ? tiles : []
  for (const tile of list) {
    if (!tile) continue
    const name = tile.author
    if (name == null || name === '') continue
    if (tile.id == null) continue
    if (!index.has(name)) index.set(name, tile.id)
  }
  return index
}

/**
 * Найти authorId по имени автора в готовом индексе.
 *
 * @param {Map<string, number|string>} index — индекс из `buildAuthorIndex`
 * @param {string} name — имя автора (`song.author`)
 * @returns {string|null} authorId строкой (path-параметр роутера) или null
 */
export function authorIdFromIndex(index, name) {
  if (!index || typeof index.get !== 'function') return null
  if (name == null || name === '') return null
  const id = index.get(name)
  return id == null ? null : String(id)
}
