import { ref } from 'vue'
import { authGet } from '../services/authApi'
import { useAuth } from './useAuth'
import { getAnonId } from '../services/tracking'

// Опрашивает /api/public/player/{id}/access, который решает, можно ли прямо сейчас встроить
// онлайн-плеер на странице песни вместо видео ВК ("в эфире") или сообщения об ожидании
// (не в эфире/эксклюзив). При canWatch=true бэкенд уже выдал токен — кладём его в sessionStorage
// под тем же ключом kp_token_<id>, который читает существующий /player/:id роут, так что сам
// плеер (PlayerView.vue, router guard) переиспользуется без изменений — просто встраивается через
// iframe вместо открытия в новой вкладке.
export function usePlayerAccess() {
  const { token } = useAuth()
  const ready = ref(false)
  const isPremiumUser = ref(false)
  const canWatch = ref(false)
  const canExport = ref(false)
  const loaded = ref(false)

  async function checkAccess(songId) {
    loaded.value = false
    ready.value = false
    canWatch.value = false
    canExport.value = false
    isPremiumUser.value = false
    if (!songId) return
    try {
      const { status, body } = await authGet(`/api/public/player/${songId}/access?anonId=${encodeURIComponent(getAnonId())}`, token.value)
      if (status === 200 && body) {
        ready.value = !!body.ready
        isPremiumUser.value = !!body.isPremiumUser
        canWatch.value = !!body.canWatch
        canExport.value = !!body.canExport
        if (canWatch.value && body.token) {
          sessionStorage.setItem(`kp_token_${songId}`, body.token)
        }
      }
    } catch (e) {
      // Сетевая ошибка — остаёмся в состоянии "плеер недоступен", вызывающий код откатится
      // на видео ВК (если в эфире) или сообщение об ожидании.
    }
    loaded.value = true
  }

  return { ready, isPremiumUser, canWatch, canExport, loaded, checkAccess }
}
