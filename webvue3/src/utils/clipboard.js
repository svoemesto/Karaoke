// Копирование в буфер обмена (админский SPA webvue3).
//
// Контракт тот же, что в публичном SPA (karaoke-public/src/utils/clipboard.js):
// копирование возвращает `true` ТОЛЬКО если текст действительно попал в буфер —
// либо `navigator.clipboard.writeText()` разрешился, либо `document.execCommand('copy')`
// вернул `true`. Любой иной исход — `false`, и UI показывает «Скопируйте вручную».
//
// ЗАЧЕМ. Clipboard API доступен не везде: требует secure context (https или localhost)
// и может быть отклонён браузером (нет прав, окно не в фокусе). Прямой вызов
// `navigator.clipboard.writeText` без обработки бросает TypeError — кнопка
// «Скопировать» молча не работала, и пользователь не понимал, что делать.
// Запасной путь (execCommand через скрытое textarea) в админке был только в
// удалённой легаси-странице песен и при её удалении в SPA не переехал.
//
// Модуль без зависимостей — тест запускается node --test без node_modules.

/**
 * Скопировать текст в буфер обмена.
 *
 * @param {string} text — текст для копирования
 * @param {object} [deps] — подменяемые зависимости (для тестов): { navigator, document }
 * @returns {Promise<boolean>} true — текст в буфере; false — копирование не удалось
 */
export async function copyTextToClipboard(text, deps = {}) {
  if (text === null || text === undefined || text === '') return false

  const nav = deps.navigator !== undefined ? deps.navigator : globalThis.navigator
  const doc = deps.document !== undefined ? deps.document : globalThis.document

  // Шаг 1 — современный Clipboard API (требует secure context и разрешения).
  if (nav && nav.clipboard && typeof nav.clipboard.writeText === 'function') {
    try {
      await nav.clipboard.writeText(text)
      return true
    } catch (_) {
      // Отказ (нет разрешения, документ не в фокусе, не secure context) — пробуем fallback.
    }
  }

  // Шаг 2 — fallback через скрытый textarea + execCommand('copy').
  // ВАЖНО: результат execCommand нужно ПРОВЕРИТЬ — Firefox без разрешения clipboard,
  // iOS Safari и часть политик браузера возвращают false (а не бросают).
  if (!doc || !doc.body || typeof doc.execCommand !== 'function') return false

  let textarea = null
  try {
    textarea = doc.createElement('textarea')
    textarea.value = text
    textarea.style.position = 'fixed'
    textarea.style.opacity = '0'
    doc.body.appendChild(textarea)
    textarea.select()
    return doc.execCommand('copy') === true
  } catch (_) {
    return false
  } finally {
    if (textarea && textarea.parentNode) textarea.parentNode.removeChild(textarea)
  }
}
