import Vue from 'vue'
import VueRouter from 'vue-router'
import HomeView from '../views/HomeView'
import SongsView from '../views/SongsView'

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
    // route level code-splitting
    // this generates a separate chunk (about.[hash].js) for this route
    // which is lazy-loaded when the route is visited.
    component: SongsView
  },
  {
    path: '/publications',
    name: 'publications',
    component: () => import(/* webpackChunkName: "about" */ '../views/PublicationsView.vue')
  },
  {
    path: '/unpublications',
    name: 'unpublications',
    component: () => import(/* webpackChunkName: "about" */ '../views/UnpublicationsView.vue')
  },
  {
    path: '/zakroma',
    name: 'zakroma',
    component: () => import(/* webpackChunkName: "about" */ '../views/ZakromaView.vue')
  },
  {
    path: '/processes',
    name: 'processes',
    component: () => import(/* webpackChunkName: "about" */ '../views/ProcessesView.vue')
  }
]

const router = new VueRouter({
  routes
})

export default router
