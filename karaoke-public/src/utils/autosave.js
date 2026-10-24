// Решение «сбрасывать ли отложенное автосохранение при уходе со страницы».
//
// BR-327 (аудит #336, пакет PKG-S2-14, OpenProject #377): автосохранение в
// EditorWorkView отложено на 3 с, а `beforeUnmount` просто гасил таймер.
// `beforeunload`/`beforeRouteLeave` в файле отсутствовали — уход в окне 0–3 с
// после последней правки терял её без следа и без предупреждения.
//
// Правило: если таймер взведён и правка разрешена — уход обязан сначала
// выполнить сохранение (flush), а не отменять его.
//
// Модуль без зависимостей — тест запускается node --test без node_modules.

export const AUTOSAVE_PLAN = Object.freeze({
  NONE: 'none',
  FLUSH: 'flush',
})

/**
 * @param {{timerArmed: boolean, canEdit: boolean}} state
 * @returns {'none'|'flush'}
 */
export function autosaveFlushPlan({ timerArmed, canEdit }) {
  if (!canEdit) return AUTOSAVE_PLAN.NONE
  if (!timerArmed) return AUTOSAVE_PLAN.NONE
  return AUTOSAVE_PLAN.FLUSH
}
