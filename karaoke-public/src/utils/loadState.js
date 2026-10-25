// BR-322 / BR-323 / BR-334 / BR-341 / BR-343 / BR-346 / BR-318
// (аудит #336, пакет PKG-S3-27, OpenProject #399): «пусто» ≠ «ошибка».
//
// ЧТО БЫЛО. Загрузчики в views/store не различали сбой от пустого результата:
//   - `HistoryView.reload()` — ни catch, ни ветки на status !== 200: 5xx показывался как «пусто»;
//   - `stats.loadStats` — catch отсутствует, `isLoading` сбрасывается, а на главной рисуются
//     нули как реальные данные («0 песен в коллекции»);
//   - `EditorWorkView.load()` — reject от `fetchTask` оставлял `loading = true` навсегда;
//   - `PlaylistEditView.load()` — 403/5xx трактовались как «Плейлист не найден»;
//   - `NewsView` — пустой catch превращал 500/503 в «Пока новостей нет»;
//   - `PremiumView` — ошибка расчёта цены вообще не показывалась;
//   - `usePlaylistMembership.loadPlaylists()` — единственная из трёх bulk-загрузок без catch.
//
// ЧТО СТАЛО. Единая классификация ответа: READY / EMPTY / NOT_FOUND / UNAUTHORIZED / ERROR.
// «Пусто» — это успешный ответ без данных; всё остальное — явная ошибка с сообщением.
//
// Модуль без зависимостей — тест запускается node --test без node_modules.

export const LOAD_STATE = Object.freeze({
  READY: 'ready',
  EMPTY: 'empty',
  NOT_FOUND: 'not_found',
  UNAUTHORIZED: 'unauthorized',
  ERROR: 'error',
})

export const LOAD_MESSAGE = Object.freeze({
  [LOAD_STATE.NOT_FOUND]: 'Не найдено.',
  [LOAD_STATE.UNAUTHORIZED]: 'Сессия истекла — войдите заново.',
  [LOAD_STATE.ERROR]: 'Не удалось загрузить данные — попробуйте ещё раз.',
})

/**
 * Классифицировать ответ загрузчика данных.
 *
 * @param {number} status — HTTP-статус
 * @param {*} body — тело ответа
 * @param {object} [opts]
 * @param {() => boolean} [opts.isEmpty] — «успех, но данных нет»
 * @param {number[]} [opts.notFoundStatuses] — статусы «не найдено» (по умолчанию [404])
 * @param {number[]} [opts.unauthorizedStatuses] — статусы «нужен вход» (по умолчанию [401, 403])
 * @returns {{state: string, message: string}}
 */
export function classifyLoad(status, body, opts = {}) {
  const {
    isEmpty = () => false,
    notFoundStatuses = [404],
    unauthorizedStatuses = [401, 403],
  } = opts

  if (status === 200) {
    if (isEmpty(body)) return { state: LOAD_STATE.EMPTY, message: '' }
    return { state: LOAD_STATE.READY, message: '' }
  }

  if (notFoundStatuses.includes(status)) {
    return { state: LOAD_STATE.NOT_FOUND, message: LOAD_MESSAGE[LOAD_STATE.NOT_FOUND] }
  }
  if (unauthorizedStatuses.includes(status)) {
    return { state: LOAD_STATE.UNAUTHORIZED, message: LOAD_MESSAGE[LOAD_STATE.UNAUTHORIZED] }
  }
  return { state: LOAD_STATE.ERROR, message: LOAD_MESSAGE[LOAD_STATE.ERROR] }
}

/**
 * Текст ошибки загрузки по состоянию (для показа пользователю).
 *
 * @param {string} state — значение LOAD_STATE
 * @param {string} [detail] — дополнительная деталь (например, код ошибки сервера)
 * @returns {string}
 */
export function loadErrorMessage(state, detail) {
  const base = LOAD_MESSAGE[state] || LOAD_MESSAGE[LOAD_STATE.ERROR]
  return detail ? `${base} (${detail})` : base
}

/**
 * Разобрать код ошибки из тела ответа (backend отдаёт { error } или { errorCode }).
 *
 * @param {*} body
 * @returns {string} '' — кода нет
 */
export function loadErrorCode(body) {
  if (!body || typeof body !== 'object') return ''
  const code = body.error || body.errorCode
  return typeof code === 'string' ? code : ''
}
