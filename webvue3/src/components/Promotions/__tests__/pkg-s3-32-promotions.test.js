// Регрессионные тесты пакета PKG-S3-32 (аудит #336, OpenProject #404) — акции (промо).
//
// Запись пакета в этом файле:
//   BR-406 — сохранение/удаление/создание акции при отказе ничего не сообщали;
//            сервер сообщает об отказе ТЕЛОМ (`0L` для create/update, `false` для
//            delete), а HTTP-код был 200 — «правило не найдено» выглядело как успех.
//
// Запуск: cd webvue3 && node --test src/components/Promotions/__tests__/pkg-s3-32-promotions.test.js

import { test, describe, beforeEach } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

/**
 * Ограничивает ожидание промиса.
 *
 * На СТАРОМ коде некоторые экшены не возвращают промис (или возвращают промис,
 * который никогда не резолвится: внутри него остаётся неотвеченный запрос). Без
 * ограничения тест зависал бы вместо того, чтобы упасть с AssertionError.
 *
 * @param {Promise<any>} promise — ожидаемый промис.
 * @param {number} ms — предел ожидания, мс.
 * @returns {Promise<any>} результат промиса либо отказ по таймауту.
 */
const withTimeout = (promise, ms = 2000) =>
  Promise.race([
    promise,
    new Promise((_resolve, reject) =>
      setTimeout(() => reject(new Error(`таймаут ожидания промиса (${ms} мс)`)), ms),
    ),
  ])

import promotions from '../store.js'
import { FakeXhr } from '../../../test-utils/fakeXhr.js'

const stripComments = (source) =>
  source
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/(^|[^:])\/\/[^\n]*/g, '$1')

const PROMOTIONS_TABLE = stripComments(
  readFileSync(new URL('../PromotionsTable.vue', import.meta.url), 'utf-8'),
)

/**
 * Минимальный Vuex-контекст: state/commit/dispatch, без зависимости от vuex.
 *
 * @param {Object} module — модуль стора.
 * @returns {{ctx: Object, state: Object}} контекст и state.
 */
function createCtx(module) {
  const state = structuredClone(module.state)
  const ctx = { state, getters: {} }
  ctx.commit = (type, payload) => {
    const mutation = module.mutations[type]
    assert.ok(mutation, `неизвестная мутация ${type}`)
    mutation(state, payload)
  }
  ctx.dispatch = (type, payload) => {
    const action = module.actions[type]
    if (!action) return Promise.resolve(undefined)
    return action(ctx, payload)
  }
  return { ctx, state }
}

describe('PKG-S3-32 / Акции (промо)', () => {
  beforeEach(() => {
    FakeXhr.install()
  })

  describe('BR-406 — отказ сервера не выдаётся за успех', () => {
    test('update с id=0 отклоняется', async () => {
      const { ctx, state } = createCtx(promotions)
      state.promoRulesListTarget = 'local'
      const save = ctx.dispatch('savePromoRule', { id: 7, name: 'x' })
      FakeXhr.last((request) => request.url === '/api/promorules/update').respond(200, '0')
      await assert.rejects(
        withTimeout(save),
        /не найдена/i,
        'BR-406: id=0 — это отказ («правило не найдено»), а не успех',
      )
    })

    test('update с id>0 проходит', async () => {
      const { ctx, state } = createCtx(promotions)
      state.promoRulesListTarget = 'local'
      const save = ctx.dispatch('savePromoRule', { id: 7, name: 'x' })
      FakeXhr.last((request) => request.url === '/api/promorules/update').respond(200, '7')
      // После успешной мутации стор перезагружает список — отвечаем и на него.
      await new Promise((resolve) => setImmediate(resolve))
      FakeXhr.last((request) => request.url === '/api/promorules/list').respond(200, {
        promoRules: [],
      })
      await withTimeout(save)
    })

    test('delete с false отклоняется', async () => {
      const { ctx, state } = createCtx(promotions)
      state.promoRulesListTarget = 'local'
      const remove = ctx.dispatch('deletePromoRule', 7)
      FakeXhr.last((request) => request.url === '/api/promorules/delete').respond(200, 'false')
      await assert.rejects(
        withTimeout(remove),
        /не удалена/i,
        'BR-406: false — это отказ, а не успех',
      )
    })

    test('delete с true проходит', async () => {
      const { ctx, state } = createCtx(promotions)
      state.promoRulesListTarget = 'local'
      const remove = ctx.dispatch('deletePromoRule', 7)
      FakeXhr.last((request) => request.url === '/api/promorules/delete').respond(200, 'true')
      await new Promise((resolve) => setImmediate(resolve))
      FakeXhr.last((request) => request.url === '/api/promorules/list').respond(200, {
        promoRules: [],
      })
      await withTimeout(remove)
    })

    test('create с id=0 отклоняется', async () => {
      const { ctx, state } = createCtx(promotions)
      state.promoRulesListTarget = 'local'
      const create = ctx.dispatch('createPromoRule', { name: 'x', type: 'FLAT_PERCENT' })
      FakeXhr.last((request) => request.url === '/api/promorules/create').respond(200, '0')
      await assert.rejects(withTimeout(create), /не создана/i, 'BR-406: id=0 — отказ')
    })

    test('таблица показывает ошибку мутации', () => {
      assert.match(
        PROMOTIONS_TABLE,
        /mutationError/,
        'BR-406: ошибка мутации должна быть видна в таблице',
      )
      assert.match(
        PROMOTIONS_TABLE,
        /async save\(item\)[\s\S]*catch\s*\(/,
        'BR-406: сохранение должно ловить отказ',
      )
      assert.match(
        PROMOTIONS_TABLE,
        /async remove\(item\)[\s\S]*catch\s*\(/,
        'BR-406: удаление должно ловить отказ',
      )
      assert.match(
        PROMOTIONS_TABLE,
        /async create\(\)[\s\S]*catch\s*\(/,
        'BR-406: создание должно ловить отказ',
      )
    })
  })
})
