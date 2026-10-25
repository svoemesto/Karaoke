// BR-348 (аудит #336, пакет PKG-S3-28, OpenProject #400): таймеры ожидания токена.
//
// ЧТО БЫЛО. `PlayerView.requestToken` заводил `setTimeout(..., 8000)` без сохранения
// хэндла: снять его при размонтировании было нечем, промисы висели, цепочка
// `advanceAfterEnd()` продолжалась после ухода со страницы.
//
// ЧТО СТАЛО. Хэндлы хранятся, при размонтировании таймеры снимаются и все ожидающие
// промисы резолвятся `null`.
//
// Запуск: cd karaoke-public && npm run test:unit

import { describe, test } from 'node:test'
import assert from 'node:assert/strict'

import { clearTokenTimers, pendingTokenCount } from '../tokenTimers.js'
import { readCode } from './sourceContract.js'

describe('BR-348: снятие таймеров ожидания токена', () => {
  test('таймеры снимаются, ожидающие промисы резолвятся null', () => {
    const timers = { 1: 11, 2: 22 }
    const resolved = []
    const pendingToken = {
      1: [(t) => resolved.push(['a', t])],
      2: [(t) => resolved.push(['b', t])],
    }
    const cleared = clearTokenTimers(timers, pendingToken)
    assert.equal(cleared, 2)
    assert.deepEqual(Object.keys(timers), [])
    assert.deepEqual(resolved, [
      ['a', null],
      ['b', null],
    ])
    assert.equal(pendingTokenCount(pendingToken), 0)
  })

  test('исключение в резолве не мешает размонтированию', () => {
    const pendingToken = {
      1: [
        () => {
          throw new Error('boom')
        },
      ],
    }
    assert.doesNotThrow(() => clearTokenTimers({}, pendingToken))
  })

  test('пустые карты — no-op', () => {
    assert.equal(clearTokenTimers(null, null), 0)
    assert.equal(clearTokenTimers({}, {}), 0)
    assert.equal(pendingTokenCount(null), 0)
  })

  test('таймер без хэндла не считается снятым, но карта чистится', () => {
    const timers = { 1: null }
    const cleared = clearTokenTimers(timers, {})
    assert.equal(cleared, 0)
    assert.deepEqual(Object.keys(timers), [])
  })
})

describe('BR-348: контракт исходников (падает на старом коде)', () => {
  test('PlayerView хранит хэндлы таймеров и чистит их при размонтировании', () => {
    const src = readCode('src/views/PlayerView.vue')
    assert.ok(src.includes('tokenTimers'), 'нужна карта хэндлов таймеров')
    assert.ok(src.includes('clearTokenTimers'), 'нужна очистка таймеров при размонтировании')
    assert.ok(src.includes('destroyed'), 'нужен флаг размонтирования')
  })
})
