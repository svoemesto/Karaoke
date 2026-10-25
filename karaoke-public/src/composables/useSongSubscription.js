import { ref } from 'vue'
import { authGet, authPost } from '../services/authApi'
import { useAuth } from './useAuth'
import { createRequestGuard } from '../utils/requestGuard.js'

// Оформление подписки на ОДНУ песню (бессрочно) — см. план монетизации, "подписка на песню".
// Термин «покупка» нигде не используется. Цена всегда пересчитывается на сервере (PriceService) —
// это лишь ОТОБРАЖЕНИЕ цены пользователю ДО оплаты, сервер не доверяет тому, что ушло обратно.
export function useSongSubscription() {
  const { token } = useAuth()
  const loadingPrice = ref(false)
  const priceInfo = ref(null) // { tariffId, tariffName, base, discount, final, promoApplied }
  const submitting = ref(false)
  const error = ref('')

  // BR-300 (аудит #336, PKG-S3-27): модалка переиспользует один `priceInfo`/`loadingPrice`,
  // а loadPrice() не имел ни идентификатора запроса, ни отмены. Ответ по предыдущей песне
  // приходил последним и применялся к текущей — пользователь видел цену чужой песни.
  const priceGuard = createRequestGuard()

  async function loadPrice(songId) {
    const requestId = priceGuard.begin()
    loadingPrice.value = true
    error.value = ''
    priceInfo.value = null
    try {
      const { status, body } = await authGet(
        `/api/public/account/subscription/price?scope=SONG&songId=${songId}`,
        token.value,
      )
      // Устаревший ответ (песня сменилась) — не трогаем ни цену, ни ошибку, ни флаг загрузки.
      if (!priceGuard.isCurrent(requestId)) return
      if (status === 200 && body) {
        priceInfo.value = body
      } else {
        error.value = (body && body.error) || 'price_unavailable'
      }
    } catch (e) {
      if (!priceGuard.isCurrent(requestId)) return
      error.value = 'network_error'
    }
    if (priceGuard.isCurrent(requestId)) loadingPrice.value = false
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
