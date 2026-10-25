// BR-316 / BR-317 (аудит #336, пакет PKG-S3-27, OpenProject #399): навигация и polling.
//
// BR-316 — ЧТО БЫЛО. `router.beforeEach` для `/zakroma/:id` ждал
// `store.dispatch('zakroma/loadAuthorTiles')` БЕЗ try/catch. Экшен делает `apiGet`, а
// `apiGet` реджектит промис на ЛЮБОМ не-2xx. При 5xx/сетевой ошибке отклонённый guard
// ОТМЕНЯЛ навигацию: при первом заходе по прямой ссылке router-view оставался пустым
// (белый экран), при клике переход «не срабатывал» без сообщения.
//
// BR-317 — ЧТО БЫЛО. `useNewsUnreadCount.startPolling()` проверял `isAuthenticated()` только
// в момент создания интервала, а `stopPolling()` вызывался исключительно при переходе на
// скрытый маршрут (`news`/`player`/`share`). После выхода из аккаунта на обычном маршруте
// интервал продолжал тикать каждые 45 секунд и запрашивать `/api/public/news/since` без токена —
// вопреки собственному инварианту файла.
//
// Модуль без зависимостей — тест запускается node --test без node_modules.

/**
 * Что делать router-guard, если предзагрузка данных упала.
 *
 * @param {Error|null} error — reject-причина (null — ошибки не было)
 * @returns {{abort: boolean, notify: boolean}} abort — отменять ли навигацию
 */
export function routeGuardOutcome(error) {
  if (!error) return { abort: false, notify: false }
  // Ошибка предзагрузки НЕ должна отменять навигацию: пользователь должен попасть на
  // страницу и увидеть её собственное сообщение («Автор не найден» / кнопку «Повторить»).
  return { abort: false, notify: true }
}

/**
 * Нужно ли держать polling новостей включённым.
 *
 * @param {boolean} isAuthenticated — есть ли токен
 * @param {boolean} hiddenRoute — текущий маршрут скрыт (news/player/share)
 * @returns {boolean}
 */
export function shouldPollNews(isAuthenticated, hiddenRoute) {
  if (!isAuthenticated) return false
  if (hiddenRoute) return false
  return true
}
