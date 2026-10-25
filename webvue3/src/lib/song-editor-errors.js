// BR-425 (аудит #336, пакет PKG-S3-32, OpenProject #404).
//
// Разбор кода ошибки контракта `POST /api/songeditor/edit/byId`.
//
// ДЕФЕКТ. Контракт отдаёт отличимый код `error: "song_not_found_in_local_db"` именно
// для того, чтобы UI показал понятное сообщение (FR-005 спеки specs/232), а модалка
// проверяла только `body.found` и всегда писала общий текст «Не удалось загрузить
// данные для редактирования». Админ не мог отличить «песня есть на сервере, но не
// стянута в LOCAL» от прочих отказов — и не понимал, что надо запустить sync.
//
// РЕШЕНИЕ. Коды ошибок разбираются в таблицу текстов; общий текст остаётся
// fallback'ом для неизвестного кода.
//
// Тексты — в одном месте, чтобы правило «показывать отличимый код» не расползлось
// по компонентам.

/**
 * Тексты ошибок загрузки данных редактора по коду из тела ответа.
 *
 * @type {Object<string, string>}
 */
export const SONG_EDITOR_LOAD_ERROR_MESSAGES = {
  song_not_found_in_local_db:
    'Песня есть на сервере, но не стянута в локальную БД — запустите sync (LOCAL ← SERVER).',
  song_not_found: 'Песня не найдена.',
  bad_mode: 'Некорректный режим редактирования (song / assignment).',
}

/**
 * Возвращает текст ошибки по телу ответа `POST /api/songeditor/edit/byId`.
 *
 * @param {Object|null} body — разобранное тело ответа.
 * @param {string} [fallback] — общий текст для неизвестного кода.
 * @returns {string} текст для показа пользователю.
 */
export function describeSongEditorLoadError(body, fallback) {
  const code = body && typeof body === 'object' ? body.error : undefined
  if (code && SONG_EDITOR_LOAD_ERROR_MESSAGES[code]) {
    return SONG_EDITOR_LOAD_ERROR_MESSAGES[code]
  }
  return fallback || 'Не удалось загрузить данные для редактирования.'
}
