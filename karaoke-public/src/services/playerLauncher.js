import { authGet } from './authApi'
import { getAnonId } from './clientId'

// Открывает онлайн-плеер песни в новой вкладке. Дёргает /access с source=list — тот же эндпоинт,
// что и встроенный плеер на странице песни, но с source=list бэкенд логирует событие
// event_type=player/link_type=opened (осознанное открытие из таблицы «Закрома»/«Поиск»), а не
// пассивный shown. access() же выдаёт токен доступа — кладём его в sessionStorage под ключом
// kp_token_<id>, который читает роут /player/:id, и открываем плеер новой вкладкой (position:fixed
// внутри плеера не хватает — иначе он унаследует .modernScreen-обёртку App.vue; sessionStorage
// клонируется в same-origin вкладку, открытую через window.open, так что токен уже на месте).
// Получить токен доступа к плееру песни БЕЗ открытия новой вкладки — для встроенного плеера на
// странице плейлиста. Возвращает { canWatch, token } (или { canWatch:false } при недоступности).
export async function fetchPlayerToken(songId) {
  const token = localStorage.getItem('km_auth_token')
  try {
    const { status, body } = await authGet(
      `/api/public/player/${songId}/access?source=list&anonId=${encodeURIComponent(getAnonId())}`,
      token
    )
    if (status === 200 && body && body.canWatch && body.token) {
      return { canWatch: true, token: body.token }
    }
  } catch (e) { /* сетевая ошибка — тихо */ }
  return { canWatch: false, token: null }
}

export async function openPlayer(songId) {
  const token = localStorage.getItem('km_auth_token')
  try {
    const { status, body } = await authGet(
      `/api/public/player/${songId}/access?source=list&anonId=${encodeURIComponent(getAnonId())}`,
      token
    )
    if (status === 200 && body && body.canWatch && body.token) {
      sessionStorage.setItem(`kp_token_${songId}`, body.token)
      window.open(`/player/${songId}`, '_blank')
      return true
    }
  } catch (e) {
    // Сетевая ошибка — тихо ничего не делаем (тот же fire-and-forget-стиль, что и трекинг).
  }
  return false
}
