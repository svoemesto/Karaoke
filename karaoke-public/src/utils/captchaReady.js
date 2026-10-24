// Готовность виджета Yandex SmartCaptcha и клиентский гейт отправки формы.
//
// BR-340 (аудит #336, пакет PKG-S2-14, OpenProject #377): наличие <script> с
// URL капчи в DOM трактовалось как «капча готова» — ветка
// `if (existing) { onReady(); return }` звала onReady() немедленно, без проверки
// `window.smartCaptcha`. Если скрипт ещё грузился или упал раньше
// (`script.onerror = () => resolve()`, элемент при этом оставался в head
// навсегда), виджет не рендерился, `captchaToken` оставался пустым, а гейт
// отправки требовал «Подтвердите, что вы не робот» — то есть блокировал
// регистрацию до полной перезагрузки страницы.
//
// Модуль без зависимостей — тест запускается node --test без node_modules.

export const CAPTCHA_STATE = Object.freeze({
  READY: 'ready',
  WAIT: 'wait',
  LOAD: 'load',
  UNAVAILABLE: 'unavailable',
})

/**
 * Что делать с script-тегом капчи.
 *
 * - READY — глобал `window.smartCaptcha` есть, можно рендерить виджет;
 * - WAIT — тег в DOM есть, глобал ещё не определён: ждать `load` (или таймаут);
 * - LOAD — тега нет, создать и грузить;
 * - UNAVAILABLE — скрипт упал/не ответил: капчи на странице не будет.
 *
 * @param {{scriptPresent: boolean, globalReady: boolean, failed?: boolean, timedOut?: boolean}} state
 * @returns {string} одно из CAPTCHA_STATE
 */
export function captchaScriptState({ scriptPresent, globalReady, failed, timedOut }) {
  if (globalReady) return CAPTCHA_STATE.READY
  if (failed || timedOut) return CAPTCHA_STATE.UNAVAILABLE
  return scriptPresent ? CAPTCHA_STATE.WAIT : CAPTCHA_STATE.LOAD
}

/**
 * Нужно ли блокировать отправку формы клиентским гейтом.
 *
 * Гейт включается ТОЛЬКО когда виджет реально отрисован (state === READY):
 * иначе пользователь видит требование, которое физически не может выполнить.
 * Если клиентского ключа нет или капча недоступна — решение остаётся за
 * серверной проверкой (fail-closed на бэкенде: captcha_not_configured).
 *
 * @param {{clientKey: string, token: string, state: string}} state
 * @returns {boolean} true — показать «Подтвердите, что вы не робот» и не отправлять
 */
export function captchaClientGateEnabled({ clientKey, token, state }) {
  if (!clientKey) return false
  if (state !== CAPTCHA_STATE.READY) return false
  return !token
}
