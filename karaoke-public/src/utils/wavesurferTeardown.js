// Жизненный цикл экземпляра WaveSurfer в редакторе: уничтожение старого и
// защита от повторного входа в load().
//
// BR-329 (аудит #336, пакет PKG-S2-14, OpenProject #377): `load()` не был
// идемпотентен — `initWaveSurfer()` всегда создавал новый WaveSurfer и
// перезаписывал `this.ws`, не уничтожая предыдущий. `load()` вызывается
// повторно из `recall()` при любом не-200 (например, админ уже одобрил
// задание). Старый экземпляр оставался с аудиоэлементом, AudioContext,
// обработчиками timeupdate и регионами; ссылка терялась,
// `beforeUnmount` уничтожал только последний.
//
// Модуль без зависимостей — тест запускается node --test без node_modules.

/**
 * Уничтожить текущий WaveSurfer владельца и обнулить ссылки.
 * Идемпотентно: повторный вызов на уже очищенном владельце безопасен.
 *
 * @param {{ws?: any, wsRegions?: any}} owner — компонент/объект с полями ws, wsRegions
 * @returns {boolean} true, если экземпляр действительно был уничтожен
 */
export function destroyWaveform(owner) {
  if (!owner) return false
  const instance = owner.ws || null
  if (instance && typeof instance.destroy === 'function') {
    try {
      instance.destroy()
    } catch (e) {
      // destroy() падает, если контейнер уже вынесен из DOM (размонтирование) —
      // ссылки всё равно обнуляем ниже, иначе утечка гарантирована.
    }
  }
  owner.ws = null
  owner.wsRegions = null
  return !!instance
}

/**
 * Взять «замок» загрузки вейвформы. false — загрузка уже идёт, повторный вход
 * запрещён (иначе два параллельных initWaveSurfer создают два экземпляра).
 *
 * @param {object} owner
 * @returns {boolean}
 */
export function beginWaveformLoad(owner) {
  if (!owner) return false
  if (owner._waveformLoading) return false
  owner._waveformLoading = true
  return true
}

/**
 * Снять «замок» загрузки. Вызывается из finally.
 *
 * @param {object} owner
 */
export function endWaveformLoad(owner) {
  if (owner) owner._waveformLoading = false
}
