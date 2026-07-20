import { ref, reactive, computed } from 'vue'
import { fetchCart, toggleCart, clearCart } from '../services/cartApi'
import { useAuth } from './useAuth'

// Синглтон «Корзины» — небольшой личный список, в отличие от usePlaylistMembership не требует
// чанкованной батч-загрузки по id строк таблицы: грузим весь список целиком одним запросом.
const items = ref([]) // [{ songId, songName, author }]
const songIds = reactive(new Set())
let loaded = false

async function load(force = false) {
  const { token } = useAuth()
  if (!token.value) {
    items.value = []
    songIds.clear()
    loaded = true
    return
  }
  if (loaded && !force) return
  const { status, body } = await fetchCart()
  if (status === 200 && Array.isArray(body)) {
    items.value = body
    songIds.clear()
    body.forEach((i) => songIds.add(i.songId))
  }
  loaded = true
}

function isInCart(songId) {
  return songIds.has(songId) || songIds.has(Number(songId))
}

// Возвращает { status, body } как toggleCart — вызывающий код (CartIcon) сам решает, что показать
// при ошибке (already_subscribed/song_not_for_subscription).
async function toggle(songId) {
  const result = await toggleCart(songId)
  if (result.status === 200 && result.body) {
    // Проще перезагрузить список целиком (нужны songName/author для новой позиции), чем тащить их
    // отдельным полем в ответ toggle — корзина маленькая, лишний запрос не заметен.
    await load(true)
  }
  return result
}

async function clear() {
  const result = await clearCart()
  if (result.status === 200) {
    items.value = []
    songIds.clear()
  }
  return result
}

const count = computed(() => items.value.length)

export function useCart() {
  return { items, count, isInCart, load, toggle, clear }
}
