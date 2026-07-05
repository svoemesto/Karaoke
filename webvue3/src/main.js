import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import store from './store'
import { createBootstrap } from 'bootstrap-vue-next'
import VueApexCharts from 'vue3-apexcharts'
import 'bootstrap/dist/css/bootstrap.css'
import 'bootstrap-vue-next/dist/bootstrap-vue-next.css' // Если используете BootstrapVueNext

const app = createApp(App)

app.use(router)
app.use(store)
app.use(createBootstrap())
app.use(VueApexCharts)

app.mount('#app')
