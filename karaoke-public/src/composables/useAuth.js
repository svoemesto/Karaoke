import { ref, computed } from 'vue'
import { authGet } from '../services/authApi'

// Module-level singleton (паттерн useDesign.js) — одно состояние на всё приложение.
// localStorage, не sessionStorage: в отличие от одноразового токена плеера (kp_token),
// сессия личного кабинета должна переживать закрытие вкладки.
const token = ref(localStorage.getItem('km_auth_token') || '')
const user = ref(JSON.parse(localStorage.getItem('km_auth_user') || 'null'))

// Период фонового обновления премиум-статуса (specs/161-fix-header-stale-premium-status) — без
// этого шапка сайта показывает статус на момент логина сколь угодно долго: например, конец
// подписки не отражается в AuthStatusWidget, пока пользователь случайно не откроет /account
// (или /account/editor/*, там тоже вызывается fetchMe()) или не перелогинится.
const AUTO_REFRESH_INTERVAL_MS = 5 * 60 * 1000
let autoRefreshStarted = false

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
  try {
    const { status, body } = await authGet('/api/public/auth/me', token.value)
    if (status === 200 && body) {
      user.value = body
      localStorage.setItem('km_auth_user', JSON.stringify(body))
      return body
    }
    if (status === 401) clearSession()
    return null
  } catch (e) {
    // Сетевой сбой (authGet реджектит промис на xhr.onerror) — оставляем user/token как есть:
    // временная недоступность проверки не должна ни ронять фоновый таймер, ни изображать
    // премиум-статус, которого на самом деле нет (specs/161-fix-header-stale-premium-status, FR-005).
    return null
  }
}

// Ленивый module-level старт периодического обновления — гарантированно один раз за жизнь
// вкладки, даже если useAuth() вызывается из нескольких компонентов/страниц (AuthStatusWidget
// монтируется на HomeView/SearchView/ZakromaView/SongView). Интервал ставится независимо от
// текущего состояния логина — сам тик проверяет token.value, поэтому корректно подхватывает и
// более поздний логин, если useAuth() впервые был вызван анонимным посетителем.
function startAutoRefresh() {
  if (autoRefreshStarted) return
  autoRefreshStarted = true
  if (token.value) fetchMe()
  setInterval(() => {
    if (token.value) fetchMe()
  }, AUTO_REFRESH_INTERVAL_MS)
}

export function useAuth() {
  const isLoggedIn = computed(() => !!token.value)
  startAutoRefresh()
  return { token, user, isLoggedIn, setSession, clearSession, fetchMe }
}
