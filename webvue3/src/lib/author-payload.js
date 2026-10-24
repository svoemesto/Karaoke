// BR-370 (аудит #336, пакет PKG-S2-17, OpenProject #380).
//
// Payload для `POST /api/authors/updateauthor`.
//
// ДЕФЕКТ (до правки): inline-сохранение `sort_order` в `AuthorsTable` отправляло
// `{ fldName, fldValue, id }`. `apisUpdateAuthor` — НЕ generic-saver: он требует
// `author`, `ymId`, `vkId`, `lastAlbumYm`, `lastAlbumVk`, `lastAlbumProcessed`,
// `watched`, `skip` (`@RequestParam(required = true)`), а ключей `fldName`/`fldValue`
// не знает вовсе; `sortOrder` в payload отсутствовал. Каждое изменение колонки Sort
// давало HTTP 400 (MissingServletRequestParameterException) → `catch` → откат значения,
// то есть сохранить `sort_order` из ячейки было нельзя никогда (FR-008/US3 спеки 307).
//
// ПОЧЕМУ ПОЛНЫЙ PAYLOAD, А НЕ НОВЫЙ ENDPOINT: частичное обновление потребовало бы
// нового контракта на бэкенде и правки валидации; полный payload уже используется
// рабочими путями (`AuthorAliasesModal.save`, `AlbumsTable.onAlbumTypeChange`) —
// это тот же контракт, что и у модалки «Изменение автора».
//
// Экспортируется для регрессионного теста (lib/__tests__/author-payload.test.js).

/**
 * Собирает полный payload `apisUpdateAuthor` из строки таблицы авторов.
 *
 * @param {Object} item — строка дайджеста авторов (AuthorDTO).
 * @param {Object} overrides — изменяемые поля (например, `{ sortOrder: 100 }`).
 * @returns {Object} payload со всеми обязательными параметрами бэкенда.
 */
export function buildAuthorUpdatePayload(item, overrides = {}) {
  const source = item || {}
  return {
    id: source.id,
    author: source.author,
    ymId: source.ymId,
    vkId: source.vkId,
    lastAlbumYm: source.lastAlbumYm,
    lastAlbumVk: source.lastAlbumVk,
    lastAlbumProcessed: source.lastAlbumProcessed,
    watched: !!source.watched,
    skip: !!source.skip,
    ...overrides,
  }
}
