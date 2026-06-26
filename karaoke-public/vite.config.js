import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    proxy: {
      // 8897 — порт, на котором deploy/do.sh build_start_web публикует локальный контейнер karaoke-web
      // (WEB_PORT_HOST в deploy/do.env). Если запускаете karaoke-web напрямую через gradle bootRun
      // (без docker), поменяйте на 7799 — порт из karaoke-web/src/main/resources/application.yml.
      '/api/public': 'http://localhost:8897',
      '/api/storage': 'http://localhost:8897'
    }
  }
})
