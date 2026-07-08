import { createRouter, createWebHistory } from 'vue-router'
import { trackUi } from '../services/tracking'
import HomeView from '../views/HomeView.vue'
import SearchView from '../views/SearchView.vue'
import ZakromaView from '../views/ZakromaView.vue'
import SongView from '../views/SongView.vue'
import PlayerView from '../views/PlayerView.vue'
import LoginView from '../views/LoginView.vue'
import RegisterView from '../views/RegisterView.vue'
import AccountView from '../views/AccountView.vue'
import PlaylistsView from '../views/PlaylistsView.vue'
import PlaylistEditView from '../views/PlaylistEditView.vue'
import AuthorPlaylistView from '../views/AuthorPlaylistView.vue'
import EditorTasksView from '../views/EditorTasksView.vue'
import EditorWorkView from '../views/EditorWorkView.vue'
import SubscriptionReturnView from '../views/SubscriptionReturnView.vue'
import PremiumView from '../views/PremiumView.vue'
import SubscriptionsView from '../views/SubscriptionsView.vue'
import CartView from '../views/CartView.vue'
import OfertaView from '../views/OfertaView.vue'

// Быстрая синхронная проверка токена для защищённых маршрутов личного кабинета — сами страницы
// перепроверяют через fetchMe(); здесь лишь чтобы не мигнуть защищённым контентом анониму.
const requireAuth = (to) => {
  if (!localStorage.getItem('km_auth_token')) return { path: '/login', query: { redirect: to.fullPath } }
}

const routes = [
  { path: '/', name: 'home', component: HomeView },
  { path: '/filter', name: 'filter', component: SearchView },
  { path: '/zakroma', name: 'zakroma', component: ZakromaView },
  { path: '/song', name: 'song', component: SongView },
  { path: '/login', name: 'login', component: LoginView },
  { path: '/register', name: 'register', component: RegisterView },
  { path: '/premium', name: 'premium', component: PremiumView },
  { path: '/oferta', name: 'oferta', component: OfertaView },
  {
    path: '/account',
    name: 'account',
    component: AccountView,
    // AccountView сама перепроверяет токен через fetchMe() и редиректит при необходимости —
    // здесь достаточно быстрой синхронной проверки, чтобы не мигать защищённым контентом.
    beforeEnter: requireAuth
  },
  // Без requireAuth: аноним не редиректится, а видит внутри страницы сообщение «только для
  // зарегистрированных» с кнопками Войти/Регистрация (LoginRequired).
  { path: '/account/playlists', name: 'playlists', component: PlaylistsView },
  { path: '/account/subscriptions', name: 'subscriptions', component: SubscriptionsView, beforeEnter: requireAuth },
  { path: '/account/cart', name: 'cart', component: CartView, beforeEnter: requireAuth },
  { path: '/account/playlists/:id', name: 'playlist-edit', component: PlaylistEditView },
  // Динамический read-only плейлист автора (все песни автора). Аноним — LoginRequired внутри.
  { path: '/author-playlist', name: 'author-playlist', component: AuthorPlaylistView },
  { path: '/account/editor', name: 'editor-tasks', component: EditorTasksView, beforeEnter: requireAuth },
  { path: '/account/editor/:id', name: 'editor-work', component: EditorWorkView, beforeEnter: requireAuth },
  // Возврат redirect-подтверждения ЮKassa после оплаты подписки (см. PublicSubscriptionController).
  { path: '/subscription/return', name: 'subscription-return', component: SubscriptionReturnView, beforeEnter: requireAuth },
  {
    path: '/player/:id',
    name: 'player',
    component: PlayerView,
    // Without a token for this song already in sessionStorage, this route should behave like
    // it doesn't exist — no hint that a hidden unlock mechanism exists.
    beforeEnter: (to) => {
      if (!sessionStorage.getItem(`kp_token_${to.params.id}`)) return '/'
    }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  // Без этого SPA-навигация сохраняет текущий scrollY страницы — переход на новый маршрут (например,
  // с промотанных Закромов на страницу песни) открывался бы там же, где прокручен был список.
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) return savedPosition
    return { top: 0 }
  }
})

// Трекинг навигации по SPA-маршрутам (кроме скрытого плеера — его существование не палим в лог).
router.afterEach((to) => {
  if (to.name === 'player') return
  const songId = to.name === 'song' ? (to.query.id || undefined) : undefined
  trackUi('navigate', to.name || to.path, songId)
})

export default router
