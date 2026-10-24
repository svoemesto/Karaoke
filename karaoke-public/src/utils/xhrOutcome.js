// Единая классификация ответов auth-клиента (authApi.js) для вызывающего кода.
//
// BR-330 / BR-339 / BR-350 (аудит #336, пакет PKG-S2-14, OpenProject #377).
// `authGet`/`authPost` резолвят промис с `{status, body}` на ЛЮБОМ `onload`
// (тело доступно и на 4xx/5xx) и реджектят только на сетевой ошибке/таймауте.
// Компоненты же проверяли только `status`, поэтому:
//   - reject уходил в unhandled rejection, а флаги `saving`/`submitting`/
//     `loading` оставались включёнными до перезагрузки (BR-330, BR-339);
//   - «сеть не ответила» и «сервер сказал 401» превращались в одно состояние
//     «ничего не найдено» (BR-350).
//
// Модуль без зависимостей — тест запускается node --test без node_modules.

export const AUTH_OUTCOME = Object.freeze({
  OK: 'ok',
  NETWORK: 'network',
  TIMEOUT: 'timeout',
  UNAUTHORIZED: 'unauthorized',
  HTTP: 'http',
})

/**
 * Что произошло с auth-запросом.
 *
 * @param {Error|null} error — reject-причина authGet/authPost (null, если промис отрезолвился)
 * @param {number} status — HTTP-статус (имеет смысл только при error == null)
 * @returns {string} одно из AUTH_OUTCOME
 */
export function classifyAuthOutcome(error, status) {
  if (error) {
    const message = String((error && error.message) || '')
    return message === 'timeout' ? AUTH_OUTCOME.TIMEOUT : AUTH_OUTCOME.NETWORK
  }
  if (status === 200) return AUTH_OUTCOME.OK
  if (status === 401) return AUTH_OUTCOME.UNAUTHORIZED
  return AUTH_OUTCOME.HTTP
}

/**
 * Текст ошибки для пользователя. Пустая строка для AUTH_OUTCOME.OK — чтобы
 * вызывающий код мог просто присвоить результат в поле ошибки.
 *
 * @param {string} outcome — значение из AUTH_OUTCOME
 * @returns {string}
 */
export function describeAuthOutcome(outcome) {
  switch (outcome) {
    case AUTH_OUTCOME.TIMEOUT:
      return 'Сервер не ответил вовремя — попробуйте ещё раз'
    case AUTH_OUTCOME.NETWORK:
      return 'Нет связи с сервером — проверьте интернет и попробуйте ещё раз'
    case AUTH_OUTCOME.UNAUTHORIZED:
      return 'Сессия истекла — войдите заново'
    case AUTH_OUTCOME.HTTP:
      return 'Сервер вернул ошибку — попробуйте ещё раз'
    default:
      return ''
  }
}
