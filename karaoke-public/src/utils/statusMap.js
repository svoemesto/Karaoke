// BR-298 / BR-325 (аудит #336, пакет PKG-S3-27, OpenProject #399): статусные карты.
//
// BR-298 — ЧТО БЫЛО. `ChatUnreadBadge` наблюдал только за `isLoggedIn`, а премиум-статус
// читается из `user.effectivePremium` (обновляется фоновым `fetchMe()` раз в 5 минут и при
// входе в /account). Пользователь, ставший премиумом в открытой вкладке, не запускал polling —
// бейдж чата не появлялся до полной перезагрузки; при истечении подписки polling продолжался
// каждые 20 секунд впустую.
//
// BR-325 — ЧТО БЫЛО. `AuthorPlaylistView.statusOf(songId)` искал песню линейным `find` по всему
// массиву, а вызывался из шаблона на каждую строку 4 раза + в `hasPlayable`/`playableIds`.
// Итог — O(n²) на каждый рендер (n ≈ 2500 песен автора), причём перерисовка идёт на каждое
// событие плеера.
//
// Модуль без зависимостей — тест запускается node --test без node_modules.

export const CHAT_POLL_DECISION = Object.freeze({
  START: 'start',
  STOP: 'stop',
})

/**
 * Нужно ли опрашивать счётчик непрочитанных чата.
 *
 * @param {boolean} isPremium — есть ли активный премиум
 * @returns {string} CHAT_POLL_DECISION
 */
export function chatPollDecision(isPremium) {
  // Премиум — гейт доступа к чату; без него backend всё равно отдаёт count=0.
  return isPremium ? CHAT_POLL_DECISION.START : CHAT_POLL_DECISION.STOP
}

export const SONG_STATUS = Object.freeze({
  AVAILABLE: 'available',
  PREMIUM: 'premium',
  NOT_READY: 'notready',
  LOADING: 'loading',
})

/**
 * Построить карту «песня → статус» ОДИН раз на изменение songs/isPremium/subscriptionIds.
 *
 * @param {Array<{songId: number, contentReady?: boolean, freelyAvailableNow?: boolean}>} songs
 * @param {boolean} isPremium
 * @param {Set<number>|null} subscriptionIds
 * @returns {Map<number, string>} songId → SONG_STATUS
 */
export function buildSongStatusMap(songs, isPremium, subscriptionIds) {
  const map = new Map()
  const list = Array.isArray(songs) ? songs : []
  const subs = subscriptionIds || null
  for (const s of list) {
    if (!s) continue
    const id = s.songId
    if (!s.contentReady) {
      map.set(id, SONG_STATUS.NOT_READY)
      continue
    }
    if (s.freelyAvailableNow || isPremium || (subs && subs.has(Number(id)))) {
      map.set(id, SONG_STATUS.AVAILABLE)
      continue
    }
    map.set(id, SONG_STATUS.PREMIUM)
  }
  return map
}

/**
 * Статус песни по готовой карте (O(1) вместо линейного find).
 *
 * @param {Map<number, string>} statusMap
 * @param {number|string} songId
 * @returns {string} SONG_STATUS
 */
export function songStatusOf(statusMap, songId) {
  if (!statusMap) return SONG_STATUS.LOADING
  const found = statusMap.get(songId)
  return found === undefined ? SONG_STATUS.LOADING : found
}

/**
 * Иконка замка по статусу.
 *
 * @param {string} status
 * @returns {string}
 */
export function lockIconFor(status) {
  switch (status) {
    case SONG_STATUS.AVAILABLE:
      return '♪'
    case SONG_STATUS.LOADING:
      return '…'
    case SONG_STATUS.PREMIUM:
      return '🪙'
    default:
      return '🔒'
  }
}

/**
 * Подпись замка по статусу.
 *
 * @param {string} status
 * @returns {string}
 */
export function lockTitleFor(status) {
  switch (status) {
    case SONG_STATUS.AVAILABLE:
      return 'Доступна'
    case SONG_STATUS.PREMIUM:
      return 'Доступно премиум-пользователям'
    case SONG_STATUS.NOT_READY:
      return 'Песня ещё готовится'
    default:
      return ''
  }
}

/**
 * Список id доступных песен — из той же карты, за О(n) вместо O(n²).
 *
 * @param {Array<{songId: number}>} songs
 * @param {Map<number, string>} statusMap
 * @returns {number[]}
 */
export function playableSongIds(songs, statusMap) {
  const list = Array.isArray(songs) ? songs : []
  const ids = []
  for (const s of list) {
    if (!s) continue
    if (songStatusOf(statusMap, s.songId) === SONG_STATUS.AVAILABLE) ids.push(s.songId)
  }
  return ids
}
