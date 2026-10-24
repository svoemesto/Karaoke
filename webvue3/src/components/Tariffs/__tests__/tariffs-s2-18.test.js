// Регрессии PKG-S2-18 (аудит #336) по тарифам (деньги): BR-461.
//
// Мутации `createTariff`/`saveTariff`/`deleteTariff` не имели `.catch`: при 4xx/5xx
// промис отклонялся, `loadTariffsList` не вызывался, сообщения пользователю не было, а
// таблица продолжала показывать отредактированные значения как сохранённые в прод-БД.
// Плюс `canCreate` пропускал пустую цену (`'' >= 0` истинно) — запрос уходил с
// `priceRub=` и получал 400.
//
// Экшены исполняются по-настоящему через шов FakeXhr; проверки уровня компонента —
// по исходнику SFC (у Vue-компонента нет экспортируемых методов).

import { test, describe } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

import tariffs from '../store.js'
import { isValidTariffPrice } from '../store.js'
import { FakeXhr } from '../../../test-utils/fakeXhr.js'

const TABLE_SOURCE = readFileSync(new URL('../TariffsTable.vue', import.meta.url), 'utf-8')

/**
 * Минимальный Vuex-контекст: state + commit, применяющий настоящие мутации модуля.
 *
 * @param {Object} storeModule
 * @returns {{ctx: Object, state: Object}}
 */
function createCtx(storeModule) {
  const state = structuredClone(storeModule.state)
  const ctx = { state }
  ctx.commit = (type, payload) => {
    const mutation = storeModule.mutations[type]
    if (!mutation) throw new Error(`unknown mutation: ${type}`)
    mutation(state, payload)
  }
  ctx.dispatch = (type, payload) => storeModule.actions[type](ctx, payload)
  return { ctx, state }
}

describe('BR-461: отказ операции с тарифом виден и не выдаётся за успех', () => {
  test('isValidTariffPrice отсекает пустую и отрицательную цену', () => {
    assert.equal(isValidTariffPrice(0), true)
    assert.equal(isValidTariffPrice('2.5'), true)
    assert.equal(isValidTariffPrice(''), false)
    assert.equal(isValidTariffPrice('abc'), false)
    assert.equal(isValidTariffPrice(-1), false)
    assert.equal(isValidTariffPrice(Number.NaN), false)
    assert.equal(isValidTariffPrice(null), false)
  })

  test('createTariff с пустой ценой не шлёт запрос и возвращает false', async () => {
    FakeXhr.install()
    const { ctx, state } = createCtx(tariffs)
    const result = await tariffs.actions.createTariff(ctx, { name: 'X', priceRub: '' })
    assert.equal(result, false)
    assert.equal(FakeXhr.sent.length, 0, 'заведомо битый запрос не должен уходить на сервер')
    assert.ok(state.tariffsError, 'причина отказа должна попасть в state')
  })

  test('saveTariff при 500 возвращает false и фиксирует ошибку', async () => {
    FakeXhr.install()
    const { ctx, state } = createCtx(tariffs)
    const promise = tariffs.actions.saveTariff(ctx, { id: 1, name: 'X', priceRub: 100 })
    FakeXhr.sent[0].respond(500, 'boom')
    assert.equal(await promise, false, 'неуспех должен возвращаться, а не проглатываться')
    assert.ok(state.tariffsError, 'ошибка должна быть в state (её показывает баннер таблицы)')
  })

  test('deleteTariff при сетевой ошибке возвращает false', async () => {
    FakeXhr.install()
    const { ctx } = createCtx(tariffs)
    const promise = tariffs.actions.deleteTariff(ctx, 5)
    FakeXhr.sent[0].status = 0
    FakeXhr.sent[0].onerror()
    assert.equal(await promise, false)
  })

  test('таблица откатывает правку при отказе и сбрасывает форму только на успехе', () => {
    assert.match(
      TABLE_SOURCE,
      /if \(!ok\) \{\s*const original = this\.original\[item\.id\]\s*if \(original\) Object\.assign\(item, original\)/,
      'при отказе editable должен вернуться к подтверждённому снимку',
    )
    assert.match(
      TABLE_SOURCE,
      /const ok = await this\.\$store\.dispatch\('createTariff', this\.newItem\)\s*if \(ok\) \{/,
      'форма нового тарифа сбрасывается только после подтверждённого успеха',
    )
    assert.match(TABLE_SOURCE, /v-if="error"/, 'ошибка операции должна быть видна в таблице')
    assert.match(
      TABLE_SOURCE,
      /isValidTariffPrice\(this\.newItem\.priceRub\)/,
      'canCreate должен проверять цену числом',
    )
  })
})
