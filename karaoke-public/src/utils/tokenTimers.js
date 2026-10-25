// BR-348 (аудит #336, пакет PKG-S3-28, OpenProject #400): таймеры ожидания токена
// в плеере плейлиста.
//
// ЧТО БЫЛО. `PlayerView.requestToken(songId)` заводил `setTimeout(..., 8000)` БЕЗ
// сохранения хэндла. Снять таймер в `onBeforeUnmount` было нечем, а промис, который
// он резолвит, не отменялся. После ухода со страницы цепочка жила дальше: таймер
// резолвил `null` → `playPos` вызывал `advanceAfterEnd()` → следующий трек → новый
// 8-секундный таймер → запрос к membership и postMessage — на каждый элемент
// очереди (до 500 песен) уже после того, как пользователь закрыл плеер.
//
// ЧТО СТАЛО. Хэндлы таймеров хранятся в карте `songId → timerId`; при размонтировании
// все таймеры снимаются, а все ожидающие токен промисы резолвятся `null` (не висят).
// Дополнительно вызывающий код проверяет флаг размонтирования перед тем, как продолжить
// цепочку (см. `PlayerView`).
//
// Модуль без зависимостей — тест запускается node --test без node_modules.

/**
 * Снять все таймеры ожидания токена и освободить ожидающие промисы.
 *
 * @param {Object<string, *>} timers — карта `songId → timerId`
 * @param {Object<string, Function[]>} pendingToken — карта `songId → [resolve...]`
 * @returns {number} сколько таймеров снято
 */
export function clearTokenTimers(timers, pendingToken) {
  const timerMap = timers || {}
  const pending = pendingToken || {}
  let cleared = 0
  for (const songId of Object.keys(timerMap)) {
    const timerId = timerMap[songId]
    delete timerMap[songId]
    if (timerId == null) continue
    try {
      clearTimeout(timerId)
      cleared++
    } catch (e) {
      /* таймер уже снят — игнорируем */
    }
  }
  for (const songId of Object.keys(pending)) {
    const resolvers = pending[songId]
    delete pending[songId]
    for (const resolve of resolvers || []) {
      try {
        resolve(null)
      } catch (e) {
        /* резолв не должен ронять размонтирование */
      }
    }
  }
  return cleared
}

/**
 * Есть ли ещё ожидающие токен запросы (для проверки в тестах/логах).
 *
 * @param {Object<string, Function[]>} pendingToken
 * @returns {number}
 */
export function pendingTokenCount(pendingToken) {
  if (!pendingToken) return 0
  return Object.keys(pendingToken).length
}
