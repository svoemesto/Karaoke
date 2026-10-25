// BR-320 (аудит #336, пакет PKG-S3-27, OpenProject #399): жизненный цикл NDJSON-стрима.
//
// ЧТО БЫЛО. `store/modules/zakroma.js` создавал composable стрима (`useZakromaStreamProgress()`)
// и (изначально) не хранил ссылку; кнопка «Отмена» на странице (`ZakromaView.cancelZakromaStream`)
// лишь ПОВТОРНО диспатчила тот же action без `force` — то есть не прерывала поток, а на первом
// заходе (дедуп ещё не сработал) запускала ВТОРОЙ NDJSON-стрим поверх живого первого.
// На бэкенде это двойная нагрузка на публичный stream-endpoint (в домене он помечен как hot
// path с `proxy_buffering off`), а первый стрим продолжал коммитить `setZakroma` уже после
// ухода пользователя со страницы.
//
// ЧТО СТАЛО. Store держит активный стрим и отдаёт наружу явные `cancelZakromaStream` /
// `abortZakromaStream`; «Отмена» на странице вызывает их — поток действительно прерывается
// (AbortController), а не перезапускается.
//
// Модуль без зависимостей — тест запускается node --test без node_modules.

/**
 * Освободить активный стрим: прервать fetch и снять подписки.
 *
 * @param {{cancel?: () => void, cleanup?: () => void}|null} stream — активный composable
 * @returns {boolean} true — был активный стрим и он освобождён
 */
export function releaseStream(stream) {
  if (!stream) return false
  try {
    if (typeof stream.cancel === 'function') stream.cancel()
  } catch (_) {
    /* ignore */
  }
  try {
    if (typeof stream.cleanup === 'function') stream.cleanup()
  } catch (_) {
    /* ignore */
  }
  return true
}

/**
 * Нужно ли начинать новый стрим, если уже идёт другой.
 *
 * Новый стрим всегда ЗАМЕНЯЕТ текущий (cancel-and-restart): два одновременных NDJSON-потока
 * на один endpoint — это ровно та двойная нагрузка, которую фиксирует аудит.
 *
 * @param {object|null} activeStream
 * @returns {boolean} true — активный стрим надо прервать перед стартом нового
 */
export function shouldReplaceActiveStream(activeStream) {
  return !!activeStream
}
