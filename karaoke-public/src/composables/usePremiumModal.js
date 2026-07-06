import { reactive } from 'vue'

// Синглтон-контроллер модалки апселла премиума / достижения лимита. Монтируется один раз
// (PremiumUpsellModal в App.vue), открывается из любого места (FavoriteIcon, PlaylistIcon и т.п.).
const state = reactive({
  open: false,
  title: '',
  message: '',
  limit: null,
  benefits: [],
})

export function usePremiumModal() {
  // Достигнут лимит (например, 100 песен в «Избранном» у не-премиума).
  function openLimit({ limit, benefits }) {
    state.title = 'Достигнут лимит бесплатного «Избранного»'
    state.message = limit
      ? `В бесплатном «Избранном» можно хранить до ${limit} песен. Снимите ограничение, став премиум-пользователем:`
      : 'Снимите ограничение, став премиум-пользователем:'
    state.limit = limit ?? null
    state.benefits = benefits || []
    state.open = true
  }
  // Функция доступна только премиуму (например, создание своих плейлистов).
  function openPremiumRequired({ benefits }) {
    state.title = 'Доступно премиум-пользователям'
    state.message = 'Плейлисты и расширенные возможности доступны с премиум-подпиской:'
    state.limit = null
    state.benefits = benefits || []
    state.open = true
  }
  function close() { state.open = false }

  return { state, openLimit, openPremiumRequired, close }
}
