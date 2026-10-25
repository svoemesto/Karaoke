// BR-296 (аудит #336, пакет PKG-S3-27, OpenProject #399): копирование в буфер обмена.
//
// ЧТО БЫЛО. `copyToClipboard()` ничего не возвращала, `document.execCommand('copy')`
// бросал исключение или возвращал `false`, а `copied = true` выставлялся БЕЗУСЛОВНО.
// UI писал «Ссылка скопирована», хотя в буфере ничего не было.
//
// ЧТО СТАЛО. Копирование возвращает `true` только если текст действительно попал в буфер:
// либо `navigator.clipboard.writeText()` разрешился, либо `document.execCommand('copy')`
// вернул `true`. Любой иной исход — `false`, и вызывающий код показывает «Скопируйте вручную».
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
