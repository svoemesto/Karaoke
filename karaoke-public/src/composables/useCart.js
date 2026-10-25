import { ref, reactive, computed } from 'vue'
import { fetchCart, toggleCart, clearCart } from '../services/cartApi'
import { useAuth } from './useAuth'
import { needsCartLoad } from '../utils/cartToggle.js'

// Синглтон «Корзины» — небольшой личный список, в отличие от usePlaylistMembership не требует
// чанкованной батч-загрузки по id строк таблицы: грузим весь список целиком одним запросом.
const items = ref([]) // [{ songId, songName, author }]
const songIds = reactive(new Set())
let loaded = false
// BR-314 (аудит #336, PKG-S3-27): для какого токена корзина загружена. Флаг `loaded` без
// привязки к токену означал, что после входа (или входа ДРУГИМ пользователем в том же
// браузере) корзина оставалась «пустой», а после выхода — с чужими позициями.
let loadedForToken = null

async function load(force = false) {
  const { token } = useAuth()
  const currentToken = token.value
  if (!currentToken) {
    // Аноним: корзины нет. ВАЖНО — НЕ помечаем её «загруженной»: после входа load()
    // обязан реально сходить на сервер, иначе иконка корзины в шапке так и не появится.
    items.value = []
    songIds.clear()
    loaded = false
    loadedForToken = null
    return
  }
  if (!force && !needsCartLoad({ hasToken: true, loaded, loadedForToken, token: currentToken }))
    return
  const { status, body } = await fetchCart()
  if (status === 200 && Array.isArray(body)) {
    items.value = body
    songIds.clear()
    body.forEach((i) => songIds.add(i.songId))
  }
  loaded = true
  loadedForToken = currentToken
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

// BR-314: полный сброс корзины — при выходе из аккаунта (иначе позиции прежнего
// пользователя остаются видны следующему в том же браузере).
function reset() {
  items.value = []
  songIds.clear()
  loaded = false
  loadedForToken = null
}

const count = computed(() => items.value.length)

export function useCart() {
  return { items, count, isInCart, load, toggle, clear, reset }
}
