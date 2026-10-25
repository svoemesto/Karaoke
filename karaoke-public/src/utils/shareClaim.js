// BR-358 / BR-359 (аудит #336, пакет PKG-S3-28, OpenProject #400): состояния лендинга
// share-ссылки и lease сессии.
//
// ЧТО БЫЛО (BR-358). `ShareView.doClaim()` сводил ВСЕ ответы `/api/public/share/claim`,
// кроме двух доменных кодов, к состоянию `notfound`: системная ошибка бэкенда
// (`500 {errorCode:"share.internal"}`), `400 tokenMissing` и любой не-200 без
// `errorCode` показывались как «Срок действия ссылки истёк…», а сетевой сбой — как
// «Не удалось открыть ссылку». Временный сбой сервера был неотличим от мёртвой ссылки.
//
// ЧТО СТАЛО. `notfound` — только для `share.notFound`/`share.tokenMissing` (и 404);
// отдельное состояние `error` («сервис недоступен», с кнопкой «Повторить») — для 5xx,
// `share.internal` и сетевых сбоев.
//
// ЧТО БЫЛО (BR-359). Лендинг сохранял `expiresAt` (lease, 90 с) и НИГДЕ его не
// использовал: кнопка «Открыть плеер» переходила на `/player/{id}?session=<hash>` с
// заведомо истёкшим lease → `ShareLinkSweeper` обнулял сессию → `/playerdata` отдавал
// 404 → «Ошибка: HTTP 404» вместо плеера.
//
// ЧТО СТАЛО. Перед переходом в плеер lease проверяется; истёкший (или неизвестный)
// lease — повторный claim. Пользователь видит «сессия истекла — обновить», а не 404.
//
// Модуль без зависимостей — тест запускается node --test без node_modules.

export const SHARE_STATE = Object.freeze({
  CLAIMING: 'claiming',
  READY: 'ready',
  CONCURRENT: 'concurrent',
  RATE_LIMITED: 'ratelimited',
  NOT_FOUND: 'notfound',
  ERROR: 'error',
})

export const SHARE_ERROR_CODE = Object.freeze({
  CONCURRENT: 'share.concurrentLimit',
  RATE_LIMITED: 'share.rateLimited',
  NOT_FOUND: 'share.notFound',
  TOKEN_MISSING: 'share.tokenMissing',
  INTERNAL: 'share.internal',
})

export const SHARE_LEASE = Object.freeze({
  VALID: 'valid',
  EXPIRED: 'expired',
  UNKNOWN: 'unknown',
})

/**
 * Состояние лендинга по ответу `/api/public/share/claim`.
 *
 * @param {number} status — HTTP-статус
 * @param {*} body — тело ответа (может быть null)
 * @returns {string} значение SHARE_STATE
 */
export function shareClaimState(status, body) {
  if (status === 200) return SHARE_STATE.READY
  const code = body && typeof body === 'object' ? body.errorCode : null
  if (code === SHARE_ERROR_CODE.CONCURRENT) return SHARE_STATE.CONCURRENT
  if (code === SHARE_ERROR_CODE.RATE_LIMITED) return SHARE_STATE.RATE_LIMITED
  if (code === SHARE_ERROR_CODE.NOT_FOUND || code === SHARE_ERROR_CODE.TOKEN_MISSING) {
    return SHARE_STATE.NOT_FOUND
  }
  if (status === 404) return SHARE_STATE.NOT_FOUND
  return SHARE_STATE.ERROR
}

/**
 * Текст ошибки для пользователя (пустая строка — ошибки нет).
 *
 * @param {string} state — значение SHARE_STATE
 * @returns {string}
 */
export function shareErrorText(state) {
  switch (state) {
    case SHARE_STATE.NOT_FOUND:
      return 'Срок действия ссылки истёк, она была отозвана владельцем или песня снята с публикации.'
    case SHARE_STATE.CONCURRENT:
      return 'По этой ссылке уже открыт другой плеер — дождитесь, пока он закроется.'
    case SHARE_STATE.RATE_LIMITED:
      return 'Слишком много попыток — подождите минуту и попробуйте снова.'
    case SHARE_STATE.ERROR:
      return 'Сервис временно недоступен. Попробуйте ещё раз.'
    default:
      return ''
  }
}

/**
 * Состояние lease-сессии по времени её окончания.
 *
 * @param {number|string} expiresAt — epoch ms окончания lease (0 — неизвестно)
 * @param {number} [now] — текущее время (для тестов)
 * @returns {string} значение SHARE_LEASE
 */
export function shareLeaseState(expiresAt, now = Date.now()) {
  const ts = Number(expiresAt) || 0
  if (!ts) return SHARE_LEASE.UNKNOWN
  return ts <= now ? SHARE_LEASE.EXPIRED : SHARE_LEASE.VALID
}

/**
 * Нужен ли повторный claim перед открытием плеера.
 *
 * @param {number|string} expiresAt — epoch ms окончания lease
 * @param {number} [now] — текущее время
 * @param {number} [marginMs] — запас до истечения (lease почти истёк → тоже claim)
 * @returns {boolean}
 */
export function needsReclaim(expiresAt, now = Date.now(), marginMs = 5000) {
  const state = shareLeaseState(expiresAt, now)
  if (state === SHARE_LEASE.EXPIRED) return true
  if (state === SHARE_LEASE.UNKNOWN) return true
  return Number(expiresAt) - now <= marginMs
}
