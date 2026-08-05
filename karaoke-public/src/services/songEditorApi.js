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

// Отозвать с проверки — вернуть в работу, пока админ ещё не вынес вердикт (статус submitted).
export function recallTask(id) {
  return authPost(`${BASE}/tasks/${id}/recall`, {}, token())
}

/**
 * Отказаться от активного задания (`assigned`/`in_progress`/`submitted`/`rejected`).
 * Удаляет запись задания + связанный черновик; песня и разметка не трогаются.
 * Идемпотентно — повторный клик возвращает `{ok: false, error: 'assignment_not_found'}`.
 *
 * @see docs/features/editor-tasks.md
 */
export function refuseTask(id) {
  return authPost(`${BASE}/tasks/${id}/refuse`, {}, token())
}

/**
 * Удалить одобренное задание (`approved`) из моего списка. Удаляется ТОЛЬКО запись задания —
 * песня (`tbl_songs`) и её разметка остаются нетронутыми.
 *
 * @see docs/features/editor-tasks.md
 */
export function deleteTask(id) {
  return authPost(`${BASE}/tasks/${id}/delete`, {}, token())
}

/**
 * Удалить все мои одобренные задания одним запросом (батч). Активные не трогаются.
 * Идемпотентно — повторный клик возвращает `{ok: true, deleted: 0}`.
 *
 * @see docs/features/editor-tasks.md
 */
export function deleteApprovedTasks() {
  return authPost(`${BASE}/tasks/delete-approved`, {}, token())
}
