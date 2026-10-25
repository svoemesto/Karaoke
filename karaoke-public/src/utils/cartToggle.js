// BR-297 / BR-314 (аудит #336, пакет PKG-S3-27, OpenProject #399): корзина.
//
// BR-297 — ЧТО БЫЛО. `useCart.toggle()` возвращает `{status, body}`, а `CartIcon.onClick()`
// результат НЕ анализировал: `await toggle(props.songId)` в try/finally без catch. Ответ
// 409 `already_subscribed`, 400 `song_not_for_subscription`, 401 (истёкший JWT) и 5xx
// оставляли иконку в прежнем состоянии — пользователь не получал ни сообщения, ни смены
// состояния: кнопка «не работала».
//
// BR-314 — ЧТО БЫЛО. `load()` для анонима ставил общий модульный `loaded = true`, из-за чего
// после входа `load()` выходил по `if (loaded && !force) return` и корзина оставалась пустой.
// При выходе `items`/`songIds` предыдущего пользователя оставались доступны следующему.
//
// Модуль без зависимостей — тест запускается node --test без node_modules.

export const CART_TOGGLE_RESULT = Object.freeze({
  OK: 'ok',
  ALREADY_SUBSCRIBED: 'already_subscribed',
  NOT_FOR_SUBSCRIPTION: 'song_not_for_subscription',
  UNAUTHORIZED: 'unauthorized',
  ERROR: 'error',
})

/**
 * Разобрать ответ `POST /api/public/account/cart/toggle`.
 *
 * @param {number} status — HTTP-статус
 * @param {object|null} body — тело ответа ({ error: 'already_subscribed' | ... } | null)
 * @returns {{result: string, message: string, hideIcon: boolean}}
 */
export function classifyCartToggle(status, body) {
  if (status === 200) {
    return { result: CART_TOGGLE_RESULT.OK, message: '', hideIcon: false }
  }

  const code = body && body.error

  if (code === 'already_subscribed') {
    // Песня уже куплена (например, подписка оформлена в другой вкладке, а локальный
    // subscriptionIds устарел) — иконку корзины надо убрать, состояние привести к «есть подписка».
    // Серверный контракт — 409; при 5xx тело с таким кодом — недоверенное, иконку не прячем.
    return {
      result: CART_TOGGLE_RESULT.ALREADY_SUBSCRIBED,
      message: 'Подписка на эту песню уже оформлена.',
      hideIcon: status < 500,
    }
  }

  if (code === 'song_not_for_subscription') {
    // id_tariff < 0 — песня вообще не продаётся поштучно.
    return {
      result: CART_TOGGLE_RESULT.NOT_FOR_SUBSCRIPTION,
      message: 'Подписка на эту песню недоступна — доступна только в составе подписки на сайт.',
      hideIcon: false,
    }
  }

  if (status === 401 || status === 403) {
    return {
      result: CART_TOGGLE_RESULT.UNAUTHORIZED,
      message: 'Сессия истекла — войдите заново.',
      hideIcon: false,
    }
  }

  return {
    result: CART_TOGGLE_RESULT.ERROR,
    message: 'Не удалось изменить корзину — попробуйте ещё раз.',
    hideIcon: false,
  }
}

/**
 * Нужно ли грузить корзину?
 *
 * Аноним — не грузим (и НЕ помечаем корзину «загруженной»: после входа флаг должен сняться).
 * Для залогиненного — грузим, если раньше не грузили или если токен сменился (другой
 * пользователь в том же браузере: содержимое корзины у пользователей разное).
 *
 * @param {object} params
 * @param {boolean} params.hasToken — есть ли токен
 * @param {boolean} params.loaded — был ли уже выполнен успешный load
 * @param {string|null} params.loadedForToken — токен, для которого грузили
 * @param {string} params.token — текущий токен
 * @returns {boolean}
 */
export function needsCartLoad({ hasToken, loaded, loadedForToken, token }) {
  if (!hasToken) return false
  if (!loaded) return true
  return loadedForToken !== token
}
