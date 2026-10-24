// BR-328 (аудит #336, пакет PKG-S2-14, OpenProject #377): регрессионный тест на
// END-маркер при неизвестной длительности трека.
//
// ЧТО БЫЛО. `submit()` гарантировал END по `this.duration`, но duration равен 0 до
// события `decode` WaveSurfer (и навсегда остаётся 0, если стем не загрузился).
// `ensureEndMarker(markers, 0)` создавал END в 0 либо переносил существующий END в 0 —
// для ВСЕХ голосов сразу. Условие submit проверяло только `hasAnyMarkers`, готовность
// аудио не проверялась, поэтому терминальный маркер уезжал в начало трека.
//
// ЧТО ПРОВЕРЯЕТСЯ. Пока длительность неизвестна (0/NaN/отрицательная), маркеры не
// меняются; при известной длительности поведение прежнее.
//
// Запуск: cd karaoke-public && npm run test:unit

import { describe, test } from 'node:test'
import assert from 'node:assert/strict'

import { ensureEndMarker } from '../useKaraokeEditor.js'

const syl = (label, time) => ({
  uid: 's' + time,
  time,
  label,
  color: '#000',
  position: 'top',
  markertype: 'syllables',
})

const endMarker = (time) => ({
  uid: 'end',
  time,
  label: 'END',
  color: '#000080',
  position: 'top',
  markertype: 'setting',
})

describe('BR-328: END не ставится при duration <= 0', () => {
  test('duration = 0 — END НЕ создаётся', () => {
    const markers = [syl('раз', 1)]
    ensureEndMarker(markers, 0)
    assert.equal(
      markers.length,
      1,
      'END в 0 сдвинул бы терминальный маркер в начало трека: ' + JSON.stringify(markers),
    )
    assert.ok(!markers.some((m) => m.label === 'END'))
  })

  test('duration = 0 — существующий END НЕ переносится в 0', () => {
    const markers = [syl('раз', 1), endMarker(12)]
    ensureEndMarker(markers, 0)
    const end = markers.find((m) => m.label === 'END')
    assert.equal(end.time, 12, 'END остаётся там, где его поставил редактор')
  })

  test('duration = NaN/undefined/отрицательная — маркеры не трогаются', () => {
    for (const bad of [NaN, undefined, null, -1]) {
      const markers = [syl('раз', 1)]
      ensureEndMarker(markers, bad)
      assert.equal(markers.length, 1, `duration=${String(bad)} не должен создавать END`)
    }
  })

  test('регресс-контроль: duration > 0 — END создаётся на длительности', () => {
    const markers = [syl('раз', 1)]
    ensureEndMarker(markers, 42.5)
    const end = markers.find((m) => m.label === 'END')
    assert.ok(end, 'END должен появиться при известной длительности')
    assert.equal(end.time, 42.5)
  })

  test('регресс-контроль: duration > 0 — существующий END переносится на длительность', () => {
    const markers = [syl('раз', 1), endMarker(12)]
    ensureEndMarker(markers, 42.5)
    assert.equal(markers.find((m) => m.label === 'END').time, 42.5)
  })
})
