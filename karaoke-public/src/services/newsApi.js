// «Новости» проекта — публичные, доступны и анонимам (apiGet сам подмешивает anonId/referrer,
// см. services/api.js). Читает с PROD-БД через karaoke-web (/api/public/news, без SiteAuthInterceptor).
import { apiGet } from './api'

const BASE = '/api/public/news'

// Постранично (specs/090-news-pagination) — ответ {items, total, hasMore} вместо плоского массива,
// т.к. tbl_news уже накопил 19000+ строк (см. specs/089-auto-news-song-release).
export function fetchNews(page = 0, size = 20) {
  return apiGet(BASE, { page, size })
}

export function fetchNewsSince(lastSeenId) {
  return apiGet(`${BASE}/since`, { id: lastSeenId })
}
