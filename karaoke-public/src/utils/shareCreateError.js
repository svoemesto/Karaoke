// KARAOKE-399: коды ошибок share-флоу при СОЗДАНИИ ссылки и пользовательские тексты.
//
// Раньше тексты ошибок были зашиты прямо в `ShareLinkModal.vue` (функция `handleError`),
// а бэкенд отдавал код `share.tokenMissing` и при недопустимом сроке действия ссылки —
// хотя токен тут ни при чём. Фронт показывал текст не про то.
//
// Теперь коды и тексты — здесь, в одном месте, и новый код `share.invalidTtl`
// (недопустимый TTL) имеет собственный текст.
//
// Модуль без зависимостей — тест запускается node --test без node_modules.

/**
 * Коды ошибок share-флоу (значения совпадают с enum `ShareErrorCode` в
 * `karaoke-web/.../util/ShareErrorCode.kt` — это единственный источник правды:
 * бэкенд отдаёт `errorCode`, фронт по нему выбирает текст).
 */
export const SHARE_CREATE_ERROR_CODE = Object.freeze({
  NOT_OWNER: 'share.notOwner',
  SONG_UNAVAILABLE: 'share.songUnavailable',
  LINK_ALREADY_ACTIVE: 'share.linkAlreadyActive',
  INVALID_TTL: 'share.invalidTtl',
  INTERNAL: 'share.internal',
})

/** Текст по умолчанию — когда код неизвестен или пришёл не тот статус. */
const DEFAULT_MESSAGE = 'Не удалось создать ссылку'

/**
 * Текст ошибки при создании share-ссылки.
 *
 * @param {number} status — HTTP-статус ответа
 * @param {*} body — тело ответа (может быть null)
 * @returns {string} текст для показа пользователю
 */
export function shareCreateErrorMessage(status, body) {
  const code = body && typeof body === 'object' ? body.errorCode : null

  if (status === 403 && code === SHARE_CREATE_ERROR_CODE.NOT_OWNER) {
    return 'Только премиум-пользователи могут создавать ссылки'
  }
  if (status === 409 && code === SHARE_CREATE_ERROR_CODE.SONG_UNAVAILABLE) {
    return 'Песня недоступна для share-ссылки'
  }
  if (status === 400 && code === SHARE_CREATE_ERROR_CODE.INVALID_TTL) {
    // KARAOKE-399: раньше здесь приходил share.tokenMissing и пользователю показывался
    // текст про отсутствие токена вместо текста про недопустимый срок.
    return 'Недопустимый срок действия ссылки'
  }
  if (status === 429 && code === SHARE_CREATE_ERROR_CODE.LINK_ALREADY_ACTIVE) {
    // Бэкенд сообщает reason + limit/actual — без жёстко прошитого текста.
    const reason = body.reason
    const limit = body.limit
    const actual = body.actual
    if (reason === 'maxActivePerUser')
      return `Превышен лимит активных ссылок (${actual}/${limit}). Отзовите старые ссылки и попробуйте снова.`
    if (reason === 'maxGenerationsPerDay')
      return `Превышен дневной лимит созданий (${actual}/${limit}).`
    if (reason === 'maxReissuesPerSongPerHour')
      return `Превышен часовой лимит перевыпусков этой песни (${actual}/${limit}).`
    return 'Превышен лимит ссылок'
  }
  return DEFAULT_MESSAGE
}
