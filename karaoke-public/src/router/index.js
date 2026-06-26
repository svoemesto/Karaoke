import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import SearchView from '../views/SearchView.vue'
import ZakromaView from '../views/ZakromaView.vue'
import SongView from '../views/SongView.vue'

const routes = [
  { path: '/', name: 'home', component: HomeView },
  { path: '/filter', name: 'filter', component: SearchView },
  { path: '/zakroma', name: 'zakroma', component: ZakromaView },
  { path: '/song', name: 'song', component: SongView }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
