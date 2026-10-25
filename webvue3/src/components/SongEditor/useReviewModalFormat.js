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
//   - loadEditorSettings() — возвращает ПОЛНЫЙ набор настроек редактора из
//     localStorage ({textFontSize, previewFontSize, volume, playbackRate, zoom,
//     activeSound, showKeyboard}); для ReviewModal.vue — источник правды для шрифта
//     «Текст пользователя» и «Разметка». BR-428: набор ключей обязан совпадать с
//     каноническим (karaoke-public), иначе запись из ReviewModal стирала настройки
//     публичного редактора.
//
// @see karaoke-public/src/composables/useKaraokeEditor.js (источник)
// @see karaoke-public/src/views/EditorWorkView.vue:1845-1888 (стили .ke-fx-…)

function uppercaseFirstLetter(s) {
  return s && s.length ? s.charAt(0).toUpperCase() + s.slice(1) : s
}

// Экранирование пользовательского текста перед интерполяцией в HTML-буфер.
//
// BR-312 (аудит #336, пакет PKG-S2-14): эта копия formatText — sink админского
// ReviewModal.vue (`v-html="parsedMarkupHtml"`). Метка `COMMENT|<img onerror=…>`
// приходит из сохранённого черновика редактора и без экранирования становится
// живой разметкой в origin админ-панели (stored XSS). Экранируем текст, а не
// доверяем источнику.
//
// Копия канонического webvue3/src/utils/escapeHtml.js. Локальная, а не импорт,
// потому что tools/check-marker-escape-guard.sh прогоняет этот файл изолированно
// (копирует один файл в staging-каталог) — импорт сломал бы guard.
function escapeHtml(text) {
  return String(text ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
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
          result += `<span class="ke-fx-comment">${escapeHtml(txt)}</span><br>`
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
        result += escapeHtml(txt)
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

// BR-428 (аудит #336, пакет PKG-S3-32, OpenProject #404): набор ключей настроек
// редактора — ПОЛНЫЙ, как в karaoke-public/src/composables/useKaraokeEditor.js.
//
// ДЕФЕКТ. Локальная копия `loadEditorSettings`/`saveEditorSettings` знала только
// `textFontSize`/`previewFontSize`. Ключ localStorage — тот же (`karaoke-editor-settings`),
// что и у публичного редактора, поэтому `saveEditorSettings` (merge с урезанным
// объектом) ЗАТИРАЛ `volume`, `playbackRate`, `zoom`, `activeSound`, `showKeyboard`.
// Достаточно было открыть ReviewModal (в `mounted` размеры шрифтов присваиваются из
// storage → срабатывает watcher) или подвигать слайдер шрифта.
//
// РЕШЕНИЕ. Копия приведена в соответствие с канонической (полный набор ключей и
// clamp'ов), а `saveEditorSettings` мержит partial в ПОЛНЫЙ сохранённый объект —
// чужие ключи не теряются.
//
// Почему не общий модуль: Docker-сборка webvue3 копирует только ./webvue3/, поэтому
// прямой импорт из karaoke-public не резолвится (см. шапку файла). Общего модуля
// «вне контейнеров» здесь нет — поэтому копия должна быть полной, а не урезанной.
export const EDITOR_DEFAULTS = Object.freeze({
  textFontSize: 16,
  previewFontSize: 18,
  volume: 1,
  playbackRate: 0.75,
  zoom: 100,
  activeSound: 'voice',
  showKeyboard: false,
})

/**
 * Загружает настройки редактора из localStorage. При недоступном localStorage или
 * невалидном JSON возвращает EDITOR_DEFAULTS (без падений — никаких throw наружу).
 * Значения clamp'ятся в допустимый диапазон [6, 36].
 * @returns {{textFontSize: number, previewFontSize: number, volume: number,
 *   playbackRate: number, zoom: number, activeSound: string, showKeyboard: boolean}}
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
      volume: clamp(s.volume, 0, 1) ?? EDITOR_DEFAULTS.volume,
      playbackRate: clamp(s.playbackRate, 0.3, 1) ?? EDITOR_DEFAULTS.playbackRate,
      zoom: clamp(s.zoom, 20, 400) ?? EDITOR_DEFAULTS.zoom,
      activeSound: s.activeSound === 'music' ? 'music' : EDITOR_DEFAULTS.activeSound,
      showKeyboard:
        typeof s.showKeyboard === 'boolean' ? s.showKeyboard : EDITOR_DEFAULTS.showKeyboard,
    }
  } catch (e) {
    return { ...EDITOR_DEFAULTS }
  }
}

/**
 * Сохраняет настройки редактора в localStorage (merge с существующими ключами).
 * Безопасна при недоступном localStorage (приватный режим / квота) — тихий no-op.
 * Принимает partial: `{ textFontSize?: number, previewFontSize?: number, ... }`.
 *
 * BR-428: partial мержится в ПОЛНЫЙ сохранённый объект, а не в урезанный набор
 * ключей — иначе запись из ReviewModal стирала настройки публичного редактора.
 * @see loadEditorSettings
 */
export function saveEditorSettings(partial) {
  try {
    let stored = {}
    try {
      const raw = localStorage.getItem(EDITOR_SETTINGS_LS_KEY)
      if (raw) stored = JSON.parse(raw) || {}
    } catch (e) {
      stored = {}
    }
    localStorage.setItem(EDITOR_SETTINGS_LS_KEY, JSON.stringify({ ...stored, ...partial }))
  } catch (e) {
    /* no-op */
  }
}
