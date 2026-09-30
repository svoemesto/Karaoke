// Доказательство, что двоичный поиск ведёт себя РОВНО как прежняя линейная
// реализация из SubsEdit.vue. Линейный эталон переписан здесь независимо и
// используется только в тестах — в коде его нет.

import { test, describe } from 'node:test'
import assert from 'node:assert/strict'

import { findMarkerIndexByTime } from '../markerIndex.js'

/** Прежняя реализация, дословно. */
function linearReference(markers, currentTime) {
  const diff = 0.02
  if (markers.length > 0 && currentTime < markers[0].time - diff) return -1
  for (let i = 0; i < markers.length - 1; i++) {
    const marker = markers[i]
    const nextMarker = markers[i + 1]
    if (currentTime >= marker.time - diff && currentTime < nextMarker.time - diff) {
      return i
    }
  }
  return markers.length - 1
}

const times = (arr) => arr.map((m) => m.time)

describe('findMarkerIndexByTime совпадает с прежней линейной реализацией', () => {
  test('пустой массив', () => {
    assert.equal(findMarkerIndexByTime([], 10), -1)
    assert.equal(linearReference([], 10), -1)
  })

  test('один маркер', () => {
    const m = [{ time: 5 }]
    for (const t of [0, 4.9, 5, 5.02, 5.1, 100]) {
      assert.equal(findMarkerIndexByTime(m, t), linearReference(m, t), `t=${t}`)
    }
  })

  test('крайние позиции', () => {
    const m = [{ time: 1 }, { time: 2 }, { time: 3 }]
    for (const t of [0, 0.98, 0.99, 1, 1.5, 2, 2.99, 3, 3.02, 10]) {
      assert.equal(findMarkerIndexByTime(m, t), linearReference(m, t), `t=${t}`)
    }
  })

  // Главный тест: случайные массивы со случайным playhead, много повторов.
  test('фаззинг: 4000 случайных случаев', () => {
    let checked = 0
    for (let trial = 0; trial < 1000; trial++) {
      const n = 1 + Math.floor(Math.random() * 200)
      const step = Math.random() * 0.5
      const m = []
      let t = Math.random() * 5
      for (let i = 0; i < n; i++) {
        t += step * Math.random() * 3 // неубывает, как реальные маркеры
        m.push({ time: t })
      }
      for (let k = 0; k < 4; k++) {
        const cur = Math.random() * (t + 3)
        assert.equal(findMarkerIndexByTime(m, cur), linearReference(m, cur), `n=${n} t=${cur}`)
        checked++
      }
    }
    assert.equal(checked, 4000)
  })

  // Одинаковые времена: реально бывает у соседних слогов.
  test('маркеры с одинаковым временем', () => {
    const m = [{ time: 1 }, { time: 1 }, { time: 1 }, { time: 2 }]
    for (const t of [0.5, 0.98, 1, 1.02, 1.5, 2, 5]) {
      assert.equal(findMarkerIndexByTime(m, t), linearReference(m, t), `t=${t}`)
    }
  })

  test('совпадает с эталоном на реальной форме маркеров песни', () => {
    // 2170 маркеров, как в песне 11718, равномерно от 0 до 604 с.
    const m = []
    for (let i = 0; i < 2170; i++) m.push({ time: (i * 604) / 2170 })
    for (let i = 0; i < 500; i++) {
      const cur = Math.random() * 610
      assert.equal(findMarkerIndexByTime(m, cur), linearReference(m, cur), `t=${cur}`)
    }
  })
})

describe('границы допуска diff = 0.02', () => {
  test('ровно на границе', () => {
    const m = [{ time: 10 }, { time: 20 }]
    // currentTime = 9.98 -> ровно markers[0].time - 0.02, первый маркер уже «пройден»
    assert.equal(findMarkerIndexByTime(m, 9.98), linearReference(m, 9.98))
    assert.equal(findMarkerIndexByTime(m, 9.97), linearReference(m, 9.97))
    // currentTime = 19.98 -> ровно markers[1].time - 0.02
    assert.equal(findMarkerIndexByTime(m, 19.98), linearReference(m, 19.98))
  })

  test('допуск применяется к ОБЕИМ границам интервала', () => {
    const m = [{ time: 10 }, { time: 10.01 }, { time: 20 }]
    for (let t = 9.9; t < 10.2; t += 0.005) {
      assert.equal(findMarkerIndexByTime(m, t), linearReference(m, t), `t=${t.toFixed(3)}`)
    }
  })
})

describe('свойства ответа', () => {
  test('индекс всегда в допустимом диапазоне', () => {
    const m = []
    for (let i = 0; i < 50; i++) m.push({ time: i * 0.3 })
    for (let t = -1; t < 20; t += 0.05) {
      const idx = findMarkerIndexByTime(m, t)
      assert.ok(idx === -1 || (idx >= 0 && idx < m.length), `t=${t} idx=${idx}`)
    }
  })

  test('индекс не убывает вместе со временем', () => {
    const m = []
    for (let i = 0; i < 50; i++) m.push({ time: i * 0.3 })
    let prev = -2
    for (let t = 0; t < 16; t += 0.05) {
      const idx = findMarkerIndexByTime(m, t)
      assert.ok(idx >= prev, `индекс уменьшился на t=${t}: ${prev} -> ${idx}`)
      prev = idx
    }
  })

  test('на маркере возвращается именно этот маркер', () => {
    const m = []
    for (let i = 0; i < 50; i++) m.push({ time: i * 0.3 })
    times(m).forEach((t, i) => {
      assert.equal(findMarkerIndexByTime(m, t), i, `маркер ${i} на t=${t}`)
    })
  })
})
