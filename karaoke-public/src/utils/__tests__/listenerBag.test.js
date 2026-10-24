// BR-313 (аудит #336, пакет PKG-S2-14, OpenProject #377): регрессионный тест на
// снятие подписок window/document в cleanup().
//
// ЧТО БЫЛО. `useZakromaStreamProgress` навешивал `pagehide` + два `visibilitychange`
// при каждом создании composable, а composable создаётся в store-экшене на КАЖДУЮ
// загрузку автора; `cleanup()` снимал только AbortController, removeEventListener в
// файле не было вовсе. Каждая загрузка автора оставляла три листенера и удерживала
// замыкание с полным списком песен автора (`albums`, 388–2500 объектов).
//
// ЧТО ПРОВЕРЯЕТСЯ. Мешок подписок реально снимает то, что навесил, повторный clear()
// безопасен, и два экземпляра не снимают подписки друг друга.
//
// Запуск: cd karaoke-public && npm run test:unit

import { describe, test } from 'node:test'
import assert from 'node:assert/strict'

import { createListenerBag } from '../listenerBag.js'

// Минимальная замена window/document: считает активные подписки.
function fakeTarget() {
  const handlers = new Map()
  return {
    handlers,
    addEventListener(type, handler) {
      if (!handlers.has(type)) handlers.set(type, new Set())
      handlers.get(type).add(handler)
    },
    removeEventListener(type, handler) {
      if (handlers.has(type)) handlers.get(type).delete(handler)
    },
    count(type) {
      return handlers.has(type) ? handlers.get(type).size : 0
    },
    total() {
      let sum = 0
      for (const set of handlers.values()) sum += set.size
      return sum
    },
  }
}

describe('BR-313: листенеры composable снимаются в cleanup()', () => {
  test('три подписки (pagehide + два visibilitychange) снимаются одним clear()', () => {
    const doc = fakeTarget()
    const win = fakeTarget()
    const bag = createListenerBag()

    bag.add(win, 'pagehide', () => {})
    bag.add(doc, 'visibilitychange', () => {})
    bag.add(doc, 'visibilitychange', () => {})
    assert.equal(bag.size, 3)
    assert.equal(win.total() + doc.total(), 3)

    bag.clear()
    assert.equal(bag.size, 0)
    assert.equal(
      win.total() + doc.total(),
      0,
      'после cleanup не должно остаться ни одного листенера',
    )
  })

  test('повторный clear() безопасен и ничего не «снимает дважды»', () => {
    const doc = fakeTarget()
    const bag = createListenerBag()
    bag.add(doc, 'visibilitychange', () => {})
    bag.clear()
    bag.clear()
    assert.equal(bag.size, 0)
    assert.equal(doc.total(), 0)
  })

  test('две «загрузки автора» подряд не накапливают листенеры', () => {
    const doc = fakeTarget()
    const first = createListenerBag()
    first.add(doc, 'visibilitychange', () => {})
    // вторая загрузка: предыдущий экземпляр обязан быть очищен вызывающим кодом
    first.clear()
    const second = createListenerBag()
    second.add(doc, 'visibilitychange', () => {})
    assert.equal(doc.total(), 1, 'после двух загрузок активна ровно одна подписка, а не две')
    second.clear()
    assert.equal(doc.total(), 0)
  })

  test('clear() не трогает подписки чужого экземпляра', () => {
    const doc = fakeTarget()
    const first = createListenerBag()
    const second = createListenerBag()
    first.add(doc, 'visibilitychange', () => {})
    second.add(doc, 'visibilitychange', () => {})
    first.clear()
    assert.equal(doc.total(), 1, 'подписка второго экземпляра должна остаться')
    second.clear()
    assert.equal(doc.total(), 0)
  })
})
