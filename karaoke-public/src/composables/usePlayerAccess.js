import { ref } from 'vue'
import { authGet } from '../services/authApi'
import { useAuth } from './useAuth'
import { getAnonId } from '../services/tracking'

// Опрашивает /api/public/player/{id}/access, который решает, можно ли прямо сейчас встроить
// онлайн-плеер на странице песни вместо видео ВК ("в эфире") или сообщения об ожидании
// (не в эфире/эксклюзив). При canWatch=true бэкенд уже выдал токен — кладём его в sessionStorage
// под тем же ключом kp_token_<id>, который читает существующий /player/:id роут, так что сам
// плеер (PlayerView.vue, router guard) переиспользуется без изменений — просто встраивается через
// iframe вместо открытия в новой вкладке. isDemo=true (canWatch остаётся false) — тот же токен, но
// ограниченный по времени: плеер всё равно встраивается и сам обрежет себя до демо-фрагмента
// (см. KaraokePlayer.js — духа/watermark/оверлей включаются по data.isDemo из playerdata).
export function usePlayerAccess() {
  const { token } = useAuth()
  const ready = ref(false)
  const isPremiumUser = ref(false)
  const canWatch = ref(false)
  const canExport = ref(false)
  const isDemo = ref(false)
  const demoLimitSeconds = ref(null)
  const loaded = ref(false)

  async function checkAccess(songId) {
    loaded.value = false
    ready.value = false
    canWatch.value = false
    canExport.value = false
    isPremiumUser.value = false
    isDemo.value = false
    demoLimitSeconds.value = null
    if (!songId) return
    try {
      const { status, body } = await authGet(`/api/public/player/${songId}/access?anonId=${encodeURIComponent(getAnonId())}`, token.value)
      if (status === 200 && body) {
        ready.value = !!body.ready
        isPremiumUser.value = !!body.isPremiumUser
        canWatch.value = !!body.canWatch
        canExport.value = !!body.canExport
        isDemo.value = !!body.isDemo
        demoLimitSeconds.value = body.demoLimitSeconds ?? null
        if ((canWatch.value || isDemo.value) && body.token) {
          sessionStorage.setItem(`kp_token_${songId}`, body.token)
        }
      }
    } catch (e) {
      // Сетевая ошибка — остаёмся в состоянии "плеер недоступен", вызывающий код откатится
      // на видео ВК (если в эфире) или сообщение об ожидании.
    }
    loaded.value = true
  }

  return { ready, isPremiumUser, canWatch, canExport, isDemo, demoLimitSeconds, loaded, checkAccess }
}
