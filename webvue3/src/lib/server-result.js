// BR-406 / BR-420 (аудит #336, пакет PKG-S3-32, OpenProject #404).
//
// Разбор тела ответа сервера, который сообщает об отказе ТЕЛОМ, а не кодом.
//
// Многие эндпоинты админки отвечают HTTP 200 даже при отказе:
//   POST /api/promorules/create  -> Long  (id акции; 0 — не создана)
//   POST /api/promorules/update  -> Long  (id акции; 0 — не найдена)
//   POST /api/promorules/delete  -> Boolean (false — не удалена)
//   POST /api/siteusers/update  -> Long  (id пользователя; 0 — не найден)
//   POST /api/siteusers/ban     -> Boolean (false — не найден)
//   POST /api/siteusers/unban   -> Boolean (false — не найден)
//   POST /api/siteusers/delete  -> Boolean (false — не удалён)
//
// Без проверки тела фронт считал такой ответ успехом: UI молча перезагружал список,
// а админ — «изменение применено». Правило одно на все эти эндпоинты, поэтому оно
// живёт здесь, а не дублируется в каждом сторе.

/**
 * Разбирает id из тела ответа.
 *
 * @param {*} raw — тело ответа (`xhr.response`).
 * @returns {number} id либо 0 (отказ).
 */
export function parseServerId(raw) {
  const text = String(raw === undefined || raw === null ? '' : raw).trim()
  if (text === '') return 0
  const value = Number(text)
  return Number.isFinite(value) ? value : 0
}

/**
 * Разбирает флаг из тела ответа.
 *
 * @param {*} raw — тело ответа (`xhr.response`).
 * @returns {boolean} признак успеха.
 */
export function parseServerFlag(raw) {
  return (
    String(raw === undefined || raw === null ? '' : raw)
      .trim()
      .toLowerCase() === 'true'
  )
}
