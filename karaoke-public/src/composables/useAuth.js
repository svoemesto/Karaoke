import { ref, computed } from 'vue'
import { authGet } from '../services/authApi'

// Module-level singleton (паттерн useDesign.js) — одно состояние на всё приложение.
// localStorage, не sessionStorage: в отличие от одноразового токена плеера (kp_token),
// сессия личного кабинета должна переживать закрытие вкладки.
const token = ref(localStorage.getItem('km_auth_token') || '')
const user = ref(JSON.parse(localStorage.getItem('km_auth_user') || 'null'))

function setSession(newToken, newUser) {
  token.value = newToken
  user.value = newUser
  localStorage.setItem('km_auth_token', newToken)
  localStorage.setItem('km_auth_user', JSON.stringify(newUser))
}

function clearSession() {
  token.value = ''
  user.value = null
  localStorage.removeItem('km_auth_token')
  localStorage.removeItem('km_auth_user')
}

async function fetchMe() {
  if (!token.value) return null
  const { status, body } = await authGet('/api/public/auth/me', token.value)
  if (status === 200 && body) {
    user.value = body
    localStorage.setItem('km_auth_user', JSON.stringify(body))
    return body
  }
  if (status === 401) clearSession()
  return null
}

export function useAuth() {
  const isLoggedIn = computed(() => !!token.value)
  return { token, user, isLoggedIn, setSession, clearSession, fetchMe }
}
