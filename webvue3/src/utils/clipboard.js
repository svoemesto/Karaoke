/**
 * Копирование в буфер обмена с запасным путём.
 *
 * **Зачем запасной путь.** Clipboard API (`navigator.clipboard.writeText`) доступен
 * не везде: он требует secure context (https или localhost) и может быть отклонён
 * браузером (нет прав, окно не в фокусе, политика разрешений). Раньше админка
 * вызывала `navigator.clipboard.writeText` напрямую — если API недоступен,
 * вызов бросал `TypeError`, и пользователь не получал ни копирования, ни
 * сообщения: кнопка просто «молча» не работала.
 *
 * Поведение (совпадает с решением, принятым в легаси-странице песен):
 *   1. `navigator.clipboard.writeText` — основной путь;
 *   2. при недоступности API или его отказе — `execCommand('copy')` через скрытое
 *      `textarea` (работает везде, где есть DOM);
 *   3. если и это не вышло — возвращается `false`, вызывающий показывает текст,
 *      чтобы пользователь скопировал его вручную.
 *
 * Функция возвращает признак успеха — это и есть обратная связь для UI.
 *
 * Зависимости (`navigator`, `document`) внедряются параметром: тесты идут на
 * node:test, где DOM нет.
 */

/**
 * Запасной путь: копирование через скрытое `textarea` и `execCommand('copy')`.
 *
 * @param {string} text текст для копирования
 * @param {Document} [doc] документ (по умолчанию `globalThis.document`)
 * @returns {boolean} удалось ли скопировать
 */
export function copyWithExecCommand(text, doc = globalThis.document) {
  if (!doc || typeof doc.createElement !== 'function') {
    return false
  }
  try {
    const textarea = doc.createElement('textarea')
    textarea.value = text
    textarea.setAttribute('readonly', '')
    textarea.style.position = 'absolute'
    textarea.style.left = '-9999px'
    doc.body.appendChild(textarea)
    textarea.select()
    const ok = doc.execCommand ? doc.execCommand('copy') : false
    doc.body.removeChild(textarea)
    return ok === true
  } catch (e) {
    console.warn('execCommand не удался', e)
    return false
  }
}

/**
 * Копирование текста с запасным путём.
 *
 * @param {string} text текст для копирования
 * @param {{navigator?: object, document?: Document}} [deps] внедряемые зависимости (для тестов)
 * @returns {Promise<boolean>} удалось ли скопировать
 */
export async function copyToClipboardSafely(text, deps = {}) {
  const nav = deps.navigator ?? globalThis.navigator
  const doc = deps.document ?? globalThis.document
  if (nav && nav.clipboard && typeof nav.clipboard.writeText === 'function') {
    try {
      await nav.clipboard.writeText(text)
      return true
    } catch (e) {
      console.warn('clipboard.writeText не удался, пробуем execCommand', e)
    }
  }
  return copyWithExecCommand(text, doc)
}
