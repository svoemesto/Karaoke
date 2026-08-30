// Feature 263 (review modal): локальный fallback для импорта formatText и loadEditorSettings,
// которые в karaoke-public/src/composables/useKaraokeEditor.js. Создан, потому что Docker-сборка
// webvue3 (`deploy/karaoke-webvue3/Dockerfile`) копирует только ./webvue3/, а прямой импорт
// '../../../../karaoke-public/...' резолвится локально (на хост-машине есть доступ к файловой
// системе), но НЕ в Docker-контексте (см. ошибку `Rollup failed to resolve import`).
//
// Содержимое — МИНИМАЛЬНАЯ копия из karaoke-public/src/composables/useKaraokeEditor.js только тех
// функций, которые нужны ReviewModal.vue:
//   - formatText(markers, curMarkerIndex) — генерирует HTML с классами ke-fx-* (karaoke-public
//     палитра). Используется для блока «Разметка» в ReviewModal.vue.
//   - loadEditorSettings() — возвращает {textFontSize, previewFontSize, ...} из localStorage;
//     единственный источник правды для шрифта «Текст пользователя» и «Разметка».
//
// @see karaoke-public/src/composables/useKaraokeEditor.js (источник)
// @see karaoke-public/src/views/EditorWorkView.vue:1845-1888 (стили .ke-fx-…)

function uppercaseFirstLetter(s) {
  return s && s.length ? s.charAt(0).toUpperCase() + s.slice(1) : s
}

/**
 * Генерирует HTML-представление разметки с подсветкой текущего слога и группами голоса.
 * Идентично `formatText` в karaoke-public — генерирует span с `class="ke-fx-…"`.
 * @param {Array} markers — массив маркеров текущего голоса.
 * @param {number} curMarkerIndex — индекс текущего маркера для подсветки, `-1` для «никого».
 * @returns {string} HTML-строка для v-html.
 */
export function formatText(markers, curMarkerIndex) {
  const CUR = '<span class="ke-fx-cur">'
  const GROUP_CLASS = {
    'GROUP|0': 'ke-fx-group0',
    'GROUP|1': 'ke-fx-group1',
    'GROUP|2': 'ke-fx-group2',
    'GROUP|3': 'ke-fx-group3',
  }
  let spanClass = 'ke-fx-group0'
  let wasBr = true
  let result = ''
  for (let i = 0; i < markers.length; i++) {
    const marker = markers[i]
    switch (marker.markertype) {
      case 'setting': {
        if (GROUP_CLASS[marker.label]) {
          spanClass = GROUP_CLASS[marker.label]
        } else if (marker.label && marker.label.startsWith('COMMENT|')) {
          const txt = uppercaseFirstLetter((marker.label.split('|')[1] || '').replaceAll('_', ' '))
          result += `<span class="ke-fx-comment">${txt}</span><br>`
          wasBr = true
        }
        break
      }
      case 'endofline':
      case 'newline':
        result += '<br>'
        wasBr = true
        break
      case 'syllables': {
        result += i === curMarkerIndex ? CUR : `<span class="${spanClass}">`
        let txt = marker.label ? marker.label.replaceAll('_', ' ') : ''
        if (wasBr) {
          txt = uppercaseFirstLetter(txt)
          wasBr = false
        }
        result += txt
        result += '</span>'
        break
      }
      default:
        break
    }
  }
  return result
}

const EDITOR_SETTINGS_LS_KEY = 'karaoke-editor-settings'

export const EDITOR_DEFAULTS = Object.freeze({
  textFontSize: 16,
  previewFontSize: 18,
})

/**
 * Загружает настройки редактора из localStorage. При недоступном localStorage или
 * невалидном JSON возвращает EDITOR_DEFAULTS (без падений — никаких throw наружу).
 * Значения clamp'ятся в допустимый диапазон [6, 36].
 * @returns {{textFontSize: number, previewFontSize: number, ...}}
 */
export function loadEditorSettings() {
  try {
    const raw = localStorage.getItem(EDITOR_SETTINGS_LS_KEY)
    if (!raw) return { ...EDITOR_DEFAULTS }
    const s = JSON.parse(raw) || {}
    const clamp = (v, lo, hi) => (Number.isFinite(v) ? Math.min(hi, Math.max(lo, v)) : null)
    return {
      ...EDITOR_DEFAULTS,
      textFontSize: clamp(s.textFontSize, 6, 36) ?? EDITOR_DEFAULTS.textFontSize,
      previewFontSize: clamp(s.previewFontSize, 6, 36) ?? EDITOR_DEFAULTS.previewFontSize,
    }
  } catch (e) {
    return { ...EDITOR_DEFAULTS }
  }
}

/**
 * Сохраняет настройки редактора в localStorage (merge с существующими ключами).
 * Безопасна при недоступном localStorage (приватный режим / квота) — тихий no-op.
 * Принимает partial: `{ textFontSize?: number, previewFontSize?: number, ... }`.
 * @see loadEditorSettings
 */
export function saveEditorSettings(partial) {
  try {
    const current = loadEditorSettings()
    localStorage.setItem(EDITOR_SETTINGS_LS_KEY, JSON.stringify({ ...current, ...partial }))
  } catch (e) {
    /* no-op */
  }
}
