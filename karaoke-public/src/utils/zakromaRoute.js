// BR-353 / BR-355 / BR-357 (аудит #336, пакет PKG-S3-28, OpenProject #400):
// состояние страницы «Закромы» выводится из МАРШРУТА, а не из обработчика клика.
//
// ЧТО БЫЛО (BR-353). `data()` ставил `authorChosen: hasAuthor`, а для маршрута
// `/zakroma/special-bucket` `hasAuthor` всегда `false` (нет `params.authorId`).
// Прямой заход (F5, закладка, внешняя ссылка) рендерил сетку авторов вместо таблицы
// спец-корзины. Вторая половина: `zakromaHeaderBack` проверял `authorChosen` ПЕРВЫМ и
// строил `/zakroma/${selectedAuthorId}/albums` — при пустом `selectedAuthorId` это
// `/zakroma//albums`, маршрута которого нет (пустая страница).
//
// ЧТО СТАЛО. `authorChosen` = «на маршруте есть автор ИЛИ спец-корзина»; back-link
// для спец-корзины проверяется ПЕРВЫМ и никогда не строится с пустым authorId.
//
// ЧТО БЫЛО (BR-355). Кнопка «Повторить» после ошибки стрима вызывала
// `onAuthorSelect(...)` — а тот уводил на `/zakroma/{id}/albums`, т.е. «Повторить»
// НЕ повторял загрузку, а менял страницу.
//
// ЧТО СТАЛО. Retry повторяет запуск стрима с ТЕМИ ЖЕ параметрами, что и штатный
// старт (автор, expectedCount, albumId, force) — без навигации.
//
// ЧТО БЫЛО (BR-357). Если для `authorId` из URL нет тайла, `tryStartZakromaStream()`
// уходил в `else` и не делал ничего: ни стрима, ни ошибки, ни «Автор не найден» —
// пустая страница с фильтром.
//
// ЧТО СТАЛО. Явное пустое состояние «Автор не найден», выставляемое после того, как
// каталог тайлов загружен (и тайла действительно нет).
//
// Модуль без зависимостей — тест запускается node --test без node_modules.

export const ZAKROMA_SPECIAL_PATH = '/zakroma/special-bucket'

/**
 * На маршруте ли автора (в `params.authorId` — числовой id)?
 *
 * @param {object} params — параметры маршрута
 * @returns {boolean}
 */
export function isAuthorRoute(params) {
  const id = params && params.authorId
  if (id == null || id === '') return false
  return /^\d+$/.test(String(id))
}

/**
 * Это маршрут спец-корзины?
 *
 * @param {string} path — путь маршрута
 * @returns {boolean}
 */
export function isSpecialBucketPath(path) {
  return path === ZAKROMA_SPECIAL_PATH
}

/**
 * Должна ли страница показывать блок автора (а не сетку авторов).
 *
 * @param {string} path — путь маршрута
 * @param {object} params — параметры маршрута
 * @returns {boolean}
 */
export function authorChosenForRoute(path, params) {
  return isAuthorRoute(params) || isSpecialBucketPath(path)
}

/**
 * Back-link в шапке: спец-корзина → список авторов; автор → альбомы автора.
 *
 * @param {{authorChosen?: boolean, specialBucketShown?: boolean, selectedAuthorId?: string|number, selectedAlbumId?: *}} state
 * @returns {{to: string, label: string}|null}
 */
export function zakromaBackTarget(state) {
  const s = state || {}
  if (s.specialBucketShown) {
    return { to: '/zakroma', label: '← К списку авторов' }
  }
  if (s.authorChosen || s.selectedAlbumId != null) {
    const authorId = s.selectedAuthorId == null ? '' : String(s.selectedAuthorId)
    // Пустой authorId → такого маршрута не существует (/zakroma//albums) — ведём в список.
    if (!/^\d+$/.test(authorId)) {
      return { to: '/zakroma', label: '← К списку авторов' }
    }
    return { to: `/zakroma/${authorId}/albums`, label: '← К альбомам автора' }
  }
  return null
}

/**
 * Параметры запуска NDJSON-стрима автора.
 *
 * Один источник для штатного старта и для «Повторить» (BR-355) — иначе retry
 * повторял бы не тот запрос (и уводил со страницы).
 *
 * @param {{author?: string, songCount?: number}} tile — тайл автора
 * @param {number|string|null} albumId — фильтр по альбому (null — все песни)
 * @returns {{author: string, expectedCount: number|undefined, force: boolean, albumId: number|string|null}|null}
 */
export function zakromaStreamParams(tile, albumId) {
  if (!tile || !tile.author) return null
  const album = albumId == null ? null : albumId
  return {
    author: tile.author,
    // specs/444-fix-album-progress: при фильтре по альбому знаменатель прогресса —
    // счётчик альбома (его считает сервер), авторский счётчик НЕ подходит.
    expectedCount: album != null ? undefined : tile.songCount || undefined,
    force: true,
    albumId: album,
  }
}

/**
 * Автор не найден в каталоге тайлов?
 *
 * @param {object|null} tile — найденный тайл автора
 * @param {boolean} tilesLoaded — каталог тайлов уже загружен (и попытка была)
 * @returns {boolean}
 */
export function isAuthorMissing(tile, tilesLoaded) {
  return !tile && !!tilesLoaded
}
