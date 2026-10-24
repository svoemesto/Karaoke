// Регрессии PKG-S2-18 (аудит #336) по таблице подписок: BR-458.
//
// Таблица пагинировала КЛИЕНТОМ одну серверную страницу: `b-pagination` менял
// `currentPage`, watcher только писал его в store — запрос не уходил, а бэкенд
// отдаёт ровно `pageSize` строк (`drop/take`). Итог: страницы 2+ всегда пусты.
// Размер страницы уходил как `perPage`, тогда как контроллер ждёт `pageSize`
// (Spring неизвестный параметр игнорирует) — настройка «строк на странице» не
// применялась никогда.
//
// Экшен проверяется через шов FakeXhr (реальные параметры запроса), пагинация
// таблицы — по исходнику SFC.

import { test, describe } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

import subscriptions from '../store.js'
import { FakeXhr } from '../../../test-utils/fakeXhr.js'

const TABLE_SOURCE = readFileSync(new URL('../SubscriptionsTable.vue', import.meta.url), 'utf-8')
const FILTER_SOURCE = readFileSync(
  new URL('../SubscriptionsFilterModal.vue', import.meta.url),
  'utf-8',
)

describe('BR-458: пагинация подписок серверная', () => {
  test('page и pageSize уходят в запрос, target — из store', async () => {
    FakeXhr.install()
    const state = structuredClone(subscriptions.state)
    const ctx = { state }
    ctx.commit = (type, payload) => subscriptions.mutations[type](state, payload)
    ctx.dispatch = (type, payload) => subscriptions.actions[type](ctx, payload)

    const promise = subscriptions.actions.loadSubscriptionsDigest(ctx, { page: 3, pageSize: 50 })
    const params = FakeXhr.sent[0].params
    assert.equal(params.page, '3')
    assert.equal(params.pageSize, '50', 'контроллер ждёт pageSize, а не perPage')
    assert.equal(params.target, state.subscriptionsTarget)
    FakeXhr.sent[0].respond(200, { subscriptionsDigest: [], totalCount: 0 })
    await promise
  })

  test('смена страницы перезапрашивает данные, а не режет локально', () => {
    assert.match(
      TABLE_SOURCE,
      /currentPage\(newPage, oldPage\)[\s\S]{0,400}?if \(newPage !== oldPage\) this\.reload\(\)/,
      'смена страницы должна идти за данными на сервер',
    )
    assert.match(
      TABLE_SOURCE,
      /reload\(\) \{\s*\/\/[\s\S]*?pageSize: this\.perPage/,
      'reload должен передавать page и pageSize',
    )
    const table = /<b-table[\s\S]*?>/.exec(TABLE_SOURCE)[0]
    assert.doesNotMatch(table, /:per-page=/, 'b-table не должен резать серверную страницу повторно')
    assert.doesNotMatch(table, /:current-page=/, 'b-table не должен листать локально')
  })

  test('смена БД очищает список до загрузки новой', () => {
    assert.match(
      TABLE_SOURCE,
      /onTargetChange\(\)[\s\S]{0,600}?this\.\$store\.commit\('setSubscriptionsDigest', \[\]\)/,
      'данные прежней БД нельзя показывать под новой меткой',
    )
  })

  test('фильтр отправляет страницу и её размер вместе с параметрами', () => {
    const ok = /ok\(\) \{[\s\S]*?\n    \},/.exec(FILTER_SOURCE)[0]
    assert.match(ok, /params\.page = 1/, 'фильтр применяется к первой странице')
    assert.match(ok, /params\.pageSize = this\.\$store\.getters\.getRowsPerPage\('subscriptions'\)/)
  })
})
