// Клиент «Чат с автором проекта» (пользовательская сторона). Поверх authApi (тело ответа доступно и
// на 4xx — нужно для premium_required), см. playlistApi.js. Токен читаем из localStorage напрямую.
import { authGet, authPost } from './authApi'

function token() {
  return localStorage.getItem('km_auth_token') || ''
}

const BASE = '/api/public/account/chat'

// params: { limit, beforeId, afterId } — курсорная пагинация, см. ChatView.vue.
export function fetchMessages(params) {
  const query = Object.entries(params || {})
    .filter(([, v]) => v !== undefined && v !== null)
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`)
    .join('&')
  return authGet(`${BASE}/messages${query ? `?${query}` : ''}`, token())
}
export function sendMessage(body) {
  return authPost(`${BASE}/send`, { body }, token())
}
export function fetchUnreadCount() {
  return authGet(`${BASE}/unreadcount`, token())
}
