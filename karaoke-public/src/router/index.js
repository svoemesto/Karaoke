import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import SearchView from '../views/SearchView.vue'
import ZakromaView from '../views/ZakromaView.vue'
import SongView from '../views/SongView.vue'
import PlayerView from '../views/PlayerView.vue'
import LoginView from '../views/LoginView.vue'
import RegisterView from '../views/RegisterView.vue'
import AccountView from '../views/AccountView.vue'

const routes = [
  { path: '/', name: 'home', component: HomeView },
  { path: '/filter', name: 'filter', component: SearchView },
  { path: '/zakroma', name: 'zakroma', component: ZakromaView },
  { path: '/song', name: 'song', component: SongView },
  { path: '/login', name: 'login', component: LoginView },
  { path: '/register', name: 'register', component: RegisterView },
  {
    path: '/account',
    name: 'account',
    component: AccountView,
    // AccountView сама перепроверяет токен через fetchMe() и редиректит при необходимости —
    // здесь достаточно быстрой синхронной проверки, чтобы не мигать защищённым контентом.
    beforeEnter: (to) => {
      if (!localStorage.getItem('km_auth_token')) return { path: '/login', query: { redirect: to.fullPath } }
    }
  },
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
  routes
})

export default router
