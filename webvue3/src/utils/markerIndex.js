/**
 * Поиск индекса маркера по позиции playhead.
 *
 * СЕМАНТИКА (совпадает с прежней линейной реализацией в SubsEdit.vue, 1:1):
 * возвращается индекс маркера, чей интервал `[marker.time, nextMarker.time)`
 * содержит currentTime, с допуском `diff`; -1 — playhead раньше первого
 * маркера; последний индекс — если playhead за последним маркером либо ничего
 * не подошло.
 *
 * ЗАЧЕМ БИНАРНЫЙ ПОИСК. Прежняя реализация перебирала все маркеры: 2170 на
 * песне 11718, причём getCurrentSyllablesIndex дополнительно аллоцировал
 * filter() на весь массив при КАЖДОМ вызове. Обе функции зовутся на каждой
 * смене текущего маркера и на каждом timeupdate. Маркеры отсортированы по
 * времени, поэтому ответ находится за O(log n) без выделения памяти.
 *
 * Вынесено отдельной функцией, а не осталось методом компонента, чтобы
 * поведение можно было проверить без монтирования редактора: тест фаззит эту
 * функцию против линейного эталона (см. __tests__/markerIndex.test.js).
 */

const DIFF = 0.02

/**
 * @param {Array<{time: number}>} markers - маркеры, ОТСОРТИРОВАННЫЕ ПО ВРЕМЕНИ
 * @param {number} currentTime - позиция playhead, секунды
 * @returns {number} индекс маркера или -1, если playhead раньше первого
 */
export function findMarkerIndexByTime(markers, currentTime) {
  const n = markers.length
  if (n === 0) return -1
  if (currentTime < markers[0].time - DIFF) return -1

  // Ищем первый индекс j, для которого markers[j].time - DIFF > currentTime.
  // Тогда j - 1 и есть искомый: markers[j-1].time - DIFF <= currentTime
  // (условие «текущее время не меньше времени маркера») и
  // currentTime < markers[j].time - DIFF (условие «меньше времени следующего»).
  // Если такого j нет, playhead за последним маркером — возвращаем n - 1.
  let lo = 0
  let hi = n
  while (lo < hi) {
    const mid = (lo + hi) >> 1
    if (markers[mid].time - DIFF > currentTime) hi = mid
    else lo = mid + 1
  }
  return lo - 1
}
