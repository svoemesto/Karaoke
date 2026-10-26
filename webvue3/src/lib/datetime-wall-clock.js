// BR-463 (аудит #336, пакет PKG-S3-33, OpenProject #405): стенное время без зоны.
//
// ДО ПРАВКИ значение поля `datetime-local` уходило на сервер как
// `new Date(value).toISOString()` — то есть UTC-строка с суффиксом `Z`.
// Колонка `created_at` в БД объявлена как `timestamp without time zone` и
// заполняется `now()` — то есть хранит СТЕННОЕ время сессии БД (по конвенции
// проекта — МСК). PostgreSQL при сравнении `timestamp without time zone` с
// литералом, в котором указана зона, зону ИГНОРИРУЕТ: литерал
// '2026-10-23T07:00:00.000Z' сравнивается с 07:00, а не с 10:00. Фильтр
// «с 10:00» возвращал лишние 3 часа, «по 10:00» — терял их.
//
// ПОСЛЕ ПРАВКИ на сервер уходит стенное время БЕЗ зоны ('YYYY-MM-DD HH:mm:ss')
// в часовом поясе сервера (МСК) — ровно то, что лежит в колонке.
//
// ВАЖНО: `datetime-local` — это стенное время в часовом поясе БРАУЗЕРА, а колонка
// заполняется временем СЕССИИ БД. Поэтому значение переводится в часовой пояс
// сервера явно (Europe/Moscow — конвенция проекта: JVM/TZ админки и БД — МСК).
// Если сервер/BД когда-нибудь поедет в другую зону, менять нужно здесь и в
// контракте бэкенда (см. BR-463 в отчёте пакета — «за владельцем»).

// Часовой пояс сервера/БД (конвенция проекта — МСК).
export const SERVER_TIME_ZONE = 'Europe/Moscow'

/**
 * Разбирает `datetime-local` в мгновение (по часовому поясу браузера).
 *
 * @param {string} value — значение поля `datetime-local` ('YYYY-MM-DDTHH:mm').
 * @returns {Date|null} мгновение либо null, если значение пустое/некорректное.
 */
export function parseDateTimeLocal(value) {
  if (!value) return null
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return null
  return date
}

/**
 * Форматирует мгновение в стенное время часового пояса сервера.
 *
 * @param {Date} date — мгновение.
 * @param {string} timeZone — часовой пояс (по умолчанию — SERVER_TIME_ZONE).
 * @returns {string} 'YYYY-MM-DD HH:mm:ss' либо '' для некорректной даты.
 */
export function formatServerWallClock(date, timeZone = SERVER_TIME_ZONE) {
  if (!date || Number.isNaN(date.getTime())) return ''
  const parts = new Intl.DateTimeFormat('ru-RU', {
    timeZone: timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(date)
  const values = {}
  parts.forEach((part) => {
    if (part.type !== 'literal') values[part.type] = part.value
  })
  if (!values.year || !values.month || !values.day) return ''
  const hour = values.hour === '24' ? '00' : values.hour
  return `${values.year}-${values.month}-${values.day} ${hour}:${values.minute}:${values.second}`
}

/**
 * Переводит значение `datetime-local` в стенное время сервера без зоны.
 *
 * @param {string} value — значение поля `datetime-local`.
 * @param {string} timeZone — часовой пояс сервера (по умолчанию — SERVER_TIME_ZONE).
 * @returns {string} 'YYYY-MM-DD HH:mm:ss' либо '' (значение не задано/некорректно).
 */
export function toServerWallClock(value, timeZone = SERVER_TIME_ZONE) {
  return formatServerWallClock(parseDateTimeLocal(value), timeZone)
}
