// Состояние доступа к чату с автором проекта.
//
// BR-319 (аудит #336, пакет PKG-S2-14, OpenProject #377): ChatView решал про
// доступ в `mounted()` по кэшу `km_auth_user`, хотя `isPremium` — computed от
// асинхронно обновляемого `user.effectivePremium`. Если кэш был старым, гейт
// срабатывал по устаревшему флагу: тред не грузился, `loading` оставался true
// навсегда, а пользователь видел «Загрузка...» до перезагрузки страницы.
// Обратный случай (кэш «премиум», сервер — нет) давал 403 premium_required,
// который молча превращался в пустой тред «Сообщений пока нет».
//
// Модуль без зависимостей — тест запускается node --test без node_modules.

export const CHAT_ACCESS = Object.freeze({
  LOGIN: 'login',
  CHECKING: 'checking',
  LOCKED: 'locked',
  READY: 'ready',
})

/**
 * Состояние экрана чата.
 *
 * @param {{isLoggedIn: boolean, isPremium: boolean, premiumChecked: boolean}} state
 *   premiumChecked — ответ /auth/me уже получен (кэш localStorage больше не решает)
 * @returns {string} одно из CHAT_ACCESS
 */
export function chatAccessState({ isLoggedIn, isPremium, premiumChecked }) {
  if (!isLoggedIn) return CHAT_ACCESS.LOGIN
  if (!premiumChecked) return CHAT_ACCESS.CHECKING
  return isPremium ? CHAT_ACCESS.READY : CHAT_ACCESS.LOCKED
}

/**
 * Почему загрузка сообщений не удалась. null — успех.
 *
 * 403 premium_required означает «подписка неактивна» (это НЕ пустой тред),
 * 401 — истёкшую сессию; всё остальное — ошибку загрузки с возможностью повтора.
 *
 * @param {number} status
 * @param {any} body
 * @returns {'premium_required'|'unauthorized'|'error'|null}
 */
export function chatLoadFailure(status, body) {
  if (status === 200) return null
  if (status === 403 && body && body.error === 'premium_required') return 'premium_required'
  if (status === 401) return 'unauthorized'
  return 'error'
}
