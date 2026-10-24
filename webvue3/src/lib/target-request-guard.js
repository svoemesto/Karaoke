// BR-397 / BR-407 / BR-409 (аудит #336, пакет PKG-RACE-TARGET, OpenProject #367).
//
// Админ-SPA умеет работать с двумя БД (`target: 'local' | 'remote'`). Запрос идёт в
// ту БД, которая выбрана в момент отправки, а ответ приходит позже — и до этой правки
// применялся безусловно. Если админ успевал переключить target, ответ старой БД
// перезаписывал список новой: таблица показывала строки одной БД, а кнопки
// сохранения/удаления подставляли в запрос текущий target (другую БД). Итог — правка
// или отзыв записи не в той базе.
//
// Решение на фронте (без новых зависимостей и без изменения API): у каждого
// target-aware запроса есть номер поколения. Смена target и каждый новый запрос
// увеличивают поколение; ответ применяется, только если (а) его поколение всё ещё
// текущее и (б) target, с которым он был отправлен, всё ещё выбран. Иначе ответ
// отбрасывается — «побеждает последний запрос», а не последний пришедший ответ.
//
// Почему не отмена запроса: `promisedXMLHttpRequest` не отдаёт `AbortController`
// (webvue3/src/lib/utils.js), а переписывать транспорт ради трёх экранов — лишний
// риск. Отбросить ответ дешевле и достаточно: состояние меняет только актуальный.
//
// Почему не Vuex state: счётчик нужен только экшенам и не участвует в рендере;
// мутация состояния на каждый запрос давала бы лишние перерисовки и «грязный» state
// в devtools. Экземпляр создаётся на модуль store (один на SPA), этого достаточно:
// поколение монотонно и не сбрасывается.
/**
 * Создаёт монотонную «эпоху запросов».
 *
 * @returns {{next: function(): number, isCurrent: function(number): boolean, current: function(): number}}
 */
export function createRequestGeneration() {
  let current = 0
  return {
    next() {
      current += 1
      return current
    },
    isCurrent(ticket) {
      return ticket === current
    },
    current() {
      return current
    },
  }
}

/**
 * Нужно ли отбросить ответ target-aware запроса.
 *
 * @param {{ticket: number, requestTarget: string, currentTarget: string, generation: object}} params
 * @returns {boolean} true — ответ устарел (другая эпоха или другой target).
 */
export function isStaleTargetResponse({ ticket, requestTarget, currentTarget, generation }) {
  return !generation.isCurrent(ticket) || requestTarget !== currentTarget
}
