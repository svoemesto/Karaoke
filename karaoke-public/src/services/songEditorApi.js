// Клиент онлайн-редактора караоке-разметки. Поверх authApi (тело ответа доступно и на 4xx — нужно
// для not_editable/no_draft). Токен из localStorage напрямую (как playlistApi). GET-параметры —
// вручную в query-string (authGet шлёт только path).
import { authGet, authPost } from './authApi'

function token() {
  return localStorage.getItem('km_auth_token') || ''
}

const BASE = '/api/public/account/editor'

// Список моих заданий: [{id, songId, songName, author, album, year, status, reviewComment}]
export function fetchTasks() {
  return authGet(`${BASE}/tasks`, token())
}

// Одно задание (ВСЯ песня, все голоса): метаданные + sourceTexts[]/markersPerVoice[] + URL стемов
// (с токеном) + статус/canEdit/comment.
export function fetchTask(id) {
  return authGet(`${BASE}/tasks/${id}`, token())
}

// Сохранить черновик. sourceTexts/markersPerVoice — JSON-строки МАССИВОВ (индекс = номер голоса).
export function saveTask(id, sourceTexts, markersPerVoice) {
  return authPost(`${BASE}/tasks/${id}/save`, { sourceTexts, markersPerVoice }, token())
}

// Отправить на проверку админу.
export function submitTask(id) {
  return authPost(`${BASE}/tasks/${id}/submit`, {}, token())
}
