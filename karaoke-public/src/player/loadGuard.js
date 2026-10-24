// Инварианты жизненного цикла загрузки плеера.
//
// BR-302 (аудит #336, пакет PKG-S2-14, OpenProject #377): защита от гонки при
// переключении трека была привязана к общему полю `this._activeAbortController`,
// а не к контроллеру СВОЕЙ загрузки. `init()` создавал контроллер и клал его в
// поле, а все проверки после `await` читали поле заново. Если за время `init()`
// стартовал следующий `playSong()`, он делал `_abortActive()` и создавал свой
// контроллер — старая `init()` видела ЧУЖОЙ (не отменённый) сигнал и продолжала
// работу, перезаписывая state нового трека.
//
// BR-303: терминальный `release` share-сессии вызывался не только при закрытии
// вкладки, но и на любом `visibilitychange → hidden` (переключение вкладки,
// сворачивание окна, блокировка экрана телефона). Бэкенд обнулял
// `active_session_token_hash`, heartbeat отвечал 410, гость видел «Время сеанса
// истекло», хотя ссылка ещё валидна.
//
// Модуль без зависимостей (KaraokePlayer импортирует soundtouchjs, которого нет в
// node_modules у теста) — поэтому инварианты живут здесь и покрыты юнит-тестом.

/**
 * Устарела ли загрузка, которая после `await` хочет писать в state.
 *
 * Загрузка устарела, если:
 *   - контроллера своей загрузки нет (не должно случаться — fail-closed);
 *   - поле `_activeAbortController` уже указывает на ДРУГУЮ загрузку
 *     (значит, пока мы ждали, стартовал `playSong()` следующего трека);
 *   - наш собственный контроллер отменён.
 *
 * @param {AbortController|null} activeController — текущее значение поля this._activeAbortController
 * @param {AbortController|null} myController — контроллер, захваченный в начале своей init()
 * @returns {boolean} true — записывать state нельзя, нужно выйти
 */
export function isStaleLoad(activeController, myController) {
  if (!myController) return true
  if (activeController !== myController) return true
  return myController.signal ? myController.signal.aborted === true : true
}

/**
 * Причина терминального `release` share-сессии для события страницы, либо null,
 * если по этому событию освобождать сессию НЕЛЬЗЯ.
 *
 * Терминальный release разрушает сессию без восстановления (бэкенд обнуляет
 * active_session_token_hash), поэтому допустим только когда страница реально
 * уходит: `pagehide` / `beforeunload`. `visibilitychange → hidden` — это
 * переключение вкладки/сворачивание окна/блокировка экрана, гость вернётся;
 * освобождение слота там делает истечение lease по TTL, а не release.
 *
 * @param {string} eventType — 'pagehide' | 'beforeunload' | 'visibilitychange' | ...
 * @returns {'closed'|null}
 */
export function resolveShareReleaseReason(eventType) {
  if (eventType === 'pagehide' || eventType === 'beforeunload') return 'closed'
  return null
}
