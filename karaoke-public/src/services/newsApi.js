// «Новости» проекта — публичные, доступны и анонимам (apiGet сам подмешивает anonId/referrer,
// см. services/api.js). Читает с PROD-БД через karaoke-web (/api/public/news, без SiteAuthInterceptor).
import { apiGet } from './api'

const BASE = '/api/public/news'

export function fetchNews() {
  return apiGet(BASE)
}

export function fetchNewsSince(lastSeenId) {
  return apiGet(`${BASE}/since`, { id: lastSeenId })
}
