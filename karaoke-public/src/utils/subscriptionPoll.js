// Состояния опроса статуса оплаты на странице возврата из ЮKassa.
//
// BR-350 (аудит #336, пакет PKG-S2-14, OpenProject #377): poll() не различал
// «ошибка проверки» и «подписки нет». Любой ответ, кроме 200 с массивом (401 от
// SiteAuthInterceptor, 500 от БД), и любой reject не меняли sub/orderSubs;
// после 6 попыток loading=false, и шаблон показывал «Подписка не найдена» без
// текста ошибки и без кнопки повтора. Оплата при этом уже могла пройти
// (Subscription.status → PAID выполняет вебхук, а не этот GET) — пользователь
// видел ложное «не найдена» и мог оплатить повторно.
//
// Модуль без зависимостей — тест запускается node --test без node_modules.

export const POLL_OUTCOME = Object.freeze({
  OK: 'ok',
  UNAUTHORIZED: 'unauthorized',
  CHECK_ERROR: 'check-error',
})

export const POLL_VIEW = Object.freeze({
  CHECKING: 'checking',
  PROCESSING: 'processing',
  TERMINAL: 'terminal',
  NOT_FOUND: 'not-found',
  CHECK_ERROR: 'check-error',
  UNAUTHORIZED: 'unauthorized',
})

/**
 * Классификация одного ответа списка подписок.
 *
 * @param {Error|null} error — reject (сеть/таймаут), null если промис отрезолвился
 * @param {number} status
 * @param {any} body
 * @returns {string} одно из POLL_OUTCOME
 */
export function classifyPollResponse(error, status, body) {
  if (error) return POLL_OUTCOME.CHECK_ERROR
  if (status === 401) return POLL_OUTCOME.UNAUTHORIZED
  if (status !== 200 || !Array.isArray(body)) return POLL_OUTCOME.CHECK_ERROR
  return POLL_OUTCOME.OK
}

/**
 * Что показать пользователю после очередной попытки.
 *
 * Инварианты:
 *   - «не найдена» — ТОЛЬКО если был хотя бы один успешный ответ 200 (иначе
 *     spinner «проверяем»/состояние ошибки);
 *   - ошибка проверки (5xx/сеть/не-массив) НЕ подменяется на «не найдена»: показываем
 *     «не удалось проверить» с кнопкой повтора, даже если следующая попытка уже
 *     запланирована;
 *   - 401 — отдельное состояние «войдите заново».
 *
 * @param {{outcome: string, successfulPolls: number, hasResult: boolean, terminal: boolean}} state
 * @returns {string} одно из POLL_VIEW
 */
export function subscriptionPollView({ outcome, successfulPolls, hasResult, terminal }) {
  if (outcome === POLL_OUTCOME.UNAUTHORIZED) return POLL_VIEW.UNAUTHORIZED
  if (terminal) return POLL_VIEW.TERMINAL
  if (outcome === POLL_OUTCOME.CHECK_ERROR) return POLL_VIEW.CHECK_ERROR
  // outcome === OK, но вызывающий код не зачёл успешный ответ — «не найдена» объявлять нельзя.
  if (successfulPolls === 0) return POLL_VIEW.CHECKING
  return hasResult ? POLL_VIEW.PROCESSING : POLL_VIEW.NOT_FOUND
}

/**
 * Задержка до следующей попытки или null, если попытки кончились.
 *
 * @param {number} attempts — сколько попыток уже сделано
 * @param {number} maxAttempts
 * @param {number} delayMs
 * @returns {number|null}
 */
export function nextPollDelay(attempts, maxAttempts, delayMs) {
  return attempts < maxAttempts ? delayMs : null
}
