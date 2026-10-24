// Регрессионный тест на очередь автосохранения карточки песни (BR-088, аудит #336,
// пакет PKG-S2-17, OpenProject #380).
//
// ДО ПРАВКИ в `SongEdit.executeSave` стояло `if (this.isSaving) return`: вызов
// сохранения во время идущего запроса ПРОСТО ОТБРАСЫВАЛСЯ. Поэтому принудительный flush
// перед сменой песни или размонтированием карточки мог не выполниться: правка,
// сделанная менее секунды назад, оставалась только в памяти вкладки, а локальная копия
// песни в `songPages` выглядела сохранённой — после F5 правка исчезала.
//
// Тест фиксирует: вызов во время запроса НЕ теряется, выполняется после текущего, а
// `whenIdle()` дожидается и его (именно на это опирается flush перед навигацией).
//
// Запуск: cd webvue3 && node --test src/lib/__tests__/autosave-queue.test.js

import { test, describe } from 'node:test'
import assert from 'node:assert/strict'

import { createAutosaveQueue } from '../autosave-queue.js'

function deferred() {
  let resolve
  let reject
  const promise = new Promise((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

const tick = () => new Promise((resolve) => setImmediate(resolve))

describe('очередь автосохранения: вызов во время запроса не теряется (BR-088)', () => {
  test('второй вызов выполняется после первого, а не отбрасывается', async () => {
    const queue = createAutosaveQueue()
    const first = deferred()
    const calls = []

    const p1 = queue.run(() => {
      calls.push('first')
      return first.promise
    })
    // Вызов приходит, пока первый запрос в полёте (в старом коде он исчезал).
    const p2 = queue.run(() => {
      calls.push('second')
      return Promise.resolve()
    })

    assert.deepEqual(calls, ['first'], 'второй запрос не должен стартовать параллельно')
    first.resolve('ok')
    await p1
    await queue.whenIdle()
    await p2

    assert.deepEqual(calls, ['first', 'second'], 'второй вызов обязан выполниться')
    assert.equal(queue.isRunning(), false)
  })

  test('whenIdle дожидается и текущей, и поставленной в очередь задачи', async () => {
    const queue = createAutosaveQueue()
    const first = deferred()
    const done = []

    queue.run(() => first.promise.then(() => done.push('first')))
    queue.run(() => Promise.resolve().then(() => done.push('second')))

    const idle = queue.whenIdle()
    first.resolve()
    await idle

    assert.deepEqual(
      done,
      ['first', 'second'],
      'flush перед навигацией должен видеть оба сохранения',
    )
  })

  test('ошибка задачи не блокирует очередь', async () => {
    const queue = createAutosaveQueue()
    const calls = []

    const p1 = queue.run(() => Promise.reject(new Error('HTTP 500')))
    await assert.rejects(() => p1, /HTTP 500/)

    queue.run(() => {
      calls.push('after-error')
      return Promise.resolve()
    })
    await queue.whenIdle()
    assert.deepEqual(calls, ['after-error'], 'после сбоя очередь обязана продолжить работу')
  })

  test('одна и та же задача не выполняется дважды подряд без ожидания', async () => {
    const queue = createAutosaveQueue()
    const first = deferred()
    let count = 0

    queue.run(() => {
      count += 1
      return first.promise
    })
    queue.run(() => {
      count += 1
      return Promise.resolve()
    })
    await tick()
    assert.equal(count, 1)
    first.resolve()
    await queue.whenIdle()
    assert.equal(count, 2)
  })
})
