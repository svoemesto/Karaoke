// BR-349 (аудит #336, пакет PKG-S3-28, OpenProject #400): доверие postMessage.
//
// ЧТО БЫЛО. `PlayerView.onParentMessage` проверял только строковый маркер
// `e.data.source === 'kp-playlist'` — константа из бандла, а не барьер. Сторонняя
// страница могла встроить плеер и управлять сессией.
//
// ЧТО СТАЛО. Проверяются origin (свой сайт) и источник (родительское окно).
//
// Запуск: cd karaoke-public && npm run test:unit

import { describe, test } from 'node:test'
import assert from 'node:assert/strict'

import { isTrustedParentMessage } from '../messageOrigin.js'
import { readCode } from './sourceContract.js'

describe('BR-349: доверие postMessage-сообщению', () => {
  const parent = { name: 'parent-window' }

  test('свой origin + родительское окно — доверяем', () => {
    const event = { origin: 'https://site.ru', source: parent, data: {} }
    assert.equal(isTrustedParentMessage(event, { origin: 'https://site.ru', source: parent }), true)
  })

  test('чужой origin — не доверяем', () => {
    const event = { origin: 'https://evil.example', source: parent, data: {} }
    assert.equal(
      isTrustedParentMessage(event, { origin: 'https://site.ru', source: parent }),
      false,
    )
  })

  test('сообщение не от родительского окна — не доверяем', () => {
    const event = { origin: 'https://site.ru', source: { name: 'other' }, data: {} }
    assert.equal(
      isTrustedParentMessage(event, { origin: 'https://site.ru', source: parent }),
      false,
    )
  })

  test('пустое событие — не доверяем', () => {
    assert.equal(isTrustedParentMessage(null, { origin: 'https://site.ru' }), false)
    assert.equal(isTrustedParentMessage(undefined, {}), false)
  })

  test('без ожидаемого origin/source проверка пропускается (совместимость)', () => {
    assert.equal(isTrustedParentMessage({ origin: 'x', source: 1 }, {}), true)
  })
})

describe('BR-349: контракт исходников (падает на старом коде)', () => {
  test('PlayerView проверяет origin и источник сообщения', () => {
    const src = readCode('src/views/PlayerView.vue')
    assert.ok(src.includes('isTrustedParentMessage'), 'нужна проверка доверия postMessage')
    assert.ok(src.includes('currentOrigin()'), 'нужна сверка origin текущего сайта')
    assert.ok(src.includes('window.parent'), 'нужна сверка источника (window.parent)')
  })

  test('origin берётся из location.origin', () => {
    const src = readCode('src/utils/messageOrigin.js')
    assert.ok(src.includes('window.location.origin'), 'origin — это origin текущего сайта')
  })
})
