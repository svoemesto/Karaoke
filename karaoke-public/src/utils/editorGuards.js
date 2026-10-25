// BR-331 / BR-332 / BR-333 / BR-335 / BR-336 / BR-337 / BR-338
// (аудит #336, пакет PKG-S3-27, OpenProject #399): инварианты редактора заданий.
//
// Модуль без зависимостей — тест запускается node --test без node_modules.

// ─── BR-331: результат сохранения черновика ─────────────────────────────────────────────

/**
 * Успешно ли сохранён черновик.
 *
 * @param {string} saveState — 'saved' | 'error' | 'saving' | ...
 * @returns {boolean}
 */
export function isSaveSuccessful(saveState) {
  return saveState === 'saved'
}

// ─── BR-332: композитный статус задания ────────────────────────────────────────────────

/**
 * Какой статус показывать после сохранения.
 *
 * Сервер `save` ВСЕГДА отвечает `in_progress`, хотя композитный статус при
 * `adminStatus=rejected` + `userStatus=in_progress` — `REJECTED`. Слепое присваивание
 * статуса из ответа save скрывало баннер «Возвращено на доработку» ровно тогда, когда
 * редактор исправляет замечания.
 *
 * @param {string} currentStatus — текущий локальный статус
 * @param {string} bodyStatus — статус из ответа save
 * @returns {string} статус, который надо показать
 */
export function mergeSaveStatus(currentStatus, bodyStatus) {
  // Отклонённое задание остаётся отклонённым до повторной отправки — баннер с комментарием
  // админа должен висеть.
  if (currentStatus === 'rejected') return 'rejected'
  if (currentStatus === 'submitted') return 'submitted'
  return bodyStatus || currentStatus
}

// ─── BR-333: размонтирование во время загрузки ─────────────────────────────────────────

/**
 * Размонтирован ли компонент (проверять после каждого await).
 *
 * @param {{_unmounted?: boolean}} state
 * @returns {boolean}
 */
export function isUnmounted(state) {
  return !!(state && state._unmounted)
}

// ─── BR-335: пересборка превью разметки ────────────────────────────────────────────────

/**
 * Нужно ли пересобирать HTML-превью разметки.
 *
 * `timeupdate` приходит каждые 16 мс; пересборка HTML по всем маркерам на каждом тике —
 * O(n) на 60 обновлений в секунду. Превью имеет смысл обновлять только при смене индекса
 * текущего маркера.
 *
 * @param {number} lastIndex — индекс, на котором превью собрано последний раз
 * @param {number} currentIndex — текущий индекс
 * @returns {boolean}
 */
export function shouldRefreshPreview(lastIndex, currentIndex) {
  return lastIndex !== currentIndex
}

// ─── BR-336: переключение стема ───────────────────────────────────────────────────────

export const SOUND_SWITCH = Object.freeze({
  OK: 'ok',
  NO_URL: 'no_url',
  FAILED: 'failed',
})

/**
 * Итог переключения стема: что фиксировать в `activeSound`.
 *
 * Стем фиксируется ТОЛЬКО после успешной загрузки. Иначе при 403 (истёкший токен стемов) UI
 * показывал выбранный стем, звук оставался старым, а повторный клик по той же кнопке
 * выходил по раннему `if (this.activeSound === sound)` и не повторял загрузку.
 *
 * @param {string} requested — запрошенный стем
 * @param {string|null} url — URL стема
 * @param {Error|null} error — reject-причина загрузки
 * @returns {{result: string, activeSound: string|null, message: string}}
 */
export function soundSwitchOutcome(requested, url, error) {
  if (!url) {
    return {
      result: SOUND_SWITCH.NO_URL,
      activeSound: null,
      message: 'Файл стема недоступен.',
    }
  }
  if (error) {
    return {
      result: SOUND_SWITCH.FAILED,
      activeSound: null,
      message: 'Не удалось загрузить стем — попробуйте ещё раз.',
    }
  }
  return { result: SOUND_SWITCH.OK, activeSound: requested, message: '' }
}

// ─── BR-337: потеря фокуса во время перемотки ──────────────────────────────────────────

/**
 * Сбросить ВСЕ интервалы перемотки/навигации и подсветку зажатых клавиш.
 *
 * При потере фокуса окна (Alt-Tab, блокировка экрана) `keyup` не приходит — интервал
 * перемотки продолжал крутиться бесконечно.
 *
 * @param {Record<string, *>} scrubTimers — карта таймеров (будет очищена)
 * @param {Record<string, boolean>} heldKeys — карта зажатых клавиш (будет очищена)
 * @returns {string[]} коды клавиш, которые были освобождены
 */
export function releaseAllScrubTimers(scrubTimers, heldKeys) {
  const released = []
  if (scrubTimers) {
    for (const code of Object.keys(scrubTimers)) {
      const timer = scrubTimers[code]
      if (timer) clearInterval(timer)
      delete scrubTimers[code]
      released.push(code)
    }
  }
  if (heldKeys) {
    for (const code of Object.keys(heldKeys)) {
      heldKeys[code] = false
    }
  }
  return released
}

// ─── BR-338: расхождение текста и разметки ────────────────────────────────────────────

/**
 * План разметки голоса.
 *
 * Если у голоса ЕСТЬ текст — слоги пересобираются и метки переставляются. Если текста НЕТ,
 * но маркеры есть — `relabelSyllables(markers, [])` обнулял `label` ВСЕХ слоговых маркеров,
 * а следующий автосохранение закрепляло пустые метки в черновике (после апрува текст голоса
 * терялся — он хранится только в метках).
 *
 * @param {{sourceText?: string, markers?: Array}} voice
 * @returns {{relabel: boolean, hasText: boolean, hasMarkers: boolean}}
 */
export function voiceLabelPlan(voice) {
  const text = (voice && voice.sourceText) || ''
  const markers = (voice && voice.markers) || []
  const hasText = text.trim().length > 0
  return {
    relabel: hasText,
    hasText,
    hasMarkers: markers.length > 0,
  }
}

/**
 * Предупреждение о расхождении длин текста и разметки (не атомарная правка на бэкенде).
 *
 * @param {number} textsCount
 * @param {number} markersCount
 * @returns {string} '' — расхождения нет
 */
export function voiceLengthMismatchWarning(textsCount, markersCount) {
  if (textsCount === markersCount) return ''
  return (
    'Внимание: число голосов в тексте и в разметке различается ' +
    `(${textsCount} и ${markersCount}). Проверьте разметку перед сохранением.`
  )
}

/**
 * Убрать «синтезированные» пустые голоса с КОНЦА списка.
 *
 * Голос без текста и без маркеров не несёт данных; в конце списка он безопасно удаляется
 * (индексы остальных голосов не сдвигаются). В середине — не удаляется, чтобы не потерять
 * соответствие «индекс голоса» на сервере.
 *
 * @param {Array<{sourceText?: string, markers?: Array}>} voices
 * @returns {Array} новый массив (исходный не мутируется)
 */
export function trimTrailingEmptyVoices(voices) {
  const list = Array.isArray(voices) ? voices.slice() : []
  while (list.length) {
    const last = list[list.length - 1]
    const text = (last && last.sourceText) || ''
    const markers = (last && last.markers) || []
    if (text.trim().length === 0 && markers.length === 0) {
      list.pop()
      continue
    }
    break
  }
  return list
}
