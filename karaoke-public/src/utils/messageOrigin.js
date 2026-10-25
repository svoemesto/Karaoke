// BR-349 (аудит #336, пакет PKG-S3-28, OpenProject #400): проверка источника
// postMessage-сообщений плеера плейлиста.
//
// ЧТО БЫЛО. `PlayerView.onParentMessage(e)` проверял ТОЛЬКО строковый маркер
// `e.data.source === 'kp-playlist'`. Это единственный барьер, а константа известна
// любому, кто прочитал клиентский бандл: сторонняя страница может встроить плеер
// (`/player/:id?pl=1&session=...` — guard роутера пускает непустой `query.session`)
// и слать ему `setqueue`/`playid`/`toggle`/`setmodes`/`token`, то есть управлять
// сессией плеера и подмешивать значения в `tokenCache`.
//
// ЧТО СТАЛО. Сообщение принимается только если совпадают origin (свой сайт) и
// источник (родительское окно). Строковый маркер остаётся как ТРЕТИЙ фильтр —
// по нему выбирается тип команды.
//
// Модуль без зависимостей — тест запускается node --test без node_modules.

/**
 * Доверять ли postMessage-сообщению.
 *
 * @param {{origin?: string, source?: *}} event — событие `message`
 * @param {{origin?: string|null, source?: *}} expected — ожидаемые origin и источник
 * @returns {boolean}
 */
export function isTrustedParentMessage(event, expected = {}) {
  if (!event || typeof event !== 'object') return false
  const { origin = null, source = null } = expected
  if (origin != null && event.origin !== origin) return false
  if (source != null && event.source !== source) return false
  return true
}

/**
 * Origin текущего сайта — безопасная обёртка над `window.location.origin`
 * (в node-тестах `window` нет → пустая строка).
 *
 * @returns {string}
 */
export function currentOrigin() {
  if (typeof window === 'undefined' || !window.location) return ''
  return window.location.origin || ''
}
