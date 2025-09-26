import { createRouter, createWebHistory } from 'vue-router'
// Импортируете компоненты представлений, которые вы создадите
import HomeView from '../views/HomeView.vue'
import SongsView from '../views/SongsView.vue'
import PublishView from '../views/PublishView.vue'
import ProcessesView from '../views/ProcessesView.vue'
import PropertiesView from '../views/PropertiesView.vue'
import AuthorsView from '../views/AuthorsView.vue'
import PicturesView from '../views/PicturesView.vue'

const routes = [
  {
    path: '/',
    name: 'home',
    component: HomeView
  },
  {
    path: '/songs',
    name: 'songs',
    component: SongsView
  },
  {
    path: '/publish',
    name: 'publish',
    component: PublishView
  },
  {
    path: '/processes',
    name: 'processes',
    component: ProcessesView
    // component: () => import(/* webpackChunkName: "about" */ '../views/ProcessesView.vue')
  },
  {
    path: '/properties',
    name: 'properties',
    component: PropertiesView
  },
  {
    path: '/authors',
    name: 'authors',
    component: AuthorsView
  },
  {
    path: '/pictures',
    name: 'pictures',
    component: PicturesView
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router