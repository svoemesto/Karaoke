// Учёт подписок на window/document с гарантированным снятием.
//
// BR-313 (аудит #336, пакет PKG-S2-14, OpenProject #377): composable
// `useZakromaStreamProgress` навешивал `pagehide` + два `visibilitychange` на
// КАЖДЫЙ вызов `start()`, а `cleanup()` снимал только AbortController. Ссылок на
// обработчики не сохранялось, `removeEventListener` в файле не было вовсе —
// каждая загрузка автора оставляла три листенера и удерживала closure с полным
// списком песен автора (`albums`) и `metricsBatch`.
//
// Модуль нарочно без зависимостей: вызывающий код (composable) импортирует Vue,
// а тест на утечку должен запускаться node --test без node_modules.

/**
 * Мешок подписок: `add()` запоминает (target, type, handler, options), `clear()`
 * снимает всё добавленное и очищает список. Повторный `clear()` безопасен.
 *
 * @returns {{add: Function, clear: Function, size: number}}
 */
export function createListenerBag() {
  const entries = []

  return {
    /**
     * Подписать обработчик и запомнить подписку.
     * Возвращает переданный handler (удобно для ref-присваивания в вызывающем коде).
     */
    add(target, type, handler, options) {
      if (!target || typeof target.addEventListener !== 'function') return handler
      target.addEventListener(type, handler, options)
      entries.push({ target, type, handler, options })
      return handler
    },
    get size() {
      return entries.length
    },
    clear() {
      // splice(0) — чтобы повторный clear() ничего не снимал дважды и список не рос.
      for (const entry of entries.splice(0)) {
        try {
          entry.target.removeEventListener(entry.type, entry.handler, entry.options)
        } catch (e) {
          // removeEventListener не бросает на «не найденном» обработчике; сюда попадаем только
          // если target уже уничтожен (iframe/document заменён) — игнорируем.
        }
      }
    },
  }
}
