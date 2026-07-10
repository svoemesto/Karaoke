// Клиент «Чат с автором проекта» (пользовательская сторона). Поверх authApi (тело ответа доступно и
// на 4xx — нужно для premium_required), см. playlistApi.js. Токен читаем из localStorage напрямую.
import { authGet, authPost } from './authApi'

function token() {
  return localStorage.getItem('km_auth_token') || ''
}

const BASE = '/api/public/account/chat'

export function fetchMessages() {
  return authGet(`${BASE}/messages`, token())
}
export function sendMessage(body) {
  return authPost(`${BASE}/send`, { body }, token())
}
export function fetchUnreadCount() {
  return authGet(`${BASE}/unreadcount`, token())
}
