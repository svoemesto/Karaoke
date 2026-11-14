// KARAOKE-399: тексты ошибок при создании share-ссылки.
//
// Бэкенд отдаёт код `errorCode` (enum `ShareErrorCode`), фронт по нему выбирает
// пользовательский текст. До правки недопустимый срок приходил с кодом
// `share.tokenMissing` — пользователю показывался текст про отсутствие токена.
//
// Запуск: cd karaoke-public && npm run test:unit

import { describe, test } from 'node:test'
import assert from 'node:assert/strict'

import { SHARE_CREATE_ERROR_CODE, shareCreateErrorMessage } from '../shareCreateError.js'

describe('KARAOKE-399: тексты ошибок создания share-ссылки', () => {
  test('недопустимый срок даёт свой текст, а не текст про токен', () => {
    const text = shareCreateErrorMessage(400, { errorCode: SHARE_CREATE_ERROR_CODE.INVALID_TTL })

    assert.ok(text.includes('срок'), `текст должен быть про срок, а не про токен: ${text}`)
    assert.ok(!text.includes('токен'), `текст не должен быть про токен: ${text}`)
  })

  test('код недопустимого срока совпадает с бэкендовым', () => {
    assert.equal(SHARE_CREATE_ERROR_CODE.INVALID_TTL, 'share.invalidTtl')
  })

  test('премиум-ошибка и недоступная песня сохранили прежние тексты', () => {
    assert.equal(
      shareCreateErrorMessage(403, { errorCode: SHARE_CREATE_ERROR_CODE.NOT_OWNER }),
      'Только премиум-пользователи могут создавать ссылки',
    )
    assert.equal(
      shareCreateErrorMessage(409, { errorCode: SHARE_CREATE_ERROR_CODE.SONG_UNAVAILABLE }),
      'Песня недоступна для share-ссылки',
    )
  })

  test('лимиты ссылок сохранили прежние тексты', () => {
    assert.equal(
      shareCreateErrorMessage(429, {
        errorCode: SHARE_CREATE_ERROR_CODE.LINK_ALREADY_ACTIVE,
        reason: 'maxActivePerUser',
        limit: 3,
        actual: 4,
      }),
      'Превышен лимит активных ссылок (4/3). Отзовите старые ссылки и попробуйте снова.',
    )
    assert.equal(
      shareCreateErrorMessage(429, {
        errorCode: SHARE_CREATE_ERROR_CODE.LINK_ALREADY_ACTIVE,
        reason: 'maxGenerationsPerDay',
        limit: 5,
        actual: 6,
      }),
      'Превышен дневной лимит созданий (6/5).',
    )
    assert.equal(
      shareCreateErrorMessage(429, {
        errorCode: SHARE_CREATE_ERROR_CODE.LINK_ALREADY_ACTIVE,
        reason: 'maxReissuesPerSongPerHour',
        limit: 2,
        actual: 3,
      }),
      'Превышен часовой лимит перевыпусков этой песни (3/2).',
    )
    assert.equal(
      shareCreateErrorMessage(429, { errorCode: SHARE_CREATE_ERROR_CODE.LINK_ALREADY_ACTIVE }),
      'Превышен лимит ссылок',
    )
  })

  test('неизвестный код и пустое тело дают общий текст', () => {
    assert.equal(shareCreateErrorMessage(500, null), 'Не удалось создать ссылку')
    assert.equal(
      shareCreateErrorMessage(500, { errorCode: 'share.internal' }),
      'Не удалось создать ссылку',
    )
    assert.equal(
      shareCreateErrorMessage(400, { errorCode: 'share.tokenMissing' }),
      'Не удалось создать ссылку',
    )
  })
})
