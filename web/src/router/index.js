import Vue from 'vue'
import VueRouter from 'vue-router'
import HomeView from '../views/HomeView'
import SongsView from '../views/SongsView'
import SongsBvView from '../views/SongsBvView'
import PublishView from '../views/PublishView'
import PublicationsBvView from '../views/PublicationsBvView'
import PublicationsView from '../views/PublicationsView'
import UnpublicationsView from '../views/UnpublicationsView'
import ZakromaView from '../views/ZakromaView'
import ProcessesView from '../views/ProcessesView'
import ProcessesBvView from '../views/ProcessesBvView'

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
    path: '/songsbv',
    name: 'songsbv',
    component: SongsBvView
  },
  {
    path: '/publish',
    name: 'publish',
    component: PublishView
  },
  {
    path: '/publicationsbv',
    name: 'publicationsbv',
    component: PublicationsBvView
  },
  {
    path: '/publications',
    name: 'publications',
    component: PublicationsView
  },
  {
    path: '/unpublications',
    name: 'unpublications',
    component: UnpublicationsView
  },
  {
    path: '/zakroma',
    name: 'zakroma',
    component: ZakromaView
  },
  {
    path: '/processes',
    name: 'processes',
    component: ProcessesView
    // component: () => import(/* webpackChunkName: "about" */ '../views/ProcessesView.vue')
  }
  ,
  {
    path: '/processesbv',
    name: 'processesbv',
    component: ProcessesBvView
    // component: () => import(/* webpackChunkName: "about" */ '../views/ProcessesView.vue')
  }
]

const router = new VueRouter({
  routes
})

export default router
