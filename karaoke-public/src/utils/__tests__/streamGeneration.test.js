// BR-356 (аудит #336, пакет PKG-S3-28, OpenProject #400): поколение NDJSON-стрима.
//
// ЧТО БЫЛО. Store коммитил результат того стрима, который завершился последним: стрим
// автора A, завершившийся после старта стрима B, показывал песни A на странице B.
//
// ЧТО СТАЛО. У стрима есть номер поколения; коммитить может только текущее.
//
// Запуск: cd karaoke-public && npm run test:unit

import { describe, test } from 'node:test'
import assert from 'node:assert/strict'

import {
  createGenerationCounter,
  nextGeneration,
  isCurrentGeneration,
  invalidateGeneration,
} from '../streamGeneration.js'
import { readCode } from './sourceContract.js'

describe('BR-356: поколение стрима', () => {
  test('каждый запуск получает новое поколение', () => {
    const counter = createGenerationCounter()
    const first = nextGeneration(counter)
    const second = nextGeneration(counter)
    assert.equal(first, 1)
    assert.equal(second, 2)
    assert.equal(isCurrentGeneration(counter, second), true)
    assert.equal(isCurrentGeneration(counter, first), false)
  })

  test('отмена инвалидирует текущий стрим', () => {
    const counter = createGenerationCounter()
    const generation = nextGeneration(counter)
    invalidateGeneration(counter)
    assert.equal(isCurrentGeneration(counter, generation), false)
  })

  test('без счётчика — ничего не актуально', () => {
    assert.equal(isCurrentGeneration(null, 1), false)
    assert.equal(nextGeneration(null), 0)
  })
})

describe('BR-356: контракт исходников (падает на старом коде)', () => {
  test('store учитывает поколение стрима перед коммитом', () => {
    const src = readCode('src/store/modules/zakroma.js')
    assert.ok(src.includes('nextGeneration'), 'нужен счётчик поколений')
    assert.ok(src.includes('isCurrentGeneration'), 'нужна проверка «это ещё актуальный стрим»')
  })
})
