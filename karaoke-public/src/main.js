import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import store from './store'
import { createBootstrap } from 'bootstrap-vue-next'
import 'bootstrap/dist/css/bootstrap.css'
import 'bootstrap-vue-next/dist/bootstrap-vue-next.css'
import './style.css'
import { useDesign } from './composables/useDesign'
import { useAuthBootstrap } from './composables/useAuthBootstrap'

useDesign() // инициализация темы из localStorage при старте

const app = createApp(App)

app.use(router)
app.use(store)
app.use(createBootstrap())

app.mount('#app')

// Pass 239 (specs/239-zakroma-author-songs-batch-render): bulk-fetch избранного/подписок/плейлистов
// при логине, сброс при logout. Должен стартовать после mount, чтобы token в localStorage уже был
// доступен (useAuth.token читает из localStorage синхронно).
useAuthBootstrap()
