// src/router/index.js
import { createRouter, createWebHistory } from 'vue-router';
import HomeView from '../views/HomeView.vue';
import SongsView from '../views/SongsView.vue';
import PublishView from '../views/PublishView.vue';
import ProcessesView from '../views/ProcessesView.vue';
import PropertiesView from '../views/PropertiesView.vue';
import AuthorsView from '../views/AuthorsView.vue';
import UsersView from '../views/UsersView.vue';
import PicturesView from '../views/PicturesView.vue';
import AuthView from '../views/AuthView.vue';
import CallbackView from '../views/CallbackView.vue';
import AuthService from '../services/AuthService';

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
  },
  {
    path: '/users',
    name: 'users',
    component: UsersView
  },
  {
    path: '/auth',
    name: 'auth',
    component: AuthView
  },
  {
    path: '/callback', // Маршрут для обработки callback
    name: 'callback',
    component: CallbackView
  }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

// Обработчик перед каждым переходом
router.beforeEach(async (to, from, next) => {
  if (to.path === '/callback') {
    try {
      await AuthService.signinCallback(); // Обрабатываем callback
      next('/');
    } catch (error) {
      console.error('Callback processing error:', error);
      next('/auth');
    }
  } else {
    next();
  }
});

export default router;