// BR-356 (аудит #336, пакет PKG-S3-28, OpenProject #400): поколение NDJSON-стрима.
//
// ЧТО БЫЛО. `store/modules/zakroma.js` коммитил результат того стрима, который
// завершился последним, без проверки «а тот ли это автор». Сценарий: открыт автор A
// (стрим идёт), пользователь уходит на сетку и открывает автора B — запускается стрим B;
// стрим A завершается позже и делает `setZakroma([A])` — страница `/zakroma/B`
// показывает песни автора A. Дополнительно `finally` стрима A гасит `isStreaming`,
// из-за чего прогресс-бар B исчезает на середине, а ошибка A показывается на странице B.
//
// ЧТО СТАЛО. У стрима есть номер поколения. Только «текущее» поколение вправе
// коммитить данные/ошибку/флаг стриминга; устаревшее (перебитое новым) — молча
// уходит, освобождая свои ресурсы.
//
// Модуль без зависимостей — тест запускается node --test без node_modules.

/**
 * Счётчик поколений стрима.
 *
 * @returns {{current: number}}
 */
export function createGenerationCounter() {
  return { current: 0 }
}

/**
 * Взять следующее поколение (поколение текущего запроса).
 *
 * @param {{current: number}} counter — счётчик
 * @returns {number} номер нового поколения
 */
export function nextGeneration(counter) {
  if (!counter) return 0
  counter.current = (counter.current || 0) + 1
  return counter.current
}

/**
 * Актуально ли ещё поколение (не перебито ли новым стримом).
 *
 * @param {{current: number}} counter — счётчик
 * @param {number} generation — поколение, которое проверяем
 * @returns {boolean}
 */
export function isCurrentGeneration(counter, generation) {
  if (!counter) return false
  return counter.current === generation
}

/**
 * Перебить текущий стрим (инвалидировать его поколение) — например, при явной отмене.
 *
 * @param {{current: number}} counter — счётчик
 * @returns {number} новое (невалидное для прежнего) поколение
 */
export function invalidateGeneration(counter) {
  return nextGeneration(counter)
}
