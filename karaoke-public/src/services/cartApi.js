// Клиент «Корзины» (пакетная оплата нескольких подписок на песни одним заказом). Паттерн — как
// playlistApi.js: токен из localStorage напрямую, тонкие обёртки над authGet/authPost.
import { authGet, authPost } from './authApi'

function token() {
  return localStorage.getItem('km_auth_token') || ''
}

const BASE = '/api/public/account/cart'

export function fetchCart() {
  return authGet(`${BASE}/list`, token())
}
export function toggleCart(songId) {
  return authPost(`${BASE}/toggle`, { songId }, token())
}
export function clearCart() {
  return authPost(`${BASE}/clear`, {}, token())
}
export function fetchCartPrice() {
  return authGet(`${BASE}/price`, token())
}
export function checkoutCart(disclaimerAccepted) {
  return authPost(`${BASE}/checkout`, { disclaimerAccepted }, token())
}
