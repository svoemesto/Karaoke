import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import store from './store'
import { createBootstrap } from 'bootstrap-vue-next'
import 'bootstrap/dist/css/bootstrap.css'
import 'bootstrap-vue-next/dist/bootstrap-vue-next.css'
import './style.css'
import { useDesign } from './composables/useDesign'

useDesign() // инициализация темы из localStorage при старте

const app = createApp(App)

app.use(router)
app.use(store)
app.use(createBootstrap())

app.mount('#app')
