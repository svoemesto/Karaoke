import { ref } from 'vue'
import { authGet, authPost } from '../services/authApi'
import { useAuth } from './useAuth'

// Оформление подписки на ОДНУ песню (бессрочно) — см. план монетизации, "подписка на песню".
// Термин «покупка» нигде не используется. Цена всегда пересчитывается на сервере (PriceService) —
// это лишь ОТОБРАЖЕНИЕ цены пользователю ДО оплаты, сервер не доверяет тому, что ушло обратно.
export function useSongSubscription() {
  const { token } = useAuth()
  const loadingPrice = ref(false)
  const priceInfo = ref(null) // { tariffId, tariffName, base, discount, final, promoApplied }
  const submitting = ref(false)
  const error = ref('')

  async function loadPrice(songId) {
    loadingPrice.value = true
    error.value = ''
    priceInfo.value = null
    try {
      const { status, body } = await authGet(
        `/api/public/account/subscription/price?scope=SONG&songId=${songId}`,
        token.value,
      )
      if (status === 200 && body) {
        priceInfo.value = body
      } else {
        error.value = (body && body.error) || 'price_unavailable'
      }
    } catch (e) {
      error.value = 'network_error'
    }
    loadingPrice.value = false
  }

  // Возвращает { ok, confirmationUrl, status, error }. confirmationUrl===null означает мгновенную
  // разблокировку (акция довела цену до нуля) — вызывающий код должен перепроверить доступ
  // (usePlayerAccess.checkAccess), а не ждать редиректа.
  async function subscribe(songId, disclaimerAccepted) {
    submitting.value = true
    error.value = ''
    try {
      const { status, body } = await authPost(
        '/api/public/account/subscription/create',
        { scope: 'SONG', songId, disclaimerAccepted },
        token.value,
      )
      submitting.value = false
      if (status === 200 && body)
        return { ok: true, confirmationUrl: body.confirmationUrl, status: body.status }
      error.value = (body && body.error) || 'subscribe_failed'
      return { ok: false, error: error.value }
    } catch (e) {
      submitting.value = false
      error.value = 'network_error'
      return { ok: false, error: error.value }
    }
  }

  return { loadingPrice, priceInfo, submitting, error, loadPrice, subscribe }
}
