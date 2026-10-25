// BR-358 / BR-359 (аудит #336, пакет PKG-S3-28, OpenProject #400): состояния лендинга
// share-ссылки и lease.
//
// ЧТО БЫЛО. Все ответы claim сводились к «ссылка мертва» (notfound), а истёкший lease
// (90 с) приводил к 404 в плеере.
//
// ЧТО СТАЛО. `notfound` — только для доменных кодов «ссылки нет»; 5xx/сеть — отдельное
// состояние `error` с повтором; перед плеером lease проверяется и при необходимости
// claim повторяется.
//
// Запуск: cd karaoke-public && npm run test:unit

import { describe, test } from 'node:test'
import assert from 'node:assert/strict'

import {
  SHARE_LEASE,
  SHARE_STATE,
  shareClaimState,
  shareErrorText,
  shareLeaseState,
  needsReclaim,
} from '../shareClaim.js'
import { readCode } from './sourceContract.js'

describe('BR-358: состояние claim', () => {
  test('200 — готовы', () => {
    assert.equal(shareClaimState(200, { sessionTokenHash: 'x' }), SHARE_STATE.READY)
  })

  test('доменные коды разведены', () => {
    assert.equal(
      shareClaimState(409, { errorCode: 'share.concurrentLimit' }),
      SHARE_STATE.CONCURRENT,
    )
    assert.equal(shareClaimState(429, { errorCode: 'share.rateLimited' }), SHARE_STATE.RATE_LIMITED)
  })

  test('«ссылки нет» — только для notFound/tokenMissing', () => {
    assert.equal(shareClaimState(404, { errorCode: 'share.notFound' }), SHARE_STATE.NOT_FOUND)
    assert.equal(shareClaimState(400, { errorCode: 'share.tokenMissing' }), SHARE_STATE.NOT_FOUND)
  })

  test('500 share.internal — это ошибка сервиса, а не «ссылка мертва»', () => {
    assert.equal(shareClaimState(500, { errorCode: 'share.internal' }), SHARE_STATE.ERROR)
    assert.equal(shareClaimState(503, null), SHARE_STATE.ERROR)
    assert.equal(shareClaimState(500, {}), SHARE_STATE.ERROR)
  })

  test('у ошибки есть свой текст, отличный от текста «ссылка мертва»', () => {
    assert.notEqual(shareErrorText(SHARE_STATE.ERROR), shareErrorText(SHARE_STATE.NOT_FOUND))
    assert.ok(shareErrorText(SHARE_STATE.ERROR).length > 0)
    assert.equal(shareErrorText(SHARE_STATE.READY), '')
  })
})

describe('BR-359: lease сессии', () => {
  const now = 1_700_000_000_000

  test('живой lease — валиден', () => {
    assert.equal(shareLeaseState(now + 60_000, now), SHARE_LEASE.VALID)
    assert.equal(needsReclaim(now + 60_000, now), false)
  })

  test('истёкший lease — истёк, нужен повторный claim', () => {
    assert.equal(shareLeaseState(now - 1, now), SHARE_LEASE.EXPIRED)
    assert.equal(needsReclaim(now - 1, now), true)
  })

  test('неизвестный lease (нет expiresAt) — нужен повторный claim', () => {
    assert.equal(shareLeaseState(0, now), SHARE_LEASE.UNKNOWN)
    assert.equal(needsReclaim(0, now), true)
  })

  test('почти истёкший lease — тоже переclaim (запас)', () => {
    assert.equal(needsReclaim(now + 1000, now, 5000), true)
  })
})

describe('BR-358/BR-359: контракт исходников (падает на старом коде)', () => {
  test('ShareView разводит notfound и error и проверяет lease', () => {
    const src = readCode('src/views/ShareView.vue')
    assert.ok(src.includes('shareClaimState'), 'нужна классификация ответа claim')
    assert.ok(src.includes('needsReclaim'), 'нужна проверка lease перед переходом в плеер')
    assert.ok(!/else state\.value = 'notfound'/.test(src), '«всё остальное → notfound» ушёл')
  })
})
