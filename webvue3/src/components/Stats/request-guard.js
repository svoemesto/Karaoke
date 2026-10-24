// Поколение запросов статистики (аудит #336, BR-459).
//
// Ответы статистики коммитились в state без привязки к запросу: смена БД
// (`target`) или периода (`days`) запускала новую загрузку, но позже приходил
// ответ ранее отправленного запроса и снова записывал цифры ДРУГОЙ БД/периода,
// а TTL (60 с) «замораживал» подмену. Пользователь видел неверные данные под
// новой меткой селектора.
//
// Гард выдаёт монотонный номер на каждый запуск action'а; ответ применяется,
// только если его номер всё ещё последний для этого ключа.

/**
 * Создать гард поколений запросов.
 *
 * @returns {{begin: (key: String) => Number, isCurrent: (key: String, seq: Number) => Boolean, reset: () => void}}
 */
export function createRequestGuard() {
  const sequences = new Map()
  return {
    /**
     * Начать новый запрос: вернуть его номер и сделать его последним для ключа.
     * @param {String} key — ключ группы (endpoint/вкладка)
     * @returns {Number}
     */
    begin(key) {
      const next = (sequences.get(key) || 0) + 1
      sequences.set(key, next)
      return next
    },
    /**
     * Проверить, что ответ с номером `seq` ещё актуален.
     * @param {String} key
     * @param {Number} seq
     * @returns {Boolean}
     */
    isCurrent(key, seq) {
      return sequences.get(key) === seq
    },
    /** Сбросить состояние (для тестов и повторной инициализации). */
    reset() {
      sequences.clear()
    },
  }
}

// Единственный на приложение экземпляр: модуль store создаётся один раз.
export const statsRequestGuard = createRequestGuard()
