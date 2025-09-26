import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'url' // Импортируем для работы с абсолютными путями

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()]
})
