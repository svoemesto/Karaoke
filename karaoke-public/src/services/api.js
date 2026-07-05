import { promisedXMLHttpRequest } from '../lib/utils'
import { getAnonId } from './clientId'

function buildUrl(path, params) {
  const query = Object.entries(params || {})
    .filter(([, v]) => v !== undefined && v !== null && v !== '')
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`)
    .join('&')
  return query ? `${path}?${query}` : path
}

// Bearer-заголовок залогиненного посетителя. Читаем localStorage напрямую (тот же ключ, что и
// useAuth), чтобы api.js не тянул composables/useAuth и не рисковал циклом импортов.
function authHeader() {
  const t = localStorage.getItem('km_auth_token')
  return t ? { Authorization: `Bearer ${t}` } : undefined
}

// Все публичные GET-запросы данных несут anonId + (если есть) bearer-токен, чтобы бэкенд мог
// привязать server-side событие просмотра страницы (callRest) к анониму/залогиненному
// пользователю. Эндпоинты, которые эти параметры не читают, просто их игнорируют.
export async function apiGet(path, params) {
  const withAnon = { ...(params || {}), anonId: getAnonId() }
  const response = await promisedXMLHttpRequest({ method: 'GET', url: buildUrl(path, withAnon), headers: authHeader() })
  return JSON.parse(response)
}

export async function apiPost(path, params, headers) {
  const response = await promisedXMLHttpRequest({ method: 'POST', url: path, params, headers })
  return response ? JSON.parse(response) : null
}
