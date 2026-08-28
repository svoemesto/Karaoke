import { createRouter, createWebHistory } from 'vue-router'
import { trackUi } from '../services/tracking'
import store from '../store'
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
import HistoryView from '../views/HistoryView.vue'
import ChatView from '../views/ChatView.vue'
import AuthorPlaylistView from '../views/AuthorPlaylistView.vue'
import EditorTasksView from '../views/EditorTasksView.vue'
import EditorWorkView from '../views/EditorWorkView.vue'
import SubscriptionReturnView from '../views/SubscriptionReturnView.vue'
import PremiumView from '../views/PremiumView.vue'
import SubscriptionsView from '../views/SubscriptionsView.vue'
import StemJobsView from '../views/StemJobsView.vue'
import CartView from '../views/CartView.vue'
import OfertaView from '../views/OfertaView.vue'
import NewsView from '../views/NewsView.vue'
import AboutView from '../views/AboutView.vue'
import ShareView from '../views/ShareView.vue'

// Быстрая синхронная проверка токена для защищённых маршрутов личного кабинета — сами страницы
// перепроверяют через fetchMe(); здесь лишь чтобы не мигнуть защищённым контентом анониму.
const requireAuth = (to) => {
  if (!localStorage.getItem('km_auth_token'))
    return { path: '/login', query: { redirect: to.fullPath } }
}

const routes = [
  { path: '/', name: 'home', component: HomeView },
  { path: '/filter', name: 'filter', component: SearchView },
  // specs/258-zakroma-routing-refactor: «закрома» разнесены на 3 URL — тайтлы, песни автора по ID, спец-корзина.
  { path: '/zakroma', name: 'zakroma', component: ZakromaView },
  // \\d+ — только цифры, иначе vue-router отдаёт 404 (RT-6.A).
  { path: '/zakroma/:authorId(\\d+)', name: 'zakroma-author', component: ZakromaView },
  // Спец-корзина «Отдельные песни разных авторов» — самостоятельный route (FR-A6).
  { path: '/zakroma/special-bucket', name: 'zakroma-special-bucket', component: ZakromaView },
  { path: '/song', name: 'song', component: SongView },
  { path: '/login', name: 'login', component: LoginView },
  { path: '/register', name: 'register', component: RegisterView },
  { path: '/premium', name: 'premium', component: PremiumView },
  { path: '/oferta', name: 'oferta', component: OfertaView },
  // Публично, без requireAuth — новости видны и анонимам.
  { path: '/news', name: 'news', component: NewsView },
  // Публично, без requireAuth — страница «О проекте» видна и анонимам.
  { path: '/about', name: 'about', component: AboutView },
  { path: '/share/:id/:secret', name: 'share', component: ShareView },
  {
    path: '/account',
    name: 'account',
    component: AccountView,
    // AccountView сама перепроверяет токен через fetchMe() и редиректит при необходимости —
    // здесь достаточно быстрой синхронной проверки, чтобы не мигать защищённым контентом.
    beforeEnter: requireAuth,
  },
  // Без requireAuth: аноним не редиректится, а видит внутри страницы сообщение «только для
  // зарегистрированных» с кнопками Войти/Регистрация (LoginRequired).
  { path: '/account/playlists', name: 'playlists', component: PlaylistsView },
  // Без requireAuth — как /account/playlists: LoginRequired внутри компонента (US3, не редирект).
  { path: '/account/history', name: 'history', component: HistoryView },
  { path: '/account/chat', name: 'chat', component: ChatView, beforeEnter: requireAuth },
  {
    path: '/account/subscriptions',
    name: 'subscriptions',
    component: SubscriptionsView,
    beforeEnter: requireAuth,
  },
  {
    path: '/account/stemjobs',
    name: 'stemjobs',
    component: StemJobsView,
    beforeEnter: requireAuth,
  },
  { path: '/account/cart', name: 'cart', component: CartView, beforeEnter: requireAuth },
  { path: '/account/playlists/:id', name: 'playlist-edit', component: PlaylistEditView },
  // Динамический read-only плейлист автора (все песни автора). Аноним — LoginRequired внутри.
  { path: '/author-playlist', name: 'author-playlist', component: AuthorPlaylistView },
  {
    path: '/account/editor',
    name: 'editor-tasks',
    component: EditorTasksView,
    beforeEnter: requireAuth,
  },
  {
    path: '/account/editor/:id',
    name: 'editor-work',
    component: EditorWorkView,
    beforeEnter: requireAuth,
  },
  // Возврат redirect-подтверждения ЮKassa после оплаты подписки (см. PublicSubscriptionController).
  {
    path: '/subscription/return',
    name: 'subscription-return',
    component: SubscriptionReturnView,
    beforeEnter: requireAuth,
  },
  {
    path: '/player/:id',
    name: 'player',
    component: PlayerView,
    // Without a token for this song already in sessionStorage, this route should behave like
    // it doesn't exist — no hint that a hidden unlock mechanism exists. Также пускаем гостей
    // по share-сессии: либо через ?session= в query (первый переход с лендинга), либо через
    // sessionStorage['kp_share_session_<id>'] (после F5). См. spec.md FR-003.
    beforeEnter: (to) => {
      const id = to.params.id
      const hasToken = !!sessionStorage.getItem(`kp_token_${id}`)
      const hasShareSession =
        (typeof to.query.session === 'string' && to.query.session) ||
        !!sessionStorage.getItem(`kp_share_session_${id}`)
      if (!hasToken && !hasShareSession) return '/'
    },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  // Без этого SPA-навигация сохраняет текущий scrollY страницы — переход на новый маршрут (например,
  // с промотанных Закромов на страницу песни) открывался бы там же, где прокручен был список.
  // Spec 262-search-pagination (Pass 243): для query-only изменений внутри одной страницы
  // (например, при клике «Загрузить ещё» — $router.replace({ query: { ..., page: N } }))
  // возвращаем false (не скроллить) — иначе страница прыгает в начало при подгрузке
  // новых элементов в конец списка, и пользователь вынужден снова скроллить к новым записям.
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) return savedPosition
    if (to.path === from.path) return false
    return { top: 0 }
  },
})

// specs/258-zakroma-routing-refactor (FR-A2, FR-A7, US4): legacy redirect
//   /zakroma?author=X               → /zakroma/:authorId (резолвинг имени в ID через Vuex authorTiles)
//   /zakroma?specialBucket=true      → /zakroma/special-bucket
// replace: true — не плодим промежуточный URL в истории браузера.
// Также: для прямого перехода на /zakroma/:authorId догружаем authorTiles до mounted(),
// чтобы ZakromaView мог резолвить ID → имя сразу (без ложного «Автор не найден»).
router.beforeEach(async (to) => {
  if (to.path === '/zakroma' && to.query.specialBucket === 'true') {
    return { path: '/zakroma/special-bucket', replace: true }
  }
  if (to.path === '/zakroma' && to.query.author) {
    const authorName = String(to.query.author)
    // Дедуп 30 сек внутри loadAuthorTiles — лишних HTTP-запросов не будет.
    await store.dispatch('zakroma/loadAuthorTiles', 'main')
    const tile = store.state.zakroma?.authorTiles?.find((t) => t.author === authorName)
    if (tile && tile.id) {
      return { path: `/zakroma/${tile.id}`, replace: true }
    }
    // Автор не найден → тайты + уведомление (App.vue может не иметь showNotify —
    // используем window.alert как fallback, чтобы не падать молча).
    if (typeof window !== 'undefined' && window.alert) {
      window.alert(`Автор «${authorName}» не найден`)
    }
    return { path: '/zakroma', replace: true }
  }
  if (/^\/zakroma\/\d+$/.test(to.path)) {
    await store.dispatch('zakroma/loadAuthorTiles', 'main')
  }
})

// Трекинг навигации по SPA-маршрутам (кроме скрытого плеера — его существование не палим в лог).
router.afterEach((to) => {
  if (to.name === 'player') return
  const songId = to.name === 'song' ? to.query.id || undefined : undefined
  trackUi('navigate', to.name || to.path, songId)
})

export default router
