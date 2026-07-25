// Клиент «Истории прослушиваний» (QW-13). Тот же паттерн, что playlistApi.js: authGet поверх
// authApi (тело ответа доступно и на 4xx), токен читаем из localStorage напрямую.
import { authGet } from './authApi'

function token() {
  return localStorage.getItem('km_auth_token') || ''
}

const BASE = '/api/public/account'

export function fetchHistory() {
  return authGet(`${BASE}/history`, token())
}
