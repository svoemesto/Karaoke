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
 * @see archive/docs/features/editor-tasks.md
 */
export function refuseTask(id) {
  return authPost(`${BASE}/tasks/${id}/refuse`, {}, token())
}

/**
 * Удалить одобренное задание (`approved`) из моего списка. Удаляется ТОЛЬКО запись задания —
 * песня (`tbl_songs`) и её разметка остаются нетронутыми.
 *
 * @see archive/docs/features/editor-tasks.md
 */
export function deleteTask(id) {
  return authPost(`${BASE}/tasks/${id}/delete`, {}, token())
}

/**
 * Удалить все мои одобренные задания одним запросом (батч). Активные не трогаются.
 * Идемпотентно — повторный клик возвращает `{ok: true, deleted: 0}`.
 *
 * @see archive/docs/features/editor-tasks.md
 */
export function deleteApprovedTasks() {
  return authPost(`${BASE}/tasks/delete-approved`, {}, token())
}

// ---- Self-assign из «Закромов» (FR-005) ---------------------------------------------------
// Self-assign ВЫНЕСЕН из /api/public/account/editor/* в отдельный контроллер
// /api/public/songeditor/ (см. PublicSongEditorController.assignSelf) — потому что «Закрома»
// НЕ защищены SiteAuthInterceptor (см. WebMvcConfig) и редактор может кликать «Взять в работу»
// прямо из публичного каталога без логина в /account. Контракт: 200 {ok, id, idempotent} либо
// 409 {ok:false, error:'song_already_taken'} при гонке. Бэкенд всегда возвращает тело на 4xx/5xx
// через authApi, поэтому фронт может сразу показать toast.

/**
 * Self-assign: берёт свободную песню себе (FR-005). Только для редакторов с флагом canSelfAssignTasks.
 *
 * @param {number} songId
 * @returns {Promise<{status: number, body: any}>} при status===200 — {ok:true, id, idempotent};
 *          при status===409 — {ok:false, error:'song_already_taken'}; при 403 — нет прав.
 *
 * @see specs/182-editor-self-assign-tasks/
 */
export function assignSelf(songId) {
  return authPost('/api/public/songeditor/assign-self', { songId }, token())
}
