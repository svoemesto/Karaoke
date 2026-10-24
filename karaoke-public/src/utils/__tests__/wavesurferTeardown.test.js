// BR-329 (аудит #336, пакет PKG-S2-14, OpenProject #377): регрессионный тест на
// уничтожение предыдущего экземпляра WaveSurfer и защиту load() от повторного входа.
//
// ЧТО БЫЛО. `load()` не идемпотентен: `initWaveSurfer()` всегда создавал новый
// WaveSurfer и перезаписывал `this.ws`, не уничтожая предыдущий. `load()` вызывается
// повторно из `recall()` при любом не-200. Старый экземпляр оставался с аудиоэлементом,
// AudioContext, обработчиками timeupdate и регионами, ссылка терялась, а
// `beforeUnmount` уничтожал только последний.
//
// Запуск: cd karaoke-public && npm run test:unit

import { describe, test } from 'node:test'
import assert from 'node:assert/strict'

import { destroyWaveform, beginWaveformLoad, endWaveformLoad } from '../wavesurferTeardown.js'

function fakeWaveSurfer() {
  return {
    destroyed: 0,
    destroy() {
      this.destroyed++
    },
  }
}

describe('BR-329: старый WaveSurfer уничтожается до создания нового', () => {
  test('destroyWaveform уничтожает экземпляр и обнуляет ссылки', () => {
    const ws = fakeWaveSurfer()
    const owner = { ws, wsRegions: { id: 'regions' } }
    assert.equal(destroyWaveform(owner), true)
    assert.equal(ws.destroyed, 1)
    assert.equal(owner.ws, null)
    assert.equal(owner.wsRegions, null)
  })

  test('повторный вызов безопасен: второй destroy не вызывается и не бросает', () => {
    const ws = fakeWaveSurfer()
    const owner = { ws, wsRegions: {} }
    destroyWaveform(owner)
    assert.equal(destroyWaveform(owner), false)
    assert.equal(ws.destroyed, 1)
  })

  test('destroy() бросает (контейнер уже вне DOM) — ссылки всё равно обнуляются', () => {
    const owner = {
      ws: {
        destroy() {
          throw new Error('container removed')
        },
      },
      wsRegions: {},
    }
    assert.equal(destroyWaveform(owner), true)
    assert.equal(owner.ws, null)
    assert.equal(owner.wsRegions, null)
  })

  test('beginWaveformLoad не пускает второй параллельный load()', () => {
    const owner = {}
    assert.equal(beginWaveformLoad(owner), true)
    assert.equal(beginWaveformLoad(owner), false, 'второй вход создал бы второй экземпляр')
    endWaveformLoad(owner)
    assert.equal(beginWaveformLoad(owner), true, 'после finally замок снимается')
  })
})
