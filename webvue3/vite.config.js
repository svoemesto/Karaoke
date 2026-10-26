import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'url' // Импортируем для работы с абсолютными путями

// BR-488 (аудит #336, пакет PKG-S3-33, OpenProject #405): прокси dev-сервера на API.
//
// ДО ПРАВКИ в `webvue3/vite.config.js` не было блока `server.proxy`. Весь фронт
// обращается к бэкенду ОТНОСИТЕЛЬНЫМИ URL (`/api/stats/summary` и т.п. через
// `promisedXMLHttpRequest`, src/lib/utils.js), а dev-сервер Vite такие запросы не
// проксирует: они уходят в корень dev-сервера и дают 404. `JSON.parse` падает,
// `catch` в сторах проглатывает ошибку — и админка в dev-режиме показывала пустые
// таблицы ПО ВСЕМ разделам, без сообщения об ошибке. Документированный в
// DEVELOPMENT.md режим `cd webvue3 && npm run dev` из-за этого был неработоспособен.
//
// Порт бэкенда: `karaoke-app` (Spring Boot) в dev-режиме слушает 8899
// (karaoke-app/src/main/resources/application.yml → server.port). Порт контейнера
// на admin-машине задаётся `APP_PORT_HOST` в deploy/do.env — если он отличается,
// адрес переопределяется переменной окружения KARAOKE_APP_PROXY (без VITE_:
// значение читается в конфиге Node, а не вшивается в клиентский бандл).
const KARAOKE_APP_PROXY = process.env.KARAOKE_APP_PROXY || 'http://localhost:8899'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    proxy: {
      // Все API-эндпоинты админки живут в karaoke-app (`/api/**`).
      '/api': KARAOKE_APP_PROXY,
    },
  },
  preview: {
    proxy: {
      '/api': KARAOKE_APP_PROXY,
    },
  },
})
