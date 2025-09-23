import Vue from 'vue'
import VueRouter from 'vue-router'
import HomeView from '../views/HomeView'
import SongsView from '../views/SongsView'
import PublishView from '../views/PublishView'
import ProcessesView from '../views/ProcessesView.vue'
import PropertiesView from '../views/PropertiesView'
import AuthorsView from '../views/AuthorsView.vue'

Vue.use(VueRouter)

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
  }
]

const router = new VueRouter({
  routes
})

export default router
