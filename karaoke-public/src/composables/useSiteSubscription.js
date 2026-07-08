import { ref } from 'vue'
import { authGet, authPost } from '../services/authApi'
import { useAuth } from './useAuth'

// Периодическая подписка на сайт (scope=SITE) — см. план монетизации. Аналог useSongSubscription.js,
// но с выбором тарифа (несколько сроков/цен) и флагом автопродления (включён по умолчанию).
export function useSiteSubscription() {
  const { token } = useAuth()
  const loadingTariffs = ref(false)
  const tariffs = ref([]) // [{id, name, priceRub, periodDays, isDefault}, ...]
  const loadingPrice = ref(false)
  const priceInfo = ref(null)
  const submitting = ref(false)
  const error = ref('')

  // Список тарифов теперь публичный (не требует токена, см. WebMvcConfig.excludePathPatterns) —
  // цена должна быть видна анониму на /premium и странице песни, до входа/регистрации.
  async function loadTariffs(scope = 'SITE') {
    loadingTariffs.value = true
    try {
      const { status, body } = await authGet(`/api/public/account/subscription/tariffs?scope=${scope}`, token.value)
      if (status === 200 && Array.isArray(body)) tariffs.value = body
    } catch (e) { /* оставляем пустой список — вызывающий код покажет "тарифов нет" */ }
    loadingTariffs.value = false
  }

  async function loadPrice(tariffId) {
    loadingPrice.value = true
    error.value = ''
    priceInfo.value = null
    try {
      const { status, body } = await authGet(`/api/public/account/subscription/price?scope=SITE&tariffId=${tariffId}`, token.value)
      if (status === 200 && body) priceInfo.value = body
      else error.value = (body && body.error) || 'price_unavailable'
    } catch (e) {
      error.value = 'network_error'
    }
    loadingPrice.value = false
  }

  async function subscribe(tariffId, autoRenew, disclaimerAccepted) {
    submitting.value = true
    error.value = ''
    try {
      const { status, body } = await authPost(
        '/api/public/account/subscription/create',
        { scope: 'SITE', tariffId, autoRenew, disclaimerAccepted },
        token.value,
      )
      submitting.value = false
      if (status === 200 && body) return { ok: true, confirmationUrl: body.confirmationUrl, status: body.status }
      error.value = (body && body.error) || 'subscribe_failed'
      return { ok: false, error: error.value }
    } catch (e) {
      submitting.value = false
      error.value = 'network_error'
      return { ok: false, error: error.value }
    }
  }

  return { loadingTariffs, tariffs, loadingPrice, priceInfo, submitting, error, loadTariffs, loadPrice, subscribe }
}
