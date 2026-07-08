// src/router/index.js
import { createRouter, createWebHistory } from 'vue-router';
import HomeView from '../views/HomeView.vue';
import SongsView from '../views/SongsView.vue';
import PublishView from '../views/PublishView.vue';
import ProcessesView from '../views/ProcessesView.vue';
import PropertiesView from '../views/PropertiesView.vue';
import AuthorsView from '../views/AuthorsView.vue';
import SiteUsersView from '../views/SiteUsersView.vue';
import SitePlaylistsView from '../views/SitePlaylistsView.vue';
import SongEditorView from '../views/SongEditorView.vue';
import PublicSettingsView from '../views/PublicSettingsView.vue';
import PicturesView from '../views/PicturesView.vue';
import StatsView from '../views/StatsView.vue';
import SyncView from '../views/SyncView.vue';
import TariffsView from '../views/TariffsView.vue';
import PromotionsView from '../views/PromotionsView.vue';
import SponsrSyncView from '../views/SponsrSyncView.vue';

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
    path: '/siteusers',
    name: 'siteusers',
    component: SiteUsersView
  },
  {
    path: '/siteplaylists',
    name: 'siteplaylists',
    component: SitePlaylistsView
  },
  {
    path: '/songeditor',
    name: 'songeditor',
    component: SongEditorView
  },
  {
    path: '/publicsettings',
    name: 'publicsettings',
    component: PublicSettingsView
  },
  {
    path: '/stats',
    name: 'stats',
    component: StatsView
  },
  {
    path: '/sync',
    name: 'sync',
    component: SyncView
  },
  {
    path: '/tariffs',
    name: 'tariffs',
    component: TariffsView
  },
  {
    path: '/promotions',
    name: 'promotions',
    component: PromotionsView
  },
  {
    path: '/sponsrsync',
    name: 'sponsrsync',
    component: SponsrSyncView
  },
  {
    path: '/player/:id',
    name: 'player',
    component: () => import('../views/PlayerView.vue')
  }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

export default router;